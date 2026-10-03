package omut.aichat.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Persistent user settings stored in ~/.aichat/config.properties.
 * Survives application restarts.
 */
public class UserConfig {

    private static final String CONFIG_DIR = ".aichat";
    private static final String CONFIG_FILE = "config.properties";

    private final Path file;
    private final Properties properties = new Properties();

    public UserConfig() {
        this.file = Path.of(System.getProperty("user.home"), CONFIG_DIR, CONFIG_FILE);
        load();
    }

    private void load() {
        if (!Files.exists(file)) return;
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        } catch (IOException e) {
            // ignore — start with empty settings
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                properties.store(out, "AIChat user settings");
            }
        } catch (IOException e) {
            // ignore — settings won't persist, app keeps working
        }
    }

    public String getBaseUrl() {
        return properties.getProperty("ollama.base.url");
    }

    public void setBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            properties.remove("ollama.base.url");
        } else {
            properties.setProperty("ollama.base.url", baseUrl);
        }
        save();
    }

    public String getSystemPrompt() {
        return properties.getProperty("chat.system.prompt");
    }

    public void setSystemPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            properties.remove("chat.system.prompt");
        } else {
            properties.setProperty("chat.system.prompt", prompt);
        }
        save();
    }

    public void clear() {
        properties.clear();
        save();
    }
}