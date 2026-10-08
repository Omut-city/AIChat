package omut.aichat.service;

import omut.aichat.chat.AIChatMessage;
import omut.aichat.chat.StreamingCallback;

import java.util.List;

public interface LlmService {
    boolean isAvailable();
    List<String> listModels();
    void switchModel(String modelName);
    String currentModel();
    void setBaseUrl(String baseUrl);
    void askStreaming(List<AIChatMessage> conversation, StreamingCallback callback);
    /**
     * Rebuilds the underlying streaming model from the current
     * configuration. Call after changing any setting that feeds into
     * the model — base URL, temperature, request timeout.
     */
    void rebuildModel();
}
