package omut.aichat.utils;

/**
 * Exception traversal helpers.
 */
public final class Throwables {

    private Throwables() {}

    /**
     * Walks the cause chain and returns the deepest cause.
     * Stops early if a cycle is detected (some exceptions wrap themselves).
     */
    public static Throwable rootCause(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}