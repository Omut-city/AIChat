package omut.aichat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.output.Response;
import omut.aichat.chat.AIChatMessage;
import omut.aichat.chat.LlmResponse;
import omut.aichat.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class OllamaLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(OllamaLlmService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AppConfig config;
    private ChatLanguageModel model;
    private String currentModel;

    public OllamaLlmService(AppConfig config, String initialModel) {
        this.config = config;
        switchModel(initialModel);
    }

    @Override
    public LlmResponse ask(List<AIChatMessage> conversation) {
        List<ChatMessage> messages = new ArrayList<>();
        for (AIChatMessage msg : conversation) {
            messages.add(toLangchainMessage(msg));
        }

        long start = System.nanoTime();
        Response<AiMessage> response = model.generate(messages);
        long elapsedNanos = System.nanoTime() - start;

        AiMessage ai = response.content();
        int outputTokens = response.tokenUsage() != null
                ? response.tokenUsage().outputTokenCount()
                : 0;

        return new LlmResponse(
                ai.text(),
                elapsedNanos / 1_000_000,
                outputTokens,
                elapsedNanos
        );
    }

    @Override
    public boolean isAvailable() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)
                    URI.create(config.getBaseUrl() + "/api/tags").toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(1500);
            connection.setReadTimeout(1500);
            int code = connection.getResponseCode();
            return code == 200;
        } catch (Exception e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    public List<String> listModels() {
        List<String> result = new ArrayList<>();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)
                    URI.create(config.getBaseUrl() + "/api/tags").toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);

            JsonNode root = MAPPER.readTree(connection.getInputStream());
            for (JsonNode node : root.get("models")) {
                result.add(node.get("name").asText());
            }
        } catch (Exception e) {
            // return empty list on failure
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
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
                .baseUrl(config.getBaseUrl())
                .modelName(modelName)
                .timeout(Duration.ofMinutes(config.requestTimeoutMinutes()))
                .temperature(config.temperature())
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Override
    public String currentModel() {
        return currentModel;
    }

    @Override
    public void setBaseUrl(String baseUrl) {
        config.setBaseUrl(baseUrl);
        if (currentModel != null) {
            switchModel(currentModel);
        }
    }

    @Override
    public String baseUrl() {
        return config.getBaseUrl();
    }

    private ChatMessage toLangchainMessage(AIChatMessage msg) {
        return switch (msg.role()) {
            case USER -> UserMessage.from(msg.text());
            case ASSISTANT -> AiMessage.from(msg.text());
            case SYSTEM -> SystemMessage.from(msg.text());
        };
    }

    @Override
    public String defaultBaseUrl() {
        return config.defaultBaseUrl();
    }

    @Override
    public String systemPrompt() {
        return config.systemPrompt();
    }

    @Override
    public void setSystemPrompt(String prompt) {
        config.setSystemPrompt(prompt);
    }

    @Override
    public String defaultSystemPrompt() {
        return config.defaultSystemPrompt();
    }

    @Override
    public int historyMaxMessages() {
        return config.historyMaxMessages();
    }

    @Override
    public String highlightJsPath() {
        return config.highlightJsPath();
    }

    @Override
    public String highlightCssPath() {
        return config.highlightCssPath();
    }

    @Override
    public String chatTemplatePath() {
        return config.chatTemplatePath();
    }

    @Override
    public int attachMaxChars() {
        return config.attachMaxChars();
    }
}