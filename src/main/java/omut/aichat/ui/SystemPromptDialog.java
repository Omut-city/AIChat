package omut.aichat.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class SystemPromptDialog extends Dialog<String> {

    public SystemPromptDialog(String currentPrompt, String defaultPrompt) {
        setTitle("System prompt");
        setHeaderText("Instructions sent to the model");

        TextArea promptArea = new TextArea(currentPrompt);
        promptArea.setWrapText(true);
        promptArea.setPrefRowCount(6);
        VBox.setVgrow(promptArea, Priority.ALWAYS);

        Button resetButton = new Button("Reset to default");
        resetButton.setOnAction(e -> promptArea.setText(defaultPrompt));

        HBox buttons = new HBox(8, resetButton);

        VBox content = new VBox(10,
                new Label("System prompt:"),
                promptArea,
                buttons
        );
        content.setPadding(new Insets(10));
        content.setPrefWidth(500);

        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            String text = promptArea.getText();
            return text == null ? null : text.trim();
        });
    }
}