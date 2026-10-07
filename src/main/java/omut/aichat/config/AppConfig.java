package omut.aichat.config;

/**
 * Facade over bundled defaults ({@link AppDefaults}) and persisted
 * user overrides ({@link UserConfig}).
 * <p>
 * Every getter resolves "user value if present, otherwise default"
 * — that combination lives here, and only here. Anything that only
 * reads {@code application.properties} belongs to AppDefaults;
 * anything that only persists user settings belongs to UserConfig.
 */
public class AppConfig {

    private final AppDefaults defaults = new AppDefaults();
    private final UserConfig userConfig = new UserConfig();

    private volatile String baseUrl;
    private volatile String systemPrompt;

    public AppConfig() {
        this.baseUrl = resolveBaseUrl();
        this.systemPrompt = resolveSystemPrompt();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            this.baseUrl = defaults.baseUrl();
            userConfig.setBaseUrl(null);
        } else {
            this.baseUrl = baseUrl.trim();
            userConfig.setBaseUrl(this.baseUrl);
        }
    }

    public String defaultBaseUrl() {
        return defaults.baseUrl();
    }

    public String defaultModel() {
        return defaults.model();
    }

    public int requestTimeoutMinutes() {
        return defaults.requestTimeoutMinutes();
    }

    public double temperature() {
        return defaults.temperature();
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

    private String resolveBaseUrl() {
        String userValue = userConfig.getBaseUrl();
        if (userValue != null && !userValue.isBlank()) {
            return userValue;
        }
        return defaults.baseUrl();
    }

    private String resolveSystemPrompt() {
        String userValue = userConfig.getSystemPrompt();
        if (userValue != null) {
            return userValue;
        }
        return defaults.systemPrompt();
    }

    public int historyMaxMessages() {
        return defaults.historyMaxMessages();
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

    public int attachMaxChars() {
        return defaults.attachMaxChars();
    }

    public String defaultTheme() {
        return defaults.theme();
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
}