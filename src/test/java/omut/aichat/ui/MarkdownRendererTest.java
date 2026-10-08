package omut.aichat.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownRendererTest {

    @Test
    @DisplayName("null → \"\"")
    void nullInput() {
        assertThat(MarkdownRenderer.stripMarkdown(null)).isEmpty();
    }

    @Test
    @DisplayName("Empty string → \"\"")
    void emptyInput() {
        assertThat(MarkdownRenderer.stripMarkdown("")).isEmpty();
    }

    @Nested
    @DisplayName("Headings")
    class Headings {

        @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
        @CsvSource(delimiter = '|', value = {
                "# Заголовок              | Заголовок",
                "## Подзаголовок         | Подзаголовок",
                "### Третий уровень      | Третий уровень",
                "###### Шестой            | Шестой"
        })
        @DisplayName("ATX heading markers are dropped")
        void atx(String input, String expected) {
            assertThat(MarkdownRenderer.stripMarkdown(input)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Emphasis")
    class Emphasis {

        @Test
        @DisplayName("**bold** → bold")
        void strongAsterisks() {
            assertThat(MarkdownRenderer.stripMarkdown("**bold**")).isEqualTo("bold");
        }

        @Test
        @DisplayName("__bold__ → bold")
        void strongUnderscores() {
            assertThat(MarkdownRenderer.stripMarkdown("__bold__")).isEqualTo("bold");
        }

        @Test
        @DisplayName("*italic* → italic")
        void italicAsterisks() {
            assertThat(MarkdownRenderer.stripMarkdown("*italic*")).isEqualTo("italic");
        }

        @Test
        @DisplayName("Inline: **a** and *b* in one line")
        void mixedInline() {
            assertThat(MarkdownRenderer.stripMarkdown("**a** and *b*"))
                    .isEqualTo("a and b");
        }
    }

    @Nested
    @DisplayName("Inline code")
    class InlineCode {

        @Test
        @DisplayName("`code` → code")
        void single() {
            assertThat(MarkdownRenderer.stripMarkdown("`code`")).isEqualTo("code");
        }

        @Test
        @DisplayName("Backticks don't interfere with normal text")
        void around() {
            assertThat(MarkdownRenderer.stripMarkdown("before `x` after"))
                    .isEqualTo("before x after");
        }
    }

    @Nested
    @DisplayName("Fenced code blocks")
    class FencedBlocks {

        @Test
        @DisplayName("Language tag dropped, content preserved")
        void withLanguage() {
            String input = "```java\nint x = 1;\n```";
            String result = MarkdownRenderer.stripMarkdown(input);

            assertThat(result).doesNotContain("```");
            assertThat(result).doesNotContain("java");
            assertThat(result).contains("int x = 1;");
        }

        @Test
        @DisplayName("No language tag → no marker left")
        void withoutLanguage() {
            String input = "```\nplain\n```";
            String result = MarkdownRenderer.stripMarkdown(input);

            assertThat(result).doesNotContain("```");
            assertThat(result).contains("plain");
        }
    }

    @Nested
    @DisplayName("Lists")
    class Lists {

        @ParameterizedTest(name = "marker \"{0}\" → dropped")
        @CsvSource({
                "-",
                "*",
                "+"
        })
        @DisplayName("Bullet markers at line start are dropped")
        void bullets(String marker) {
            String input = marker + " item one\n" + marker + " item two";
            assertThat(MarkdownRenderer.stripMarkdown(input))
                    .isEqualTo("item one\nitem two");
        }

        @Test
        @DisplayName("Ordered list markers are dropped")
        void ordered() {
            String input = "1. first\n2. second\n3. third";
            assertThat(MarkdownRenderer.stripMarkdown(input))
                    .isEqualTo("first\nsecond\nthird");
        }
    }

    @Nested
    @DisplayName("Blockquotes and rules")
    class BlockquoteAndRule {

        @Test
        @DisplayName("> blockquote → text")
        void blockquote() {
            assertThat(MarkdownRenderer.stripMarkdown("> quoted"))
                    .isEqualTo("quoted");
        }

        @Test
        @DisplayName("Multi-line blockquote")
        void multiLineBlockquote() {
            String input = "> first\n> second";
            assertThat(MarkdownRenderer.stripMarkdown(input))
                    .isEqualTo("first\nsecond");
        }

        @ParameterizedTest(name = "rule \"{0}\" → dropped")
        @CsvSource({
                "---",
                "***",
                "___"
        })
        @DisplayName("Horizontal rules are dropped")
        void rules(String rule) {
            assertThat(MarkdownRenderer.stripMarkdown(rule)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Whitespace")
    class Whitespace {

        @Test
        @DisplayName("Three or more blank lines collapse into two")
        void collapse() {
            String input = "a\n\n\n\nb";
            assertThat(MarkdownRenderer.stripMarkdown(input))
                    .isEqualTo("a\n\nb");
        }

        @Test
        @DisplayName("Two blank lines preserved")
        void twoKept() {
            String input = "a\n\nb";
            assertThat(MarkdownRenderer.stripMarkdown(input))
                    .isEqualTo("a\n\nb");
        }
    }

    @Nested
    @DisplayName("Combined")
    class Combined {

        @Test
        @DisplayName("Typical assistant reply with heading, list, code")
        void fullReply() {
            String input = """
                    ## Заголовок

                    Список:

                    - **первый**
                    - *второй*

                    ```java
                    int x = 1;
                    """;

            String result = MarkdownRenderer.stripMarkdown(input);

            assertThat(result)
                    .doesNotContain("##")
                    .doesNotContain("**")
                    .doesNotContain("```")
                    .contains("Заголовок")
                    .contains("первый")
                    .contains("второй")
                    .contains("int x = 1;");
        }
    }
}