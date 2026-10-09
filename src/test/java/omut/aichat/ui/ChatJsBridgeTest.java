package omut.aichat.ui;

import omut.aichat.chat.ChatCommands;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ChatJsBridgeTest {

    @Nested
    @DisplayName("deleteMessage")
    class DeleteMessage {

        @Test
        @DisplayName("Delegates the id to ChatCommands")
        void delegates() {
            ChatCommands commands = mock(ChatCommands.class);
            ChatJsBridge bridge = new ChatJsBridge(commands, id -> null);

            bridge.deleteMessage("m-42");

            verify(commands).deleteFrom("m-42");
            verifyNoMoreInteractions(commands);
        }

        @Test
        @DisplayName("Passes any id through, even blank")
        void blankId() {
            ChatCommands commands = mock(ChatCommands.class);
            ChatJsBridge bridge = new ChatJsBridge(commands, id -> null);

            bridge.deleteMessage("");
            bridge.deleteMessage(null);

            verify(commands).deleteFrom("");
            verify(commands).deleteFrom(null);
        }
    }

    @Nested
    @DisplayName("editUserMessage")
    class EditUserMessage {

        @Test
        @DisplayName("Opens the dialog and forwards the result")
        void happy() {
            ChatCommands commands = mock(ChatCommands.class);
            AtomicReference<String> dialogArg = new AtomicReference<>();
            ChatJsBridge bridge = new ChatJsBridge(commands, id -> {
                dialogArg.set(id);
                return "edited text";
            });

            bridge.editUserMessage("m-7");

            assertThat(dialogArg.get()).isEqualTo("m-7");
            verify(commands).editUserMessage("m-7", "edited text");
        }

        @Test
        @DisplayName("Null dialog result does not reach ChatCommands")
        void cancel() {
            ChatCommands commands = mock(ChatCommands.class);
            ChatJsBridge bridge = new ChatJsBridge(commands, id -> null);

            bridge.editUserMessage("m-7");

            verifyNoInteractions(commands);
        }

        @Test
        @DisplayName("Blank dialog result does not reach ChatCommands")
        void blank() {
            ChatCommands commands = mock(ChatCommands.class);
            ChatJsBridge bridge = new ChatJsBridge(commands, id -> "   ");

            bridge.editUserMessage("m-7");

            verifyNoInteractions(commands);
        }

        @Test
        @DisplayName("Empty string is also rejected")
        void empty() {
            ChatCommands commands = mock(ChatCommands.class);
            ChatJsBridge bridge = new ChatJsBridge(commands, id -> "");

            bridge.editUserMessage("m-7");

            verifyNoInteractions(commands);
        }

        @Test
        @DisplayName("Whitespace around the dialog result is preserved")
        void whitespacePreserved() {
            ChatCommands commands = mock(ChatCommands.class);
            ChatJsBridge bridge = new ChatJsBridge(commands, id -> "  text  ");

            bridge.editUserMessage("m-7");

            verify(commands).editUserMessage("m-7", "  text  ");
        }

        @Test
        @DisplayName("Dialog is called with the exact id")
        void dialogArg() {
            ChatCommands commands = mock(ChatCommands.class);
            AtomicReference<String> seen = new AtomicReference<>();
            ChatJsBridge bridge = new ChatJsBridge(commands, id -> {
                seen.set(id);
                return "ok";
            });

            bridge.editUserMessage("special:id:with:colons");

            assertThat(seen.get()).isEqualTo("special:id:with:colons");
        }
    }
}