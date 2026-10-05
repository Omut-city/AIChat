package omut.aichat.ui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import omut.aichat.AiChatApp;
import omut.aichat.chat.ChatExporter;
import omut.aichat.chat.ChatListener;
import omut.aichat.chat.AIChatMessage;
import omut.aichat.chat.ChatSession;
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
    private final ChatSession session;
    private final ChatViewBuilder view;
    private final StringBuilder markdownHistory = new StringBuilder();
    private final MarkdownRenderer markdown = new MarkdownRenderer();

    private long lastStreamRenderNanos = 0;
    private boolean busy = false;
    private boolean llmAvailable = false;

    public ChatView(ChatSession session) {
        this.session = session;
        this.session.addListener(this);
        this.view = new ChatViewBuilder();
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

        Theme current = Theme.fromId(session.theme());
        switch (current) {
            case NORD_LIGHT   -> view.nordLightItem.setSelected(true);
            case NORD_DARK    -> view.nordDarkItem.setSelected(true);
            case PRIMER_LIGHT -> view.primerLightItem.setSelected(true);
            case PRIMER_DARK  -> view.primerDarkItem.setSelected(true);
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
                view.statusLabel.setText("Status: Ollama is available");
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
        Platform.runLater(() -> {
            markdownHistory.append(markdown.formatMessage(message)).append("\n\n");
            renderMarkdown();
        });
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
            markdownHistory.setLength(0);
            view.chatView.getEngine().loadContent(
                    assets.wrapInHtml("", Theme.fromId(session.theme()),
                            session.chatTemplatePath(), session.highlightJsPath()
                    )
            );
            view.noticeLabel.setText("");
        });
    }

    @Override
    public void onResponseTime(long millis) {
        Platform.runLater(() -> {
            if (millis < 1000) {
                view.lastResponseLabel.setText("Last: " + millis + "ms");
            } else {
                view.lastResponseLabel.setText(String.format(java.util.Locale.US, "Last: %.1fs", millis / 1000.0));
            }
        });
    }

    @Override
    public void onRequestCancelled() {
        Platform.runLater(() -> {
            busy = false;
            view.typingLabel.setText("");
            view.stopButton.setDisable(true);
            markdownHistory.append("> System: Generation cancelled.\n\n");
            renderMarkdown();
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

    @Override
    public void onFileAttached(String fileName) {
        Platform.runLater(() -> {
            showNotice("Attached: " + fileName, Color.SEAGREEN);
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
                session.systemPrompt(),
                session.defaultSystemPrompt()
        );
        dialog.initOwner(view.chatView.getScene().getWindow());
        String newPrompt = dialog.showAndWait().orElse(null);
        if (newPrompt != null && !newPrompt.isBlank() && !newPrompt.equals(session.systemPrompt())) {
            session.setSystemPrompt(newPrompt);
        }
    }

    private void onSettings() {
        SettingsDialog dialog = new SettingsDialog(session.baseUrl(), session.defaultBaseUrl());
        dialog.initOwner(view.chatView.getScene().getWindow());
        String newUrl = dialog.showAndWait().orElse(null);
        if (newUrl != null && !newUrl.isBlank() && !newUrl.equals(session.baseUrl())) {
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
        String html = markdown.renderToHtml(markdownHistory.toString());
        view.chatView.getEngine().loadContent(
                assets.wrapInHtml(html, Theme.fromId(session.theme()),
                        session.chatTemplatePath(), session.highlightJsPath()
                )
        );
    }

    private void onAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About AIChat");
        alert.setHeaderText("AIChat — Local Offline LLM");
        alert.initOwner(view.chatView.getScene().getWindow());

        Hyperlink link = new Hyperlink("https://github.com/Omut-city/AIChat");
        link.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(
                        java.net.URI.create("https://github.com/Omut-city/AIChat"));
            } catch (Exception ex) {
                log.warn("Cannot open browser for {}: {}",
                        "https://github.com/Omut-city/AIChat", ex.getMessage());
            }
        });

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
        session.setTheme(theme.id());
        AiChatApp.applyTheme(theme.id());
        renderMarkdown();
    }
}