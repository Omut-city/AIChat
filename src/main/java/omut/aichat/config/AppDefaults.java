package omut.aichat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Bundled read-only defaults from {@code /application.properties}.
 * <p>
 * Provides typed accessors for every key that has a default value.
 * Never touches user overrides — that is {@link AppConfig}'s job.
 * <p>
 * Loading happens once at construction. If the resource is missing
 * or unreadable, hardcoded constants are used and a warning is
 * logged; the application still runs.
 */
final class AppDefaults {

    private static final Logger log = LoggerFactory.getLogger(AppDefaults.class);

    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:11434";
    private static final String DEFAULT_MODEL = "qwen2.5:7b";
    private static final String PROPERTIES_FILE = "/application.properties";

    private final Properties properties = new Properties();

    AppDefaults() {
        load();
    }

    private void load() {
        try (InputStream in = AppDefaults.class.getResourceAsStream(PROPERTIES_FILE)) {
            if (in == null) {
                log.warn("Properties file not found on classpath: {} — using built-in defaults",
                        PROPERTIES_FILE);
                return;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
                log.debug("Loaded {} properties from {}", properties.size(), PROPERTIES_FILE);
            }
        } catch (IOException e) {
            log.warn("Failed to read {} — using built-in defaults: {}", PROPERTIES_FILE, e.getMessage());
        }
    }

    String baseUrl() {
        return properties.getProperty(ConfigKeys.OLLAMA_BASE_URL, DEFAULT_BASE_URL);
    }

    String model() {
        return properties.getProperty(ConfigKeys.OLLAMA_DEFAULT_MODEL, DEFAULT_MODEL);
    }

    int requestTimeoutMinutes() {
        return getInt(ConfigKeys.OLLAMA_REQUEST_TIMEOUT_MINUTES, 5);
    }

    double temperature() {
        return getDouble(ConfigKeys.OLLAMA_TEMPERATURE, 0.5);
    }

    String windowTitle() {
        return properties.getProperty(ConfigKeys.APP_WINDOW_TITLE, "AIChat - Local Offline LLM");
    }

    int windowWidth() {
        return getInt(ConfigKeys.APP_WINDOW_WIDTH, 640);
    }

    int windowHeight() {
        return getInt(ConfigKeys.APP_WINDOW_HEIGHT, 540);
    }

    String systemPrompt() {
        return properties.getProperty(ConfigKeys.CHAT_SYSTEM_PROMPT, "");
    }

    int historyMaxMessages() {
        return Math.max(2, getInt(ConfigKeys.CHAT_HISTORY_MAX_MESSAGES, 20));
    }

    int attachMaxChars() {
        return getInt(ConfigKeys.CHAT_ATTACH_MAX_CHARS, 100_000);
    }

    String highlightJsPath() {
        return properties.getProperty(ConfigKeys.HIGHLIGHT_JS_PATH, "/highlight/highlight.min.js");
    }

    String chatTemplatePath() {
        return properties.getProperty(ConfigKeys.CHAT_TEMPLATE_PATH, "/templates/chat.html");
    }

    String applicationCssPath() {
        return properties.getProperty(ConfigKeys.APP_CSS_PATH, "/css/application.css");
    }

    String chatCssPath() {
        return properties.getProperty(ConfigKeys.CHAT_CSS_PATH, "/css/chat.css");
    }

    String chatJsPath() {
        return properties.getProperty(ConfigKeys.CHAT_JS_PATH, "/templates/chat.js");
    }

    String theme() {
        return properties.getProperty(ConfigKeys.APP_THEME, "NordLight");
    }

    private int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid integer for {} = '{}' — using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    private double getDouble(String key, double defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid double for {} = '{}' — using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }
}