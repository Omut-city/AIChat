package omut.aichat.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HtmlTemplateTest {

    @Test
    @DisplayName("Missing resource throws IllegalStateException")
    void missing() {
        assertThatThrownBy(() -> new HtmlTemplate("/nope.html"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("render substitutes all four placeholders")
    void renderSubstitutes() {
        HtmlTemplate t = new HtmlTemplate("/templates/chat.html");
        String out = t.render("CSS", "JS", "theme-dark", "BODY");

        assertThat(out)
                .contains("CSS").contains("JS")
                .contains("theme-dark").contains("BODY")
                .doesNotContain("{{CSS}}")
                .doesNotContain("{{JS}}")
                .doesNotContain("{{BODY_CLASS}}")
                .doesNotContain("{{BODY}}");
    }

    @Test
    @DisplayName("readResource returns empty for missing")
    void readMissing() {
        assertThat(HtmlTemplate.readResource("/nope.html")).isEmpty();
    }

    @Test
    @DisplayName("readResource loads a real classpath resource")
    void readReal() {
        String chat = HtmlTemplate.readResource("/templates/chat.html");
        assertThat(chat).isNotEmpty().contains("<!DOCTYPE html>");
    }
}