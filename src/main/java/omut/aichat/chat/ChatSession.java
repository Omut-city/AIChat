package omut.aichat.chat;

import omut.aichat.config.AppConfig;
import omut.aichat.service.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class ChatSession {

    private static final Logger log = LoggerFactory.getLogger(ChatSession.class);

    private final AtomicReference<Future<?>> currentRequest = new AtomicReference<>();
    private final AtomicReference<Object> requestToken = new AtomicReference<>();
    private final LlmService llmService;
    private final AppConfig config;
    private final ChatDispatcher dispatcher = new ChatDispatcher();
    private final HistoryEditor editor = new HistoryEditor();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "chat-worker"));

    public ChatSession(
            LlmService llmService,
            AppConfig config
    ) {
        this.llmService = llmService;
        this.config = config;
        String prompt = config.systemPrompt();
        if (!prompt.isBlank()) {
            editor.append(AIChatMessage.system(prompt));
        }
    }

    public void addListener(ChatListener listener) {
        dispatcher.add(listener);
    }

    public List<AIChatMessage> getHistory() {
        return editor.snapshot();
    }

    public void checkAvailability() {
        submit(() -> dispatcher.status(llmService.isAvailable()));
    }

    public void send(String userText) {
        if (userText == null || userText.isBlank()) return;
        if (executor.isShutdown()) return;
        if (requestToken.get() != null) return;

        AIChatMessage userMessage = AIChatMessage.user(userText.trim());

        Object token = new Object();
        requestToken.set(token);

        editor.append(userMessage);
        dispatcher.message(userMessage);
        dispatcher.thinkingStarted();

        submitStreamingRequest(token);
    }

    private void submitStreamingRequest(Object token) {
        try {
            Future<?> future = executor.submit(() -> {
                long start = System.nanoTime();
                try {
                    List<AIChatMessage> requestHistory =
                            RequestHistoryBuilder.trim(editor.snapshot(), config.historyMaxMessages());
                    llmService.askStreaming(requestHistory, new StreamingCallback() {

                        @Override
                        public void onToken(String chunk, String fullText) {
                            if (requestToken.get() != token) return;
                            dispatcher.token(chunk, fullText);
                        }

                        @Override
                        public void onComplete(LlmResponse response) {
                            if (requestToken.get() != token) return;
                            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
                            AIChatMessage assistantMessage = AIChatMessage.assistant(
                                    response.text(), response.durationMillis(), currentModel());
                            editor.append(assistantMessage);
                            dispatcher.message(assistantMessage);
                            dispatcher.responseTime(elapsedMillis);
                            dispatcher.tokensPerSecond(response.tokensPerSecond());
                        }

                        @Override
                        public void onError(Throwable error) {
                            if (requestToken.get() != token) return;
                            AIChatMessage errorMsg = AIChatMessage.system(ErrorMessages.humanize(error));
                            editor.append(errorMsg);
                            dispatcher.message(errorMsg);
                        }
                    });

                } catch (Exception e) {
                    if (requestToken.get() != token) return;
                    AIChatMessage error = AIChatMessage.system(ErrorMessages.humanize(e));
                    editor.append(error);
                    dispatcher.message(error);
                } finally {
                    if (requestToken.compareAndSet(token, null)) {
                        currentRequest.set(null);
                        dispatcher.thinkingFinished();
                    }
                }
            });
            currentRequest.set(future);
        } catch (RejectedExecutionException e) {
            requestToken.set(null);
            dispatcher.thinkingFinished();
        }
    }

    public void regenerateLast() {
        if (executor.isShutdown()) return;
        if (requestToken.get() != null) return;

        int lastUserIndex = editor.lastUserIndex();

        if (lastUserIndex < 0) {
            dispatcher.notice("Nothing to regenerate.", NoticeLevel.INFO);
            return;
        }

        editor.cutAfter(lastUserIndex);
        dispatcher.historyChanged();

        Object token = new Object();
        requestToken.set(token);
        dispatcher.thinkingStarted();
        submitStreamingRequest(token);
    }

    public void deleteFrom(String messageId) {
        if (messageId == null || messageId.isBlank()) return;
        if (executor.isShutdown()) return;
        if (requestToken.get() != null) return;

        int idx = editor.indexOf(messageId);
        if (idx < 0) return;
        if (editor.get(idx).role() == AIChatMessage.Role.SYSTEM) return;

        editor.cutFrom(idx);
        dispatcher.historyChanged();
    }

    public void editUserMessage(String messageId, String newText) {
        if (messageId == null || messageId.isBlank()) return;
        if (newText == null || newText.isBlank()) return;
        if (executor.isShutdown()) return;
        if (requestToken.get() != null) return;

        int idx = editor.indexOf(messageId);
        if (idx < 0) return;
        if (editor.get(idx).role() != AIChatMessage.Role.USER) return;

        editor.cutFrom(idx);
        AIChatMessage userMessage = AIChatMessage.user(newText.trim());
        editor.append(userMessage);
        dispatcher.historyChanged();

        Object token = new Object();
        requestToken.set(token);
        dispatcher.thinkingStarted();
        submitStreamingRequest(token);
    }

    public void loadModels() {
        submit(() -> {
            List<String> models = llmService.listModels();
            dispatcher.modelsLoaded(models);
        });
    }

    public void switchModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            dispatcher.notice("Invalid model name.", NoticeLevel.ERROR);
            return;
        }
        submit(() -> {
            try {
                llmService.switchModel(modelName);
                config.setSelectedModel(modelName);
                dispatcher.modelChanged(modelName);
            } catch (Exception e) {
                dispatcher.notice("Failed to switch model: " + e.getMessage(), NoticeLevel.ERROR);
            }
        });
    }

    public String currentModel() {
        return llmService.currentModel();
    }

    public void attachFile(String fileName, String content) {
        if (fileName == null || fileName.isBlank()) return;
        if (content == null || content.isBlank()) {
            dispatcher.notice("Attached file is empty: " + fileName, NoticeLevel.ERROR);
            return;
        }

        int max = config.attachMaxChars();
        if (content.length() > max) {
            dispatcher.notice(
                    "File too large: " + content.length() + " chars, limit is " + max, NoticeLevel.ERROR);
            return;
        }

        submit(() -> {
            String text = "[Attached: " + fileName + "]\n\n" + content;
            AIChatMessage attachment = AIChatMessage.system(text);
            editor.append(attachment);
            dispatcher.notice("Attached: " + fileName, NoticeLevel.SUCCESS);
        });
    }

    public void clear() {
        submit(() -> {
            editor.clear();
            String prompt = config.systemPrompt();
            if (!prompt.isBlank()) {
                editor.append(AIChatMessage.system(prompt));
            }
            dispatcher.cleared();
            dispatcher.notice("Chat cleared.", NoticeLevel.INFO);
        });
    }

    public void shutdown() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                log.warn("chat-worker did not terminate within 2 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void cancelCurrentRequest() {
        Object token = requestToken.get();
        if (token == null) return;

        if (!requestToken.compareAndSet(token, null)) return;

        Future<?> request = currentRequest.getAndSet(null);
        if (request != null && !request.isDone()) {
            request.cancel(true);
        }

        AIChatMessage cancelNote = AIChatMessage.system("Generation cancelled.");
        editor.append(cancelNote);
        dispatcher.message(cancelNote);
        dispatcher.requestCancelled();
        dispatcher.thinkingFinished();
    }

    public void setBaseUrl(String baseUrl) {
        submit(() -> {
            try {
                llmService.setBaseUrl(baseUrl);
                dispatcher.notice("Base URL set to: " + config.getBaseUrl(), NoticeLevel.SUCCESS);
                dispatcher.status(llmService.isAvailable());
            } catch (Exception e) {
                dispatcher.notice("Failed to set base URL: " + e.getMessage(), NoticeLevel.ERROR);
            }
        });
    }

    public void setSystemPrompt(String prompt) {
        submit(() -> {
            try {
                config.setSystemPrompt(prompt);
                String updated = config.systemPrompt();
                boolean hasSystem = !editor.isEmpty()
                        && editor.first().role() == AIChatMessage.Role.SYSTEM;

                if (updated.isBlank()) {
                    if (hasSystem) editor.removeFirst();
                } else {
                    if (hasSystem) {
                        editor.set(0, AIChatMessage.system(updated));
                    } else {
                        editor.prepend(AIChatMessage.system(updated));
                    }
                }
                dispatcher.notice("System prompt updated.", NoticeLevel.SUCCESS);
            } catch (Exception e) {
                dispatcher.notice("Failed to update system prompt: " + e.getMessage(), NoticeLevel.ERROR);
            }
        });
    }

    private void submit(Runnable task) {
        if (executor.isShutdown()) return;
        try {
            executor.submit(task);
        } catch (RejectedExecutionException e) {
            log.warn("Task rejected — executor is shutting down");
        }
    }
}