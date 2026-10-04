package omut.aichat.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class AppConfig {

    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:11434";
    private static final String DEFAULT_MODEL = "qwen2.5:7b";

    private static final String PROPERTIES_FILE = "/application.properties";

    private final Properties properties = new Properties();
    private final UserConfig userConfig = new UserConfig();

    private String baseUrl;
    private String systemPrompt;

    public AppConfig() {
        load();
        this.baseUrl = resolveBaseUrl();
        this.systemPrompt = resolveSystemPrompt();
    }

    private void load() {
        try (InputStream in = AppConfig.class.getResourceAsStream(PROPERTIES_FILE)) {
            if (in != null) {
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
            }
        } catch (IOException e) {
            // Fall back to defaults; properties file is optional
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
        return properties.getProperty("ollama.base.url", DEFAULT_BASE_URL);
    }

    public String defaultModel() {
        return properties.getProperty("ollama.default.model", DEFAULT_MODEL);
    }

    public int requestTimeoutMinutes() {
        return getInt("ollama.request.timeout.minutes", 5);
    }

    public double temperature() {
        return getDouble("ollama.temperature", 0.5);
    }
    public String windowTitle() {
        return properties.getProperty("app.window.title", "AIChat - Local Offline LLM");
    }

    public int windowWidth() {
        return getInt("app.window.width", 640);
    }

    public int windowHeight() {
        return getInt("app.window.height", 540);
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
        return properties.getProperty("chat.system.prompt", "");
    }

    private String resolveBaseUrl() {
        String userValue = userConfig.getBaseUrl();
        if (userValue != null && !userValue.isBlank()) {
            return userValue;
        }
        return properties.getProperty("ollama.base.url", DEFAULT_BASE_URL);
    }

    private String resolveSystemPrompt() {
        String userValue = userConfig.getSystemPrompt();
        if (userValue != null) {
            return userValue;
        }
        return properties.getProperty("chat.system.prompt", "");
    }

    public int historyMaxMessages() {
        return Math.max(2, getInt("chat.history.max.messages", 20));
    }

    public String highlightJsPath() {
        return properties.getProperty("highlight.js.path", "/highlight/highlight.min.js");
    }

    public String highlightCssPath() {
        return properties.getProperty("highlight.css.path", "/highlight/github.min.css");
    }

    public String chatTemplatePath() {
        return properties.getProperty("chat.template.path", "/templates/chat.html");
    }

    public int attachMaxChars() {
        return getInt("chat.attach.max.chars", 100_000);
    }

    private int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double getDouble(String key, double defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public String defaultTheme() {
        return properties.getProperty("app.theme", "NordLight");
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