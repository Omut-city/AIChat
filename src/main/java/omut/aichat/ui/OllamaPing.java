package omut.aichat.ui;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Pings Ollama's {@code /api/tags} endpoint and reports the result
 * in a short human-readable string.
 * <p>
 * Used by {@link ServerEditDialog} for the "Test" button. Kept as a
 * standalone utility so the same logic can be reused — for example,
 * by a future online/offline indicator, or by a background poller
 * that needs to check several servers without opening the dialog.
 * <p>
 * Stateless and thread-safe. Never throws: any failure (unresolved
 * host, refused connection, bad response, malformed URL) maps to
 * {@code null}.
 */
public final class OllamaPing {

    private static final int DEFAULT_TIMEOUT_MS = 2000;

    private OllamaPing() {}

    /**
     * Pings {@code protocol://host:port/api/tags} with the default
     * timeout and returns:
     * <ul>
     *   <li>{@code "Connected"} if the endpoint responds 200 with no
     *       recognizable models</li>
     *   <li>{@code "Connected (N models)"} if it responds 200 and
     *       {@code N} occurrences of {@code "name"} were found</li>
     *   <li>{@code null} on any failure</li>
     * </ul>
     */
    public static String ping(String protocol, String host, int port) {
        return ping(protocol, host, port, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Same as {@link #ping(String, String, int)} but with an explicit
     * timeout in milliseconds. Exposed for tests that need a short
     * timeout.
     */
    public static String ping(String protocol, String host, int port, int timeoutMillis) {
        if (protocol == null || protocol.isBlank()) protocol = "http";
        if (host == null || host.isBlank()) return null;
        if (port <= 0 || port > 65535) return null;

        String baseUrl = protocol + "://" + host + ":" + port;

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)
                    URI.create(baseUrl + "/api/tags").toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);

            int code = connection.getResponseCode();
            if (code != 200) return null;

            byte[] body = connection.getInputStream().readAllBytes();
            String text = new String(body, StandardCharsets.UTF_8);
            int count = countOccurrences(text, "\"name\"");
            return count == 0 ? "Connected" : "Connected (" + count + " models)";
        } catch (IOException | IllegalArgumentException e) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}