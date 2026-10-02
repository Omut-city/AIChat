package omut.aichat.chat;

import omut.aichat.service.LlmService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatSession {

    private final LlmService llmService;
    private final List<AIChatMessage> history = new ArrayList<>();
    private final List<ChatListener> listeners = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "chat-worker"));

    public ChatSession(LlmService llmService) {
        this.llmService = llmService;
        String prompt = llmService.systemPrompt();
        if (!prompt.isBlank()) {
            history.add(AIChatMessage.system(prompt));
        }
    }

    public void addListener(ChatListener listener) {
        listeners.add(listener);
    }

    public List<AIChatMessage> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public void checkAvailability() {
        executor.submit(() -> notifyStatus(llmService.isAvailable()));
    }

    public void send(String userText) {
        if (userText == null || userText.isBlank()) return;

        AIChatMessage userMessage = AIChatMessage.user(userText.trim());
        history.add(userMessage);
        notifyMessage(userMessage);
        notifyThinkingStarted();

        executor.submit(() -> {
            long start = System.nanoTime();
            try {
                String replyText = llmService.ask(buildRequestHistory());
                long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
                AIChatMessage reply = AIChatMessage.assistant(replyText, elapsedMillis);
                history.add(reply);
                notifyMessage(reply);
                notifyResponseTime(elapsedMillis);
            } catch (Exception e) {
                AIChatMessage error = AIChatMessage.system(friendlyError(e));
                history.add(error);
                notifyMessage(error);
            } finally {
                notifyThinkingFinished();
            }
        });
    }

    public void loadModels() {
        executor.submit(() -> {
            List<String> models = llmService.listModels();
            notifyModelsLoaded(models);
        });
    }

    public void switchModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            notifyMessage(AIChatMessage.system("Invalid model name."));
            return;
        }
        executor.submit(() -> {
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

    public String defaultBaseUrl() {
        return llmService.defaultBaseUrl();
    }

    public void clear() {
        history.clear();
        String prompt = llmService.systemPrompt();
        if (!prompt.isBlank()) {
            history.add(AIChatMessage.system(prompt));
        }
        notifyCleared();
        notifyMessage(AIChatMessage.system("Chat cleared."));
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void notifyMessage(AIChatMessage m) {
        listeners.forEach(l -> l.onMessage(m));
    }

    private void notifyThinkingStarted() {
        listeners.forEach(ChatListener::onThinkingStarted);
    }

    private void notifyThinkingFinished() {
        listeners.forEach(ChatListener::onThinkingFinished);
    }

    private void notifyStatus(boolean available) {
        listeners.forEach(l -> l.onStatusChanged(available));
    }

    private String friendlyError(Throwable ex) {
        if (ex == null) return "Unknown error.";
        String msg = ex.getMessage();
        if (msg == null) return ex.getClass().getSimpleName();

        if (msg.contains("Connection refused") || msg.contains("ConnectException")) {
            return "Cannot connect to Ollama. Is it running?";
        }
        if (msg.contains("model") && msg.contains("not found")) {
            return "Model not found. Check that the model is pulled via 'ollama list'.";
        }
        if (msg.contains("timeout") || msg.contains("Timeout")) {
            return "Request timed out. The model may be loading, try again.";
        }
        return msg;
    }

    public void setBaseUrl(String baseUrl) {
        executor.submit(() -> {
            try {
                llmService.setBaseUrl(baseUrl);
                notifyMessage(AIChatMessage.system("Base URL set to: " + llmService.baseUrl()));
                notifyStatus(llmService.isAvailable());
            } catch (Exception e) {
                notifyMessage(AIChatMessage.system("Failed to set base URL: " + e.getMessage()));
            }
        });
    }

    public void setSystemPrompt(String prompt) {
        executor.submit(() -> {
            try {
                llmService.setSystemPrompt(prompt);
                String updated = llmService.systemPrompt();
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

    public String systemPrompt() {
        return llmService.systemPrompt();
    }

    public String defaultSystemPrompt() {
        return llmService.defaultSystemPrompt();
    }

    public String baseUrl() {
        return llmService.baseUrl();
    }

    private void notifyCleared() {
        listeners.forEach(ChatListener::onCleared);
    }

    private void notifyModelsLoaded(List<String> models) {
        listeners.forEach(l -> l.onModelsLoaded(models));
    }

    private void notifyModelChanged(String modelName) {
        listeners.forEach(l -> l.onModelChanged(modelName));
    }

    private void notifyResponseTime(long millis) {
        listeners.forEach(l -> l.onResponseTime(millis));
    }

    private List<AIChatMessage> buildRequestHistory() {
        int max = llmService.historyMaxMessages();
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
}