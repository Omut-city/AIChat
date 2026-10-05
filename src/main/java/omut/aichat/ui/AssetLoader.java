package omut.aichat.ui;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads and caches classpath assets (CSS, JS, HTML templates) used
 * by the WebView.
 * <p>
 * All access happens from the JavaFX Application Thread; the internal
 * caches are plain HashMaps and not synchronized.
 */
public final class AssetLoader {

    private final Map<String, String> assetCache = new HashMap<>();
    private HtmlTemplate template;

    /** Wraps a body fragment into the chat HTML template for a theme. */
    public String wrapInHtml(
            String body,
            Theme theme,
            String templatePath,
            String highlightJsPath
    ) {
        String css = asset("/highlight/" + theme.highlightCss());
        String js = asset(highlightJsPath);
        String base = template(templatePath).render(css, js, body);
        return base.replace("<body>", "<body class=\"" + theme.bodyClass() + "\">");
    }

    private HtmlTemplate template(String path) {
        if (template == null) {
            template = new HtmlTemplate(path);
        }
        return template;
    }

    private String asset(String path) {
        return assetCache.computeIfAbsent(path, HtmlTemplate::readResource);
    }
}