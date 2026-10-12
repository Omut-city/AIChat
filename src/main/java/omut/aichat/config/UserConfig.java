package omut.aichat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Persistent user settings stored in ~/.aichat/config.properties.
 * Survives application restarts.
 * <p>
 * Besides the flat scalar settings, this class is the persistence
 * backend for two collections: named Ollama servers (F18) and named
 * prompts (F13). Both follow the same shape — a comma-separated id
 * list, an active id, and per-item fields keyed by a fixed prefix.
 * The {@code local} server and the {@code default} prompt are special;
 * see {@link AppConfig} for the invariants.
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

    /** Comma-separated id list, in display order. Never {@code null}. */
    public synchronized String getServerList() {
        String raw = properties.getProperty(ConfigKeys.OLLAMA_SERVER_LIST);
        return raw == null ? "" : raw;
    }

    public synchronized void setServerList(String csv) {
        if (csv == null || csv.isBlank()) {
            properties.remove(ConfigKeys.OLLAMA_SERVER_LIST);
        } else {
            properties.setProperty(ConfigKeys.OLLAMA_SERVER_LIST, csv);
        }
        save();
    }

    /** Active server id, or {@code null} if unset. */
    public synchronized String getActiveServer() {
        return properties.getProperty(ConfigKeys.OLLAMA_ACTIVE_SERVER);
    }

    public synchronized void setActiveServer(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            properties.remove(ConfigKeys.OLLAMA_ACTIVE_SERVER);
        } else {
            properties.setProperty(ConfigKeys.OLLAMA_ACTIVE_SERVER, serverId);
        }
        save();
    }

    /** One field of one server, or {@code null} if unset. */
    public synchronized String getServerField(String serverId, String fieldSuffix) {
        if (serverId == null || fieldSuffix == null) return null;
        return properties.getProperty(ConfigKeys.serverKey(serverId, fieldSuffix));
    }

    /**
     * Writes one field of one server. A {@code null} or blank value
     * removes the key entirely — servers are expected to be complete
     * or absent, not half-populated.
     */
    public synchronized void setServerField(
            String serverId, String fieldSuffix, String value
    ) {
        if (serverId == null || fieldSuffix == null) return;
        String key = ConfigKeys.serverKey(serverId, fieldSuffix);
        if (value == null || value.isBlank()) {
            properties.remove(key);
        } else {
            properties.setProperty(key, value);
        }
        save();
    }

    /**
     * Removes every property belonging to the given server id, plus
     * its entry in the list. Used by "Remove server" in Settings.
     */
    public synchronized void removeServer(String serverId) {
        if (serverId == null || serverId.isBlank()) return;

        properties.remove(ConfigKeys.serverKey(serverId, ConfigKeys.SERVER_NAME_SUFFIX));
        properties.remove(ConfigKeys.serverKey(serverId, ConfigKeys.SERVER_PROTOCOL_SUFFIX));
        properties.remove(ConfigKeys.serverKey(serverId, ConfigKeys.SERVER_HOST_SUFFIX));
        properties.remove(ConfigKeys.serverKey(serverId, ConfigKeys.SERVER_PORT_SUFFIX));

        List<String> ids = splitCsv(getServerList());
        ids.remove(serverId);
        setServerList(String.join(",", ids));

        if (serverId.equals(getActiveServer())) {
            setActiveServer(null);
        }
        save();
    }

    /** Comma-separated prompt id list, in display order. Never {@code null}. */
    public synchronized String getPromptsList() {
        String raw = properties.getProperty(ConfigKeys.PROMPTS_LIST);
        return raw == null ? "" : raw;
    }

    public synchronized void setPromptsList(String csv) {
        if (csv == null || csv.isBlank()) {
            properties.remove(ConfigKeys.PROMPTS_LIST);
        } else {
            properties.setProperty(ConfigKeys.PROMPTS_LIST, csv);
        }
        save();
    }

    /** Active prompt id, or {@code null} if unset. */
    public synchronized String getActivePrompt() {
        return properties.getProperty(ConfigKeys.ACTIVE_PROMPT);
    }

    public synchronized void setActivePrompt(String promptId) {
        if (promptId == null || promptId.isBlank()) {
            properties.remove(ConfigKeys.ACTIVE_PROMPT);
        } else {
            properties.setProperty(ConfigKeys.ACTIVE_PROMPT, promptId);
        }
        save();
    }

    /** One field of one prompt, or {@code null} if unset. */
    public synchronized String getPromptField(String promptId, String fieldSuffix) {
        if (promptId == null || fieldSuffix == null) return null;
        return properties.getProperty(ConfigKeys.promptKey(promptId, fieldSuffix));
    }

    /**
     * Writes one field of one prompt. A {@code null} or blank value
     * removes the key entirely — prompts are expected to be complete
     * or absent, not half-populated.
     */
    public synchronized void setPromptField(String promptId, String fieldSuffix, String value) {
        if (promptId == null || fieldSuffix == null) return;
        String key = ConfigKeys.promptKey(promptId, fieldSuffix);
        if (value == null || value.isBlank()) {
            properties.remove(key);
        } else {
            properties.setProperty(key, value);
        }
        save();
    }

    /**
     * Removes every property belonging to the given prompt id, plus
     * its entry in the list. Used by "Remove prompt" in Settings.
     */
    public synchronized void removePrompt(String promptId) {
        if (promptId == null || promptId.isBlank()) return;

        properties.remove(ConfigKeys.promptKey(promptId, ConfigKeys.PROMPT_NAME_SUFFIX));
        properties.remove(ConfigKeys.promptKey(promptId, ConfigKeys.PROMPT_TEXT_SUFFIX));

        List<String> ids = splitCsv(getPromptsList());
        ids.remove(promptId);
        setPromptsList(String.join(",", ids));

        if (promptId.equals(getActivePrompt())) {
            setActivePrompt(null);
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

    /**
     * Splits a comma-separated string into trimmed non-blank tokens.
     * Public so {@link AppConfig} can reuse it for both server and
     * prompt lists.
     */
    public static List<String> splitCsv(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) return out;
        for (String token : csv.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }
}