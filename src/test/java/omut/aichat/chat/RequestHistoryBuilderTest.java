package omut.aichat.chat;

import omut.aichat.chat.AIChatMessage.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestHistoryBuilderTest {

    private static AIChatMessage user(String text) {
        return AIChatMessage.user(text);
    }

    private static AIChatMessage assistant(String text) {
        return AIChatMessage.assistant(text, 0L, "model");
    }

    private static AIChatMessage system(String text) {
        return AIChatMessage.system(text);
    }

    @Nested
    @DisplayName("When the history is within the limit")
    class WithinLimit {

        @Test
        @DisplayName("Empty history → empty list")
        void empty() {
            assertThat(RequestHistoryBuilder.trim(List.of(), 10))
                    .isEmpty();
        }

        @Test
        @DisplayName("Exactly at limit → returned as-is")
        void exactlyAtLimit() {
            var history = List.of(user("1"), assistant("2"), user("3"));
            assertThat(RequestHistoryBuilder.trim(history, 3))
                    .hasSize(3);
        }

        @Test
        @DisplayName("Below limit → returned as-is")
        void belowLimit() {
            var history = List.of(user("1"), assistant("2"));
            assertThat(RequestHistoryBuilder.trim(history, 10))
                    .hasSize(2);
        }

        @Test
        @DisplayName("Result is a new list, not the same instance")
        void returnsCopy() {
            var history = List.of(user("1"), assistant("2"));
            List<AIChatMessage> trimmed = RequestHistoryBuilder.trim(history, 10);
            assertThat(trimmed).isNotSameAs(history);
        }
    }

    @Nested
    @DisplayName("When the history exceeds the limit")
    class OverLimit {

        @Test
        @DisplayName("Without system message → last N messages kept")
        void withoutSystem() {
            var history = List.of(
                    user("u1"), assistant("a1"),
                    user("u2"), assistant("a2"),
                    user("u3"), assistant("a3"));

            List<AIChatMessage> trimmed = RequestHistoryBuilder.trim(history, 2);

            assertThat(trimmed)
                    .hasSize(2)
                    .extracting(AIChatMessage::text)
                    .containsExactly("u3", "a3");
        }

        @Test
        @DisplayName("With system message → system kept plus last N-1")
        void withSystem() {
            var history = List.of(
                    system("prompt"),
                    user("u1"), assistant("a1"),
                    user("u2"), assistant("a2"),
                    user("u3"), assistant("a3"));

            List<AIChatMessage> trimmed = RequestHistoryBuilder.trim(history, 3);

            assertThat(trimmed)
                    .hasSize(3)
                    .extracting(AIChatMessage::role)
                    .containsExactly(Role.SYSTEM, Role.USER, Role.ASSISTANT);
            assertThat(trimmed.getFirst().text()).isEqualTo("prompt");
            assertThat(trimmed.get(1).text()).isEqualTo("u3");
            assertThat(trimmed.get(2).text()).isEqualTo("a3");
        }

        @Test
        @DisplayName("System message at the start only — not duplicated elsewhere")
        void systemOnlyAtStart() {
            var history = List.of(
                    system("prompt"),
                    user("u1"), assistant("a1"),
                    user("u2"), assistant("a2"));

            List<AIChatMessage> trimmed = RequestHistoryBuilder.trim(history, 3);

            assertThat(trimmed)
                    .extracting(AIChatMessage::role)
                    .containsExactly(Role.SYSTEM, Role.USER, Role.ASSISTANT);
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("max = 1 with system only → just the system message")
        void systemOnlyWithinLimit() {
            var history = List.of(system("prompt"), user("u1"), assistant("a1"));

            List<AIChatMessage> trimmed = RequestHistoryBuilder.trim(history, 1);

            assertThat(trimmed)
                    .hasSize(1)
                    .extracting(AIChatMessage::role)
                    .containsExactly(Role.SYSTEM);
        }

        @Test
        @DisplayName("max = 2 with system + user + assistant → system + assistant")
        void systemPlusLastOne() {
            var history = List.of(system("prompt"), user("u1"), assistant("a1"));

            List<AIChatMessage> trimmed = RequestHistoryBuilder.trim(history, 2);

            assertThat(trimmed)
                    .extracting(AIChatMessage::role)
                    .containsExactly(Role.SYSTEM, Role.ASSISTANT);
        }

        @Test
        @DisplayName("System appears mid-history — only first is preserved")
        void systemMidHistoryNotSpecial() {
            var history = List.of(
                    user("u1"), system("mid"),
                    user("u2"), assistant("a2"));

            List<AIChatMessage> trimmed = RequestHistoryBuilder.trim(history, 2);

            assertThat(trimmed)
                    .extracting(AIChatMessage::text)
                    .containsExactly("u2", "a2");
        }

        @Test
        @DisplayName("Result content belongs to source list")
        void elementsShared() {
            AIChatMessage u1 = user("u1");
            AIChatMessage a1 = assistant("a1");
            var history = List.of(u1, a1);

            List<AIChatMessage> trimmed = RequestHistoryBuilder.trim(history, 2);

            assertThat(trimmed).containsExactly(u1, a1);
        }
    }
}