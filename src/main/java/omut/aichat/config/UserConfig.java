package omut.aichat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Persistent user settings stored in ~/.aichat/config.properties.
 * Survives application restarts.
 */
public class UserConfig {

    private static final Logger log = LoggerFactory.getLogger(UserConfig.class);

    private static final String CONFIG_DIR = ".aichat";
    private static final String CONFIG_FILE = "config.properties";

    private final Path file;
    private final Properties properties = new Properties();

    public UserConfig() {
        this(Path.of(System.getProperty("user.home"), CONFIG_DIR, CONFIG_FILE));
    }

    UserConfig(Path file) {
        this.file = file;
        load();
    }

    private void load() {
        if (!Files.exists(file)) {
            log.debug("User config not found at {} — starting with empty settings", file);
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
            log.debug("Loaded {} user settings from {}", properties.size(), file);
        } catch (IOException e) {
            log.warn("Failed to read user config {} — starting with empty settings: {}",
                    file, e.getMessage());
        }
    }

    private synchronized void save() {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                properties.store(writer, "AIChat user settings");
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Saved {} user settings to {}", properties.size(), file);
        } catch (IOException e) {
            log.warn("Failed to save user config {} — settings will not persist: {}",
                    file, e.getMessage());
        }
    }

    public synchronized String getBaseUrl() {
        return properties.getProperty(ConfigKeys.OLLAMA_BASE_URL);
    }

    public synchronized void setBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            properties.remove(ConfigKeys.OLLAMA_BASE_URL);
        } else {
            properties.setProperty(ConfigKeys.OLLAMA_BASE_URL, baseUrl);
        }
        save();
    }

    public synchronized String getSystemPrompt() {
        return properties.getProperty(ConfigKeys.CHAT_SYSTEM_PROMPT);
    }

    public synchronized void setSystemPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            properties.remove(ConfigKeys.CHAT_SYSTEM_PROMPT);
        } else {
            properties.setProperty(ConfigKeys.CHAT_SYSTEM_PROMPT, prompt);
        }
        save();
    }

    public synchronized String getSelectedModel() {
        return properties.getProperty(ConfigKeys.OLLAMA_SELECTED_MODEL);
    }

    public synchronized void setSelectedModel(String model) {
        if (model == null || model.isBlank()) {
            properties.remove(ConfigKeys.OLLAMA_SELECTED_MODEL);
        } else {
            properties.setProperty(ConfigKeys.OLLAMA_SELECTED_MODEL, model);
        }
        save();
    }

    public synchronized String getTheme() {
        return properties.getProperty(ConfigKeys.APP_THEME);
    }

    public synchronized void setTheme(String theme) {
        if (theme == null || theme.isBlank()) {
            properties.remove(ConfigKeys.APP_THEME);
        } else {
            properties.setProperty(ConfigKeys.APP_THEME, theme);
        }
        save();
    }

    public synchronized Double getTemperature() {
        return parseDouble(ConfigKeys.OLLAMA_TEMPERATURE);
    }

    public synchronized void setTemperature(Double value) {
        if (value == null) {
            properties.remove(ConfigKeys.OLLAMA_TEMPERATURE);
        } else {
            properties.setProperty(ConfigKeys.OLLAMA_TEMPERATURE, value.toString());
        }
        save();
    }

    public synchronized Integer getRequestTimeoutMinutes() {
        return parseInt(ConfigKeys.OLLAMA_REQUEST_TIMEOUT_MINUTES);
    }

    public synchronized void setRequestTimeoutMinutes(Integer value) {
        if (value == null) {
            properties.remove(ConfigKeys.OLLAMA_REQUEST_TIMEOUT_MINUTES);
        } else {
            properties.setProperty(ConfigKeys.OLLAMA_REQUEST_TIMEOUT_MINUTES, value.toString());
        }
        save();
    }

    public synchronized Integer getHistoryMaxMessages() {
        return parseInt(ConfigKeys.CHAT_HISTORY_MAX_MESSAGES);
    }

    public synchronized void setHistoryMaxMessages(Integer value) {
        if (value == null) {
            properties.remove(ConfigKeys.CHAT_HISTORY_MAX_MESSAGES);
        } else {
            properties.setProperty(ConfigKeys.CHAT_HISTORY_MAX_MESSAGES, value.toString());
        }
        save();
    }

    public synchronized Integer getAttachMaxChars() {
        return parseInt(ConfigKeys.CHAT_ATTACH_MAX_CHARS);
    }

    public synchronized void setAttachMaxChars(Integer value) {
        if (value == null) {
            properties.remove(ConfigKeys.CHAT_ATTACH_MAX_CHARS);
        } else {
            properties.setProperty(ConfigKeys.CHAT_ATTACH_MAX_CHARS, value.toString());
        }
        save();
    }

    private Double parseDouble(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) return null;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid double in user config: {} = '{}' — ignoring", key, value);
            return null;
        }
    }

    private Integer parseInt(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid integer in user config: {} = '{}' — ignoring", key, value);
            return null;
        }
    }
}