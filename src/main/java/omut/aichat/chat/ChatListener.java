package omut.aichat.chat;

import java.util.List;

public interface ChatListener {
    void onMessage(ChatMessage message);
    void onThinkingStarted();
    void onThinkingFinished();
    void onStatusChanged(boolean available);
    void onCleared();
    void onModelsLoaded(List<String> models);
    void onModelChanged(String modelName);

}
