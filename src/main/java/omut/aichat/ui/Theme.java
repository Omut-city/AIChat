package omut.aichat.ui;

import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;

import java.util.function.Supplier;

/**
 * Available UI themes.
 * <p>
 * The {@code id} is stored in user config and must remain stable.
 */
public enum Theme {

    NORD_LIGHT("NordLight", "github.min.css", "theme-light",
            () -> new NordLight().getUserAgentStylesheet()),
    NORD_DARK("NordDark", "github-dark.min.css", "theme-dark",
            () -> new NordDark().getUserAgentStylesheet()),
    PRIMER_LIGHT("PrimerLight", "github.min.css", "theme-light",
            () -> new PrimerLight().getUserAgentStylesheet()),
    PRIMER_DARK("PrimerDark", "github-dark.min.css", "theme-dark",
            () -> new PrimerDark().getUserAgentStylesheet());

    private final String id;
    private final String highlightCss;
    private final String bodyClass;
    private final Supplier<String> stylesheet;

    Theme(
            String id,
            String highlightCss,
            String bodyClass,
            Supplier<String> stylesheet
    ) {
        this.id = id;
        this.highlightCss = highlightCss;
        this.bodyClass = bodyClass;
        this.stylesheet = stylesheet;
    }

    public String id() { return id; }
    public String highlightCss() { return highlightCss; }
    public String bodyClass() { return bodyClass; }

    /** Applies this theme to the running JavaFX application. */
    public void apply() {
        Application.setUserAgentStylesheet(stylesheet.get());
    }

    public static Theme fromId(String id) {
        if (id == null) return NORD_LIGHT;
        for (Theme t : values()) {
            if (t.id.equals(id)) return t;
        }
        return NORD_LIGHT;
    }
}