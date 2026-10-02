package omut.aichat.chat;

public interface ChatListener {
    void onMessage(ChatMessage message);
    void onThinkingStarted();
    void onThinkingFinished();
    void onStatusChanged(boolean available);
    void onCleared();
}
