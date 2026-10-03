package omut.aichat.service;

import omut.aichat.chat.AIChatMessage;
import omut.aichat.chat.LlmResponse;

import java.util.List;

public interface LlmService {
    LlmResponse ask(List<AIChatMessage> conversation);
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
}
