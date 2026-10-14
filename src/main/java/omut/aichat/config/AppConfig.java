package omut.aichat.config;

import omut.aichat.utils.UrlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Facade over bundled defaults ({@link AppDefaults}) and persisted
 * user overrides ({@link UserConfig}).
 * <p>
 * Every getter resolves "user value if present, otherwise default"
 * — that combination lives here, and only here. Anything that only
 * reads {@code application.properties} belongs to AppDefaults;
 * anything that only persists user settings belongs to UserConfig.
 * <p>
 * Two collections are modelled on top of the flat storage, both with
 * the same shape — a list of named items, one active, and one item
 * that cannot be removed:
 * <ul>
 *   <li><b>Named Ollama servers (F18).</b> {@link #getBaseUrl()} is
 *       derived from the active server. The {@code local} server is
 *       mandatory: re-created from the legacy {@code ollama.base.url}
 *       on first run, restored on startup if missing, and never
 *       removable. The persisted {@code active.server} falls back to
 *       {@code local} when it points at a removed id.</li>
 *   <li><b>Named prompts (F13).</b> {@link #systemPrompt()} returns
 *       the active prompt's text. The {@code default} prompt is
 *       mandatory: re-created from the legacy {@code chat.system.prompt}
 *       on first run, restored on startup if missing, and never
 *       removable. The persisted {@code active.prompt} falls back to
 *       {@code default} when it points at a removed id.</li>
 * </ul>
 */
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    public static final String LOCAL_SERVER_ID = "local";
    public static final String DEFAULT_PROMPT_ID = "default";

    private static final String DEFAULT_PROTOCOL = "http";

    private final AppDefaults defaults = new AppDefaults();
    private final UserConfig userConfig;

    private volatile String baseUrl;
    private volatile String systemPrompt;
    private volatile double temperature;
    private volatile int requestTimeoutMinutes;
    private volatile int historyMaxMessages;
    private volatile int attachMaxChars;

    public AppConfig() {
        this(new UserConfig());
    }

    AppConfig(UserConfig userConfig) {
        this.userConfig = userConfig;
        migrateLegacyBaseUrlIfNeeded();
        ensureLocalServer();
        migrateLegacySystemPromptIfNeeded();
        ensureDefaultPrompt();
        this.baseUrl = deriveBaseUrlFromActiveServer();
        this.systemPrompt = deriveSystemPromptFromActivePrompt();
        this.temperature = resolveTemperature();
        this.requestTimeoutMinutes = resolveRequestTimeoutMinutes();
        this.historyMaxMessages = resolveHistoryMaxMessages();
        this.attachMaxChars = resolveAttachMaxChars();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Sets the URL of the <i>active</i> server, parsing protocol, host
     * and port out of it. Other servers are untouched. Blank input
     * resets the active server's host/port to the bundled defaults.
     */
    public void setBaseUrl(String baseUrl) {
        String normalized = UrlParser.normalize(baseUrl);
        if (normalized == null) {
            resetActiveServerToDefaults();
        } else {
            parseAndStoreActiveServer(normalized);
        }
        this.baseUrl = deriveBaseUrlFromActiveServer();
    }

    public String defaultBaseUrl() {
        return defaults.baseUrl();
    }

    /** Ids of all configured servers, in display order. Never empty. */
    public List<String> serverIds() {
        return UserConfig.splitCsv(userConfig.getServerList());
    }

    /** Id of the currently active server. Never {@code null}. */
    public String activeServerId() {
        String id = userConfig.getActiveServer();
        if (id == null || id.isBlank() || !serverIds().contains(id)) {
            userConfig.setActiveServer(LOCAL_SERVER_ID);
            return LOCAL_SERVER_ID;
        }
        return id;
    }

    /** Display name of the server with the given id, or the id itself. */
    public String serverName(String serverId) {
        String name = userConfig.getServerField(serverId, ConfigKeys.SERVER_NAME_SUFFIX);
        return (name == null || name.isBlank()) ? serverId : name;
    }

    /**
     * Derived URL of the given server, or {@code null} if the server
     * has no host set.
     */
    public String serverBaseUrl(String serverId) {
        return buildServerUrl(serverId);
    }

    /** Convenience: derived URL of the active server. */
    public String activeServerBaseUrl() {
        return serverBaseUrl(activeServerId());
    }

    /**
     * Adds a new server with a generated id and returns that id. The
     * server is appended to the list; the active one is unchanged.
     */
    public String addServer(String name, String host, int port) {
        String id = generateId("srv", serverIds());
        userConfig.setServerField(id, ConfigKeys.SERVER_NAME_SUFFIX, name);
        userConfig.setServerField(id, ConfigKeys.SERVER_PROTOCOL_SUFFIX, DEFAULT_PROTOCOL);
        userConfig.setServerField(id, ConfigKeys.SERVER_HOST_SUFFIX, host);
        userConfig.setServerField(id, ConfigKeys.SERVER_PORT_SUFFIX, Integer.toString(port));

        List<String> ids = new ArrayList<>(serverIds());
        ids.add(id);
        userConfig.setServerList(String.join(",", ids));
        return id;
    }

    public void updateServer(String serverId, String name, String host, int port) {
        if (serverId == null || serverId.isBlank()) return;
        if (!serverIds().contains(serverId)) return;

        userConfig.setServerField(serverId, ConfigKeys.SERVER_NAME_SUFFIX, name);
        userConfig.setServerField(serverId, ConfigKeys.SERVER_HOST_SUFFIX, host);
        userConfig.setServerField(serverId, ConfigKeys.SERVER_PORT_SUFFIX, Integer.toString(port));

        if (serverId.equals(activeServerId())) {
            this.baseUrl = deriveBaseUrlFromActiveServer();
        }
    }

    /**
     * Removes a non-local server. The {@code local} server cannot be
     * removed. If the removed server was active, the active id falls
     * back to {@code local}.
     */
    public void removeServer(String serverId) {
        if (serverId == null || serverId.isBlank()) return;
        if (LOCAL_SERVER_ID.equals(serverId)) return;
        if (!serverIds().contains(serverId)) return;

        userConfig.removeServer(serverId);
        this.baseUrl = deriveBaseUrlFromActiveServer();
    }

    /** Switches the active server. Unknown ids are ignored. */
    public void setActiveServer(String serverId) {
        if (serverId == null || serverId.isBlank()) return;
        if (!serverIds().contains(serverId)) return;
        userConfig.setActiveServer(serverId);
        this.baseUrl = deriveBaseUrlFromActiveServer();
    }

    /** Ids of all configured prompts, in display order. Never empty. */
    public List<String> promptIds() {
        return UserConfig.splitCsv(userConfig.getPromptsList());
    }

    /** Id of the currently active prompt. Never {@code null}. */
    public String activePromptId() {
        String id = userConfig.getActivePrompt();
        if (id == null || id.isBlank() || !promptIds().contains(id)) {
            userConfig.setActivePrompt(DEFAULT_PROMPT_ID);
            return DEFAULT_PROMPT_ID;
        }
        return id;
    }

    /** Display name of the prompt with the given id, or the id itself. */
    public String promptName(String promptId) {
        String name = userConfig.getPromptField(promptId, ConfigKeys.PROMPT_NAME_SUFFIX);
        return (name == null || name.isBlank()) ? promptId : name;
    }

    /** Full text of the given prompt, or {@code null} if not set. */
    public String promptText(String promptId) {
        return userConfig.getPromptField(promptId, ConfigKeys.PROMPT_TEXT_SUFFIX);
    }

    /**
     * Adds a new prompt with a generated id and returns that id.
     * The prompt is appended to the list; the active one is unchanged.
     */
    public String addPrompt(String name, String text) {
        String id = generateId("prm", promptIds());
        userConfig.setPromptField(id, ConfigKeys.PROMPT_NAME_SUFFIX, name);
        userConfig.setPromptField(id, ConfigKeys.PROMPT_TEXT_SUFFIX, text);

        List<String> ids = new ArrayList<>(promptIds());
        ids.add(id);
        userConfig.setPromptsList(String.join(",", ids));
        return id;
    }

    public void updatePrompt(String promptId, String name, String text) {
        if (promptId == null || promptId.isBlank()) return;
        if (!promptIds().contains(promptId)) return;

        userConfig.setPromptField(promptId, ConfigKeys.PROMPT_NAME_SUFFIX, name);
        userConfig.setPromptField(promptId, ConfigKeys.PROMPT_TEXT_SUFFIX, text);

        if (promptId.equals(activePromptId())) {
            this.systemPrompt = deriveSystemPromptFromActivePrompt();
        }
    }

    /**
     * Removes a non-default prompt. The {@code default} prompt cannot
     * be removed. If the removed prompt was active, the active id
     * falls back to {@code default}.
     */
    public void removePrompt(String promptId) {
        if (promptId == null || promptId.isBlank()) return;
        if (DEFAULT_PROMPT_ID.equals(promptId)) return;
        if (!promptIds().contains(promptId)) return;

        userConfig.removePrompt(promptId);
        this.systemPrompt = deriveSystemPromptFromActivePrompt();
    }

    /** Switches the active prompt. Unknown ids are ignored. */
    public void setActivePrompt(String promptId) {
        if (promptId == null || promptId.isBlank()) return;
        if (!promptIds().contains(promptId)) return;
        userConfig.setActivePrompt(promptId);
        this.systemPrompt = deriveSystemPromptFromActivePrompt();
    }

    public int requestTimeoutMinutes() {
        return requestTimeoutMinutes;
    }

    public double temperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
        userConfig.setTemperature(temperature);
    }

    public double defaultTemperature() {
        return defaults.temperature();
    }

    public void setRequestTimeoutMinutes(int minutes) {
        this.requestTimeoutMinutes = minutes;
        userConfig.setRequestTimeoutMinutes(minutes);
    }

    public int defaultRequestTimeoutMinutes() {
        return defaults.requestTimeoutMinutes();
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String prompt) {
        updatePrompt(activePromptId(), promptName(activePromptId()), prompt);
    }

    public String defaultSystemPrompt() {
        return defaults.systemPrompt();
    }

    public String selectedModel() {
        String userValue = userConfig.getSelectedModel();
        if (userValue != null && !userValue.isBlank()) {
            return userValue;
        }
        return defaults.model();
    }

    public void setSelectedModel(String model) {
        if (model == null || model.isBlank()) {
            userConfig.setSelectedModel(null);
        } else {
            userConfig.setSelectedModel(model);
        }
    }

    public String theme() {
        String userValue = userConfig.getTheme();
        if (userValue != null && !userValue.isBlank()) {
            return userValue;
        }
        return defaults.theme();
    }

    public void setTheme(String theme) {
        if (theme == null || theme.isBlank()) {
            userConfig.setTheme(null);
        } else {
            userConfig.setTheme(theme);
        }
    }

    public int historyMaxMessages() {
        return historyMaxMessages;
    }

    public void setHistoryMaxMessages(int max) {
        this.historyMaxMessages = max;
        userConfig.setHistoryMaxMessages(max);
    }

    public int defaultHistoryMaxMessages() {
        return defaults.historyMaxMessages();
    }

    public int attachMaxChars() {
        return attachMaxChars;
    }

    public void setAttachMaxChars(int max) {
        this.attachMaxChars = max;
        userConfig.setAttachMaxChars(max);
    }

    public int defaultAttachMaxChars() {
        return defaults.attachMaxChars();
    }

    public String windowTitle() {
        return defaults.windowTitle();
    }

    public int windowWidth() {
        return defaults.windowWidth();
    }

    public int windowHeight() {
        return defaults.windowHeight();
    }

    public String highlightJsPath() {
        return defaults.highlightJsPath();
    }

    public String chatTemplatePath() {
        return defaults.chatTemplatePath();
    }

    public String applicationCssPath() {
        return defaults.applicationCssPath();
    }

    public String chatCssPath() {
        return defaults.chatCssPath();
    }

    public String chatJsPath() {
        return defaults.chatJsPath();
    }

    private void migrateLegacyBaseUrlIfNeeded() {
        String existingList = userConfig.getServerList();
        if (existingList != null && !existingList.isBlank()) {
            return;
        }

        String legacy = userConfig.getBaseUrl();
        String source = (legacy != null && !legacy.isBlank())
                ? legacy
                : defaults.baseUrl();

        UrlParser.Parsed parsed = UrlParser.parse(source);
        if (parsed == null) {
            parsed = UrlParser.parse(defaults.baseUrl());
        }
        if (parsed == null) {
            log.warn("Cannot parse bundled default base URL: {}", defaults.baseUrl());
            parsed = new UrlParser.Parsed(
                    UrlParser.DEFAULT_PROTOCOL, "127.0.0.1", UrlParser.DEFAULT_PORT);
        }

        userConfig.setServerField(
                LOCAL_SERVER_ID, ConfigKeys.SERVER_NAME_SUFFIX, "Local");
        userConfig.setServerField(
                LOCAL_SERVER_ID, ConfigKeys.SERVER_PROTOCOL_SUFFIX, parsed.protocol());
        userConfig.setServerField(
                LOCAL_SERVER_ID, ConfigKeys.SERVER_HOST_SUFFIX, parsed.host());
        userConfig.setServerField(
                LOCAL_SERVER_ID, ConfigKeys.SERVER_PORT_SUFFIX,
                Integer.toString(parsed.port()));

        userConfig.setServerList(LOCAL_SERVER_ID);
        userConfig.setActiveServer(LOCAL_SERVER_ID);
        log.info("Migrated legacy base URL to named server '{}': {}",
                LOCAL_SERVER_ID, source);
    }

    private void ensureLocalServer() {
        List<String> ids = new ArrayList<>(serverIds());

        if (!ids.contains(LOCAL_SERVER_ID)) {
            ids.add(0, LOCAL_SERVER_ID);
            userConfig.setServerList(String.join(",", ids));
            log.info("Self-heal: restored missing '{}' server entry", LOCAL_SERVER_ID);
        }

        if (userConfig.getServerField(LOCAL_SERVER_ID, ConfigKeys.SERVER_NAME_SUFFIX) == null) {
            userConfig.setServerField(
                    LOCAL_SERVER_ID, ConfigKeys.SERVER_NAME_SUFFIX, "Local");
        }
        if (userConfig.getServerField(LOCAL_SERVER_ID, ConfigKeys.SERVER_PROTOCOL_SUFFIX) == null) {
            userConfig.setServerField(
                    LOCAL_SERVER_ID, ConfigKeys.SERVER_PROTOCOL_SUFFIX, DEFAULT_PROTOCOL);
        }
        if (userConfig.getServerField(LOCAL_SERVER_ID, ConfigKeys.SERVER_HOST_SUFFIX) == null) {
            UrlParser.Parsed d = UrlParser.parse(defaults.baseUrl());
            String host = (d != null) ? d.host() : "127.0.0.1";
            userConfig.setServerField(
                    LOCAL_SERVER_ID, ConfigKeys.SERVER_HOST_SUFFIX, host);
        }
        if (userConfig.getServerField(LOCAL_SERVER_ID, ConfigKeys.SERVER_PORT_SUFFIX) == null) {
            UrlParser.Parsed d = UrlParser.parse(defaults.baseUrl());
            int port = (d != null) ? d.port() : UrlParser.DEFAULT_PORT;
            userConfig.setServerField(
                    LOCAL_SERVER_ID, ConfigKeys.SERVER_PORT_SUFFIX, Integer.toString(port));
        }

        String active = userConfig.getActiveServer();
        if (active == null || active.isBlank() || !ids.contains(active)) {
            userConfig.setActiveServer(LOCAL_SERVER_ID);
        }
    }

    private void migrateLegacySystemPromptIfNeeded() {
        String existingList = userConfig.getPromptsList();
        if (existingList != null && !existingList.isBlank()) {
            return;
        }

        String legacy = userConfig.getSystemPrompt();
        String text = (legacy != null && !legacy.isBlank())
                ? legacy
                : defaults.systemPrompt();

        userConfig.setPromptField(
                DEFAULT_PROMPT_ID, ConfigKeys.PROMPT_NAME_SUFFIX, "Default");
        userConfig.setPromptField(
                DEFAULT_PROMPT_ID, ConfigKeys.PROMPT_TEXT_SUFFIX, text);

        userConfig.setPromptsList(DEFAULT_PROMPT_ID);
        userConfig.setActivePrompt(DEFAULT_PROMPT_ID);
        log.info("Migrated legacy system prompt to named prompt '{}'",
                DEFAULT_PROMPT_ID);
    }

    private void ensureDefaultPrompt() {
        List<String> ids = new ArrayList<>(promptIds());

        if (!ids.contains(DEFAULT_PROMPT_ID)) {
            ids.add(0, DEFAULT_PROMPT_ID);
            userConfig.setPromptsList(String.join(",", ids));
            log.info("Self-heal: restored missing '{}' prompt entry",
                    DEFAULT_PROMPT_ID);
        }

        if (userConfig.getPromptField(DEFAULT_PROMPT_ID, ConfigKeys.PROMPT_NAME_SUFFIX) == null) {
            userConfig.setPromptField(
                    DEFAULT_PROMPT_ID, ConfigKeys.PROMPT_NAME_SUFFIX, "Default");
        }
        if (userConfig.getPromptField(DEFAULT_PROMPT_ID, ConfigKeys.PROMPT_TEXT_SUFFIX) == null) {
            userConfig.setPromptField(
                    DEFAULT_PROMPT_ID, ConfigKeys.PROMPT_TEXT_SUFFIX, defaults.systemPrompt());
        }

        String active = userConfig.getActivePrompt();
        if (active == null || active.isBlank() || !ids.contains(active)) {
            userConfig.setActivePrompt(DEFAULT_PROMPT_ID);
        }
    }

    private String deriveBaseUrlFromActiveServer() {
        String id = activeServerId();
        String url = buildServerUrl(id);
        if (url != null) return url;
        return defaults.baseUrl();
    }

    private String deriveSystemPromptFromActivePrompt() {
        String id = activePromptId();
        String text = promptText(id);
        return text != null ? text : defaults.systemPrompt();
    }

    private String buildServerUrl(String serverId) {
        String protocol = userConfig.getServerField(
                serverId, ConfigKeys.SERVER_PROTOCOL_SUFFIX);
        String host = userConfig.getServerField(
                serverId, ConfigKeys.SERVER_HOST_SUFFIX);
        String port = userConfig.getServerField(
                serverId, ConfigKeys.SERVER_PORT_SUFFIX);

        if (host == null || host.isBlank()) return null;
        if (protocol == null || protocol.isBlank()) protocol = DEFAULT_PROTOCOL;
        if (port == null || port.isBlank()) port = String.valueOf(UrlParser.DEFAULT_PORT);

        return protocol + "://" + host + ":" + port;
    }

    private void parseAndStoreActiveServer(String url) {
        UrlParser.Parsed parsed = UrlParser.parse(url);
        if (parsed == null) {
            log.warn("Cannot parse URL for active server: {}", url);
            return;
        }
        String id = activeServerId();
        userConfig.setServerField(
                id, ConfigKeys.SERVER_PROTOCOL_SUFFIX, parsed.protocol());
        userConfig.setServerField(
                id, ConfigKeys.SERVER_HOST_SUFFIX, parsed.host());
        userConfig.setServerField(
                id, ConfigKeys.SERVER_PORT_SUFFIX, Integer.toString(parsed.port()));
    }

    private void resetActiveServerToDefaults() {
        UrlParser.Parsed parsed = UrlParser.parse(defaults.baseUrl());
        if (parsed == null) return;
        String id = activeServerId();
        userConfig.setServerField(
                id, ConfigKeys.SERVER_PROTOCOL_SUFFIX, parsed.protocol());
        userConfig.setServerField(
                id, ConfigKeys.SERVER_HOST_SUFFIX, parsed.host());
        userConfig.setServerField(
                id, ConfigKeys.SERVER_PORT_SUFFIX, Integer.toString(parsed.port()));
    }

    private double resolveTemperature() {
        Double userValue = userConfig.getTemperature();
        if (userValue != null) return userValue;
        return defaults.temperature();
    }

    private int resolveRequestTimeoutMinutes() {
        Integer userValue = userConfig.getRequestTimeoutMinutes();
        if (userValue != null) return userValue;
        return defaults.requestTimeoutMinutes();
    }

    private int resolveHistoryMaxMessages() {
        Integer userValue = userConfig.getHistoryMaxMessages();
        if (userValue != null) return userValue;
        return defaults.historyMaxMessages();
    }

    private int resolveAttachMaxChars() {
        Integer userValue = userConfig.getAttachMaxChars();
        if (userValue != null) return userValue;
        return defaults.attachMaxChars();
    }

    private static String generateId(String prefix, List<String> existing) {
        for (int attempt = 0; attempt < 16; attempt++) {
            String candidate = prefix + "-"
                    + UUID.randomUUID().toString().substring(0, 8);
            if (!existing.contains(candidate)) return candidate;
        }
        throw new IllegalStateException(
                "Could not allocate a unique id with prefix '" + prefix + "'");
    }
}