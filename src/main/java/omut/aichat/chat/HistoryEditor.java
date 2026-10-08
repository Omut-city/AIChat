package omut.aichat.chat;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the conversation's message list and exposes every operation
 * that the session performs on it: append, cut, snapshot, lookups.
 * <p>
 * Wraps a plain {@link ArrayList} so callers never mutate the list
 * directly and never hold a live reference to it. The session
 * decides when to notify listeners; this class only changes state.
 * <p>
 * Thread-safe. Every method is synchronized on {@code this}, so the
 * JavaFX thread (reads, edits, cancels) and the single
 * {@code chat-worker} thread (streaming completions, background
 * commands) may call in concurrently. {@link #snapshot()} returns
 * an immutable copy, so iterating over the result is safe even
 * while the editor is being mutated.
 */
final class HistoryEditor {

    private final List<AIChatMessage> history = new ArrayList<>();

    // --- Read ---

    synchronized List<AIChatMessage> snapshot() {
        return List.copyOf(history);
    }

    synchronized int size() {
        return history.size();
    }

    synchronized boolean isEmpty() {
        return history.isEmpty();
    }

    synchronized AIChatMessage get(int idx) {
        return history.get(idx);
    }

    synchronized AIChatMessage first() {
        return history.getFirst();
    }

    synchronized int indexOf(String id) {
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i).id().equals(id)) return i;
        }
        return -1;
    }

    /** Index of the most recent USER message, or -1 if there is none. */
    synchronized int lastUserIndex() {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).role() == AIChatMessage.Role.USER) return i;
        }
        return -1;
    }

    // --- Write ---

    synchronized void append(AIChatMessage message) {
        history.add(message);
    }

    synchronized void prepend(AIChatMessage message) {
        history.addFirst(message);
    }

    synchronized void set(int idx, AIChatMessage message) {
        history.set(idx, message);
    }

    synchronized void removeFirst() {
        history.removeFirst();
    }

    /** Removes the message at {@code idx} and everything after it. */
    synchronized void cutFrom(int idx) {
        history.subList(idx, history.size()).clear();
    }

    /** Keeps the message at {@code idx}, removes everything after it. */
    synchronized void cutAfter(int idx) {
        history.subList(idx + 1, history.size()).clear();
    }

    synchronized void clear() {
        history.clear();
    }
}