package omut.aichat.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatExporterTest {

    @TempDir
    Path tmp;

    private String export(List<AIChatMessage> history) throws IOException {
        Path file = tmp.resolve("chat.md");
        ChatExporter.exportMarkdown(history, file);
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("Header")
    class Header {

        @Test
        @DisplayName("Starts with title and timestamp")
        void title() throws IOException {
            String md = export(List.of());
            assertThat(md)
                    .startsWith("# AIChat conversation\n\nExported: ")
                    .contains("\n\n---\n\n");
        }
    }

    @Nested
    @DisplayName("USER messages")
    class User {

        @Test
        @DisplayName("Header is '## You', body follows")
        void simple() throws IOException {
            String md = export(List.of(AIChatMessage.user("hello")));
            assertThat(md).contains("## You\n\nhello\n\n");
        }

        @Test
        @DisplayName("Multiline body preserved")
        void multiline() throws IOException {
            String md = export(List.of(AIChatMessage.user("one\ntwo")));
            assertThat(md).contains("## You\n\none\ntwo\n\n");
        }
    }

    @Nested
    @DisplayName("ASSISTANT messages")
    class Assistant {

        @Test
        @DisplayName("Includes model name in the header when known")
        void withModel() throws IOException {
            String md = export(List.of(
                    AIChatMessage.assistant("hi", 0L, "qwen2.5:7b")));
            assertThat(md).contains("## AI (qwen2.5:7b)\n\n");
            assertThat(md).contains("hi");
        }

        @Test
        @DisplayName("Falls back to bare '## AI' when model is null")
        void nullModel() throws IOException {
            String md = export(List.of(
                    AIChatMessage.assistant("hi", 0L, null)));
            assertThat(md).contains("## AI\n\n");
            assertThat(md).doesNotContain("## AI (");
        }

        @Test
        @DisplayName("Falls back to bare '## AI' when model is blank")
        void blankModel() throws IOException {
            String md = export(List.of(
                    AIChatMessage.assistant("hi", 0L, "   ")));
            assertThat(md).contains("## AI\n\n");
            assertThat(md).doesNotContain("## AI (");
        }

        @Test
        @DisplayName("Duration line appears when durationMillis > 0")
        void withDuration() throws IOException {
            String md = export(List.of(
                    AIChatMessage.assistant("hi", 1500L, "qwen2.5:7b")));
            assertThat(md).contains("_1.5 s_");
        }

        @Test
        @DisplayName("No duration line when durationMillis == 0")
        void zeroDuration() throws IOException {
            String md = export(List.of(
                    AIChatMessage.assistant("hi", 0L, "qwen2.5:7b")));
            assertThat(md).doesNotContain("_0 ms_");
            assertThat(md).doesNotContain("_0.0 s_");
        }
    }

    @Nested
    @DisplayName("SYSTEM messages")
    class System {

        @Test
        @DisplayName("Single-line system is a blockquote")
        void singleLine() throws IOException {
            String md = export(List.of(AIChatMessage.system("be concise")));
            assertThat(md).contains("> be concise");
        }

        @Test
        @DisplayName("Multi-line system is quoted line by line")
        void multiLine() throws IOException {
            String md = export(List.of(AIChatMessage.system("line one\nline two")));
            assertThat(md).contains("> line one\n> line two");
        }
    }

    @Nested
    @DisplayName("Conversation order")
    class Order {

        @Test
        @DisplayName("Messages appear in the order given")
        void preserved() throws IOException {
            String md = export(List.of(
                    AIChatMessage.system("prompt"),
                    AIChatMessage.user("q1"),
                    AIChatMessage.assistant("a1", 0L, "m"),
                    AIChatMessage.user("q2"),
                    AIChatMessage.assistant("a2", 0L, "m")));

            int sys = md.indexOf("> prompt");
            int q1 = md.indexOf("## You\n\nq1");
            int a1 = md.indexOf("## AI (m)\n\na1");
            int q2 = md.indexOf("## You\n\nq2");
            int a2 = md.indexOf("## AI (m)\n\na2");

            assertThat(sys).isLessThan(q1);
            assertThat(q1).isLessThan(a1);
            assertThat(a1).isLessThan(q2);
            assertThat(q2).isLessThan(a2);
        }
    }

    @Nested
    @DisplayName("quoteSystem")
    class QuoteSystem {

        @Test
        @DisplayName("Single line prefixed with '> '")
        void single() {
            assertThat(ChatExporter.quoteSystem("hello"))
                    .isEqualTo("> hello");
        }

        @Test
        @DisplayName("Each line prefixed, no trailing newline added")
        void multi() {
            assertThat(ChatExporter.quoteSystem("a\nb\nc"))
                    .isEqualTo("> a\n> b\n> c");
        }

        @Test
        @DisplayName("Trailing whitespace at the end is stripped")
        void trailingStrip() {
            assertThat(ChatExporter.quoteSystem("a\n"))
                    .isEqualTo("> a");
        }

        @Test
        @DisplayName("Empty input produces a bare '>' prefix")
        void empty() {
            assertThat(ChatExporter.quoteSystem(""))
                    .isEqualTo("> ");
        }
    }

    @Nested
    @DisplayName("File writing")
    class FileWriting {

        @Test
        @DisplayName("Output is UTF-8 and survives round-trip")
        void utf8() throws IOException {
            String original = "Привет, мир";
            String md = export(List.of(AIChatMessage.user(original)));
            assertThat(md).contains(original);
        }
    }
}
