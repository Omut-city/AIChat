package omut.aichat.chat;

import omut.aichat.service.LlmService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatSession {

    private final LlmService llmService;
    private final List<ChatMessage> history = new ArrayList<>();
    private final List<ChatListener> listeners = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "chat-worker"));

    public ChatSession(LlmService llmService) {
        this.llmService = llmService;
    }

    public void addListener(ChatListener listener) {
        listeners.add(listener);
    }

    public List<ChatMessage> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public void checkAvailability() {
        executor.submit(() -> notifyStatus(llmService.isAvailable()));
    }

    public void send(String userText) {
        if (userText == null || userText.isBlank()) return;

        ChatMessage userMessage = ChatMessage.user(userText.trim());
        history.add(userMessage);
        notifyMessage(userMessage);
        notifyThinkingStarted();

        executor.submit(() -> {
            try {
                String replyText = llmService.ask(userText);
                ChatMessage reply = ChatMessage.assistant(replyText);
                history.add(reply);
                notifyMessage(reply);
            } catch (Exception e) {
                ChatMessage error = ChatMessage.system(friendlyError(e));
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
            notifyMessage(ChatMessage.system("Invalid model name."));
            return;
        }
        executor.submit(() -> {
            try {
                llmService.switchModel(modelName);
                notifyModelChanged(modelName);
            } catch (Exception e) {
                notifyMessage(ChatMessage.system("Failed to switch model: " + e.getMessage()));
            }
        });
    }

    public String currentModel() {
        return llmService.currentModel();
    }

    public void clear() {
        history.clear();
        notifyCleared();
        ChatMessage msg = ChatMessage.system("Chat cleared.");
        history.add(msg);
        notifyMessage(msg);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void notifyMessage(ChatMessage m) {
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