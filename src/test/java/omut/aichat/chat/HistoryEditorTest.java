package omut.aichat.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoryEditorTest {

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
    @DisplayName("Read operations")
    class Read {

        @Test
        @DisplayName("Empty editor: snapshot is empty")
        void emptySnapshot() {
            HistoryEditor editor = new HistoryEditor();
            assertThat(editor.snapshot()).isEmpty();
        }

        @Test
        @DisplayName("Empty editor: isEmpty is true")
        void emptyIsEmpty() {
            HistoryEditor editor = new HistoryEditor();
            assertThat(editor.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("Empty editor: size is 0")
        void emptySize() {
            HistoryEditor editor = new HistoryEditor();
            assertThat(editor.size()).isZero();
        }

        @Test
        @DisplayName("snapshot returns a copy, not a live view")
        void snapshotIsCopy() {
            HistoryEditor editor = new HistoryEditor();
            editor.append(user("u1"));

            List<AIChatMessage> snap = editor.snapshot();
            editor.append(user("u2"));

            assertThat(snap).hasSize(1);
            assertThat(editor.snapshot()).hasSize(2);
        }

        @Test
        @DisplayName("first returns the first message")
        void firstReturnsFirst() {
            HistoryEditor editor = new HistoryEditor();
            AIChatMessage s = system("prompt");
            editor.append(s);
            editor.append(user("u1"));

            assertThat(editor.first()).isSameAs(s);
        }

        @Test
        @DisplayName("first on empty editor throws NoSuchElementException")
        void firstOnEmpty() {
            HistoryEditor editor = new HistoryEditor();

            assertThatThrownBy(editor::first)
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("get returns the message at the given index")
        void getByIndex() {
            HistoryEditor editor = new HistoryEditor();
            AIChatMessage u1 = user("u1");
            AIChatMessage u2 = user("u2");
            editor.append(u1);
            editor.append(u2);

            assertThat(editor.get(0)).isSameAs(u1);
            assertThat(editor.get(1)).isSameAs(u2);
        }

        @Test
        @DisplayName("get out of bounds throws IndexOutOfBoundsException")
        void getOutOfBounds() {
            HistoryEditor editor = new HistoryEditor();
            editor.append(user("u1"));

            assertThatThrownBy(() -> editor.get(5))
                    .isInstanceOf(IndexOutOfBoundsException.class);
        }

        @Test
        @DisplayName("indexOf finds message by id")
        void indexOfFound() {
            HistoryEditor editor = new HistoryEditor();
            AIChatMessage u1 = user("u1");
            AIChatMessage u2 = user("u2");
            editor.append(u1);
            editor.append(u2);

            assertThat(editor.indexOf(u2.id())).isEqualTo(1);
        }

        @Test
        @DisplayName("indexOf returns -1 for unknown id")
        void indexOfUnknown() {
            HistoryEditor editor = new HistoryEditor();
            editor.append(user("u1"));

            assertThat(editor.indexOf("nonexistent")).isEqualTo(-1);
        }

        @Test
        @DisplayName("lastUserIndex on empty editor returns -1")
        void lastUserOnEmpty() {
            HistoryEditor editor = new HistoryEditor();
            assertThat(editor.lastUserIndex()).isEqualTo(-1);
        }

        @Test
        @DisplayName("lastUserIndex on history without USER returns -1")
        void lastUserWithoutUser() {
            HistoryEditor editor = new HistoryEditor();
            editor.append(system("prompt"));
            editor.append(assistant("reply"));

            assertThat(editor.lastUserIndex()).isEqualTo(-1);
        }

        @Test
        @DisplayName("lastUserIndex finds the most recent USER")
        void lastUserPicksMostRecent() {
            HistoryEditor editor = new HistoryEditor();
            editor.append(user("u1"));
            editor.append(assistant("a1"));
            editor.append(user("u2"));
            editor.append(assistant("a2"));

            assertThat(editor.lastUserIndex()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Append and prepend")
    class AppendPrepend {

        @Test
        @DisplayName("append adds to the end")
        void append() {
            HistoryEditor editor = new HistoryEditor();
            editor.append(user("u1"));
            editor.append(user("u2"));

            assertThat(editor.snapshot())
                    .extracting(AIChatMessage::text)
                    .containsExactly("u1", "u2");
        }

        @Test
        @DisplayName("prepend adds to the beginning")
        void prepend() {
            HistoryEditor editor = new HistoryEditor();
            editor.append(user("u1"));
            editor.prepend(system("prompt"));

            assertThat(editor.snapshot())
                    .extracting(AIChatMessage::text)
                    .containsExactly("prompt", "u1");
        }

        @Test
        @DisplayName("prepend on empty editor behaves like append")
        void prependOnEmpty() {
            HistoryEditor editor = new HistoryEditor();
            editor.prepend(system("prompt"));

            assertThat(editor.snapshot()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Set and removeFirst")
    class SetRemove {

        @Test
        @DisplayName("set replaces the message at the given index")
        void set() {
            HistoryEditor editor = new HistoryEditor();
            editor.append(user("old"));
            AIChatMessage replacement = system("new");
            editor.set(0, replacement);

            assertThat(editor.get(0)).isSameAs(replacement);
        }

        @Test
        @DisplayName("removeFirst drops the first message")
        void removeFirst() {
            HistoryEditor editor = new HistoryEditor();
            editor.append(system("prompt"));
            editor.append(user("u1"));
            editor.removeFirst();

            assertThat(editor.snapshot())
                    .extracting(AIChatMessage::text)
                    .containsExactly("u1");
        }

        @Test
        @DisplayName("removeFirst on empty editor throws")
        void removeFirstOnEmpty() {
            HistoryEditor editor = new HistoryEditor();

            assertThatThrownBy(editor::removeFirst)
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    @DisplayName("Cut operations")
    class Cut {

        @Test
        @DisplayName("cutFrom removes the message at the index and everything after")
        void cutFromMiddle() {
            HistoryEditor editor = new HistoryEditor();
            editor.append(user("u1"));
            editor.append(assistant("a1"));
            editor.append(user("u2"));
            editor.append(assistant("a2"));

            editor.cutFrom(2);

            assertThat(editor.snapshot())
                    .extracting(AIChatMessage::text)
                    .containsExactly("u1", "a1");
        }

        @Test
        @DisplayName("cutFrom(0) clears everything")
        void cutFromZero() {
            HistoryEditor editor = new HistoryEditor();
            editor.append(user("u1"));
            editor.append(assistant("a1"));
            editor.cutFrom(0);

            assertThat(editor.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("cutAfter keeps the indexed message and removes the rest")
        void cutAfterMiddle() {
            HistoryEditor editor = new HistoryEditor();
            editor.append(user("u1"));
            editor.append(assistant("a1"));
            editor.append(user("u2"));
            editor.append(assistant("a2"));

            editor.cutAfter(1);

            assertThat(editor.snapshot())
                    .extracting(AIChatMessage::text)
                    .containsExactly("u1", "a1");
        }

        @Test
        @DisplayName("cutAfter on last index removes nothing")
        void cutAfterLast() {
            HistoryEditor editor = new HistoryEditor();
            editor.append(user("u1"));
            editor.append(assistant("a1"));

            editor.cutAfter(1);

            assertThat(editor.snapshot()).hasSize(2);
        }

        @Test
        @DisplayName("cutFrom and cutAfter on single-message editor")
        void singleMessage() {
            HistoryEditor editor = new HistoryEditor();
            editor.append(user("u1"));

            editor.cutAfter(0);
            assertThat(editor.snapshot()).hasSize(1);

            editor.cutFrom(0);
            assertThat(editor.isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("Clear")
    class Clear {

        @Test
        @DisplayName("clear empties the editor")
        void clear() {
            HistoryEditor editor = new HistoryEditor();
            editor.append(user("u1"));
            editor.append(assistant("a1"));
            editor.clear();

            assertThat(editor.isEmpty()).isTrue();
            assertThat(editor.snapshot()).isEmpty();
        }

        @Test
        @DisplayName("clear on empty editor is a no-op")
        void clearOnEmpty() {
            HistoryEditor editor = new HistoryEditor();
            editor.clear();

            assertThat(editor.isEmpty()).isTrue();
        }
    }
}