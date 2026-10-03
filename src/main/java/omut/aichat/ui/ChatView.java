package omut.aichat.ui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import omut.aichat.chat.ChatExporter;
import omut.aichat.chat.ChatListener;
import omut.aichat.chat.AIChatMessage;
import omut.aichat.chat.ChatSession;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ChatView implements ChatListener {

    private PauseTransition noticeTimer;

    private final ChatSession session;
    private final ChatViewBuilder view;
    private final StringBuilder markdownHistory = new StringBuilder();
    private final Parser markdownParser = Parser.builder().build();
    private final HtmlRenderer markdownRenderer = HtmlRenderer.builder().build();

    private boolean busy = false;
    private boolean llmAvailable = false;

    public ChatView(ChatSession session) {
        this.session = session;
        this.session.addListener(this);
        this.view = new ChatViewBuilder();
    }

    public Parent build() {
        Parent root = view.build();

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
            markdownHistory.append(formatMarkdown(message)).append("\n\n");
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
            view.chatView.getEngine().loadContent(wrapInHtml(""));
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
            view.typingLabel.setText("");
            markdownHistory.append("> System: Generation cancelled.\n\n");
            renderMarkdown();
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

    public void focusInput() {
        view.inputField.requestFocus();
    }

    private void onSend() {
        String text = view.inputField.getText();
        if (text == null || text.isBlank()) return;
        view.inputField.clear();
        session.send(text);
    }

    private String formatMarkdown(AIChatMessage message) {
        return switch (message.role()) {
            case USER -> "**You:** " + message.text();
            case ASSISTANT -> String.format(
                    java.util.Locale.US,
                    "**AI (%s, %.1fs):**%n%n%s",
                    session.currentModel(),
                    message.durationMillis() / 1000.0,
                    message.text()
            );
            case SYSTEM -> "> " + message.text();
        };
    }

    private void updateControls() {
        boolean locked = busy || !llmAvailable;
        view.inputField.setDisable(locked);
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
        String html = markdownRenderer.render(markdownParser.parse(markdownHistory.toString()));
        String page = wrapInHtml(html);
        view.chatView.getEngine().loadContent(page);
        scrollToBottom();
    }

    private void scrollToBottom() {
        Platform.runLater(() ->
                view.chatView.getEngine().executeScript("window.scrollTo(0, document.body.scrollHeight);"));
    }

    private String wrapInHtml(String body) {
        return """
        <html>
        <head>
        <meta charset="UTF-8">
        <style>
        body {
            font-family: 'Segoe UI', sans-serif;
            font-size: 14px;
            margin: 12px;
            color: #222;
        }
        pre {
            background: #f4f4f4;
            padding: 8px;
            border-radius: 4px;
            overflow-x: auto;
        }
        code {
            font-family: 'Consolas', monospace;
            font-size: 13px;
        }
        blockquote {
            border-left: 3px solid #ccc;
            margin-left: 0;
            padding-left: 10px;
            color: #555;
        }
        </style>
        </head>
        <body>
        """ + body + """
        </body>
        </html>
        """;
    }
}