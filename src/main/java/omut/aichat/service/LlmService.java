package omut.aichat.service;

import omut.aichat.chat.AIChatMessage;
import omut.aichat.chat.LlmResponse;
import omut.aichat.chat.StreamingCallback;

import java.util.List;

public interface LlmService {
    boolean isAvailable();
    List<String> listModels();
    void switchModel(String modelName);
    String currentModel();
    void setBaseUrl(String baseUrl);
    String baseUrl();
    String defaultBaseUrl();
    String systemPrompt();
    void setSystemPrompt(String prompt);
    String defaultSystemPrompt();
    int historyMaxMessages();
    String highlightJsPath();
    String highlightCssPath();
    String chatTemplatePath();
    int attachMaxChars();
    void askStreaming(List<AIChatMessage> conversation, StreamingCallback callback);
    String theme();
    void setTheme(String theme);
    String defaultTheme();
}
