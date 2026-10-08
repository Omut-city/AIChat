package omut.aichat.ui;

import omut.aichat.chat.AIChatMessage;
import omut.aichat.chat.ChatExporter;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.Locale;

/**
 * Renders Markdown to HTML for the WebView and formats chat messages
 * into their Markdown representation.
 * <p>
 * Stateful only in that it holds the parser and renderer instances;
 * the render/format methods are otherwise pure and thread-safe.
 */
public final class MarkdownRenderer {

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().build();

    /** Renders a Markdown string to an HTML fragment. */
    public String renderToHtml(String markdown) {
        return renderer.render(parser.parse(markdown));
    }

    /**
     * Strips the most common Markdown constructs from text so it can be
     * pasted into a plain-text target (email, notes, terminal).
     * <p>
     * Handles: ATX headings, bold/italic, inline code, code fences,
     * list markers, blockquotes, and horizontal rules. Deliberately
     * conservative — leaves link text intact rather than trying to
     * reflow them.
     */
    public static String stripMarkdown(String text) {
        if (text == null || text.isEmpty()) return "";
        return text
                // ```lang\n...\n``` → ... (keep inner content, drop fences)
                .replaceAll("(?m)^```[a-zA-Z0-9_-]*\\s*$", "")
                // # heading → heading
                .replaceAll("(?m)^#{1,6}\\s+", "")
                // > blockquote → text
                .replaceAll("(?m)^>\\s?", "")
                // - / * / + list marker at line start → drop
                .replaceAll("(?m)^[\\-*+]\\s+", "")
                // 1. / 2. ordered list marker → drop
                .replaceAll("(?m)^\\d+\\.\\s+", "")
                // horizontal rule
                .replaceAll("(?m)^[-*_]{3,}\\s*$", "")
                // **bold** and __bold__
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("__(.+?)__", "$1")
                // *italic* and _italic_ (не трогаем уже обработанное)
                .replaceAll("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)", "$1")
                // `inline code` → inline code
                .replaceAll("`([^`]+)`", "$1")
                // collapse 3+ blank lines into 2
                .replaceAll("\\n{3,}", "\n\n");
    }

    /**
     * Formats a chat message as Markdown for display in the transcript.
     *
     * @param message the message to format
     */
    public String formatMessage(AIChatMessage message) {
        return switch (message.role()) {
            case USER -> "**You:** " + message.text();
            case ASSISTANT -> String.format(
                    Locale.US,
                    "**AI (%s, %.1fs):**%n%n%s",
                    message.model() != null ? message.model() : "unknown",
                    message.durationMillis() / 1000.0,
                    message.text()
            );
            case SYSTEM -> ChatExporter.quoteSystem(message.text());
        };
    }

    /**
     * Converts an arbitrary string into a JavaScript string literal
     * (surround with quotes, escape special characters).
     */
    public static String jsStringLiteral(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"'  -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}