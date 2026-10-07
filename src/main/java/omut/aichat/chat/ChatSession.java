package omut.aichat.chat;

import omut.aichat.config.AppConfig;
import omut.aichat.service.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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

    /**
     * Conversation history.
     * <p>
     * Access rule: only the JavaFX Application Thread and the single
     * {@code chat-worker} thread touch this list, and never concurrently.
     * The UI enforces this via {@link omut.aichat.ui.ChatView}'s
     * {@code updateControls()}, which disables all input widgets while
     * a request is in flight. Breaking this invariant requires switching
     * to {@link java.util.Collections#synchronizedList(java.util.List)} and wrapping
     * every iteration in {@code synchronized (history)}.
     */
    private final List<AIChatMessage> history = new ArrayList<>();
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
            history.add(AIChatMessage.system(prompt));
        }
    }

    public void addListener(ChatListener listener) {
        dispatcher.add(listener);
    }

    public List<AIChatMessage> getHistory() {
        return List.copyOf(history);
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

        history.add(userMessage);
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
                            RequestHistoryBuilder.trim(history, config.historyMaxMessages());

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
                            history.add(assistantMessage);
                            dispatcher.message(assistantMessage);
                            dispatcher.responseTime(elapsedMillis);
                            dispatcher.tokensPerSecond(response.tokensPerSecond());
                        }

                        @Override
                        public void onError(Throwable error) {
                            if (requestToken.get() != token) return;
                            AIChatMessage errorMsg = AIChatMessage.system(ErrorMessages.humanize(error));
                            history.add(errorMsg);
                            dispatcher.message(errorMsg);
                        }
                    });

                } catch (Exception e) {
                    if (requestToken.get() != token) return;
                    AIChatMessage error = AIChatMessage.system(ErrorMessages.humanize(e));
                    history.add(error);
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

    /**
     * Removes the assistant's reply to the last user message and re-sends
     * the same prompt. Used by the "Regenerate" button.
     * <p>
     * If there is no user message to regenerate, emits a system notice.
     */
    public void regenerateLast() {
        if (executor.isShutdown()) return;
        if (requestToken.get() != null) return; // already in flight

        int lastUserIndex = -1;
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).role() == AIChatMessage.Role.USER) {
                lastUserIndex = i;
                break;
            }
        }

        if (lastUserIndex < 0) {
            dispatcher.message(AIChatMessage.system("Nothing to regenerate."));
            return;
        }

        history.subList(lastUserIndex + 1, history.size()).clear();
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

        int idx = indexOf(messageId);
        if (idx < 0) return;
        if (history.get(idx).role() == AIChatMessage.Role.SYSTEM) return;

        history.subList(idx, history.size()).clear();
        dispatcher.historyChanged();
    }

    /**
     * Replaces a USER message with new text and re-sends the prompt.
     * Everything from that message onward is discarded first, so the old
     * reply (and any later turns) is lost.
     * <p>
     * No-op if the id does not refer to a USER message, if the message is
     * unknown, or if a request is already in flight.
     * <p>
     * Called from the WebView bridge on the FX thread.
     */
    public void editUserMessage(String messageId, String newText) {
        if (messageId == null || messageId.isBlank()) return;
        if (newText == null || newText.isBlank()) return;
        if (executor.isShutdown()) return;
        if (requestToken.get() != null) return;

        int idx = indexOf(messageId);
        if (idx < 0) return;
        if (history.get(idx).role() != AIChatMessage.Role.USER) return;

        history.subList(idx, history.size()).clear();
        AIChatMessage userMessage = AIChatMessage.user(newText.trim());
        history.add(userMessage);
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
            dispatcher.message(AIChatMessage.system("Invalid model name."));
            return;
        }
        submit(() -> {
            try {
                llmService.switchModel(modelName);
                config.setSelectedModel(modelName);
                dispatcher.modelChanged(modelName);
            } catch (Exception e) {
                dispatcher.message(AIChatMessage.system("Failed to switch model: " + e.getMessage()));
            }
        });
    }

    public String currentModel() {
        return llmService.currentModel();
    }

    /**
     * Attaches a file's contents to the conversation as a system message.
     * The model will see it in the next request.
     */
    public void attachFile(String fileName, String content) {
        if (fileName == null || fileName.isBlank()) return;
        if (content == null || content.isBlank()) {
            dispatcher.message(AIChatMessage.system("Attached file is empty: " + fileName));
            return;
        }

        int max = config.attachMaxChars();
        if (content.length() > max) {
            dispatcher.message(AIChatMessage.system(
                    "File too large: " + content.length() + " chars, limit is " + max));
            return;
        }

        submit(() -> {
            String text = "[Attached: " + fileName + "]\n\n" + content;
            AIChatMessage attachment = AIChatMessage.system(text);
            history.add(attachment);
            dispatcher.message(AIChatMessage.system("Attached: " + fileName));
        });
    }

    public void clear() {
        submit(() -> {
            history.clear();
            String prompt = config.systemPrompt();
            if (!prompt.isBlank()) {
                history.add(AIChatMessage.system(prompt));
            }
            dispatcher.cleared();
            dispatcher.message(AIChatMessage.system("Chat cleared."));
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
        history.add(cancelNote);
        dispatcher.message(cancelNote);
        dispatcher.requestCancelled();
        dispatcher.thinkingFinished();
    }

    public void setBaseUrl(String baseUrl) {
        submit(() -> {
            try {
                llmService.setBaseUrl(baseUrl);
                dispatcher.message(AIChatMessage.system("Base URL set to: " + config.getBaseUrl()));
                dispatcher.status(llmService.isAvailable());
            } catch (Exception e) {
                dispatcher.message(AIChatMessage.system("Failed to set base URL: " + e.getMessage()));
            }
        });
    }

    public void setSystemPrompt(String prompt) {
        submit(() -> {
            try {
                config.setSystemPrompt(prompt);
                String updated = config.systemPrompt();
                boolean hasSystem = !history.isEmpty()
                        && history.getFirst().role() == AIChatMessage.Role.SYSTEM;

                if (updated.isBlank()) {
                    if (hasSystem) history.removeFirst();
                } else {
                    if (hasSystem) {
                        history.set(0, AIChatMessage.system(updated));
                    } else {
                        history.addFirst(AIChatMessage.system(updated));
                    }
                }
                dispatcher.message(AIChatMessage.system("System prompt updated."));
            } catch (Exception e) {
                dispatcher.message(AIChatMessage.system("Failed to update system prompt: " + e.getMessage()));
            }
        });
    }


    private int indexOf(String messageId) {
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i).id().equals(messageId)) return i;
        }
        return -1;
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