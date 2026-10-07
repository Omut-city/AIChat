package omut.aichat.ui;

import omut.aichat.chat.ChatSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * Bridge object exposed to chat.html as {@code window.javaBridge}.
 * <p>
 * Methods are called from JavaScript running in the WebView, i.e. from
 * the JavaFX Application Thread. All callbacks therefore run on the
 * FX thread and can touch UI state directly.
 * <p>
 * The bridge holds no state of its own; every method delegates to
 * {@link ChatSession}. The {@code editDialog} function is supplied by
 * {@link ChatView} so the bridge can request user input without
 * knowing about JavaFX dialogs.
 */
public class ChatJsBridge {

    private static final Logger log = LoggerFactory.getLogger(ChatJsBridge.class);

    private final ChatSession session;
    private final Function<String, String> editDialog;

    public ChatJsBridge(ChatSession session, Function<String, String> editDialog) {
        this.session = session;
        this.editDialog = editDialog;
    }

    /**
     * Called from JavaScript as {@code window.javaBridge.deleteMessage(id)}.
     * Deletes the message with the given id and everything after it.
     */
    public void deleteMessage(String messageId) {
        log.debug("deleteMessage: {}", messageId);
        session.deleteFrom(messageId);
    }

    /**
     * Called from JavaScript as {@code window.javaBridge.editLastUser(id)}.
     * Opens the edit dialog and, if the user saves, replaces the message
     * and re-sends the prompt.
     */
    public void editLastUser(String messageId) {
        log.debug("editLastUser: {}", messageId);
        String newText = editDialog.apply(messageId);
        if (newText == null || newText.isBlank()) return;
        session.editLastUser(messageId, newText);
    }
}