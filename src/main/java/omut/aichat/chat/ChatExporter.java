package omut.aichat.chat;

import omut.aichat.utils.TimeFormat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ChatExporter {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Exports the conversation to a Markdown file. */
    public static void exportMarkdown(List<AIChatMessage> history, Path file) throws IOException {
        StringBuilder sb = new StringBuilder();

        sb.append("# AIChat conversation\n\n");
        sb.append("Exported: ").append(LocalDateTime.now().format(TIMESTAMP)).append("\n\n");
        sb.append("---\n\n");

        for (AIChatMessage msg : history) {
            switch (msg.role()) {
                case USER -> {
                    sb.append("## You\n\n");
                    sb.append(msg.text()).append("\n\n");
                }
                case ASSISTANT -> {
                    if (msg.model() != null && !msg.model().isBlank()) {
                        sb.append("## AI (").append(msg.model()).append(")\n\n");
                    } else {
                        sb.append("## AI\n\n");
                    }
                    if (msg.durationMillis() > 0) {
                        sb.append("_").append(TimeFormat.shortDuration(msg.durationMillis())).append("_\n\n");
                    }
                    sb.append(msg.text()).append("\n\n");
                }
                case SYSTEM -> {
                    sb.append(quoteSystem(msg.text())).append("\n\n");
                }
            }
        }

        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Formats a system message as a Markdown blockquote.
     * Multi-line messages are quoted line by line.
     */
    public static String quoteSystem(String text) {
        return "> " + text.replace("\n", "\n> ").stripTrailing();
    }

}