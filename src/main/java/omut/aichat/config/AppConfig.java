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

    private String baseUrl;
    private String systemPrompt;

    public AppConfig() {
        load();
        this.baseUrl = properties.getProperty("ollama.base.url", DEFAULT_BASE_URL);
        this.systemPrompt = properties.getProperty("chat.system.prompt", "");
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
        } else {
            this.baseUrl = baseUrl.trim();
        }
    }

    public void resetToDefault() {
        this.baseUrl = defaultBaseUrl();
    }

    public String defaultBaseUrl() {
        return properties.getProperty("ollama.base.url", DEFAULT_BASE_URL);
    }

    public String defaultModel() {
        return properties.getProperty("ollama.default.model", DEFAULT_MODEL);
    }

    public int requestTimeoutMinutes() {
        return Integer.parseInt(properties.getProperty("ollama.request.timeout.minutes", "5"));
    }

    public double temperature() {
        return Double.parseDouble(properties.getProperty("ollama.temperature", "0.5"));
    }

    public String windowTitle() {
        return properties.getProperty("app.window.title", "AIChat - Local Offline LLM");
    }

    public int windowWidth() {
        return Integer.parseInt(properties.getProperty("app.window.width", "640"));
    }

    public int windowHeight() {
        return Integer.parseInt(properties.getProperty("app.window.height", "540"));
    }

    public String systemPrompt() { return systemPrompt; }

    public void setSystemPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            this.systemPrompt = defaultSystemPrompt();
        } else {
            this.systemPrompt = prompt.trim();
        }
    }

    public String defaultSystemPrompt() {
        return properties.getProperty("chat.system.prompt", "");
    }

}