package omut.aichat.service;

import java.util.List;

public interface LlmService {
    String ask(String prompt);
    boolean isAvailable();
    List<String> listModels();
    void switchModel(String modelName);
    String currentModel();
}
