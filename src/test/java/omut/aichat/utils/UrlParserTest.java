package omut.aichat.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlParserTest {

    @Nested
    @DisplayName("normalize")
    class Normalize {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("null, empty, or blank → null")
        void blankInput(String input) {
            assertThat(UrlParser.normalize(input)).isNull();
        }

        @ParameterizedTest(name = "\"{0}\" → \"{0}\"")
        @ValueSource(strings = {
                "http://127.0.0.1:11434",
                "http://localhost:11434",
                "https://ollama.example.com",
                "http://192.168.1.5:11434"
        })
        @DisplayName("Full URLs pass through unchanged")
        void fullUrl(String url) {
            assertThat(UrlParser.normalize(url)).isEqualTo(url);
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
            assertThat(UrlParser.normalize(input))
                    .isEqualTo("http://" + input);
        }

        @Test
        @DisplayName("Whitespace is trimmed")
        void trimmed() {
            assertThat(UrlParser.normalize("  http://localhost  "))
                    .isEqualTo("http://localhost");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "http://",
                "http:///path",
                "not a url",
                "http://:",
                "://localhost"
        })
        @DisplayName("Unparseable URLs → null")
        void invalid(String input) {
            assertThat(UrlParser.normalize(input)).isNull();
        }
    }

    @Nested
    @DisplayName("parse")
    class Parse {

        @Test
        @DisplayName("Full URL is split into protocol, host, port")
        void fullUrl() {
            UrlParser.Parsed p = UrlParser.parse("http://10.0.0.5:8080");
            assertThat(p).isNotNull();
            assertThat(p.protocol()).isEqualTo("http");
            assertThat(p.host()).isEqualTo("10.0.0.5");
            assertThat(p.port()).isEqualTo(8080);
        }

        @Test
        @DisplayName("Missing port defaults to 11434")
        void defaultPort() {
            UrlParser.Parsed p = UrlParser.parse("http://localhost");
            assertThat(p).isNotNull();
            assertThat(p.port()).isEqualTo(UrlParser.DEFAULT_PORT);
        }

        @Test
        @DisplayName("Missing protocol defaults to http")
        void defaultProtocol() {
            UrlParser.Parsed p = UrlParser.parse("192.168.1.5:9000");
            assertThat(p).isNotNull();
            assertThat(p.protocol()).isEqualTo(UrlParser.DEFAULT_PROTOCOL);
            assertThat(p.host()).isEqualTo("192.168.1.5");
            assertThat(p.port()).isEqualTo(9000);
        }

        @Test
        @DisplayName("HTTPS is preserved")
        void https() {
            UrlParser.Parsed p = UrlParser.parse("https://ollama.example.com");
            assertThat(p).isNotNull();
            assertThat(p.protocol()).isEqualTo("https");
            assertThat(p.host()).isEqualTo("ollama.example.com");
            assertThat(p.port()).isEqualTo(UrlParser.DEFAULT_PORT);
        }

        @Test
        @DisplayName("Blank input → null")
        void blank() {
            assertThat(UrlParser.parse(null)).isNull();
            assertThat(UrlParser.parse("")).isNull();
            assertThat(UrlParser.parse("   ")).isNull();
        }

        @Test
        @DisplayName("Unparseable input → null")
        void unparseable() {
            assertThat(UrlParser.parse("not a url")).isNull();
            assertThat(UrlParser.parse("http://")).isNull();
        }

        @Test
        @DisplayName("Round-trip via toUrl()")
        void roundTrip() {
            UrlParser.Parsed p = UrlParser.parse("http://10.0.0.5:8080");
            assertThat(p.toUrl()).isEqualTo("http://10.0.0.5:8080");
        }
    }

    @Nested
    @DisplayName("host")
    class Host {

        @Test
        @DisplayName("Full URL returns its host")
        void fromUrl() {
            assertThat(UrlParser.host("http://10.0.0.5:8080"))
                    .isEqualTo("10.0.0.5");
        }

        @Test
        @DisplayName("URL without scheme is parsed")
        void noScheme() {
            assertThat(UrlParser.host("http://localhost"))
                    .isEqualTo("localhost");
        }

        @Test
        @DisplayName("Null input returns null")
        void nullInput() {
            assertThat(UrlParser.host(null)).isNull();
        }

        @Test
        @DisplayName("Blank input returns input")
        void blankInput() {
            assertThat(UrlParser.host("   ")).isEqualTo("   ");
        }

        @Test
        @DisplayName("Unparseable input returns input as-is")
        void unparseable() {
            assertThat(UrlParser.host("not a url")).isEqualTo("not a url");
        }
    }

    @Nested
    @DisplayName("Parsed validation")
    class ParsedValidation {

        @Test
        @DisplayName("Rejects blank protocol")
        void blankProtocol() {
            assertThatThrownBy(() -> new UrlParser.Parsed("", "localhost", 11434))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("protocol");
        }

        @Test
        @DisplayName("Rejects blank host")
        void blankHost() {
            assertThatThrownBy(() -> new UrlParser.Parsed("http", "", 11434))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("host");
        }

        @Test
        @DisplayName("Rejects port <= 0")
        void lowPort() {
            assertThatThrownBy(() -> new UrlParser.Parsed("http", "localhost", 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("port");
        }

        @Test
        @DisplayName("Rejects port > 65535")
        void highPort() {
            assertThatThrownBy(() -> new UrlParser.Parsed("http", "localhost", 70000))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("port");
        }
    }
}