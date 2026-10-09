package omut.aichat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigTest {

    @TempDir
    Path tmp;

    private Path configFile() {
        return tmp.resolve("config.properties");
    }

    private AppConfig config() {
        return new AppConfig(new UserConfig(configFile()));
    }

    private Properties readBack() throws IOException {
        Properties p = new Properties();
        try (var reader = Files.newBufferedReader(configFile())) {
            p.load(reader);
        }
        return p;
    }

    @Nested
    @DisplayName("Defaults on a fresh config")
    class Fresh {

        @Test
        @DisplayName("Values fall through to bundled defaults")
        void defaults() {
            AppConfig c = config();
            assertThat(c.getBaseUrl()).isEqualTo(c.defaultBaseUrl());
            assertThat(c.temperature()).isEqualTo(c.defaultTemperature());
            assertThat(c.requestTimeoutMinutes()).isEqualTo(c.defaultRequestTimeoutMinutes());
            assertThat(c.historyMaxMessages()).isEqualTo(c.defaultHistoryMaxMessages());
            assertThat(c.attachMaxChars()).isEqualTo(c.defaultAttachMaxChars());
            assertThat(c.systemPrompt()).isEqualTo(c.defaultSystemPrompt());
        }
    }

    @Nested
    @DisplayName("Setter persists and getter returns the new value")
    class SetterRoundTrip {

        @Test
        @DisplayName("temperature")
        void temperature() throws IOException {
            AppConfig c = config();
            c.setTemperature(1.2);
            assertThat(c.temperature()).isEqualTo(1.2);
            assertThat(readBack().getProperty(ConfigKeys.OLLAMA_TEMPERATURE))
                    .isEqualTo("1.2");
        }

        @Test
        @DisplayName("requestTimeoutMinutes")
        void timeout() throws IOException {
            AppConfig c = config();
            c.setRequestTimeoutMinutes(15);
            assertThat(c.requestTimeoutMinutes()).isEqualTo(15);
            assertThat(readBack().getProperty(ConfigKeys.OLLAMA_REQUEST_TIMEOUT_MINUTES))
                    .isEqualTo("15");
        }

        @Test
        @DisplayName("historyMaxMessages")
        void history() throws IOException {
            AppConfig c = config();
            c.setHistoryMaxMessages(50);
            assertThat(c.historyMaxMessages()).isEqualTo(50);
            assertThat(readBack().getProperty(ConfigKeys.CHAT_HISTORY_MAX_MESSAGES))
                    .isEqualTo("50");
        }

        @Test
        @DisplayName("attachMaxChars")
        void attach() throws IOException {
            AppConfig c = config();
            c.setAttachMaxChars(200_000);
            assertThat(c.attachMaxChars()).isEqualTo(200_000);
            assertThat(readBack().getProperty(ConfigKeys.CHAT_ATTACH_MAX_CHARS))
                    .isEqualTo("200000");
        }

        @Test
        @DisplayName("baseUrl")
        void baseUrl() throws IOException {
            AppConfig c = config();
            c.setBaseUrl("http://192.168.1.5:11434");
            assertThat(c.getBaseUrl()).isEqualTo("http://192.168.1.5:11434");
            assertThat(readBack().getProperty(ConfigKeys.OLLAMA_BASE_URL))
                    .isEqualTo("http://192.168.1.5:11434");
        }

        @Test
        @DisplayName("systemPrompt")
        void systemPrompt() throws IOException {
            AppConfig c = config();
            c.setSystemPrompt("be concise");
            assertThat(c.systemPrompt()).isEqualTo("be concise");
            assertThat(readBack().getProperty(ConfigKeys.CHAT_SYSTEM_PROMPT))
                    .isEqualTo("be concise");
        }
    }

    @Nested
    @DisplayName("Cached getter does not read through to defaults")
    class Cache {

        @Test
        @DisplayName("After set, getter returns the cache, default stays separate")
        void cacheVsDefault() {
            AppConfig c = config();
            double originalDefault = c.defaultTemperature();

            c.setTemperature(1.7);
            assertThat(c.temperature()).isEqualTo(1.7);
            assertThat(c.defaultTemperature()).isEqualTo(originalDefault);
            assertThat(c.temperature()).isNotEqualTo(c.defaultTemperature());
        }
    }

    @Nested
    @DisplayName("Persistence across instances")
    class Persistence {

        @Test
        @DisplayName("A second config on the same file sees the saved values")
        void reloaded() {
            AppConfig first = config();
            first.setTemperature(0.9);
            first.setRequestTimeoutMinutes(20);
            first.setHistoryMaxMessages(42);
            first.setAttachMaxChars(77_000);
            first.setBaseUrl("http://localhost:8080");
            first.setSystemPrompt("hello");

            AppConfig second = new AppConfig(new UserConfig(configFile()));

            assertThat(second.temperature()).isEqualTo(0.9);
            assertThat(second.requestTimeoutMinutes()).isEqualTo(20);
            assertThat(second.historyMaxMessages()).isEqualTo(42);
            assertThat(second.attachMaxChars()).isEqualTo(77_000);
            assertThat(second.getBaseUrl()).isEqualTo("http://localhost:8080");
            assertThat(second.systemPrompt()).isEqualTo("hello");
        }
    }

    @Nested
    @DisplayName("Blank values fall back to defaults")
    class Reset {

        @Test
        @DisplayName("setBaseUrl(blank) removes the key and reverts to default")
        void baseUrl() {
            AppConfig c = config();
            c.setBaseUrl("http://custom:1234");
            assertThat(c.getBaseUrl()).isEqualTo("http://custom:1234");

            c.setBaseUrl("   ");
            assertThat(c.getBaseUrl()).isEqualTo(c.defaultBaseUrl());
        }

        @Test
        @DisplayName("setSystemPrompt(blank) reverts to default")
        void systemPrompt() {
            AppConfig c = config();
            c.setSystemPrompt("something");
            c.setSystemPrompt("");
            assertThat(c.systemPrompt()).isEqualTo(c.defaultSystemPrompt());
        }
    }

    @Nested
    @DisplayName("Corrupted user config")
    class Corrupted {

        @Test
        @DisplayName("Non-numeric value in a numeric field is ignored")
        void badNumber() throws IOException {
            Files.createDirectories(tmp);
            Files.writeString(configFile(),
                    ConfigKeys.OLLAMA_TEMPERATURE + "=not-a-number\n"
                            + ConfigKeys.OLLAMA_REQUEST_TIMEOUT_MINUTES + "=oops\n");

            AppConfig c = config();
            assertThat(c.temperature()).isEqualTo(c.defaultTemperature());
            assertThat(c.requestTimeoutMinutes()).isEqualTo(c.defaultRequestTimeoutMinutes());
        }
    }
}