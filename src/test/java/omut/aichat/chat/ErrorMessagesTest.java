package omut.aichat.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorMessagesTest {

    @Test
    @DisplayName("null → \"Unknown error.\"")
    void nullThrowable() {
        assertThat(ErrorMessages.humanize(null)).isEqualTo("Unknown error.");
    }

    @Nested
    @DisplayName("Connection failures")
    class ConnectionFailures {

        @ParameterizedTest(name = "{0}")
        @MethodSource("exceptions")
        @DisplayName("→ \"Cannot connect to Ollama. Is it running?\"")
        void direct(Throwable ex) {
            assertThat(ErrorMessages.humanize(ex))
                    .isEqualTo("Cannot connect to Ollama. Is it running?");
        }

        static Stream<Throwable> exceptions() {
            return Stream.of(
                    new ConnectException("Connection refused"),
                    new NoRouteToHostException("No route to host"),
                    new ConnectException()  // без message
            );
        }
    }

    @Nested
    @DisplayName("Timeouts")
    class Timeouts {

        @ParameterizedTest(name = "{0}")
        @MethodSource("exceptions")
        @DisplayName("→ \"Request timed out. ...\"")
        void direct(Throwable ex) {
            assertThat(ErrorMessages.humanize(ex))
                    .startsWith("Request timed out.");
        }

        static Stream<Throwable> exceptions() {
            return Stream.of(
                    new SocketTimeoutException("Read timed out"),
                    new TimeoutException("Took too long")
            );
        }
    }

    @Nested
    @DisplayName("Wrapped exceptions")
    class Wrapped {

        @Test
        @DisplayName("ConnectException inside RuntimeException → connection message")
        void wrappedConnection() {
            Throwable ex = new RuntimeException("outer",
                    new RuntimeException("middle",
                            new ConnectException("refused")));
            assertThat(ErrorMessages.humanize(ex))
                    .isEqualTo("Cannot connect to Ollama. Is it running?");
        }

        @Test
        @DisplayName("TimeoutException inside RuntimeException → timeout message")
        void wrappedTimeout() {
            Throwable ex = new RuntimeException("outer", new TimeoutException());
            assertThat(ErrorMessages.humanize(ex))
                    .startsWith("Request timed out.");
        }

        @Test
        @DisplayName("Self-referencing exception does not loop forever")
        void selfReference() {
            RuntimeException self = new RuntimeException("loop") {
                @Override
                public synchronized Throwable getCause() {
                    return this;
                }
            };
            assertThat(ErrorMessages.humanize(self)).isEqualTo("loop");
        }
    }

    @Nested
    @DisplayName("Model not found")
    class ModelNotFound {

        @Test
        @DisplayName("\"model not found\" in message → dedicated message")
        void lowercase() {
            Throwable ex = new RuntimeException("model qwen2 not found");
            assertThat(ErrorMessages.humanize(ex))
                    .startsWith("Model not found.");
        }

        @Test
        @DisplayName("Both keywords required — \"model\" alone is not enough")
        void onlyModel() {
            Throwable ex = new RuntimeException("selected model is big");
            assertThat(ErrorMessages.humanize(ex))
                    .isEqualTo("selected model is big");
        }

        @Test
        @DisplayName("Both keywords required — \"not found\" alone is not enough")
        void onlyNotFound() {
            Throwable ex = new RuntimeException("file not found");
            assertThat(ErrorMessages.humanize(ex))
                    .isEqualTo("file not found");
        }
    }

    @Nested
    @DisplayName("Fall-through")
    class FallThrough {

        @Test
        @DisplayName("Generic exception with message → its message")
        void genericWithMessage() {
            assertThat(ErrorMessages.humanize(new RuntimeException("boom")))
                    .isEqualTo("boom");
        }

        @Test
        @DisplayName("Generic exception without message → simple class name")
        void genericWithoutMessage() {
            assertThat(ErrorMessages.humanize(new RuntimeException()))
                    .isEqualTo("RuntimeException");
        }

        @Test
        @DisplayName("Custom exception class name shows up when message is null")
        void customClass() {
            assertThat(ErrorMessages.humanize(new IllegalStateException()))
                    .isEqualTo("IllegalStateException");
        }
    }
}