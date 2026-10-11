package omut.aichat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Facade over bundled defaults ({@link AppDefaults}) and persisted
 * user overrides ({@link UserConfig}).
 * <p>
 * Every getter resolves "user value if present, otherwise default"
 * — that combination lives here, and only here. Anything that only
 * reads {@code application.properties} belongs to AppDefaults;
 * anything that only persists user settings belongs to UserConfig.
 * <p>
 * Connection data is modelled as a list of named Ollama servers.
 * Exactly one of them is active at any time, and {@link #getBaseUrl()}
 * derives the flat URL from it. The server with id {@code "local"} is
 * mandatory: it is always present in the list and cannot be removed.
 * If the persisted list is empty or missing {@code "local"}, it is
 * re-created from the legacy {@code ollama.base.url} property (or from
 * bundled defaults). If the persisted {@code active.server} points at
 * a server id that no longer exists, the active server falls back to
 * {@code "local"}.
 */
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    public static final String LOCAL_SERVER_ID = "local";

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
        this.baseUrl = deriveBaseUrlFromActiveServer();
        this.systemPrompt = resolveSystemPrompt();
        this.temperature = resolveTemperature();
        this.requestTimeoutMinutes = resolveRequestTimeoutMinutes();
        this.historyMaxMessages = resolveHistoryMaxMessages();
        this.attachMaxChars = resolveAttachMaxChars();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        String normalized = normalizeUrlOrNull(baseUrl);
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

    public List<String> serverIds() {
        return parseCsv(userConfig.getServerList());
    }

    public String activeServerId() {
        String id = userConfig.getActiveServer();
        if (id == null || id.isBlank() || !serverIds().contains(id)) {
            userConfig.setActiveServer(LOCAL_SERVER_ID);
            return LOCAL_SERVER_ID;
        }
        return id;
    }

    public String serverName(String serverId) {
        String name = userConfig.getServerField(serverId, ConfigKeys.SERVER_NAME_SUFFIX);
        return (name == null || name.isBlank()) ? serverId : name;
    }

    public String serverBaseUrl(String serverId) {
        return buildUrl(serverId);
    }

    public String activeServerBaseUrl() {
        return serverBaseUrl(activeServerId());
    }

    public String addServer(String name, String host, int port) {
        String id = generateServerId();
        userConfig.setServerField(id, ConfigKeys.SERVER_NAME_SUFFIX, name);
        userConfig.setServerField(id, ConfigKeys.SERVER_PROTOCOL_SUFFIX, DEFAULT_PROTOCOL);
        userConfig.setServerField(id, ConfigKeys.SERVER_HOST_SUFFIX, host);
        userConfig.setServerField(id, ConfigKeys.SERVER_PORT_SUFFIX, Integer.toString(port));

        List<String> ids = new ArrayList<>(parseCsv(userConfig.getServerList()));
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

    public void removeServer(String serverId) {
        if (serverId == null || serverId.isBlank()) return;
        if (LOCAL_SERVER_ID.equals(serverId)) return;
        if (!serverIds().contains(serverId)) return;

        userConfig.removeServer(serverId);
        ensureLocalServer();
        this.baseUrl = deriveBaseUrlFromActiveServer();
    }

    public void setActiveServer(String serverId) {
        if (serverId == null || serverId.isBlank()) return;
        if (!serverIds().contains(serverId)) return;
        userConfig.setActiveServer(serverId);
        this.baseUrl = deriveBaseUrlFromActiveServer();
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
        if (prompt == null || prompt.isBlank()) {
            this.systemPrompt = defaults.systemPrompt();
            userConfig.setSystemPrompt(null);
        } else {
            this.systemPrompt = prompt.trim();
            userConfig.setSystemPrompt(this.systemPrompt);
        }
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

        ParsedUrl parsed = parseUrl(source);
        if (parsed == null) {
            parsed = parseUrl(defaults.baseUrl());
        }
        if (parsed == null) {
            log.warn("Cannot parse bundled default base URL: {}", defaults.baseUrl());
            parsed = new ParsedUrl(DEFAULT_PROTOCOL, "127.0.0.1", 11434);
        }

        userConfig.setServerField(
                LOCAL_SERVER_ID, ConfigKeys.SERVER_NAME_SUFFIX, parsed.host());
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
        List<String> ids = new ArrayList<>(parseCsv(userConfig.getServerList()));

        boolean hadLocal = ids.contains(LOCAL_SERVER_ID);
        if (!hadLocal) {
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
            ParsedUrl d = parseUrl(defaults.baseUrl());
            String host = (d != null) ? d.host() : "127.0.0.1";
            userConfig.setServerField(
                    LOCAL_SERVER_ID, ConfigKeys.SERVER_HOST_SUFFIX, host);
        }
        if (userConfig.getServerField(LOCAL_SERVER_ID, ConfigKeys.SERVER_PORT_SUFFIX) == null) {
            ParsedUrl d = parseUrl(defaults.baseUrl());
            int port = (d != null) ? d.port() : 11434;
            userConfig.setServerField(
                    LOCAL_SERVER_ID, ConfigKeys.SERVER_PORT_SUFFIX, Integer.toString(port));
        }

        String active = userConfig.getActiveServer();
        if (active == null || active.isBlank() || !ids.contains(active)) {
            userConfig.setActiveServer(LOCAL_SERVER_ID);
        }
    }

    private String deriveBaseUrlFromActiveServer() {
        String id = activeServerId();
        String url = buildUrl(id);
        if (url != null) return url;
        return defaults.baseUrl();
    }

    private String buildUrl(String serverId) {
        String protocol = userConfig.getServerField(
                serverId, ConfigKeys.SERVER_PROTOCOL_SUFFIX);
        String host = userConfig.getServerField(
                serverId, ConfigKeys.SERVER_HOST_SUFFIX);
        String port = userConfig.getServerField(
                serverId, ConfigKeys.SERVER_PORT_SUFFIX);

        if (host == null || host.isBlank()) return null;
        if (protocol == null || protocol.isBlank()) protocol = DEFAULT_PROTOCOL;
        if (port == null || port.isBlank()) port = "11434";

        return protocol + "://" + host + ":" + port;
    }

    private void parseAndStoreActiveServer(String url) {
        ParsedUrl parsed = parseUrl(url);
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
        ParsedUrl parsed = parseUrl(defaults.baseUrl());
        if (parsed == null) return;
        String id = activeServerId();
        userConfig.setServerField(
                id, ConfigKeys.SERVER_PROTOCOL_SUFFIX, parsed.protocol());
        userConfig.setServerField(
                id, ConfigKeys.SERVER_HOST_SUFFIX, parsed.host());
        userConfig.setServerField(
                id, ConfigKeys.SERVER_PORT_SUFFIX, Integer.toString(parsed.port()));
    }

    private String resolveSystemPrompt() {
        String userValue = userConfig.getSystemPrompt();
        if (userValue != null) {
            return userValue;
        }
        return defaults.systemPrompt();
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

    private static List<String> parseCsv(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) return out;
        for (String token : csv.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    private static String normalizeUrlOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String url = raw.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || host.isBlank()) return null;
            return url;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ParsedUrl parseUrl(String url) {
        if (url == null || url.isBlank()) return null;

        String normalized = url.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://" + normalized;
        }
        try {
            URI uri = URI.create(normalized);
            String protocol = uri.getScheme();
            String host = uri.getHost();
            if (protocol == null || host == null || host.isBlank()) return null;
            int port = uri.getPort();
            if (port <= 0) port = 11434;
            return new ParsedUrl(protocol, host, port);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String generateServerId() {
        List<String> existing = serverIds();
        for (int attempt = 0; attempt < 16; attempt++) {
            String candidate = "srv-" + Long.toHexString(
                    (long) (Math.random() * 0xFFFFFFFFL) & 0xFFFFFFFFL);
            if (!existing.contains(candidate)) return candidate;
        }
        return "srv-" + Objects.hash(System.nanoTime());
    }

    private record ParsedUrl(String protocol, String host, int port) {}
}