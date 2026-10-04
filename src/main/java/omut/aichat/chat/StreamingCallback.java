package omut.aichat.chat;

/**
 * Callback for streaming LLM responses.
 * <p>
 * Implementations must be thread-safe: callbacks are invoked
 * from the LLM worker thread, not the JavaFX Application Thread.
 */
public interface StreamingCallback {

    /**
     * Called for each chunk of text received from the model.
     *
     * @param chunk    the new piece of text (may be empty)
     * @param fullText the entire accumulated text so far
     */
    void onToken(String chunk, String fullText);

    /**
     * Called once when the stream completes successfully.
     */
    void onComplete(LlmResponse response);

    /**
     * Called if the stream fails or is cancelled.
     * The underlying transport may still deliver further chunks after
     * this point; implementations must guard against them (e.g. by
     * invalidating a request token on cancel).
     */
    void onError(Throwable error);
}