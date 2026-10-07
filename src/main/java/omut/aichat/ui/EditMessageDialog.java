package omut.aichat.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Modal dialog for editing the last user message. Returns the new
 * text (trimmed) on OK, or empty if cancelled.
 * <p>
 * Deliberately minimal: no reset button, no character counter, no
 * preview. The consequence of saving (the reply is discarded and
 * the prompt is re-sent) is not surfaced here — the user learns it
 * from the transcript after they hit OK.
 */
public class EditMessageDialog extends Dialog<String> {

    public EditMessageDialog(String currentText) {
        setTitle("Edit last message");
        setHeaderText("Edit the last message you sent");

        TextArea area = new TextArea(currentText);
        area.setWrapText(true);
        area.setPrefRowCount(6);
        VBox.setVgrow(area, Priority.ALWAYS);

        VBox content = new VBox(10,
                new Label("Message:"),
                area
        );
        content.setPadding(new Insets(10));
        content.setPrefWidth(500);

        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            String text = area.getText();
            return text == null ? null : text.trim();
        });
    }
}