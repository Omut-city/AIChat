package omut.aichat.chat;

import java.util.UUID;

/**
 * A single message in the chat transcript.
 *
 * @param id             stable identifier, unique within the session;
 *                       never {@code null}
 * @param role           who authored the message
 * @param text           message body
 * @param durationMillis for ASSISTANT — generation time in ms; 0 otherwise
 * @param timestamp      epoch millis when the message was created
 * @param model          for ASSISTANT — the model that produced the reply;
 *                       {@code null} for USER and SYSTEM messages
 */
public record AIChatMessage(
        String id,
        Role role,
        String text,
        long durationMillis,
        long timestamp,
        String model
) {

    public enum Role { USER, ASSISTANT, SYSTEM }

    public static AIChatMessage user(String text) {
        return new AIChatMessage(newId(), Role.USER, text, 0L,
                System.currentTimeMillis(), null);
    }

    public static AIChatMessage assistant(String text, long durationMillis, String model) {
        return new AIChatMessage(newId(), Role.ASSISTANT, text, durationMillis,
                System.currentTimeMillis(), model);
    }

    public static AIChatMessage system(String text) {
        return new AIChatMessage(newId(), Role.SYSTEM, text, 0L,
                System.currentTimeMillis(), null);
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}