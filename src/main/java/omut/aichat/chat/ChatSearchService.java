package omut.aichat.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Case-insensitive search over the conversation history.
 * <p>
 * Returns every occurrence of the query in every message, in
 * transcript order. Each match carries the message id, its index
 * in the list, and the character offsets inside the message text.
 * <p>
 * Stateless — all methods are static.
 */
public final class ChatSearchService {

    private ChatSearchService() {}

    /** One occurrence of the query inside one message. */
    public record SearchMatch(
            String messageId,
            int messageIndex,
            int startOffset,
            int endOffset
    ) {}

    /**
     * Returns every match of {@code query} in {@code history}, in
     * the order messages appear. Blank or null query returns an
     * empty list.
     */
    public static List<SearchMatch> findAll(
            List<AIChatMessage> history, String query
    ) {
        List<SearchMatch> result = new ArrayList<>();
        if (history == null || history.isEmpty()) return result;
        if (query == null || query.isBlank()) return result;

        String needle = query.toLowerCase(Locale.ROOT);

        for (int i = 0; i < history.size(); i++) {
            AIChatMessage msg = history.get(i);
            String haystack = msg.text().toLowerCase(Locale.ROOT);

            int from = 0;
            while (true) {
                int idx = haystack.indexOf(needle, from);
                if (idx < 0) break;
                result.add(new SearchMatch(
                        msg.id(),
                        i,
                        idx,
                        idx + needle.length()
                ));
                from = idx + needle.length();
            }
        }
        return result;
    }
}