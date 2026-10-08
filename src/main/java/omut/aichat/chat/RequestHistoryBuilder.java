package omut.aichat.chat;

import java.util.ArrayList;
import java.util.List;

/**
 * Trims a conversation to fit within a message-count limit before
 * it is sent to the model.
 * <p>
 * The system prompt, when present, is always kept. The remaining
 * limit is spent on the most recent messages. If the prompt alone
 * fills the limit, no conversation messages are included.
 */
final class RequestHistoryBuilder {

    private RequestHistoryBuilder() {}

    static List<AIChatMessage> trim(List<AIChatMessage> history, int max) {
        int total = history.size();
        if (total <= max) {
            return new ArrayList<>(history);
        }

        List<AIChatMessage> trimmed = new ArrayList<>();

        int startIndex = 0;
        if (!history.isEmpty() && history.getFirst().role() == AIChatMessage.Role.SYSTEM) {
            trimmed.add(history.getFirst());
            startIndex = 1;
        }

        int keep = max - trimmed.size();
        int from = Math.max(startIndex, total - keep);
        trimmed.addAll(history.subList(from, total));

        return trimmed;
    }
}