package omut.aichat.service;

import omut.aichat.chat.AIChatMessage;

import java.util.List;

public interface LlmService {
    String ask(List<AIChatMessage> conversation);
    boolean isAvailable();
    List<String> listModels();
    void switchModel(String modelName);
    String currentModel();
    void setBaseUrl(String baseUrl);
    String baseUrl();
    String defaultBaseUrl();
}
