package omut.aichat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import omut.aichat.chat.AIChatMessage;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class OllamaLlmService implements LlmService {

    private static final String BASE_URL = "http://127.0.0.1:11434";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ChatLanguageModel model;
    private String currentModel;

    public OllamaLlmService(String initialModel) {
        switchModel(initialModel);
    }

    @Override
    public String ask(List<AIChatMessage> conversation) {
        List<ChatMessage> messages = new ArrayList<>();
        for (AIChatMessage msg : conversation) {
            messages.add(toLangchainMessage(msg));
        }
        AiMessage reply = model.generate(messages).content();
        return reply.text();
    }

    @Override
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

    @Override
    public List<String> listModels() {
        List<String> result = new ArrayList<>();
        try {
            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(BASE_URL + "/api/tags").toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);

            JsonNode root = MAPPER.readTree(connection.getInputStream());
            for (JsonNode node : root.get("models")) {
                result.add(node.get("name").asText());
            }
            connection.disconnect();
        } catch (Exception e) {
            // return empty list on failure
        }
        return result;
    }

    @Override
    public void switchModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("Model name must not be blank");
        }
        this.currentModel = modelName;
        this.model = OllamaChatModel.builder()
                .baseUrl(BASE_URL)
                .modelName(modelName)
                .timeout(Duration.ofMinutes(5))
                .build();
    }

    @Override
    public String currentModel() {
        return currentModel;
    }

    private ChatMessage toLangchainMessage(AIChatMessage msg) {
        return switch (msg.role()) {
            case USER -> UserMessage.from(msg.text());
            case ASSISTANT -> AiMessage.from(msg.text());
            case SYSTEM -> SystemMessage.from(msg.text());
        };
    }


}