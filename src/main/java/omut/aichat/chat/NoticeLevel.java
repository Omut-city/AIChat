package omut.aichat.chat;

/**
 * Severity of a transient notice — a one-line message shown to the
 * user without becoming part of the transcript.
 * <p>
 * The distinction matters because transcript entries (user, assistant,
 * errors raised inside a request) are appended to {@link HistoryEditor}
 * and survive re-renders, while notices are shown once in the status
 * line and vanish.
 */
public enum NoticeLevel {
    INFO,
    SUCCESS,
    ERROR
}