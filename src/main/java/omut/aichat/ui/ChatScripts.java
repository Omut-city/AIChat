package omut.aichat.ui;

import javafx.scene.web.WebEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Typed wrapper over the JavaScript functions defined in chat.js.
 * <p>
 * Every {@code executeScript} call in the UI layer goes through here,
 * so the names of the JS functions live in exactly one place. When a
 * function is renamed in chat.js, only this file needs updating —
 * no call site in {@link ChatView} knows any JS names.
 * <p>
 * All methods must be called on the JavaFX Application Thread.
 * Failures are swallowed and logged; a missing JS function (e.g. the
 * document has not finished loading yet) must not bring down the UI.
 */
public class ChatScripts {

    private static final Logger log = LoggerFactory.getLogger(ChatScripts.class);

    private final WebEngine engine;

    public ChatScripts(WebEngine engine) {
        this.engine = engine;
    }

    /** Creates the streaming placeholder for the incoming reply. */
    public void beginStreaming() {
        execute("beginStreaming();");
    }

    /** Updates the streaming placeholder with new HTML content. */
    public void updateStreamingMessage(String html) {
        String escaped = MarkdownRenderer.jsStringLiteral(html);
        execute("updateStreamingMessage(" + escaped + ");");
    }

    /** Replaces the transcript with the serialised message list. */
    public void renderTranscript(String json) {
        String escaped = MarkdownRenderer.jsStringLiteral(json);
        execute("renderTranscript(" + escaped + ");");
    }

    /** Flips the busy flag; hover and context-menu handlers check it. */
    public void setBusy(boolean busy) {
        execute("setBusy(" + busy + ");");
    }

    /** Scrolls to the bottom if (and only if) the user is already there. */
    public void scrollIfAtBottom() {
        execute("if (isAtBottom()) scrollToBottom();");
    }

    /** Unconditionally scrolls to the bottom of the transcript. */
    public void scrollToBottom() {
        execute("scrollToBottom();");
    }

    /**
     * Highlights every occurrence of {@code query} in the transcript.
     * Returns the number of matches, or 0 if the query is blank or the
     * JS engine is not yet ready.
     * <p>
     * Must be called on the JavaFX Application Thread.
     */
    public int highlightMatches(String query) {
        if (query == null || query.isBlank()) {
            clearHighlight();
            return 0;
        }
        String escaped = MarkdownRenderer.jsStringLiteral(query);
        try {
            Object result = engine.executeScript(
                    "highlightMatches(" + escaped + ");");
            if (result instanceof Number n) {
                return n.intValue();
            }
            return 0;
        } catch (Exception e) {
            log.warn("highlightMatches failed: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Removes every search highlight from the transcript.
     * Must be called on the JavaFX Application Thread.
     */
    public void clearHighlight() {
        execute("clearHighlight();");
    }

    /**
     * Scrolls to the match at the given 0-based index and marks it as
     * current. Returns {@code true} if the index was valid.
     * <p>
     * Must be called on the JavaFX Application Thread.
     */
    public boolean scrollToMatch(int index) {
        try {
            Object result = engine.executeScript(
                    "scrollToMatch(" + index + ");");
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("scrollToMatch failed: {}", e.getMessage());
            return false;
        }
    }

    private void execute(String script) {
        try {
            engine.executeScript(script);
        } catch (Exception e) {
            log.warn("executeScript failed: {} — {}", script, e.getMessage());
        }
    }
}