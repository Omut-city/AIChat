package omut.aichat.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TimeFormatTest {

    @Test
    @DisplayName("0 ms → \"0 ms\"")
    void zero() {
        assertThat(TimeFormat.shortDuration(0)).isEqualTo("0 ms");
    }

    @ParameterizedTest(name = "{0} ms → \"{1}\"")
    @CsvSource({
            "1,      1 ms",
            "847,    847 ms",
            "999,    999 ms"
    })
    @DisplayName("Sub-second values render as whole milliseconds")
    void subSecond(long millis, String expected) {
        assertThat(TimeFormat.shortDuration(millis)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} ms → \"{1}\"")
    @CsvSource({
            "1000,    1.0 s",
            "1500,    1.5 s",
            "12000,   12.0 s",
            "59500,   59.5 s",
            "60000,   60.0 s"
    })
    @DisplayName("Longer durations render with one decimal in seconds")
    void seconds(long millis, String expected) {
        assertThat(TimeFormat.shortDuration(millis)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Boundary at 1000 ms switches from ms to s")
    void boundary() {
        assertThat(TimeFormat.shortDuration(999)).isEqualTo("999 ms");
        assertThat(TimeFormat.shortDuration(1000)).isEqualTo("1.0 s");
    }

    @Test
    @DisplayName("Decimal separator is always a dot, not a comma")
    void localeStable() {
        assertThat(TimeFormat.shortDuration(1500)).contains(".");
        assertThat(TimeFormat.shortDuration(1500)).doesNotContain(",");
    }
}