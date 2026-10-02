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

    private static final String SYSTEM_PROMPT =
            "Ты — локальный офлайн-ассистент. Отвечай кратко, по делу, на русском языке. "
                    + "Не выдумывай факты о себе.";

    public ChatSession(LlmService llmService) {
        this.llmService = llmService;
        history.add(AIChatMessage.system(SYSTEM_PROMPT));
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
            try {
                String replyText = llmService.ask(new ArrayList<>(history));
                AIChatMessage reply = AIChatMessage.assistant(replyText);
                history.add(reply);
                notifyMessage(reply);
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

    public void clear() {
        history.clear();
        history.add(AIChatMessage.system(SYSTEM_PROMPT));
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

    private void notifyCleared() {
        listeners.forEach(ChatListener::onCleared);
    }

    private void notifyModelsLoaded(List<String> models) {
        listeners.forEach(l -> l.onModelsLoaded(models));
    }

    private void notifyModelChanged(String modelName) {
        listeners.forEach(l -> l.onModelChanged(modelName));
    }
}