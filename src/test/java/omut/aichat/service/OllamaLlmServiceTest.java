package omut.aichat.service;

import com.sun.net.httpserver.HttpServer;
import omut.aichat.config.AppConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OllamaLlmServiceTest {

    private HttpServer server;
    private AppConfig config;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();

        config = mock(AppConfig.class);
        when(config.getBaseUrl()).thenReturn(baseUrl());
        when(config.requestTimeoutMinutes()).thenReturn(5);
        when(config.temperature()).thenReturn(0.5);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void respondWith(int status) {
        server.createContext("/api/tags", exchange -> {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
    }

    @Nested
    @DisplayName("isAvailable")
    class IsAvailable {

        @Test
        @DisplayName("Returns true when /api/tags responds 200")
        void ok() {
            respondWith(200);
            OllamaLlmService service = new OllamaLlmService(config, "qwen2.5:7b");

            assertThat(service.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("Returns false when /api/tags responds 500")
        void serverError() {
            respondWith(500);
            OllamaLlmService service = new OllamaLlmService(config, "qwen2.5:7b");

            assertThat(service.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("Returns false when /api/tags responds 404")
        void notFound() {
            respondWith(404);
            OllamaLlmService service = new OllamaLlmService(config, "qwen2.5:7b");

            assertThat(service.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("Returns false when the server is not listening")
        void connectionRefused() {
            int port = server.getAddress().getPort();
            server.stop(0);
            server = null;

            when(config.getBaseUrl()).thenReturn("http://127.0.0.1:" + port);
            OllamaLlmService service = new OllamaLlmService(config, "qwen2.5:7b");

            assertThat(service.isAvailable()).isFalse();
        }
    }

    @Nested
    @DisplayName("listModels")
    class ListModels {

        @Test
        @DisplayName("Parses model names from /api/tags response")
        void parsesNames() {
            server.createContext("/api/tags", exchange -> {
                String body = """
                        {
                          "models": [
                            {"name": "qwen2.5:7b"},
                            {"name": "llama3.1:8b"},
                            {"name": "gemma2:2b"}
                          ]
                        }
                        """;
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });

            OllamaLlmService service = new OllamaLlmService(config, "qwen2.5:7b");

            assertThat(service.listModels())
                    .containsExactly("qwen2.5:7b", "llama3.1:8b", "gemma2:2b");
        }

        @Test
        @DisplayName("Returns empty list when models array is empty")
        void emptyModels() {
            server.createContext("/api/tags", exchange -> {
                byte[] bytes = "{\"models\":[]}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });

            OllamaLlmService service = new OllamaLlmService(config, "qwen2.5:7b");

            assertThat(service.listModels()).isEmpty();
        }

        @Test
        @DisplayName("Returns empty list on server error")
        void serverError() {
            respondWith(500);
            OllamaLlmService service = new OllamaLlmService(config, "qwen2.5:7b");

            assertThat(service.listModels()).isEmpty();
        }

        @Test
        @DisplayName("Returns empty list on malformed JSON")
        void malformedJson() {
            server.createContext("/api/tags", exchange -> {
                byte[] bytes = "not json".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });

            OllamaLlmService service = new OllamaLlmService(config, "qwen2.5:7b");

            assertThat(service.listModels()).isEmpty();
        }

        @Test
        @DisplayName("Returns empty list when connection refused")
        void connectionRefused() {
            int port = server.getAddress().getPort();
            server.stop(0);
            server = null;

            when(config.getBaseUrl()).thenReturn("http://127.0.0.1:" + port);
            OllamaLlmService service = new OllamaLlmService(config, "qwen2.5:7b");

            assertThat(service.listModels()).isEmpty();
        }
    }
}