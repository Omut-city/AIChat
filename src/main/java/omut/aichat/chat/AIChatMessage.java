package omut.aichat.chat;

public record AIChatMessage(Role role, String text, long durationMillis) {

    public enum Role { USER, ASSISTANT, SYSTEM }

    public static AIChatMessage user(String text) {
        return new AIChatMessage(Role.USER, text, 0L);
    }

    public static AIChatMessage assistant(String text, long durationMillis) {
        return new AIChatMessage(Role.ASSISTANT, text, durationMillis);
    }

    public static AIChatMessage system(String text) {
        return new AIChatMessage(Role.SYSTEM, text, 0L);
    }
}