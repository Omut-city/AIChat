package omut.aichat.ui;

import omut.aichat.chat.ChatSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridge object exposed to chat.html as {@code window.javaBridge}.
 * <p>
 * Methods are called from JavaScript running in the WebView, i.e. from
 * the JavaFX Application Thread. All callbacks therefore run on the
 * FX thread and can touch UI state directly.
 * <p>
 * The bridge holds no state of its own; every method delegates to
 * {@link ChatSession}.
 */
public class ChatJsBridge {

    private static final Logger log = LoggerFactory.getLogger(ChatJsBridge.class);

    private final ChatSession session;

    public ChatJsBridge(ChatSession session) {
        this.session = session;
    }

    /**
     * Called from JavaScript as {@code window.javaBridge.deleteMessage(id)}.
     * Deletes the message with the given id and everything after it.
     */
    public void deleteMessage(String messageId) {
        log.debug("deleteMessage: {}", messageId);
        session.deleteFrom(messageId);
    }
}