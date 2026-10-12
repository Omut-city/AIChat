package omut.aichat.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Nested
    @DisplayName("split")
    class Split {

        @Test
        @DisplayName("Full URL is split into protocol, host, port")
        void fullUrl() {
            BaseUrlValidator.Parts p = BaseUrlValidator.split("http://10.0.0.5:8080");
            assertThat(p).isNotNull();
            assertThat(p.protocol()).isEqualTo("http");
            assertThat(p.host()).isEqualTo("10.0.0.5");
            assertThat(p.port()).isEqualTo(8080);
        }

        @Test
        @DisplayName("Missing port defaults to 11434")
        void defaultPort() {
            BaseUrlValidator.Parts p = BaseUrlValidator.split("http://localhost");
            assertThat(p).isNotNull();
            assertThat(p.protocol()).isEqualTo("http");
            assertThat(p.host()).isEqualTo("localhost");
            assertThat(p.port()).isEqualTo(11434);
        }

        @Test
        @DisplayName("Missing protocol defaults to http")
        void defaultProtocol() {
            BaseUrlValidator.Parts p = BaseUrlValidator.split("192.168.1.5:9000");
            assertThat(p).isNotNull();
            assertThat(p.protocol()).isEqualTo("http");
            assertThat(p.host()).isEqualTo("192.168.1.5");
            assertThat(p.port()).isEqualTo(9000);
        }

        @Test
        @DisplayName("HTTPS is preserved")
        void https() {
            BaseUrlValidator.Parts p = BaseUrlValidator.split("https://ollama.example.com");
            assertThat(p).isNotNull();
            assertThat(p.protocol()).isEqualTo("https");
            assertThat(p.host()).isEqualTo("ollama.example.com");
            assertThat(p.port()).isEqualTo(11434);
        }

        @Test
        @DisplayName("Blank input returns null")
        void blank() {
            assertThat(BaseUrlValidator.split(null)).isNull();
            assertThat(BaseUrlValidator.split("")).isNull();
            assertThat(BaseUrlValidator.split("   ")).isNull();
        }

        @Test
        @DisplayName("Unparseable input returns null")
        void unparseable() {
            assertThat(BaseUrlValidator.split("not a url")).isNull();
            assertThat(BaseUrlValidator.split("http://")).isNull();
        }
    }

    @Nested
    @DisplayName("Parts")
    class Parts {

        @Test
        @DisplayName("toBaseUrl rebuilds the URL")
        void rebuild() {
            BaseUrlValidator.Parts p =
                    new BaseUrlValidator.Parts("http", "10.0.0.5", 8080);
            assertThat(p.toBaseUrl()).isEqualTo("http://10.0.0.5:8080");
        }

        @Test
        @DisplayName("Constructor rejects blank protocol")
        void blankProtocol() {
            assertThatThrownBy(() ->
                    new BaseUrlValidator.Parts("", "localhost", 11434))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("protocol");
        }

        @Test
        @DisplayName("Constructor rejects blank host")
        void blankHost() {
            assertThatThrownBy(() ->
                    new BaseUrlValidator.Parts("http", "", 11434))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("host");
        }

        @Test
        @DisplayName("Constructor rejects non-positive port")
        void badPort() {
            assertThatThrownBy(() ->
                    new BaseUrlValidator.Parts("http", "localhost", 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("port");
        }

        @Test
        @DisplayName("Constructor rejects port > 65535")
        void highPort() {
            assertThatThrownBy(() ->
                    new BaseUrlValidator.Parts("http", "localhost", 70000))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("port");
        }
    }
}