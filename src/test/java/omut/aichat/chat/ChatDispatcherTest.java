package omut.aichat.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ChatDispatcherTest {

    /**
     * Minimal listener that records every event it receives and
     * optionally throws from every callback. One instance per test.
     */
    private static final class RecordingListener implements ChatListener {

        final List<String> events = new ArrayList<>();
        final AtomicReference<AIChatMessage> lastMessage = new AtomicReference<>();
        final AtomicReference<String> lastNotice = new AtomicReference<>();
        final AtomicReference<NoticeLevel> lastNoticeLevel = new AtomicReference<>();
        final AtomicReference<List<String>> lastModels = new AtomicReference<>();
        final AtomicReference<String> lastModel = new AtomicReference<>();
        final AtomicReference<Boolean> lastStatus = new AtomicReference<>();
        final AtomicReference<Double> lastTps = new AtomicReference<>();
        final AtomicReference<Long> lastResponseMillis = new AtomicReference<>();
        final boolean throwOnEveryCallback;

        RecordingListener() {
            this(false);
        }

        RecordingListener(boolean throwOnEveryCallback) {
            this.throwOnEveryCallback = throwOnEveryCallback;
        }

        private void record(String name) {
            events.add(name);
            if (throwOnEveryCallback) {
                throw new RuntimeException("listener failure: " + name);
            }
        }

        @Override
        public void onMessage(AIChatMessage message) {
            lastMessage.set(message);
            record("message");
        }

        @Override
        public void onHistoryChanged() {
            record("historyChanged");
        }

        @Override
        public void onThinkingStarted() {
            record("thinkingStarted");
        }

        @Override
        public void onThinkingFinished() {
            record("thinkingFinished");
        }

        @Override
        public void onStatusChanged(boolean available) {
            lastStatus.set(available);
            record("status");
        }

        @Override
        public void onCleared() {
            record("cleared");
        }

        @Override
        public void onModelsLoaded(List<String> models) {
            lastModels.set(models);
            record("modelsLoaded");
        }

        @Override
        public void onModelChanged(String modelName) {
            lastModel.set(modelName);
            record("modelChanged");
        }

        @Override
        public void onResponseTime(long millis) {
            lastResponseMillis.set(millis);
            record("responseTime");
        }

        @Override
        public void onRequestCancelled() {
            record("requestCancelled");
        }

        @Override
        public void onTokensPerSecond(double tps) {
            lastTps.set(tps);
            record("tokensPerSecond");
        }

        @Override
        public void onToken(String chunk, String fullText) {
            record("token");
        }

        @Override
        public void onNotice(String text, NoticeLevel level) {
            lastNotice.set(text);
            lastNoticeLevel.set(level);
            record("notice");
        }
    }

    @Nested
    @DisplayName("Event delegation")
    class Delegation {

        @Test
        @DisplayName("message delivers the AIChatMessage to the listener")
        void message() {
            ChatDispatcher d = new ChatDispatcher();
            RecordingListener l = new RecordingListener();
            d.add(l);

            AIChatMessage m = AIChatMessage.user("hi");
            d.message(m);

            assertThat(l.events).containsExactly("message");
            assertThat(l.lastMessage.get()).isSameAs(m);
        }

        @Test
        @DisplayName("each notify method fires its own callback exactly once")
        void allEvents() {
            ChatDispatcher d = new ChatDispatcher();
            RecordingListener l = new RecordingListener();
            d.add(l);

            d.historyChanged();
            d.thinkingStarted();
            d.thinkingFinished();
            d.status(true);
            d.cleared();
            d.modelsLoaded(List.of("a", "b"));
            d.modelChanged("qwen2.5:7b");
            d.responseTime(1234L);
            d.tokensPerSecond(42.5);
            d.requestCancelled();
            d.token("chunk", "chunk");
            d.notice("hi", NoticeLevel.INFO);

            assertThat(l.events).containsExactly(
                    "historyChanged", "thinkingStarted", "thinkingFinished",
                    "status", "cleared", "modelsLoaded", "modelChanged",
                    "responseTime", "tokensPerSecond", "requestCancelled",
                    "token", "notice");
        }

        @Test
        @DisplayName("status carries the boolean argument")
        void statusArg() {
            ChatDispatcher d = new ChatDispatcher();
            RecordingListener l = new RecordingListener();
            d.add(l);

            d.status(true);
            assertThat(l.lastStatus.get()).isTrue();

            d.status(false);
            assertThat(l.lastStatus.get()).isFalse();
        }

        @Test
        @DisplayName("modelsLoaded passes the list through")
        void modelsArg() {
            ChatDispatcher d = new ChatDispatcher();
            RecordingListener l = new RecordingListener();
            d.add(l);

            List<String> models = List.of("qwen2.5:7b", "llama3:8b");
            d.modelsLoaded(models);

            assertThat(l.lastModels.get()).isSameAs(models);
        }

        @Test
        @DisplayName("modelChanged carries the model name")
        void modelArg() {
            ChatDispatcher d = new ChatDispatcher();
            RecordingListener l = new RecordingListener();
            d.add(l);

            d.modelChanged("qwen2.5:7b");
            assertThat(l.lastModel.get()).isEqualTo("qwen2.5:7b");
        }

        @Test
        @DisplayName("responseTime and tokensPerSecond carry numeric arguments")
        void numericArgs() {
            ChatDispatcher d = new ChatDispatcher();
            RecordingListener l = new RecordingListener();
            d.add(l);

            d.responseTime(1234L);
            d.tokensPerSecond(42.5);

            assertThat(l.lastResponseMillis.get()).isEqualTo(1234L);
            assertThat(l.lastTps.get()).isEqualTo(42.5);
        }

        @Test
        @DisplayName("notice carries text and level")
        void noticeArgs() {
            ChatDispatcher d = new ChatDispatcher();
            RecordingListener l = new RecordingListener();
            d.add(l);

            d.notice("File too large", NoticeLevel.ERROR);

            assertThat(l.lastNotice.get()).isEqualTo("File too large");
            assertThat(l.lastNoticeLevel.get()).isEqualTo(NoticeLevel.ERROR);
        }
    }

    @Nested
    @DisplayName("Multiple listeners")
    class Multiple {

        @Test
        @DisplayName("every registered listener receives every event")
        void fanOut() {
            ChatDispatcher d = new ChatDispatcher();
            RecordingListener l1 = new RecordingListener();
            RecordingListener l2 = new RecordingListener();
            RecordingListener l3 = new RecordingListener();
            d.add(l1);
            d.add(l2);
            d.add(l3);

            d.thinkingStarted();

            assertThat(l1.events).containsExactly("thinkingStarted");
            assertThat(l2.events).containsExactly("thinkingStarted");
            assertThat(l3.events).containsExactly("thinkingStarted");
        }

        @Test
        @DisplayName("no listener means no crash")
        void empty() {
            ChatDispatcher d = new ChatDispatcher();
            d.thinkingStarted();
            d.message(AIChatMessage.user("hi"));
        }
    }

    @Nested
    @DisplayName("Failure isolation")
    class FailureIsolation {

        @Test
        @DisplayName("listener that throws does not prevent later listeners")
        void throwingFirst() {
            ChatDispatcher d = new ChatDispatcher();
            RecordingListener bad = new RecordingListener(true);
            RecordingListener good = new RecordingListener();
            d.add(bad);
            d.add(good);

            d.thinkingStarted();

            assertThat(bad.events).containsExactly("thinkingStarted");
            assertThat(good.events).containsExactly("thinkingStarted");
        }

        @Test
        @DisplayName("listener that throws does not prevent earlier listeners")
        void throwingLast() {
            ChatDispatcher d = new ChatDispatcher();
            RecordingListener good = new RecordingListener();
            RecordingListener bad = new RecordingListener(true);
            d.add(good);
            d.add(bad);

            d.thinkingStarted();

            assertThat(good.events).containsExactly("thinkingStarted");
            assertThat(bad.events).containsExactly("thinkingStarted");
        }

        @Test
        @DisplayName("exception in one callback does not affect the next dispatch")
        void nextDispatchStillWorks() {
            ChatDispatcher d = new ChatDispatcher();
            RecordingListener bad = new RecordingListener(true);
            d.add(bad);

            d.thinkingStarted();
            d.thinkingFinished();

            assertThat(bad.events).containsExactly("thinkingStarted", "thinkingFinished");
        }
    }

    @Nested
    @DisplayName("Listener list mutation")
    class Mutation {

        @Test
        @DisplayName("adding a listener during dispatch does not corrupt iteration")
        void addDuringDispatch() {
            ChatDispatcher d = new ChatDispatcher();
            RecordingListener first = new RecordingListener();
            RecordingListener second = new RecordingListener();

            ChatListener addOnMessage = new ChatListener() {
                @Override
                public void onMessage(AIChatMessage message) {
                    d.add(second);
                }

                @Override public void onHistoryChanged() {}
                @Override public void onThinkingStarted() {}
                @Override public void onThinkingFinished() {}
                @Override public void onStatusChanged(boolean available) {}
                @Override public void onCleared() {}
                @Override public void onModelsLoaded(List<String> models) {}
                @Override public void onModelChanged(String modelName) {}
                @Override public void onResponseTime(long millis) {}
                @Override public void onRequestCancelled() {}
                @Override public void onTokensPerSecond(double tps) {}
                @Override public void onToken(String chunk, String fullText) {}
                @Override public void onNotice(String text, NoticeLevel level) {}
            };

            d.add(addOnMessage);
            d.add(first);

            d.message(AIChatMessage.user("trigger"));

            assertThat(first.events).containsExactly("message");
            assertThat(second.events).isEmpty();  // snapshot semantics

            d.message(AIChatMessage.user("again"));
            assertThat(second.events).containsExactly("message");
        }
    }

    @Nested
    @DisplayName("Concurrency")
    class Concurrency {

        @Test
        @DisplayName("adding a listener from another thread is safe")
        void addFromAnotherThread() throws Exception {
            ChatDispatcher d = new ChatDispatcher();
            AtomicInteger received = new AtomicInteger();

            ChatListener counter = new ChatListener() {
                @Override
                public void onMessage(AIChatMessage message) {
                    received.incrementAndGet();
                }

                @Override public void onHistoryChanged() {}
                @Override public void onThinkingStarted() {}
                @Override public void onThinkingFinished() {}
                @Override public void onStatusChanged(boolean available) {}
                @Override public void onCleared() {}
                @Override public void onModelsLoaded(List<String> models) {}
                @Override public void onModelChanged(String modelName) {}
                @Override public void onResponseTime(long millis) {}
                @Override public void onRequestCancelled() {}
                @Override public void onTokensPerSecond(double tps) {}
                @Override public void onToken(String chunk, String fullText) {}
                @Override public void onNotice(String text, NoticeLevel level) {}
            };

            int events = 500;
            CountDownLatch done = new CountDownLatch(1);

            Thread adder = new Thread(() -> {
                try {
                    done.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                d.add(counter);
            });
            adder.start();

            for (int i = 0; i < events; i++) {
                d.message(AIChatMessage.user("m" + i));
            }
            done.countDown();
            adder.join(TimeUnit.SECONDS.toMillis(2));

            for (int i = 0; i < 10; i++) {
                d.message(AIChatMessage.user("tail" + i));
            }

            assertThat(received.get()).isBetween(0, 10);
        }
    }
}