package omut.aichat.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class BaseUrlValidatorTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("null, empty, or blank → null")
    void blankInput(String input) {
        assertThat(BaseUrlValidator.normalize(input)).isNull();
    }

    @Nested
    @DisplayName("Valid URLs")
    class Valid {

        @ParameterizedTest(name = "\"{0}\" → \"{0}\"")
        @ValueSource(strings = {
                "http://127.0.0.1:11434",
                "http://localhost:11434",
                "https://ollama.example.com",
                "http://192.168.1.5:11434"
        })
        @DisplayName("Full URLs pass through unchanged")
        void fullUrl(String url) {
            assertThat(BaseUrlValidator.normalize(url)).isEqualTo(url);
        }

        @ParameterizedTest(name = "\"{0}\" → \"http://{0}\"")
        @ValueSource(strings = {
                "127.0.0.1:11434",
                "localhost",
                "192.168.1.5:11434",
                "ollama.example.com"
        })
        @DisplayName("Missing protocol gets http:// prepended")
        void noProtocol(String input) {
            assertThat(BaseUrlValidator.normalize(input))
                    .isEqualTo("http://" + input);
        }

        @Test
        @DisplayName("Leading and trailing whitespace is trimmed")
        void trimmed() {
            assertThat(BaseUrlValidator.normalize("  http://localhost  "))
                    .isEqualTo("http://localhost");
        }
    }

    @Nested
    @DisplayName("Invalid URLs")
    class Invalid {

        @ParameterizedTest
        @ValueSource(strings = {
                "http://",
                "http:///path",
                "not a url",
                "http://:",
                "://localhost"
        })
        @DisplayName("Rejected URLs return null")
        void rejected(String input) {
            assertThat(BaseUrlValidator.normalize(input)).isNull();
        }
    }
}