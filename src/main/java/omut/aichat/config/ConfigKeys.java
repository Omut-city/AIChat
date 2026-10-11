package omut.aichat.config;

/**
 * Property keys used by both {@link AppConfig} (bundled defaults,
 * read-only) and {@link UserConfig} (persisted user overrides).
 * <p>
 * The keys shared between the two files must stay in sync:
 * {@code ollama.base.url}, {@code chat.system.prompt}, {@code app.theme}.
 * Keep them here so a typo cannot silently decouple them.
 */
public final class ConfigKeys {

    private ConfigKeys() {}

    // Connection
    public static final String OLLAMA_BASE_URL = "ollama.base.url";
    public static final String OLLAMA_DEFAULT_MODEL = "ollama.default.model";
    public static final String OLLAMA_SELECTED_MODEL = "ollama.selected.model";

    // Named Ollama servers
    public static final String OLLAMA_SERVER_LIST = "ollama.server.list";
    public static final String OLLAMA_ACTIVE_SERVER = "ollama.active.server";
    public static final String OLLAMA_SERVER_PREFIX = "ollama.server.";

    // LLM request
    public static final String OLLAMA_REQUEST_TIMEOUT_MINUTES = "ollama.request.timeout.minutes";
    public static final String OLLAMA_TEMPERATURE = "ollama.temperature";

    // Chat
    public static final String CHAT_SYSTEM_PROMPT = "chat.system.prompt";
    public static final String CHAT_HISTORY_MAX_MESSAGES = "chat.history.max.messages";
    public static final String CHAT_ATTACH_MAX_CHARS = "chat.attach.max.chars";
    public static final String CHAT_TEMPLATE_PATH = "chat.template.path";
    public static final String CHAT_CSS_PATH = "chat.css.path";
    public static final String CHAT_JS_PATH = "chat.js.path";

    // UI
    public static final String APP_WINDOW_TITLE = "app.window.title";
    public static final String APP_WINDOW_WIDTH = "app.window.width";
    public static final String APP_WINDOW_HEIGHT = "app.window.height";
    public static final String APP_THEME = "app.theme";
    public static final String APP_CSS_PATH = "app.css.path";

    // Syntax highlighting
    public static final String HIGHLIGHT_JS_PATH = "highlight.js.path";

    /** Suffix for the {@code name} field of a named server. */
    public static final String SERVER_NAME_SUFFIX = ".name";
    /** Suffix for the {@code protocol} field of a named server. */
    public static final String SERVER_PROTOCOL_SUFFIX = ".protocol";
    /** Suffix for the {@code host} field of a named server. */
    public static final String SERVER_HOST_SUFFIX = ".host";
    /** Suffix for the {@code port} field of a named server. */
    public static final String SERVER_PORT_SUFFIX = ".port";

    /** Builds the full property key for one field of one named server. */
    public static String serverKey(String serverId, String fieldSuffix) {
        return OLLAMA_SERVER_PREFIX + serverId + fieldSuffix;
    }
}