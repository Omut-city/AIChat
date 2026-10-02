package omut.aichat.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import omut.aichat.service.LocalLlmService;

public class ChatController {

    private final LocalLlmService llmService;

    private TextArea chatArea;
    private TextField inputField;
    private Button sendButton;
    private Button clearButton;
    private Button checkButton;
    private Label statusLabel;
    private Label typingLabel;

    public ChatController(LocalLlmService llmService) {
        this.llmService = llmService;
    }

    /**
     * Builds the root layout and wires up all UI events.
     * Called by the Application class.
     */
    public Parent buildView() {
        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setPrefHeight(400);
        VBox.setVgrow(chatArea, Priority.ALWAYS);

        inputField = new TextField();
        inputField.setPromptText("Type a message and press Enter...");
        HBox.setHgrow(inputField, Priority.ALWAYS);

        sendButton = new Button("Send");
        clearButton = new Button("Clear");
        checkButton = new Button("Check connection");

        HBox inputBox = new HBox(8, inputField, sendButton, clearButton);
        inputBox.setPadding(new Insets(10));

        statusLabel = new Label("Status: unknown");
        statusLabel.setTextFill(Color.GRAY);

        typingLabel = new Label("");
        typingLabel.setTextFill(Color.DARKSLATEGRAY);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox statusBar = new HBox(10, statusLabel, spacer, typingLabel, checkButton);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(0, 10, 10, 10));

        // --- Wire up actions ---
        sendButton.setOnAction(e -> onSend());
        inputField.setOnAction(e -> onSend());
        clearButton.setOnAction(e -> onClear());
        checkButton.setOnAction(e -> checkConnectionAsync());

        VBox root = new VBox(5, chatArea, inputBox, statusBar);
        root.setPadding(new Insets(10));

        // Initial status check on startup
        Platform.runLater(this::checkConnectionAsync);

        return root;
    }

    /** Requests focus for the input field. Called after the window is shown. */
    public void focusInput() {
        if (inputField != null) {
            inputField.requestFocus();
        }
    }

    // --- Event handlers ---

    private void onSend() {
        String userText = inputField.getText().trim();
        if (userText.isEmpty()) return;

        appendChat("You: " + userText);
        inputField.clear();
        setBusy(true, "AI is thinking...");

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return llmService.ask(userText);
            }
        };

        task.setOnSucceeded(e -> {
            appendChat("AI: " + task.getValue());
            setBusy(false, "");
            updateStatus(true);
        });

        task.setOnFailed(e -> {
            appendChat("System: " + friendlyError(task.getException()));
            setBusy(false, "");
            updateStatus(false);
        });

        new Thread(task, "llm-worker").start();
    }

    private void onClear() {
        chatArea.clear();
        appendChat("System: Chat cleared.");
    }

    private void checkConnectionAsync() {
        statusLabel.setText("Status: checking...");
        statusLabel.setTextFill(Color.GRAY);

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                return llmService.isAvailable();
            }
        };

        task.setOnSucceeded(e -> updateStatus(task.getValue()));
        task.setOnFailed(e -> updateStatus(false));

        new Thread(task, "status-check").start();
    }

    // --- UI updates ---

    private void updateStatus(boolean available) {
        Platform.runLater(() -> {
            if (available) {
                statusLabel.setText("Status: Ollama is available");
                statusLabel.setTextFill(Color.SEAGREEN);
                sendButton.setDisable(false);
            } else {
                statusLabel.setText("Status: Ollama is NOT available");
                statusLabel.setTextFill(Color.CRIMSON);
                sendButton.setDisable(true);
                appendChat("System: Cannot reach Ollama at http://127.0.0.1:11434. "
                        + "Make sure Ollama is running and the model is pulled.");
            }
        });
    }

    private void setBusy(boolean busy, String typingText) {
        Platform.runLater(() -> {
            inputField.setDisable(busy);
            sendButton.setDisable(busy);
            clearButton.setDisable(busy);
            typingLabel.setText(busy ? typingText : "");
        });
    }

    private String friendlyError(Throwable ex) {
        if (ex == null) return "Unknown error.";
        String msg = ex.getMessage();
        if (msg == null) return ex.getClass().getSimpleName();

        if (msg.contains("Connection refused") || msg.contains("ConnectException")) {
            return "Cannot connect to Ollama. Is it running?";
        }
        if (msg.contains("model") && msg.contains("not found")) {
            return "Model not found. Run: ollama pull llama3.1:8b";
        }
        if (msg.contains("timeout") || msg.contains("Timeout")) {
            return "Request timed out. The model may be loading, try again.";
        }
        return msg;
    }

    private void appendChat(String text) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> chatArea.appendText(text + "\n\n"));
        } else {
            chatArea.appendText(text + "\n\n");
        }
    }
}