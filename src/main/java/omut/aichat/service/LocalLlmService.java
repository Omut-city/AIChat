package omut.aichat.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;

public class LocalLlmService {

    private static final String BASE_URL = "http://127.0.0.1:11434";
    private static final String MODEL_NAME = "llama3.1:8b";

    private final ChatLanguageModel model;

    public LocalLlmService() {
        this.model = OllamaChatModel.builder()
                .baseUrl(BASE_URL)
                .modelName(MODEL_NAME)
                .timeout(Duration.ofMinutes(5))
                .build();
    }

    /**
     * Lightweight check whether Ollama server is reachable.
     */
    public boolean isAvailable() {
        try {
            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(BASE_URL + "/api/tags").toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(1500);
            connection.setReadTimeout(1500);
            int code = connection.getResponseCode();
            connection.disconnect();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public String ask(String userMessage) {
        return model.generate(userMessage);
    }
}