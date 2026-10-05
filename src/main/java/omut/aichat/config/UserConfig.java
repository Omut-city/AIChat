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
        this.file = Path.of(System.getProperty("user.home"), CONFIG_DIR, CONFIG_FILE);
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

    public synchronized void clear() {
        properties.clear();
        save();
    }
}