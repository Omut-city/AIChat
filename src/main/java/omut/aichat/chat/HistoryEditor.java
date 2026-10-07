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
 * Not thread-safe on its own — the same rule that governs the
 * session applies: only the JavaFX thread and the single
 * {@code chat-worker} thread touch it, and never concurrently.
 */
final class HistoryEditor {

    private final List<AIChatMessage> history = new ArrayList<>();

    // --- Read ---

    List<AIChatMessage> snapshot() {
        return List.copyOf(history);
    }

    int size() {
        return history.size();
    }

    boolean isEmpty() {
        return history.isEmpty();
    }

    AIChatMessage get(int idx) {
        return history.get(idx);
    }

    AIChatMessage first() {
        return history.getFirst();
    }

    int indexOf(String id) {
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i).id().equals(id)) return i;
        }
        return -1;
    }

    /** Index of the most recent USER message, or -1 if there is none. */
    int lastUserIndex() {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).role() == AIChatMessage.Role.USER) return i;
        }
        return -1;
    }

    // --- Write ---

    void append(AIChatMessage message) {
        history.add(message);
    }

    void prepend(AIChatMessage message) {
        history.addFirst(message);
    }

    void set(int idx, AIChatMessage message) {
        history.set(idx, message);
    }

    void removeFirst() {
        history.removeFirst();
    }

    /** Removes the message at {@code idx} and everything after it. */
    void cutFrom(int idx) {
        history.subList(idx, history.size()).clear();
    }

    /** Keeps the message at {@code idx}, removes everything after it. */
    void cutAfter(int idx) {
        history.subList(idx + 1, history.size()).clear();
    }

    void clear() {
        history.clear();
    }
}