package omut.aichat.ui;

import java.net.URI;

/**
 * Validates and normalises Ollama base URLs coming from user input.
 * <p>
 * Missing protocol is not an error — {@code "192.168.1.5:11434"} is
 * accepted and prefixed with {@code http://}. Anything without a
 * parseable host is rejected.
 */
final class BaseUrlValidator {

    private BaseUrlValidator() {}

    /**
     * Returns the normalised URL, or {@code null} if {@code raw} is
     * blank or cannot be parsed as a URL with a host.
     */
    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return null;

        String url = raw.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }

        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            return url;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}