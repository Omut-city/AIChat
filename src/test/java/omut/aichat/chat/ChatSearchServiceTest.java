package omut.aichat.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSearchServiceTest {

    @Nested
    @DisplayName("Empty input")
    class Empty {

        @Test
        @DisplayName("Null query returns empty list")
        void nullQuery() {
            assertThat(ChatSearchService.findAll(
                    List.of(AIChatMessage.user("hello")), null))
                    .isEmpty();
        }

        @Test
        @DisplayName("Blank query returns empty list")
        void blankQuery() {
            assertThat(ChatSearchService.findAll(
                    List.of(AIChatMessage.user("hello")), "   "))
                    .isEmpty();
        }

        @Test
        @DisplayName("Empty history returns empty list")
        void emptyHistory() {
            assertThat(ChatSearchService.findAll(List.of(), "hello"))
                    .isEmpty();
        }

        @Test
        @DisplayName("Null history returns empty list")
        void nullHistory() {
            assertThat(ChatSearchService.findAll(null, "hello"))
                    .isEmpty();
        }

        @Test
        @DisplayName("No match returns empty list")
        void noMatch() {
            assertThat(ChatSearchService.findAll(
                    List.of(AIChatMessage.user("hello")), "xyz"))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("Single match")
    class SingleMatch {

        @Test
        @DisplayName("Finds one occurrence with correct offsets")
        void simple() {
            AIChatMessage msg = AIChatMessage.user("hello world");
            List<ChatSearchService.SearchMatch> matches =
                    ChatSearchService.findAll(List.of(msg), "world");

            assertThat(matches).hasSize(1);
            ChatSearchService.SearchMatch m = matches.getFirst();
            assertThat(m.messageId()).isEqualTo(msg.id());
            assertThat(m.messageIndex()).isZero();
            assertThat(m.startOffset()).isEqualTo(6);
            assertThat(m.endOffset()).isEqualTo(11);
        }

        @Test
        @DisplayName("Case-insensitive match")
        void caseInsensitive() {
            AIChatMessage msg = AIChatMessage.user("Hello WORLD");
            List<ChatSearchService.SearchMatch> matches =
                    ChatSearchService.findAll(List.of(msg), "world");

            assertThat(matches).hasSize(1);
            assertThat(matches.getFirst().startOffset()).isEqualTo(6);
        }

        @Test
        @DisplayName("Match at start of text")
        void atStart() {
            AIChatMessage msg = AIChatMessage.user("hello world");
            List<ChatSearchService.SearchMatch> matches =
                    ChatSearchService.findAll(List.of(msg), "hello");

            assertThat(matches).hasSize(1);
            assertThat(matches.getFirst().startOffset()).isZero();
            assertThat(matches.getFirst().endOffset()).isEqualTo(5);
        }

        @Test
        @DisplayName("Match at end of text")
        void atEnd() {
            AIChatMessage msg = AIChatMessage.user("hello world");
            List<ChatSearchService.SearchMatch> matches =
                    ChatSearchService.findAll(List.of(msg), "world");

            assertThat(matches).hasSize(1);
            assertThat(matches.getFirst().endOffset()).isEqualTo(11);
        }

        @Test
        @DisplayName("UTF-8 text works")
        void utf8() {
            AIChatMessage msg = AIChatMessage.user("Привет, мир");
            List<ChatSearchService.SearchMatch> matches =
                    ChatSearchService.findAll(List.of(msg), "мир");

            assertThat(matches).hasSize(1);
            assertThat(matches.getFirst().startOffset()).isEqualTo(8);
        }
    }

    @Nested
    @DisplayName("Multiple matches in one message")
    class MultipleInMessage {

        @Test
        @DisplayName("Finds all occurrences")
        void allOccurrences() {
            AIChatMessage msg = AIChatMessage.user("hello hello hello");
            List<ChatSearchService.SearchMatch> matches =
                    ChatSearchService.findAll(List.of(msg), "hello");

            assertThat(matches).hasSize(3);
            assertThat(matches.get(0).startOffset()).isZero();
            assertThat(matches.get(1).startOffset()).isEqualTo(6);
            assertThat(matches.get(2).startOffset()).isEqualTo(12);
        }

        @Test
        @DisplayName("Non-overlapping: 'aaa' in 'aaaaa' gives one match")
        void nonOverlapping() {
            AIChatMessage msg = AIChatMessage.user("aaaaa");
            List<ChatSearchService.SearchMatch> matches =
                    ChatSearchService.findAll(List.of(msg), "aaa");

            assertThat(matches).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Multiple messages")
    class MultipleMessages {

        @Test
        @DisplayName("Matches across messages in order")
        void acrossMessages() {
            AIChatMessage m1 = AIChatMessage.user("hello");
            AIChatMessage m2 = AIChatMessage.system("hello system");
            AIChatMessage m3 = AIChatMessage.assistant("hello ai", 0L, "model");

            List<ChatSearchService.SearchMatch> matches =
                    ChatSearchService.findAll(List.of(m1, m2, m3), "hello");

            assertThat(matches).hasSize(3);
            assertThat(matches.get(0).messageIndex()).isZero();
            assertThat(matches.get(1).messageIndex()).isEqualTo(1);
            assertThat(matches.get(2).messageIndex()).isEqualTo(2);

            assertThat(matches.get(0).messageId()).isEqualTo(m1.id());
            assertThat(matches.get(1).messageId()).isEqualTo(m2.id());
            assertThat(matches.get(2).messageId()).isEqualTo(m3.id());
        }

        @Test
        @DisplayName("Mixed: multiple matches in multiple messages")
        void mixed() {
            AIChatMessage m1 = AIChatMessage.user("foo foo");
            AIChatMessage m2 = AIChatMessage.system("bar");
            AIChatMessage m3 = AIChatMessage.assistant("foo", 0L, "m");

            List<ChatSearchService.SearchMatch> matches =
                    ChatSearchService.findAll(List.of(m1, m2, m3), "foo");

            assertThat(matches).hasSize(3);
            assertThat(matches.get(0).messageIndex()).isZero();
            assertThat(matches.get(1).messageIndex()).isZero();
            assertThat(matches.get(2).messageIndex()).isEqualTo(2);
        }
    }
}