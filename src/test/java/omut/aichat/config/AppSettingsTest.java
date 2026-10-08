package omut.aichat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppSettingsTest {

    private static AppSettings valid() {
        return new AppSettings("http://localhost:11434", 0.7, 5, 20, 100_000);
    }

    @Test
    @DisplayName("Valid snapshot has all fields")
    void validSnapshot() {
        AppSettings s = valid();
        assertThat(s.baseUrl()).isEqualTo("http://localhost:11434");
        assertThat(s.temperature()).isEqualTo(0.7);
        assertThat(s.requestTimeoutMinutes()).isEqualTo(5);
        assertThat(s.historyMaxMessages()).isEqualTo(20);
        assertThat(s.attachMaxChars()).isEqualTo(100_000);
    }

    @Nested
    @DisplayName("baseUrl validation")
    class BaseUrl {

        @Test
        @DisplayName("null → rejected")
        void nullUrl() {
            assertThatThrownBy(() ->
                    new AppSettings(null, 0.5, 5, 20, 100_000))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("baseUrl");
        }

        @Test
        @DisplayName("blank → rejected")
        void blankUrl() {
            assertThatThrownBy(() ->
                    new AppSettings("   ", 0.5, 5, 20, 100_000))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("baseUrl");
        }

        @Test
        @DisplayName("empty → rejected")
        void emptyUrl() {
            assertThatThrownBy(() ->
                    new AppSettings("", 0.5, 5, 20, 100_000))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("temperature validation")
    class Temperature {

        @ParameterizedTest(name = "{0} → rejected")
        @ValueSource(doubles = {-0.1, -1.0, 2.1, 3.0, 100.0})
        @DisplayName("Out-of-range → rejected")
        void outOfRange(double value) {
            assertThatThrownBy(() ->
                    new AppSettings("http://x", value, 5, 20, 100_000))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("temperature");
        }

        @ParameterizedTest(name = "{0} → accepted")
        @ValueSource(doubles = {0.0, 0.5, 1.0, 1.5, 2.0})
        @DisplayName("Boundary and interior → accepted")
        void inRange(double value) {
            AppSettings s = new AppSettings("http://x", value, 5, 20, 100_000);
            assertThat(s.temperature()).isEqualTo(value);
        }
    }

    @Nested
    @DisplayName("requestTimeoutMinutes validation")
    class Timeout {

        @ParameterizedTest(name = "{0} → rejected")
        @ValueSource(ints = {0, -1, 121, 1000})
        @DisplayName("Out-of-range → rejected")
        void outOfRange(int value) {
            assertThatThrownBy(() ->
                    new AppSettings("http://x", 0.5, value, 20, 100_000))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("requestTimeoutMinutes");
        }

        @ParameterizedTest(name = "{0} → accepted")
        @ValueSource(ints = {1, 5, 60, 120})
        @DisplayName("Boundary and interior → accepted")
        void inRange(int value) {
            AppSettings s = new AppSettings("http://x", 0.5, value, 20, 100_000);
            assertThat(s.requestTimeoutMinutes()).isEqualTo(value);
        }
    }

    @Nested
    @DisplayName("historyMaxMessages validation")
    class History {

        @ParameterizedTest(name = "{0} → rejected")
        @ValueSource(ints = {0, 1, -1, 1001, 10_000})
        @DisplayName("Out-of-range → rejected")
        void outOfRange(int value) {
            assertThatThrownBy(() ->
                    new AppSettings("http://x", 0.5, 5, value, 100_000))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("historyMaxMessages");
        }

        @ParameterizedTest(name = "{0} → accepted")
        @ValueSource(ints = {2, 20, 500, 1000})
        @DisplayName("Boundary and interior → accepted")
        void inRange(int value) {
            AppSettings s = new AppSettings("http://x", 0.5, 5, value, 100_000);
            assertThat(s.historyMaxMessages()).isEqualTo(value);
        }
    }

    @Nested
    @DisplayName("attachMaxChars validation")
    class Attach {

        @ParameterizedTest(name = "{0} → rejected")
        @ValueSource(ints = {0, -1, -1000})
        @DisplayName("Non-positive → rejected")
        void nonPositive(int value) {
            assertThatThrownBy(() ->
                    new AppSettings("http://x", 0.5, 5, 20, value))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("attachMaxChars");
        }

        @ParameterizedTest(name = "{0} → accepted")
        @ValueSource(ints = {1, 1000, 100_000, 10_000_000})
        @DisplayName("Positive → accepted")
        void positive(int value) {
            AppSettings s = new AppSettings("http://x", 0.5, 5, 20, value);
            assertThat(s.attachMaxChars()).isEqualTo(value);
        }
    }
}