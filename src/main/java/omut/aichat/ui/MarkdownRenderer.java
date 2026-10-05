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