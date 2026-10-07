package omut.aichat.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import omut.aichat.chat.AIChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Serialises the chat history into a JSON payload for {@code chat.js}.
 * <p>
 * The DOM shape of a transcript row lives in JavaScript. Java only
 * hands over data: message id, role, rendered HTML, and whether the
 * row should offer the Edit action. {@code chat.js} decides which
 * element wraps them, which {@code data-*} attributes to set, and how
 * to decorate the result.
 */
final class TranscriptJson {

    private static final Logger log = LoggerFactory.getLogger(TranscriptJson.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TranscriptJson() {}

    /**
     * Serialises the history into a JSON array. Each element is:
     * {@code { "id": "...", "role": "USER|ASSISTANT|SYSTEM",
     *          "html": "<p>...</p>", "editable": true|false }}.
     * <p>
     * On serialisation failure (should not happen — we only put
     * strings and booleans in) returns an empty array so the UI
     * clears rather than crashes.
     */
    static String toJson(List<AIChatMessage> history, MarkdownRenderer markdown) {
        String lastUserId = lastUserId(history);
        ArrayNode array = MAPPER.createArrayNode();

        for (AIChatMessage message : history) {
            ObjectNode node = array.addObject();
            node.put("id", message.id());
            node.put("role", message.role().name());
            node.put("html", markdown.renderToHtml(markdown.formatMessage(message)));
            node.put("editable", message.id().equals(lastUserId));
        }

        try {
            return MAPPER.writeValueAsString(array);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise transcript: {}", e.getMessage());
            return "[]";
        }
    }

    private static String lastUserId(List<AIChatMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).role() == AIChatMessage.Role.USER) {
                return history.get(i).id();
            }
        }
        return null;
    }
}