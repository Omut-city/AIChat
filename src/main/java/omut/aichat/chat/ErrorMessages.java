package omut.aichat.chat;

import static omut.aichat.utils.Throwables.rootCause;

/**
 * Turns transport and provider exceptions into short messages
 * suitable for display in the transcript.
 * <p>
 * The mapping is deliberately small: the three cases users actually
 * hit are "Ollama is not running", "the request timed out", and
 * "that model is not pulled". Everything else falls through to the
 * deepest cause's message, or the exception class name if the cause
 * has no message.
 */
public final class ErrorMessages {

    private ErrorMessages() {}

    /**
     * Returns a one-line explanation of {@code ex} for the user.
     * Never returns {@code null}.
     */
    public static String humanize(Throwable ex) {
        if (ex == null) return "Unknown error.";

        Throwable cause = rootCause(ex);

        if (cause instanceof java.net.ConnectException
                || cause instanceof java.net.NoRouteToHostException) {
            return "Cannot connect to Ollama. Is it running?";
        }
        if (cause instanceof java.net.SocketTimeoutException
                || cause instanceof java.util.concurrent.TimeoutException) {
            return "Request timed out. The model may be loading, try again.";
        }

        String msg = cause.getMessage();
        if (msg == null) return cause.getClass().getSimpleName();

        if (msg.contains("model") && msg.contains("not found")) {
            return "Model not found. Check that the model is pulled via 'ollama list'.";
        }
        return msg;
    }
}