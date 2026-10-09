package omut.aichat.chat;

/**
 * The subset of {@link ChatSession} commands that the JavaScript
 * bridge is allowed to invoke.
 * <p>
 * Exists so {@code ChatJsBridge} can depend on a narrow contract
 * instead of the full session, and so tests can exercise the bridge
 * with a mock without standing up an executor, a dispatcher and a
 * mocked {@code LlmService}.
 * <p>
 * Every method here is called on the JavaFX Application Thread, from
 * a WebView event handler. Implementations may hand work off to a
 * background thread, but must not block the calling thread.
 */
public interface ChatCommands {

    /** Deletes the message with the given id and everything after it. */
    void deleteFrom(String messageId);

    /** Replaces the USER message with the given id and re-sends it. */
    void editUserMessage(String messageId, String newText);
}