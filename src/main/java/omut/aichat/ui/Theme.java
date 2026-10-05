package omut.aichat.ui;

/**
 * Available UI themes.
 * <p>
 * The {@code id} is stored in user config and must remain stable.
 */
public enum Theme {

    NORD_LIGHT("NordLight","github.min.css", "theme-light"),
    NORD_DARK ("NordDark","github-dark.min.css", "theme-dark"),
    PRIMER_LIGHT("PrimerLight","github.min.css", "theme-light"),
    PRIMER_DARK ("PrimerDark","github-dark.min.css", "theme-dark");

    private final String id;
    private final String highlightCss;
    private final String bodyClass;

    Theme(String id, String highlightCss, String bodyClass) {
        this.id = id;
        this.highlightCss = highlightCss;
        this.bodyClass = bodyClass;
    }

    public String id() { return id; }
    public String highlightCss() { return highlightCss; }
    public String bodyClass() { return bodyClass; }

    public static Theme fromId(String id) {
        if (id == null) return NORD_LIGHT;
        for (Theme t : values()) {
            if (t.id.equals(id)) return t;
        }
        return NORD_LIGHT;
    }
}