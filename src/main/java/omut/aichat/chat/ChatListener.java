package omut.aichat.chat;

import java.util.List;

public interface ChatListener {
    void onMessage(AIChatMessage message);
    void onThinkingStarted();
    void onThinkingFinished();
    void onStatusChanged(boolean available);
    void onCleared();
    void onModelsLoaded(List<String> models);
    void onModelChanged(String modelName);

}
