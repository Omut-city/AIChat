package omut.aichat.utils;

import java.net.URI;

/**
 * Parsing and normalization helpers for Ollama base URLs.
 * <p>
 * Missing protocol is not an error — {@code "192.168.1.5:11434"} is
 * accepted and prefixed with {@code http://}. Anything without a
 * parseable host is rejected.
 * <p>
 * Port defaults to {@value #DEFAULT_PORT} when missing.
 * Protocol defaults to {@value #DEFAULT_PROTOCOL}.
 * <p>
 * Stateless — all methods are static.
 */
public final class UrlParser {

    public static final int DEFAULT_PORT = 11434;
    public static final String DEFAULT_PROTOCOL = "http";

    private UrlParser() {}

    /**
     * Protocol, host and port extracted from a URL.
     * All fields are non-null; port is in [1, 65535].
     */
    public record Parsed(String protocol, String host, int port) {
        public Parsed {
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

        /** Rebuilds the URL from the parts. */
        public String toUrl() {
            return protocol + "://" + host + ":" + port;
        }
    }

    /**
     * Parses a raw URL string into protocol, host and port.
     * Missing protocol defaults to {@code http}; missing port to
     * {@value #DEFAULT_PORT}.
     * <p>
     * Returns {@code null} if the input is blank or has no parseable
     * host.
     */
    public static Parsed parse(String raw) {
        if (raw == null || raw.isBlank()) return null;

        String url = normalize(raw);
        if (url == null) return null;

        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || host.isBlank()) return null;

            String protocol = uri.getScheme();
            if (protocol == null || protocol.isBlank()) {
                protocol = DEFAULT_PROTOCOL;
            }
            int port = uri.getPort();
            if (port <= 0) port = DEFAULT_PORT;

            return new Parsed(protocol, host, port);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Normalizes a raw URL: trims whitespace and prepends
     * {@code http://} if no scheme is present.
     * <p>
     * Returns {@code null} if the input is blank or does not have a
     * parseable host.
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return null;

        String url = raw.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = DEFAULT_PROTOCOL + "://" + url;
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
     * Extracts the host from a URL. Returns the input as-is if it is
     * {@code null}, blank, or cannot be parsed — this method never
     * throws and never returns {@code null} for a non-null input.
     */
    public static String host(String url) {
        if (url == null || url.isBlank()) return url;
        try {
            String host = URI.create(url).getHost();
            return host != null ? host : url;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }
}