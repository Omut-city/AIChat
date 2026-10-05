package omut.aichat.chat;

import omut.aichat.config.AppConfig;
import omut.aichat.service.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static omut.aichat.utils.Throwables.rootCause;

public class ChatSession {

    private static final Logger log = LoggerFactory.getLogger(ChatSession.class);

    private final AtomicReference<Future<?>> currentRequest = new AtomicReference<>();
    private final AtomicReference<Object> requestToken = new AtomicReference<>();
    private final LlmService llmService;
    private final AppConfig config;

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
    private final List<ChatListener> listeners = new CopyOnWriteArrayList<>();
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
        listeners.add(listener);
    }

    public List<AIChatMessage> getHistory() {
        return List.copyOf(history);
    }

    public void checkAvailability() {
        submit(() -> notifyStatus(llmService.isAvailable()));
    }

    public void send(String userText) {
        if (userText == null || userText.isBlank()) return;
        if (executor.isShutdown()) return;

        AIChatMessage userMessage = AIChatMessage.user(userText.trim());

        Object token = new Object();
        requestToken.set(token);

        history.add(userMessage);
        notifyMessage(userMessage);
        notifyThinkingStarted();

        try {
            Future<?> future = executor.submit(() -> {
                long start = System.nanoTime();
                try {
                    llmService.askStreaming(buildRequestHistory(), new StreamingCallback() {

                        @Override
                        public void onToken(String chunk, String fullText) {
                            if (requestToken.get() != token) return;
                            notifyToken(chunk, fullText);
                        }

                        @Override
                        public void onComplete(LlmResponse response) {
                            if (requestToken.get() != token) return;
                            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
                            AIChatMessage assistantMessage = AIChatMessage.assistant(
                                    response.text(), response.durationMillis(), currentModel());
                            history.add(assistantMessage);
                            notifyMessage(assistantMessage);
                            notifyResponseTime(elapsedMillis);
                            notifyTokensPerSecond(response.tokensPerSecond());
                        }

                        @Override
                        public void onError(Throwable error) {
                            if (requestToken.get() != token) return;
                            AIChatMessage errorMsg = AIChatMessage.system(friendlyError(error));
                            history.add(errorMsg);
                            notifyMessage(errorMsg);
                        }
                    });

                } catch (Exception e) {
                    if (requestToken.get() != token) return;
                    AIChatMessage error = AIChatMessage.system(friendlyError(e));
                    history.add(error);
                    notifyMessage(error);
                } finally {
                    if (requestToken.compareAndSet(token, null)) {
                        currentRequest.set(null);
                        notifyThinkingFinished();
                    }
                }
            });
            currentRequest.set(future);
        } catch (RejectedExecutionException e) {
            requestToken.set(null);
            notifyThinkingFinished();
        }
    }

    public void loadModels() {
        submit(() -> {
            List<String> models = llmService.listModels();
            notifyModelsLoaded(models);
        });
    }

    public void switchModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            notifyMessage(AIChatMessage.system("Invalid model name."));
            return;
        }
        submit(() -> {
            try {
                llmService.switchModel(modelName);
                notifyModelChanged(modelName);
            } catch (Exception e) {
                notifyMessage(AIChatMessage.system("Failed to switch model: " + e.getMessage()));
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
            notifyMessage(AIChatMessage.system("Attached file is empty: " + fileName));
            return;
        }

        int max = config.attachMaxChars();
        if (content.length() > max) {
            notifyMessage(AIChatMessage.system(
                    "File too large: " + content.length() + " chars, limit is " + max));
            return;
        }

        submit(() -> {
            String text = "[Attached: " + fileName + "]\n\n" + content;
            AIChatMessage attachment = AIChatMessage.system(text);
            history.add(attachment);
            notifyMessage(AIChatMessage.system("Attached: " + fileName));
        });
    }

    public void clear() {
        submit(() -> {
            history.clear();
            String prompt = config.systemPrompt();
            if (!prompt.isBlank()) {
                history.add(AIChatMessage.system(prompt));
            }
            notifyCleared();
            notifyMessage(AIChatMessage.system("Chat cleared."));
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

        notifyRequestCancelled();
        notifyThinkingFinished();
    }

    public void setBaseUrl(String baseUrl) {
        submit(() -> {
            try {
                llmService.setBaseUrl(baseUrl);
                notifyMessage(AIChatMessage.system("Base URL set to: " + config.getBaseUrl()));
                notifyStatus(llmService.isAvailable());
            } catch (Exception e) {
                notifyMessage(AIChatMessage.system("Failed to set base URL: " + e.getMessage()));
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
                notifyMessage(AIChatMessage.system("System prompt updated."));
            } catch (Exception e) {
                notifyMessage(AIChatMessage.system("Failed to update system prompt: " + e.getMessage()));
            }
        });
    }

    private String friendlyError(Throwable ex) {
        if (ex == null) return "Unknown error.";

        Throwable cause = rootCause(ex);

        if (cause instanceof java.net.ConnectException
                || cause instanceof java.net.NoRouteToHostException) {
            return "Cannot connect to Ollama. Is it running?";
        }
        if (cause instanceof java.net.SocketTimeoutException
                || cause instanceof java.util.concurrent.TimeoutException) {
            return "Request timed out. The model may be loading, try again.";
        }

        String msg = cause.getMessage();
        if (msg == null) return cause.getClass().getSimpleName();

        if (msg.contains("model") && msg.contains("not found")) {
            return "Model not found. Check that the model is pulled via 'ollama list'.";
        }
        return msg;
    }

    private List<AIChatMessage> buildRequestHistory() {
        int max = config.historyMaxMessages();
        int total = history.size();
        if (total <= max) {
            return new ArrayList<>(history);
        }

        List<AIChatMessage> trimmed = new ArrayList<>();

        int startIndex = 0;
        if (!history.isEmpty() && history.getFirst().role() == AIChatMessage.Role.SYSTEM) {
            trimmed.add(history.getFirst());
            startIndex = 1;
        }

        int keep = max - trimmed.size();
        int from = Math.max(startIndex, total - keep);
        trimmed.addAll(history.subList(from, total));

        return trimmed;
    }

    private void submit(Runnable task) {
        if (executor.isShutdown()) return;
        try {
            executor.submit(task);
        } catch (RejectedExecutionException e) {
            log.warn("Task rejected — executor is shutting down");
        }
    }

    private void dispatch(Consumer<ChatListener> action) {
        for (ChatListener l : listeners) {
            try { action.accept(l); }
            catch (Exception e) {
                log.warn("Listener failed", e);
            }
        }
    }

    private void notifyMessage(AIChatMessage m) {
        dispatch(l -> l.onMessage(m));
    }

    private void notifyThinkingStarted() {
        dispatch(ChatListener::onThinkingStarted);
    }

    private void notifyThinkingFinished() {
        dispatch(ChatListener::onThinkingFinished);
    }

    private void notifyStatus(boolean available) {
        dispatch(l -> l.onStatusChanged(available));
    }

    private void notifyCleared() {
        dispatch(ChatListener::onCleared);
    }

    private void notifyModelsLoaded(List<String> models) {
        dispatch(l -> l.onModelsLoaded(models));
    }

    private void notifyModelChanged(String modelName) {
        dispatch(l -> l.onModelChanged(modelName));
    }

    private void notifyResponseTime(long millis) {
        dispatch(l -> l.onResponseTime(millis));
    }

    private void notifyTokensPerSecond(double tps) {
        dispatch(l -> l.onTokensPerSecond(tps));
    }

    private void notifyRequestCancelled() {
        dispatch(ChatListener::onRequestCancelled);
    }

    private void notifyToken(String chunk, String fullText) {
        dispatch(l -> l.onToken(chunk, fullText));
    }

}