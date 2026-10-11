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

    private static final int DEFAULT_PORT = 11434;

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

    /**
     * Splits a URL into protocol, host and port. Missing port defaults
     * to 11434; missing protocol defaults to {@code http}. Returns
     * {@code null} for blank or unparseable input.
     */
    public static Parts split(String raw) {
        String normalized = normalize(raw);
        if (normalized == null) return null;

        try {
            URI uri = URI.create(normalized);
            String protocol = uri.getScheme();
            String host = uri.getHost();
            if (protocol == null || host == null || host.isBlank()) {
                return null;
            }
            int port = uri.getPort();
            if (port <= 0) port = DEFAULT_PORT;
            return new Parts(protocol, host, port);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Protocol, host and port extracted from a base URL.
     * {@code protocol} is never {@code null}; {@code port} is always
     * positive.
     */
    public record Parts(String protocol, String host, int port) {
        public Parts {
            if (protocol == null || protocol.isBlank()) {
                throw new IllegalArgumentException("protocol must not be blank");
            }
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("host must not be blank");
            }
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("port out of range: " + port);
            }
        }

        public String toBaseUrl() {
            return protocol + "://" + host + ":" + port;
        }
    }
}