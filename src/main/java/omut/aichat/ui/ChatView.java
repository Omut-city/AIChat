package omut.aichat.ui;

import javafx.animation.PauseTransition;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.util.Duration;
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
    private static final String PROJECT_URL = "https://github.com/Omut-city/AIChat";

    private PauseTransition noticeTimer;

    private final AssetLoader assets = new AssetLoader();
    private final MarkdownRenderer markdown = new MarkdownRenderer();
    private final ChatViewBuilder view = new ChatViewBuilder();
    private final ChatSession session;
    private final AppConfig config;
    private final HostServices hostServices;

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
    }

    public Parent build() {
        Parent root = view.build();

        view.exitMenuItem.setOnAction(e -> Platform.exit());
        view.aboutMenuItem.setOnAction(e -> onAbout());
        view.attachButton.setOnAction(e -> onAttach());
        view.sendButton.setOnAction(e -> onSend());
        view.inputField.setOnAction(e -> onSend());
        view.clearButton.setOnAction(e -> session.clear());
        view.checkButton.setOnAction(e -> session.checkAvailability());
        view.promptButton.setOnAction(e -> onEditSystemPrompt());
        view.saveButton.setOnAction(e -> onSave());
        view.stopButton.setOnAction(e -> session.cancelCurrentRequest());
        view.settingsButton.setOnAction(e -> onSettings());
        view.refreshModelsMenuItem.setOnAction(e -> session.loadModels());
        view.modelSelector.setOnAction(e -> {
            String selected = view.modelSelector.getValue();
            if (selected != null && !selected.equals(session.currentModel())) {
                session.switchModel(selected);
            }
        });
        view.chatView.getEngine().getLoadWorker().stateProperty().addListener(
                (obs, oldState, newState) -> {
                    if (newState == Worker.State.SUCCEEDED) {
                        executeScriptSafely("window.scrollTo(0, document.body.scrollHeight);");
                    }
                });
        view.themeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
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

        renderMarkdown();

        Platform.runLater(session::checkAvailability);
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
        Platform.runLater(this::renderMarkdown);
    }

    @Override
    public void onThinkingStarted() {
        Platform.runLater(() -> {
            busy = true;
            view.typingLabel.setText("AI is thinking...");
            view.stopButton.setDisable(false);
            updateControls();
            executeScriptSafely("beginStreaming();");
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
        });
    }

    @Override
    public void onCleared() {
        Platform.runLater(() -> {
            view.chatView.getEngine().loadContent(
                    assets.wrapInHtml("", currentTheme(),
                            config.chatTemplatePath(), config.highlightJsPath()
                    )
            );
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

        File file = chooser.showOpenDialog(view.chatView.getScene().getWindow());
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
            String escaped = MarkdownRenderer.jsStringLiteral(html);
            executeScriptSafely("updateStreamingMessage(" + escaped + ");");
            executeScriptSafely("if (isAtBottom()) scrollToBottom();");
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
        view.inputField.setDisable(locked);
        view.attachButton.setDisable(locked);
        view.sendButton.setDisable(locked);
        view.clearButton.setDisable(busy);
        view.saveButton.setDisable(busy);
        view.modelSelector.setDisable(locked || view.modelSelector.getItems().isEmpty());
    }

    private void onEditSystemPrompt() {
        SystemPromptDialog dialog = new SystemPromptDialog(
                config.systemPrompt(),
                config.defaultSystemPrompt()
        );
        dialog.initOwner(view.chatView.getScene().getWindow());
        String newPrompt = dialog.showAndWait().orElse(null);
        if (newPrompt != null && !newPrompt.isBlank() && !newPrompt.equals(config.systemPrompt())) {
            session.setSystemPrompt(newPrompt);
        }
    }

    private void onSettings() {
        SettingsDialog dialog = new SettingsDialog(config.getBaseUrl(), config.defaultBaseUrl());
        dialog.initOwner(view.chatView.getScene().getWindow());
        String newUrl = dialog.showAndWait().orElse(null);
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

        File file = chooser.showSaveDialog(view.chatView.getScene().getWindow());
        if (file == null) return;

        try {
            ChatExporter.exportMarkdown(history, file.toPath());
            showNotice("Saved: " + file.getName(), Color.SEAGREEN);
        } catch (IOException e) {
            showNotice("Save failed: " + e.getMessage(), Color.CRIMSON);
        }
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
            noticeTimer.setOnFinished(e -> view.noticeLabel.setText(""));
            noticeTimer.play();
        });
    }

    private void renderMarkdown() {
        StringBuilder sb = new StringBuilder();
        for (AIChatMessage message : session.getHistory()) {
            sb.append(markdown.formatMessage(message)).append("\n\n");
        }
        String html = markdown.renderToHtml(sb.toString());
        view.chatView.getEngine().loadContent(
                assets.wrapInHtml(html, currentTheme(),
                        config.chatTemplatePath(), config.highlightJsPath()
                )
        );
    }

    private void onAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About AIChat");
        alert.setHeaderText("AIChat — Local Offline LLM");
        alert.initOwner(view.chatView.getScene().getWindow());

        Hyperlink link = new Hyperlink(PROJECT_URL);
        link.setOnAction(e -> hostServices.showDocument(PROJECT_URL));

        VBox content = new VBox(8,
                new Label("A simple offline chat with local LLMs."),
                new Label("Built with Java 25, JavaFX 25, Ollama and LangChain4j."),
                link
        );
        alert.getDialogPane().setContent(content);

        alert.showAndWait();
    }

    /**
     * Execute JS in the WebView, ignoring failures.
     * Failures can happen if the document has not finished loading yet
     * (e.g. user sent a message before WebView loaded chat.html).
     */
    private void executeScriptSafely(String script) {
        try {
            view.chatView.getEngine().executeScript(script);
        } catch (Exception e) {
            log.warn("executeScript failed: {} — {}", script, e.getMessage());
        }
    }

    private void applyTheme(String themeId) {
        Theme theme = Theme.fromId(themeId);
        config.setTheme(theme.id());
        theme.apply();
        renderMarkdown();
    }

    private Theme currentTheme() {
        return Theme.fromId(config.theme());
    }
}