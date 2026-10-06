package omut.aichat.chat;

import java.util.List;

public interface ChatListener {
    void onMessage(AIChatMessage message);
    void onHistoryChanged();
    void onThinkingStarted();
    void onThinkingFinished();
    void onStatusChanged(boolean available);
    void onCleared();
    void onModelsLoaded(List<String> models);
    void onModelChanged(String modelName);
    void onResponseTime(long millis);
    void onRequestCancelled();
    void onTokensPerSecond(double tps);
    void onToken(String chunk, String fullText);
}
