package omut.aichat.ui;

import javafx.animation.PauseTransition;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;
// Wildcard import: JSObject is deprecated since JDK 24 and IDEA
// flags a single-type import, though the type itself still works.
// There is no replacement; JSObject is how we expose the bridge.
import netscape.javascript.*;
import omut.aichat.chat.AIChatMessage;
import omut.aichat.chat.ChatExporter;
import omut.aichat.chat.ChatListener;
import omut.aichat.chat.ChatSession;
import omut.aichat.config.AppConfig;
import omut.aichat.utils.TimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ChatView implements ChatListener {

    private static final Logger log = LoggerFactory.getLogger(ChatView.class);
    private static final long STREAM_RENDER_INTERVAL_NANOS = 80_000_000L;

    private PauseTransition noticeTimer;

    private final AssetLoader assets = new AssetLoader();
    private final MarkdownRenderer markdown = new MarkdownRenderer();
    private final ChatViewBuilder view = new ChatViewBuilder();
    private final ChatSession session;
    private final AppConfig config;
    private final HostServices hostServices;
    private final ChatJsBridge jsBridge;
    private final ChatScripts scripts;

    private volatile long lastStreamRenderNanos = 0;
    private boolean busy = false;
    private boolean llmAvailable = false;

    public ChatView(
            ChatSession session,
            AppConfig config,
            HostServices hostServices
    ) {
        this.session = session;
        this.config = config;
        this.hostServices = hostServices;
        this.session.addListener(this);
        this.jsBridge = new ChatJsBridge(session, this::openEditDialog);
        this.scripts = new ChatScripts(view.chatView.getEngine());
    }

    public Parent build() {
        Parent root = view.build();

        view.exitMenuItem.setOnAction(_ -> Platform.exit());
        view.aboutMenuItem.setOnAction(_ -> onAbout());
        view.attachButton.setOnAction(_ -> onAttach());
        view.sendButton.setOnAction(_ -> onSend());
        view.inputField.setOnAction(_ -> onSend());
        view.clearButton.setOnAction(_ -> session.clear());
        view.checkButton.setOnAction(_ -> session.checkAvailability());
        view.promptButton.setOnAction(_ -> onEditSystemPrompt());
        view.saveButton.setOnAction(_ -> onSave());
        view.copyLastButton.setOnAction(_ -> onCopyLast());
        view.stopButton.setOnAction(_ -> session.cancelCurrentRequest());
        view.regenerateButton.setOnAction(_ -> session.regenerateLast());
        view.settingsButton.setOnAction(_ -> onSettings());
        view.refreshModelsMenuItem.setOnAction(_ -> session.loadModels());
        view.modelSelector.setOnAction(_ -> {
            String selected = view.modelSelector.getValue();
            if (selected != null && !selected.equals(session.currentModel())) {
                session.switchModel(selected);
            }
        });
        view.chatView.getEngine().getLoadWorker().stateProperty().addListener(
                (_, _, newState) -> {
                    if (newState == Worker.State.SUCCEEDED) {
                        registerJsBridge();
                        renderTranscript();
                        scripts.scrollToBottom();
                    }
                });
        view.themeGroup.selectedToggleProperty().addListener((_, _, newToggle) -> {
            if (newToggle == null) return;
            String themeId = (String) newToggle.getUserData();
            applyTheme(themeId);
        });

        String currentId = config.theme();
        for (Toggle toggle : view.themeGroup.getToggles()) {
            if (currentId.equals(toggle.getUserData())) {
                toggle.setSelected(true);
                break;
            }
        }

        reloadTemplate();

        Platform.runLater(session::checkAvailability);
        installAccelerators(root);
        return root;
    }

    @Override
    public void onStatusChanged(boolean available) {
        Platform.runLater(() -> {
            llmAvailable = available;
            if (available) {
                view.statusLabel.setText("Status: Ollama is available  |  Model: " + session.currentModel());
                view.statusLabel.setTextFill(Color.SEAGREEN);
                if (view.modelSelector.getItems().isEmpty()) {
                    session.loadModels();
                }
            } else {
                view.statusLabel.setText("Status: Ollama is NOT available");
                view.statusLabel.setTextFill(Color.CRIMSON);
            }
            updateControls();
        });
    }

    @Override
    public void onModelsLoaded(List<String> models) {
        Platform.runLater(() -> {
            view.modelSelector.getItems().setAll(models);
            if (!models.isEmpty()) {
                view.modelSelector.setValue(session.currentModel());
            }
            updateControls();
        });
    }

    @Override
    public void onModelChanged(String modelName) {
        Platform.runLater(() -> {
            view.statusLabel.setText("Status: Ollama is available  |  Model: " + modelName);
            view.statusLabel.setTextFill(Color.SEAGREEN);
        });
    }

    @Override
    public void onMessage(AIChatMessage message) {
        Platform.runLater(this::renderTranscript);
    }

    @Override
    public void onHistoryChanged() {
        Platform.runLater(this::renderTranscript);
    }

    @Override
    public void onThinkingStarted() {
        Platform.runLater(() -> {
            busy = true;
            view.typingLabel.setText("AI is thinking...");
            view.stopButton.setDisable(false);
            updateControls();
            scripts.beginStreaming();
            scripts.setBusy(true);
        });
    }

    @Override
    public void onThinkingFinished() {
        Platform.runLater(() -> {
            busy = false;
            view.typingLabel.setText("");
            view.stopButton.setDisable(true);
            updateControls();
            view.inputField.requestFocus();
            scripts.setBusy(false);
        });
    }

    @Override
    public void onCleared() {
        Platform.runLater(() -> {
            reloadTemplate();
            view.noticeLabel.setText("");
        });
    }

    @Override
    public void onResponseTime(long millis) {
        Platform.runLater(() -> view.lastResponseLabel.setText("Last: " + TimeFormat.shortDuration(millis)));
    }

    @Override
    public void onRequestCancelled() {
        Platform.runLater(() -> {
            busy = false;
            view.typingLabel.setText("");
            view.stopButton.setDisable(true);
            updateControls();
            view.inputField.requestFocus();
            scripts.setBusy(false);
        });
    }

    @Override
    public void onTokensPerSecond(double tps) {
        Platform.runLater(() -> {
            if (tps > 0) {
                view.speedLabel.setText(String.format(java.util.Locale.US, "%.1f tok/s", tps));
            } else {
                view.speedLabel.setText("");
            }
        });
    }

    private void onAttach() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Attach file");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text files", "*.txt", "*.md", "*.json", "*.xml", "*.yaml", "*.yml"),
                new FileChooser.ExtensionFilter("Code files", "*.java", "*.kt", "*.py", "*.js", "*.ts", "*.cpp", "*.c", "*.h"),
                new FileChooser.ExtensionFilter("All files", "*.*")
        );

        File file = chooser.showOpenDialog(window());
        if (file == null) return;

        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            session.attachFile(file.getName(), content);
        } catch (IOException e) {
            showNotice("Attach failed: " + e.getMessage(), Color.CRIMSON);
        }
    }

    @Override
    public void onToken(String chunk, String fullText) {
        long now = System.nanoTime();
        if (now - lastStreamRenderNanos < STREAM_RENDER_INTERVAL_NANOS) {
            return;
        }
        lastStreamRenderNanos = now;

        Platform.runLater(() -> {
            String html = markdown.renderToHtml(fullText);
            scripts.updateStreamingMessage(html);
            scripts.scrollIfAtBottom();
        });
    }

    public void focusInput() {
        view.inputField.requestFocus();
    }

    private void onSend() {
        String text = view.inputField.getText();
        if (text == null || text.isBlank()) return;
        view.inputField.clear();
        session.send(text);
    }

    private void updateControls() {
        boolean locked = busy || !llmAvailable;
        boolean hasAssistant = hasAssistantMessage();
        view.inputField.setDisable(locked);
        view.attachButton.setDisable(locked);
        view.sendButton.setDisable(locked);
        view.clearButton.setDisable(busy);
        view.saveButton.setDisable(busy);
        view.copyLastButton.setDisable(!hasAssistant);
        view.regenerateButton.setDisable(locked || !hasAssistant);
        view.modelSelector.setDisable(locked || view.modelSelector.getItems().isEmpty());
    }

    private boolean hasAssistantMessage() {
        for (AIChatMessage m : session.getHistory()) {
            if (m.role() == AIChatMessage.Role.ASSISTANT) return true;
        }
        return false;
    }

    private void onEditSystemPrompt() {
        String newPrompt = ChatDialogs.editSystemPrompt(
                window(), config.systemPrompt(), config.defaultSystemPrompt());
        if (newPrompt != null && !newPrompt.isBlank() && !newPrompt.equals(config.systemPrompt())) {
            session.setSystemPrompt(newPrompt);
        }
    }

    private String openEditDialog(String messageId) {
        AIChatMessage msg = findMessageById(messageId);
        if (msg == null) return null;
        return ChatDialogs.editMessage(window(), msg.text());
    }

    private Window window() {
        return view.chatView.getScene().getWindow();
    }

    private AIChatMessage findMessageById(String messageId) {
        for (AIChatMessage m : session.getHistory()) {
            if (m.id().equals(messageId)) return m;
        }
        return null;
    }

    private void onSettings() {
        String newUrl = ChatDialogs.editBaseUrl(
                window(), config.getBaseUrl(), config.defaultBaseUrl());
        if (newUrl != null && !newUrl.isBlank() && !newUrl.equals(config.getBaseUrl())) {
            session.setBaseUrl(newUrl);
        }
    }

    private void onSave() {
        List<AIChatMessage> history = new ArrayList<>(session.getHistory());
        if (history.isEmpty()) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save conversation");
        chooser.setInitialFileName(defaultFileName());
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Markdown (*.md)", "*.md"));

        File file = chooser.showSaveDialog(window());
        if (file == null) return;

        try {
            ChatExporter.exportMarkdown(history, file.toPath());
            showNotice("Saved: " + file.getName(), Color.SEAGREEN);
        } catch (IOException e) {
            showNotice("Save failed: " + e.getMessage(), Color.CRIMSON);
        }
    }

    private void onCopyLast() {
        AIChatMessage last = lastAssistantMessage();
        if (last == null) {
            showNotice("Nothing to copy", Color.DARKSLATEGRAY);
            return;
        }
        String plain = MarkdownRenderer.stripMarkdown(last.text());
        ClipboardContent content = new ClipboardContent();
        content.putString(plain);
        Clipboard.getSystemClipboard().setContent(content);
        showNotice("Copied last response", Color.SEAGREEN);
    }

    private AIChatMessage lastAssistantMessage() {
        List<AIChatMessage> history = session.getHistory();
        for (int i = history.size() - 1; i >= 0; i--) {
            AIChatMessage m = history.get(i);
            if (m.role() == AIChatMessage.Role.ASSISTANT) {
                return m;
            }
        }
        return null;
    }

    private String defaultFileName() {
        String model = session.currentModel().replace(":", "-");
        return "chat-" + model + "-" + LocalDate.now() + ".md";
    }

    private void showNotice(String text, Color color) {
        Platform.runLater(() -> {
            view.noticeLabel.setText(text);
            view.noticeLabel.setTextFill(color);

            if (noticeTimer != null) {
                noticeTimer.stop();
            }
            noticeTimer = new PauseTransition(Duration.seconds(3));
            noticeTimer.setOnFinished(_ -> view.noticeLabel.setText(""));
            noticeTimer.play();
        });
    }

    /**
     * Hands the current history to chat.js, which rebuilds the
     * transcript DOM. Does not reload the page — the JS bridge stays
     * alive across the update.
     */
    private void renderTranscript() {
        String json = TranscriptJson.toJson(session.getHistory(), markdown);
        scripts.renderTranscript(json);
    }

    /**
     * Reloads the HTML template from scratch with the current theme.
     * Used at startup, on theme change, and on clear — situations
     * where the {@code <body class>} or the page itself must be
     * rebuilt. The load listener then calls {@link #renderTranscript}
     * to repopulate the empty body.
     */
    private void reloadTemplate() {
        view.chatView.getEngine().loadContent(
                assets.wrapInHtml("", currentTheme(), config)
        );
    }

    private void onAbout() {
        ChatDialogs.showAbout(window(), hostServices);
    }

    /**
     * Exposes {@link ChatJsBridge} to the loaded document as
     * {@code window.javaBridge}. Called after every successful page load —
     * WebView creates a fresh JavaScript context on each loadContent, so
     * the bridge must be re-registered each time.
     */
    @SuppressWarnings("removal")
    private void registerJsBridge() {
        try {
            JSObject window = (JSObject) view.chatView.getEngine().executeScript("window");
            window.setMember("javaBridge", jsBridge);
        } catch (Exception e) {
            log.warn("Failed to register JS bridge: {}", e.getMessage());
        }
    }

    private void installAccelerators(Parent root) {
        root.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.L) {
                session.clear();
                event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.K) {
                view.modelSelector.requestFocus();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                view.inputField.requestFocus();
                event.consume();
            }
        });
    }

    private void applyTheme(String themeId) {
        Theme theme = Theme.fromId(themeId);
        config.setTheme(theme.id());
        theme.apply();
        reloadTemplate();
    }

    private Theme currentTheme() {
        return Theme.fromId(config.theme());
    }
}