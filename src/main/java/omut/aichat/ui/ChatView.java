package omut.aichat.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import omut.aichat.chat.ChatListener;
import omut.aichat.chat.AIChatMessage;
import omut.aichat.chat.ChatSession;

import java.util.List;

public class ChatView implements ChatListener {

    private final ChatSession session;

    private TextArea chatArea;
    private TextField inputField;
    private Button sendButton;
    private Button clearButton;
    private ComboBox<String> modelSelector;
    private Label statusLabel;
    private Label typingLabel;
    private Label lastResponseLabel;

    private boolean busy = false;
    private boolean llmAvailable = false;

    public ChatView(ChatSession session) {
        this.session = session;
        this.session.addListener(this);
    }

    public Parent build() {
        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        VBox.setVgrow(chatArea, Priority.ALWAYS);

        inputField = new TextField();
        inputField.setPromptText("Type a message and press Enter...");
        HBox.setHgrow(inputField, Priority.ALWAYS);

        sendButton = new Button("Send");
        clearButton = new Button("Clear");

        Button checkButton = new Button("Check connection");

        HBox inputBox = new HBox(8, inputField, sendButton, clearButton);
        inputBox.setPadding(new Insets(10));

        statusLabel = new Label("Status: unknown");
        statusLabel.setTextFill(Color.GRAY);
        typingLabel = new Label("");
        typingLabel.setTextFill(Color.DARKSLATEGRAY);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        modelSelector = new ComboBox<>();
        modelSelector.setPromptText("Model");
        modelSelector.setDisable(true);   // включим после загрузки списка
        modelSelector.setOnAction(e -> {
            String selected = modelSelector.getValue();
            if (selected != null && !selected.equals(session.currentModel())) {
                session.switchModel(selected);
            }
        });

        lastResponseLabel = new Label("");
        lastResponseLabel.setTextFill(Color.DARKSLATEGRAY);

        HBox statusBar = new HBox(
                10,
                statusLabel,
                modelSelector,
                spacer,
                lastResponseLabel,
                typingLabel,
                checkButton
        );

        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(0, 10, 10, 10));

        sendButton.setOnAction(e -> onSend());
        inputField.setOnAction(e -> onSend());
        clearButton.setOnAction(e -> session.clear());
        checkButton.setOnAction(e -> session.checkAvailability());

        VBox root = new VBox(5, chatArea, inputBox, statusBar);
        root.setPadding(new Insets(10));

        Platform.runLater(session::checkAvailability);

        return root;
    }

    @Override
    public void onStatusChanged(boolean available) {
        Platform.runLater(() -> {
            llmAvailable = available;
            if (available) {
                statusLabel.setText("Status: Ollama is available");
                statusLabel.setTextFill(Color.SEAGREEN);
                if (modelSelector.getItems().isEmpty()) {
                    session.loadModels();
                }
            } else {
                statusLabel.setText("Status: Ollama is NOT available");
                statusLabel.setTextFill(Color.CRIMSON);
            }
            updateControls();
        });
    }

    @Override
    public void onModelsLoaded(List<String> models) {
        Platform.runLater(() -> {
            modelSelector.getItems().setAll(models);
            if (!models.isEmpty()) {
                modelSelector.setValue(session.currentModel());
            }
            updateControls();
        });
    }

    @Override
    public void onModelChanged(String modelName) {
        Platform.runLater(() -> {
            statusLabel.setText("Status: Ollama is available  |  Model: " + modelName);
            statusLabel.setTextFill(Color.SEAGREEN);
        });
    }

    @Override
    public void onMessage(AIChatMessage message) {
        Platform.runLater(() -> {
            chatArea.appendText(format(message) + "\n\n");
            chatArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    @Override
    public void onThinkingStarted() {
        Platform.runLater(() -> {
            busy = true;
            typingLabel.setText("AI is thinking...");
            updateControls();
        });
    }

    @Override
    public void onThinkingFinished() {
        Platform.runLater(() -> {
            busy = false;
            typingLabel.setText("");
            updateControls();
            inputField.requestFocus();
        });
    }

    @Override
    public void onCleared() {
        Platform.runLater(chatArea::clear);
    }

    @Override
    public void onResponseTime(long millis) {
        Platform.runLater(() -> {
            if (millis < 1000) {
                lastResponseLabel.setText("Last: " + millis + "ms");
            } else {
                lastResponseLabel.setText(String.format(java.util.Locale.US, "Last: %.1fs", millis / 1000.0));
            }
        });
    }

    public void focusInput() {
        if (inputField != null) inputField.requestFocus();
    }

    private void onSend() {
        String text = inputField.getText();
        if (text == null || text.isBlank()) return;
        inputField.clear();
        session.send(text);
    }

    private String format(AIChatMessage message) {
        return switch (message.role()) {
            case USER -> "You: " + message.text();
            case ASSISTANT -> String.format(
                    java.util.Locale.US,
                    "AI (%s, %.1fs): %s",
                    session.currentModel(),
                    message.durationMillis() / 1000.0,
                    message.text()
            );
            case SYSTEM -> "System: " + message.text();
        };
    }

    private void updateControls() {
        boolean locked = busy || !llmAvailable;
        inputField.setDisable(locked);
        sendButton.setDisable(locked);
        clearButton.setDisable(busy);
        modelSelector.setDisable(locked || modelSelector.getItems().isEmpty());
    }

}