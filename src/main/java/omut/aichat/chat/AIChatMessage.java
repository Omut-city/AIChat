package omut.aichat.chat;

public record AIChatMessage(Role role, String text) {

    public enum Role { USER, ASSISTANT, SYSTEM }

    public static AIChatMessage user(String text) {
        return new AIChatMessage(Role.USER, text);
    }

    public static AIChatMessage assistant(String text) {
        return new AIChatMessage(Role.ASSISTANT, text);
    }

    public static AIChatMessage system(String text) {
        return new AIChatMessage(Role.SYSTEM, text);
    }
}