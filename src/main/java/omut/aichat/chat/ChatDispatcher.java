package omut.aichat.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Broadcasts chat events to registered {@link ChatListener}s.
 * <p>
 * Holds the listener list and the failure-isolation logic that used
 * to live in {@link ChatSession}. Every {@code notifyXxx} method on
 * ChatSession has a counterpart here without the prefix.
 * <p>
 * Thread-safe: listeners may be added while events are dispatched.
 * A listener that throws does not prevent other listeners from
 * receiving the same event.
 */
public class ChatDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ChatDispatcher.class);

    private final List<ChatListener> listeners = new CopyOnWriteArrayList<>();

    public void add(ChatListener listener) {
        listeners.add(listener);
    }

    public void message(AIChatMessage m) {
        dispatch(l -> l.onMessage(m));
    }

    public void historyChanged() {
        dispatch(ChatListener::onHistoryChanged);
    }

    public void thinkingStarted() {
        dispatch(ChatListener::onThinkingStarted);
    }

    public void thinkingFinished() {
        dispatch(ChatListener::onThinkingFinished);
    }

    public void status(boolean available) {
        dispatch(l -> l.onStatusChanged(available));
    }

    public void cleared() {
        dispatch(ChatListener::onCleared);
    }

    public void modelsLoaded(List<String> models) {
        dispatch(l -> l.onModelsLoaded(models));
    }

    public void modelChanged(String modelName) {
        dispatch(l -> l.onModelChanged(modelName));
    }

    public void responseTime(long millis) {
        dispatch(l -> l.onResponseTime(millis));
    }

    public void tokensPerSecond(double tps) {
        dispatch(l -> l.onTokensPerSecond(tps));
    }

    public void requestCancelled() {
        dispatch(ChatListener::onRequestCancelled);
    }

    public void token(String chunk, String fullText) {
        dispatch(l -> l.onToken(chunk, fullText));
    }

    public void notice(String text, NoticeLevel level) {
        dispatch(l -> l.onNotice(text, level));
    }

    private void dispatch(Consumer<ChatListener> action) {
        for (ChatListener l : listeners) {
            try {
                action.accept(l);
            } catch (Exception e) {
                log.warn("Listener failed", e);
            }
        }
    }
}