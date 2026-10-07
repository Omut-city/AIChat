package omut.aichat.ui;

import omut.aichat.config.AppConfig;

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
    private final Map<String, HtmlTemplate> templateCache = new HashMap<>();

    /** Wraps a body fragment into the chat HTML template for a theme. */
    public String wrapInHtml(String body, Theme theme, AppConfig config) {
        String highlightCss = asset("/highlight/" + theme.highlightCss());
        String chatCss = asset(config.chatCssPath());
        String highlightJs = asset(config.highlightJsPath());
        String chatJs = asset(config.chatJsPath());

        String css = highlightCss + "\n" + chatCss;
        String js = highlightJs + "\n" + chatJs;

        String base = template(config.chatTemplatePath()).render(css, js, body);
        return base.replace("<body>", "<body class=\"" + theme.bodyClass() + "\">");
    }

    private HtmlTemplate template(String path) {
        return templateCache.computeIfAbsent(path, HtmlTemplate::new);
    }

    private String asset(String path) {
        return assetCache.computeIfAbsent(path, HtmlTemplate::readResource);
    }
}