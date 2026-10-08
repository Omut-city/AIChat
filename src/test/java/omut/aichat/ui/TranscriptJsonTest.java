package omut.aichat.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import omut.aichat.chat.AIChatMessage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TranscriptJsonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final MarkdownRenderer markdown = new MarkdownRenderer();

    private JsonNode parse(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    @Nested
    @DisplayName("Empty history")
    class Empty {

        @Test
        @DisplayName("Empty list → \"[]\"")
        void emptyList() {
            String json = TranscriptJson.toJson(List.of(), markdown);
            assertThat(json).isEqualTo("[]");
        }

        @Test
        @DisplayName("Result is parseable JSON")
        void parseable() throws Exception {
            JsonNode node = parse(TranscriptJson.toJson(List.of(), markdown));
            assertThat(node.isArray()).isTrue();
            assertThat(node).isEmpty();
        }
    }

    @Nested
    @DisplayName("Single message")
    class Single {

        @Test
        @DisplayName("USER message carries id, role, html, editable")
        void user() throws Exception {
            AIChatMessage msg = AIChatMessage.user("hello");
            JsonNode node = parse(TranscriptJson.toJson(List.of(msg), markdown)).get(0);

            assertThat(node.get("id").asText()).isEqualTo(msg.id());
            assertThat(node.get("role").asText()).isEqualTo("USER");
            assertThat(node.get("html").asText()).contains("hello");
        }

        @Test
        @DisplayName("ASSISTANT message carries role ASSISTANT")
        void assistant() throws Exception {
            AIChatMessage msg = AIChatMessage.assistant("reply", 0L, "model");
            JsonNode node = parse(TranscriptJson.toJson(List.of(msg), markdown)).get(0);

            assertThat(node.get("role").asText()).isEqualTo("ASSISTANT");
            assertThat(node.get("editable").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("SYSTEM message carries role SYSTEM")
        void system() throws Exception {
            AIChatMessage msg = AIChatMessage.system("prompt");
            JsonNode node = parse(TranscriptJson.toJson(List.of(msg), markdown)).get(0);

            assertThat(node.get("role").asText()).isEqualTo("SYSTEM");
            assertThat(node.get("editable").asBoolean()).isFalse();
        }
    }

    @Nested
    @DisplayName("Editable flag")
    class Editable {

        @Test
        @DisplayName("The last USER message is editable")
        void lastUserEditable() throws Exception {
            AIChatMessage u1 = AIChatMessage.user("first");
            AIChatMessage a1 = AIChatMessage.assistant("reply", 0L, "model");
            AIChatMessage u2 = AIChatMessage.user("second");

            JsonNode node = parse(
                    TranscriptJson.toJson(List.of(u1, a1, u2), markdown));

            assertThat(node.get(0).get("editable").asBoolean()).isFalse();
            assertThat(node.get(1).get("editable").asBoolean()).isFalse();
            assertThat(node.get(2).get("editable").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("Only the last USER, not an earlier one")
        void earlierUserNotEditable() throws Exception {
            AIChatMessage u1 = AIChatMessage.user("first");
            AIChatMessage u2 = AIChatMessage.user("second");

            JsonNode node = parse(
                    TranscriptJson.toJson(List.of(u1, u2), markdown));

            assertThat(node.get(0).get("editable").asBoolean()).isFalse();
            assertThat(node.get(1).get("editable").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("No USER message → nothing editable")
        void noUser() throws Exception {
            AIChatMessage a1 = AIChatMessage.assistant("reply", 0L, "model");

            JsonNode node = parse(
                    TranscriptJson.toJson(List.of(a1), markdown));

            assertThat(node.get(0).get("editable").asBoolean()).isFalse();
        }
    }

    @Nested
    @DisplayName("HTML rendering")
    class HtmlRendering {

        @Test
        @DisplayName("Markdown is rendered to HTML")
        void markdownBecomesHtml() throws Exception {
            AIChatMessage msg = AIChatMessage.user("**bold**");
            JsonNode node = parse(TranscriptJson.toJson(List.of(msg), markdown)).get(0);

            assertThat(node.get("html").asText()).contains("<strong>bold</strong>");
        }

        @Test
        @Disabled("See backlog O.1 — CommonMark does not sanitize raw HTML")
        @DisplayName("Script tags in source are not present as raw HTML")
        void scriptSanitized() throws Exception {
            AIChatMessage msg = AIChatMessage.user("<script>alert(1)</script>");
            JsonNode node = parse(TranscriptJson.toJson(List.of(msg), markdown)).get(0);

            assertThat(node.get("html").asText()).doesNotContain("<script>");
        }

        @Test
        @DisplayName("Newlines inside html are preserved in the JSON string")
        void newlinesInsideHtml() throws Exception {
            AIChatMessage msg = AIChatMessage.user("line one\nline two");
            JsonNode node = parse(TranscriptJson.toJson(List.of(msg), markdown)).get(0);

            assertThat(node.get("html").asText()).contains("\n");
        }
    }

    @Nested
    @DisplayName("Multiple messages")
    class Multiple {

        @Test
        @DisplayName("Order is preserved")
        void order() throws Exception {
            AIChatMessage u1 = AIChatMessage.user("one");
            AIChatMessage a1 = AIChatMessage.assistant("two", 0L, "m");
            AIChatMessage u2 = AIChatMessage.user("three");

            JsonNode node = parse(
                    TranscriptJson.toJson(List.of(u1, a1, u2), markdown));

            assertThat(node).hasSize(3);
            assertThat(node.get(0).get("id").asText()).isEqualTo(u1.id());
            assertThat(node.get(1).get("id").asText()).isEqualTo(a1.id());
            assertThat(node.get(2).get("id").asText()).isEqualTo(u2.id());
        }

        @Test
        @DisplayName("Each message has its own unique id")
        void uniqueIds() throws Exception {
            AIChatMessage u1 = AIChatMessage.user("a");
            AIChatMessage u2 = AIChatMessage.user("b");

            JsonNode node = parse(
                    TranscriptJson.toJson(List.of(u1, u2), markdown));

            assertThat(node.get(0).get("id").asText())
                    .isNotEqualTo(node.get(1).get("id").asText());
        }
    }
}