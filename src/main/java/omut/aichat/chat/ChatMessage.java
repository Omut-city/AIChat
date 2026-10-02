package omut.aichat.chat;

public record ChatMessage(Role role, String text) {

    public enum Role { USER, ASSISTANT, SYSTEM }

    public static ChatMessage user(String text) {
        return new ChatMessage(Role.USER, text);
    }

    public static ChatMessage assistant(String text) {
        return new ChatMessage(Role.ASSISTANT, text);
    }

    public static ChatMessage system(String text) {
        return new ChatMessage(Role.SYSTEM, text);
    }
}