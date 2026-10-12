package omut.aichat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
        @DisplayName("Scalar values fall through to bundled defaults")
        void defaults() {
            AppConfig c = config();
            assertThat(c.getBaseUrl()).isEqualTo(c.defaultBaseUrl());
            assertThat(c.temperature()).isEqualTo(c.defaultTemperature());
            assertThat(c.requestTimeoutMinutes()).isEqualTo(c.defaultRequestTimeoutMinutes());
            assertThat(c.historyMaxMessages()).isEqualTo(c.defaultHistoryMaxMessages());
            assertThat(c.attachMaxChars()).isEqualTo(c.defaultAttachMaxChars());
            assertThat(c.systemPrompt()).isEqualTo(c.defaultSystemPrompt());
        }

        @Test
        @DisplayName("A single 'local' server is created and activated")
        void localServerAutoCreated() {
            AppConfig c = config();
            assertThat(c.serverIds()).containsExactly(AppConfig.LOCAL_SERVER_ID);
            assertThat(c.activeServerId()).isEqualTo(AppConfig.LOCAL_SERVER_ID);
            assertThat(c.serverName(AppConfig.LOCAL_SERVER_ID)).isEqualTo("Local");
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
        @DisplayName("baseUrl updates the active server's host/port/protocol")
        void baseUrl() throws IOException {
            AppConfig c = config();
            c.setBaseUrl("http://192.168.1.5:11434");
            assertThat(c.getBaseUrl()).isEqualTo("http://192.168.1.5:11434");

            Properties saved = readBack();
            assertThat(saved.getProperty(
                    ConfigKeys.serverKey("local", ConfigKeys.SERVER_HOST_SUFFIX)))
                    .isEqualTo("192.168.1.5");
            assertThat(saved.getProperty(
                    ConfigKeys.serverKey("local", ConfigKeys.SERVER_PORT_SUFFIX)))
                    .isEqualTo("11434");
            assertThat(saved.getProperty(
                    ConfigKeys.serverKey("local", ConfigKeys.SERVER_PROTOCOL_SUFFIX)))
                    .isEqualTo("http");
        }

        @Test
        @DisplayName("systemPrompt")
        void systemPrompt() throws IOException {
            AppConfig c = config();
            c.setSystemPrompt("be concise");
            assertThat(c.systemPrompt()).isEqualTo("be concise");

            Properties saved = readBack();
            assertThat(saved.getProperty(
                    ConfigKeys.promptKey("default", ConfigKeys.PROMPT_TEXT_SUFFIX)))
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
        @DisplayName("setBaseUrl(blank) resets the active server to bundled defaults")
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

    @Nested
    @DisplayName("Legacy URL migration")
    class Migration {

        @Test
        @DisplayName("legacy ollama.base.url becomes the 'local' server")
        void legacyUrlBecomesLocal() throws IOException {
            Files.createDirectories(tmp);
            Files.writeString(configFile(),
                    ConfigKeys.OLLAMA_BASE_URL + "=http://10.0.0.5:8080\n");

            AppConfig c = config();

            assertThat(c.serverIds()).containsExactly(AppConfig.LOCAL_SERVER_ID);
            assertThat(c.activeServerId()).isEqualTo(AppConfig.LOCAL_SERVER_ID);
            assertThat(c.getBaseUrl()).isEqualTo("http://10.0.0.5:8080");

            Properties saved = readBack();
            assertThat(saved.getProperty(
                    ConfigKeys.serverKey("local", ConfigKeys.SERVER_HOST_SUFFIX)))
                    .isEqualTo("10.0.0.5");
            assertThat(saved.getProperty(
                    ConfigKeys.serverKey("local", ConfigKeys.SERVER_PORT_SUFFIX)))
                    .isEqualTo("8080");
            assertThat(saved.getProperty(
                    ConfigKeys.serverKey("local", ConfigKeys.SERVER_PROTOCOL_SUFFIX)))
                    .isEqualTo("http");
        }

        @Test
        @DisplayName("URL without a port gets the default 11434")
        void defaultPort() throws IOException {
            Files.createDirectories(tmp);
            Files.writeString(configFile(),
                    ConfigKeys.OLLAMA_BASE_URL + "=http://192.168.1.5\n");

            AppConfig c = config();
            assertThat(c.getBaseUrl()).isEqualTo("http://192.168.1.5:11434");
        }

        @Test
        @DisplayName("Migration runs only once; later runs keep the server list")
        void notRerun() {
            AppConfig first = config();
            first.addServer("Home", "10.0.0.10", 11434);

            AppConfig second = new AppConfig(new UserConfig(configFile()));
            assertThat(second.serverIds()).hasSize(2);
            assertThat(second.serverIds().get(0)).isEqualTo(AppConfig.LOCAL_SERVER_ID);
        }
    }

    @Nested
    @DisplayName("Self-heal")
    class SelfHeal {

        @Test
        @DisplayName("Missing 'local' is restored at the head of the list")
        void missingLocalRestored() throws IOException {
            Files.createDirectories(tmp);
            Files.writeString(configFile(),
                    ConfigKeys.OLLAMA_SERVER_LIST + "=srv-aaaa,srv-bbbb\n"
                            + ConfigKeys.OLLAMA_ACTIVE_SERVER + "=srv-aaaa\n");

            AppConfig c = config();

            assertThat(c.serverIds()).first().isEqualTo(AppConfig.LOCAL_SERVER_ID);
            assertThat(c.serverIds()).contains("srv-aaaa", "srv-bbbb");
        }

        @Test
        @DisplayName("Active pointing at a missing id falls back to 'local'")
        void brokenActiveReset() throws IOException {
            Files.createDirectories(tmp);
            Files.writeString(configFile(),
                    ConfigKeys.OLLAMA_SERVER_LIST + "=local\n"
                            + ConfigKeys.OLLAMA_ACTIVE_SERVER + "=srv-gone\n");

            AppConfig c = config();
            assertThat(c.activeServerId()).isEqualTo(AppConfig.LOCAL_SERVER_ID);
        }

        @Test
        @DisplayName("Missing 'local' fields are filled from bundled defaults")
        void missingFieldsFilled() throws IOException {
            Files.createDirectories(tmp);
            Files.writeString(configFile(),
                    ConfigKeys.OLLAMA_SERVER_LIST + "=local\n"
                            + ConfigKeys.serverKey("local", ConfigKeys.SERVER_HOST_SUFFIX)
                            + "=192.168.1.5\n");

            AppConfig c = config();
            Properties saved = readBack();
            assertThat(saved.getProperty(
                    ConfigKeys.serverKey("local", ConfigKeys.SERVER_NAME_SUFFIX)))
                    .isNotBlank();
            assertThat(saved.getProperty(
                    ConfigKeys.serverKey("local", ConfigKeys.SERVER_PORT_SUFFIX)))
                    .isEqualTo("11434");
        }

        @Test
        @DisplayName("addServer after self-heal keeps 'local' first")
        void addAfterSelfHeal() throws IOException {
            Files.createDirectories(tmp);
            Files.writeString(configFile(),
                    ConfigKeys.OLLAMA_SERVER_LIST + "=srv-aaaa\n"
                            + ConfigKeys.OLLAMA_ACTIVE_SERVER + "=srv-aaaa\n");

            AppConfig c = config();
            assertThat(c.serverIds()).first().isEqualTo(AppConfig.LOCAL_SERVER_ID);

            String newId = c.addServer("New", "1.2.3.4", 11434);
            assertThat(c.serverIds()).containsExactly(
                    AppConfig.LOCAL_SERVER_ID, "srv-aaaa", newId);
        }
    }

    @Nested
    @DisplayName("addServer")
    class AddServer {

        @Test
        @DisplayName("New id has 'srv-' prefix and does not collide")
        void generatesId() {
            AppConfig c = config();
            String id = c.addServer("Home", "10.0.0.10", 11434);
            assertThat(id).startsWith("srv-");
            assertThat(c.serverIds()).contains(id);
            assertThat(c.serverIds()).hasSize(2);
        }

        @Test
        @DisplayName("Fields are persisted")
        void persistsFields() {
            AppConfig c = config();
            String id = c.addServer("Home", "10.0.0.10", 11434);

            assertThat(c.serverName(id)).isEqualTo("Home");
            assertThat(c.serverBaseUrl(id)).isEqualTo("http://10.0.0.10:11434");
        }

        @Test
        @DisplayName("Adding a server does not change the active one")
        void activeUnchanged() {
            AppConfig c = config();
            c.addServer("Home", "10.0.0.10", 11434);
            assertThat(c.activeServerId()).isEqualTo(AppConfig.LOCAL_SERVER_ID);
        }

        @Test
        @DisplayName("100 servers produce 100 unique ids")
        void uniqueIds() {
            AppConfig c = config();
            java.util.Set<String> ids = new java.util.HashSet<>();
            ids.add(AppConfig.LOCAL_SERVER_ID);

            for (int i = 0; i < 100; i++) {
                String id = c.addServer("S" + i, "127.0.0.1", 11434);
                assertThat(ids.add(id))
                        .as("duplicate id generated: " + id)
                        .isTrue();
            }
            assertThat(c.serverIds()).hasSize(101);
        }
    }

    @Nested
    @DisplayName("updateServer")
    class UpdateServer {

        @Test
        @DisplayName("Renaming updates the display name only")
        void rename() {
            AppConfig c = config();
            c.updateServer(AppConfig.LOCAL_SERVER_ID, "My Ollama",
                    "127.0.0.1", 11434);
            assertThat(c.serverName(AppConfig.LOCAL_SERVER_ID)).isEqualTo("My Ollama");
        }

        @Test
        @DisplayName("Changing host/port of the active server updates getBaseUrl()")
        void updatesActiveBaseUrl() {
            AppConfig c = config();
            c.updateServer(AppConfig.LOCAL_SERVER_ID, "Local",
                    "192.168.1.5", 9000);
            assertThat(c.getBaseUrl()).isEqualTo("http://192.168.1.5:9000");
        }

        @Test
        @DisplayName("Changing a non-active server leaves getBaseUrl() alone")
        void nonActiveDoesNotAffectBaseUrl() {
            AppConfig c = config();
            String home = c.addServer("Home", "10.0.0.10", 11434);
            c.updateServer(home, "Home", "10.0.0.11", 8080);
            assertThat(c.getBaseUrl()).isEqualTo(c.defaultBaseUrl());
        }

        @Test
        @DisplayName("Unknown id is a no-op")
        void unknownId() {
            AppConfig c = config();
            c.updateServer("srv-nope", "X", "1.2.3.4", 1234);
            assertThat(c.serverIds()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("removeServer")
    class RemoveServer {

        @Test
        @DisplayName("Removes the server and its fields")
        void removesFields() {
            AppConfig c = config();
            String id = c.addServer("Home", "10.0.0.10", 11434);
            c.removeServer(id);

            assertThat(c.serverIds()).doesNotContain(id);
            assertThat(c.serverBaseUrl(id)).isNull();
        }

        @Test
        @DisplayName("'local' cannot be removed")
        void localProtected() {
            AppConfig c = config();
            c.removeServer(AppConfig.LOCAL_SERVER_ID);
            assertThat(c.serverIds()).contains(AppConfig.LOCAL_SERVER_ID);
        }

        @Test
        @DisplayName("Removing the active server falls back to 'local'")
        void activeFallback() {
            AppConfig c = config();
            String id = c.addServer("Home", "10.0.0.10", 11434);
            c.setActiveServer(id);
            assertThat(c.activeServerId()).isEqualTo(id);

            c.removeServer(id);
            assertThat(c.activeServerId()).isEqualTo(AppConfig.LOCAL_SERVER_ID);
        }
    }

    @Nested
    @DisplayName("setActiveServer")
    class SetActive {

        @Test
        @DisplayName("Switching updates getBaseUrl() to the new server's URL")
        void switchChangesBaseUrl() {
            AppConfig c = config();
            String id = c.addServer("Home", "10.0.0.10", 8080);
            c.setActiveServer(id);

            assertThat(c.activeServerId()).isEqualTo(id);
            assertThat(c.getBaseUrl()).isEqualTo("http://10.0.0.10:8080");
        }

        @Test
        @DisplayName("Unknown id is a no-op")
        void unknownId() {
            AppConfig c = config();
            c.setActiveServer("srv-nope");
            assertThat(c.activeServerId()).isEqualTo(AppConfig.LOCAL_SERVER_ID);
        }

        @Test
        @DisplayName("Blank id is a no-op")
        void blankId() {
            AppConfig c = config();
            String id = c.addServer("Home", "10.0.0.10", 8080);
            c.setActiveServer(id);

            c.setActiveServer("   ");
            assertThat(c.activeServerId()).isEqualTo(id);
        }
    }

    @Nested
    @DisplayName("serverBaseUrl")
    class ServerBaseUrl {

        @Test
        @DisplayName("Unknown id returns null")
        void unknown() {
            AppConfig c = config();
            assertThat(c.serverBaseUrl("srv-nope")).isNull();
        }

        @Test
        @DisplayName("Local server URL matches defaultBaseUrl on a fresh config")
        void localMatchesDefault() {
            AppConfig c = config();
            assertThat(c.serverBaseUrl(AppConfig.LOCAL_SERVER_ID))
                    .isEqualTo(c.defaultBaseUrl());
        }
    }

    @Nested
    @DisplayName("serverName")
    class ServerName {

        @Test
        @DisplayName("Unknown id returns the id itself")
        void unknownId() {
            AppConfig c = config();
            assertThat(c.serverName("srv-nope")).isEqualTo("srv-nope");
        }

        @Test
        @DisplayName("Configured name wins over id")
        void configured() {
            AppConfig c = config();
            String id = c.addServer("Work", "10.0.0.10", 11434);
            assertThat(c.serverName(id)).isEqualTo("Work");
        }
    }

    @Nested
    @DisplayName("serverIds order")
    class Order {

        @Test
        @DisplayName("Local is always first")
        void localFirst() {
            AppConfig c = config();
            c.addServer("A", "1.1.1.1", 1);
            c.addServer("B", "2.2.2.2", 2);
            List<String> ids = c.serverIds();
            assertThat(ids.get(0)).isEqualTo(AppConfig.LOCAL_SERVER_ID);
        }

        @Test
        @DisplayName("New servers are appended in insertion order")
        void insertionOrder() {
            AppConfig c = config();
            String a = c.addServer("A", "1.1.1.1", 1);
            String b = c.addServer("B", "2.2.2.2", 2);
            assertThat(c.serverIds()).containsExactly(
                    AppConfig.LOCAL_SERVER_ID, a, b);
        }
    }

    @Nested
    @DisplayName("Idempotent initialization")
    class Idempotent {

        @Test
        @DisplayName("Second AppConfig on the same file does not re-migrate the server")
        void secondInitDoesNotRenameLocal() {
            AppConfig first = config();
            String firstId = first.activeServerId();
            String firstName = first.serverName(firstId);

            AppConfig second = new AppConfig(new UserConfig(configFile()));

            assertThat(second.activeServerId()).isEqualTo(firstId);
            assertThat(second.serverName(firstId)).isEqualTo(firstName);
            assertThat(second.serverIds()).hasSize(1);
        }

        @Test
        @DisplayName("Second AppConfig does not re-migrate the legacy prompt")
        void secondInitDoesNotTouchPrompt() {
            AppConfig first = config();
            first.updatePrompt(AppConfig.DEFAULT_PROMPT_ID, "Custom", "custom text");

            AppConfig second = new AppConfig(new UserConfig(configFile()));
            assertThat(second.systemPrompt()).isEqualTo("custom text");
            assertThat(second.promptIds()).containsExactly(AppConfig.DEFAULT_PROMPT_ID);
        }
    }
}