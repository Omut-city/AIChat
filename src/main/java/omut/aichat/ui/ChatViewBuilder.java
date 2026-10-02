package omut.aichat.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Builds the JavaFX scene graph for the chat window.
 * Holds references to all interactive widgets so the controller
 * can subscribe to their events and update them.
 */
public class ChatViewBuilder {

    public final TextArea chatArea = new TextArea();
    public final TextField inputField = new TextField();
    public final Button sendButton = new Button("Send");
    public final Button clearButton = new Button("Clear");
    public final Button checkButton = new Button("Check connection");
    public final Button settingsButton = new Button("Settings");
    public final Button promptButton = new Button("Prompt");
    public final Button saveButton = new Button("Save");
    public final ComboBox<String> modelSelector = new ComboBox<>();
    public final Label statusLabel = new Label("Status: unknown");
    public final Label typingLabel = new Label("");
    public final Label lastResponseLabel = new Label("");
    public final Label noticeLabel = new Label("");

    public Parent build() {
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        VBox.setVgrow(chatArea, Priority.ALWAYS);

        inputField.setPromptText("Type a message and press Enter...");
        HBox.setHgrow(inputField, Priority.ALWAYS);

        HBox inputBox = new HBox(8, inputField, sendButton, clearButton);
        inputBox.setPadding(new Insets(10));

        noticeLabel.setTextFill(Color.DARKSLATEGRAY);
        statusLabel.setTextFill(Color.GRAY);
        typingLabel.setTextFill(Color.DARKSLATEGRAY);
        lastResponseLabel.setTextFill(Color.DARKSLATEGRAY);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        modelSelector.setPromptText("Model");
        modelSelector.setDisable(true);

        HBox statusBar = new HBox(
                10,
                statusLabel,
                modelSelector,
                spacer,
                lastResponseLabel,
                noticeLabel,
                typingLabel,
                saveButton,
                checkButton,
                promptButton,
                settingsButton
        );
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(0, 10, 10, 10));

        VBox root = new VBox(5, chatArea, inputBox, statusBar);
        root.setPadding(new Insets(10));
        return root;
    }
}