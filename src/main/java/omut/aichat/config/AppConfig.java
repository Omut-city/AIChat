package omut.aichat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:11434";
    private static final String DEFAULT_MODEL = "qwen2.5:7b";

    private static final String PROPERTIES_FILE = "/application.properties";

    private final Properties properties = new Properties();
    private final UserConfig userConfig = new UserConfig();

    private volatile String baseUrl;
    private volatile String systemPrompt;

    public AppConfig() {
        load();
        this.baseUrl = resolveBaseUrl();
        this.systemPrompt = resolveSystemPrompt();
    }

    private void load() {
        try (InputStream in = AppConfig.class.getResourceAsStream(PROPERTIES_FILE)) {
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

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            this.baseUrl = defaultBaseUrl();
            userConfig.setBaseUrl(null);
        } else {
            this.baseUrl = baseUrl.trim();
            userConfig.setBaseUrl(this.baseUrl);
        }
    }

    public String defaultBaseUrl() {
        return properties.getProperty(ConfigKeys.OLLAMA_BASE_URL, DEFAULT_BASE_URL);
    }

    public String defaultModel() {
        return properties.getProperty(ConfigKeys.OLLAMA_DEFAULT_MODEL, DEFAULT_MODEL);
    }

    public int requestTimeoutMinutes() {
        return getInt(ConfigKeys.OLLAMA_REQUEST_TIMEOUT_MINUTES, 5);
    }

    public double temperature() {
        return getDouble(ConfigKeys.OLLAMA_TEMPERATURE, 0.5);
    }

    public String windowTitle() {
        return properties.getProperty(ConfigKeys.APP_WINDOW_TITLE, "AIChat - Local Offline LLM");
    }

    public int windowWidth() {
        return getInt(ConfigKeys.APP_WINDOW_WIDTH, 640);
    }

    public int windowHeight() {
        return getInt(ConfigKeys.APP_WINDOW_HEIGHT, 540);
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            this.systemPrompt = defaultSystemPrompt();
            userConfig.setSystemPrompt(null);
        } else {
            this.systemPrompt = prompt.trim();
            userConfig.setSystemPrompt(this.systemPrompt);
        }
    }

    public String defaultSystemPrompt() {
        return properties.getProperty(ConfigKeys.CHAT_SYSTEM_PROMPT, "");
    }

    private String resolveBaseUrl() {
        String userValue = userConfig.getBaseUrl();
        if (userValue != null && !userValue.isBlank()) {
            return userValue;
        }
        return properties.getProperty(ConfigKeys.OLLAMA_BASE_URL, DEFAULT_BASE_URL);
    }

    private String resolveSystemPrompt() {
        String userValue = userConfig.getSystemPrompt();
        if (userValue != null) {
            return userValue;
        }
        return properties.getProperty(ConfigKeys.CHAT_SYSTEM_PROMPT, "");
    }

    public int historyMaxMessages() {
        return Math.max(2, getInt(ConfigKeys.CHAT_HISTORY_MAX_MESSAGES, 20));
    }

    public String highlightJsPath() {
        return properties.getProperty(ConfigKeys.HIGHLIGHT_JS_PATH, "/highlight/highlight.min.js");
    }

    public String highlightCssPath() {
        return properties.getProperty(ConfigKeys.HIGHLIGHT_CSS_PATH, "/highlight/github.min.css");
    }

    public String chatTemplatePath() {
        return properties.getProperty(ConfigKeys.CHAT_TEMPLATE_PATH, "/templates/chat.html");
    }

    public int attachMaxChars() {
        return getInt(ConfigKeys.CHAT_ATTACH_MAX_CHARS, 100_000);
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

    public String defaultTheme() {
        return properties.getProperty(ConfigKeys.APP_THEME, "NordLight");
    }

    public String theme() {
        String userValue = userConfig.getTheme();
        if (userValue != null && !userValue.isBlank()) {
            return userValue;
        }
        return defaultTheme();
    }

    public void setTheme(String theme) {
        if (theme == null || theme.isBlank()) {
            userConfig.setTheme(null);
        } else {
            userConfig.setTheme(theme);
        }
    }
}