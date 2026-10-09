package omut.aichat.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ThemeTest {

    @Test
    @DisplayName("null id falls back to NordLight")
    void nullId() {
        assertThat(Theme.fromId(null)).isEqualTo(Theme.NORD_LIGHT);
    }

    @ParameterizedTest
    @CsvSource({
            "NordLight,   NORD_LIGHT",
            "NordDark,    NORD_DARK",
            "PrimerLight, PRIMER_LIGHT",
            "PrimerDark,  PRIMER_DARK"
    })
    @DisplayName("Known ids map to their enum constants")
    void knownIds(String id, Theme expected) {
        assertThat(Theme.fromId(id)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "nordlight", "BOGUS"})
    @DisplayName("Unknown ids fall back to NordLight")
    void unknownIds(String id) {
        assertThat(Theme.fromId(id)).isEqualTo(Theme.NORD_LIGHT);
    }

    @Test
    @DisplayName("Each theme has the expected body class")
    void bodyClass() {
        assertThat(Theme.NORD_LIGHT.bodyClass()).isEqualTo("theme-light");
        assertThat(Theme.NORD_DARK.bodyClass()).isEqualTo("theme-dark");
        assertThat(Theme.PRIMER_LIGHT.bodyClass()).isEqualTo("theme-light");
        assertThat(Theme.PRIMER_DARK.bodyClass()).isEqualTo("theme-dark");
    }
}