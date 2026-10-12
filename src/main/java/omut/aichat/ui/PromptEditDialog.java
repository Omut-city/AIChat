package omut.aichat.ui;

import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Modal dialog for adding or editing a named prompt. Fields:
 * display name, and a multi-line text area for the prompt body.
 * <p>
 * Returns a {@link Result} snapshot on OK, or {@code null} if
 * cancelled. OK is disabled while the name is blank; the text may
 * be blank (a prompt without text is valid — it simply removes the
 * SYSTEM message from the transcript when activated).
 * <p>
 * The dialog is deliberately unaware of the prompt id and the
 * {@code AppConfig} model. Callers decide what to do with the
 * {@code Result}.
 */
public class PromptEditDialog extends Dialog<PromptEditDialog.Result> {

    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");

    /** Immutable snapshot of the edited fields. */
    public record Result(String name, String text) {}

    /**
     * @param title      window title, e.g. "Add prompt" / "Edit prompt"
     * @param headerText header shown under the title bar
     * @param initial    values to pre-fill, or {@code null} for a new prompt
     */
    public PromptEditDialog(String title, String headerText, Result initial) {
        setTitle(title);
        setHeaderText(headerText);

        TextField nameField = new TextField(initial != null ? initial.name() : "");
        nameField.setPromptText("Default");

        TextArea textArea = new TextArea(initial != null ? initial.text() : "");
        textArea.setPromptText("You are a helpful assistant...");
        textArea.setWrapText(true);
        textArea.setPrefRowCount(8);
        VBox.setVgrow(textArea, Priority.ALWAYS);

        VBox content = new VBox(10,
                new Label("Name:"),
                nameField,
                new Label("Text:"),
                textArea
        );
        content.setPadding(new Insets(6, 14, 14, 14));
        content.setAlignment(Pos.TOP_LEFT);
        content.setPrefWidth(520);

        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Node okButton = getDialogPane().lookupButton(ButtonType.OK);

        Runnable validate = () -> {
            boolean nameOk = !nameField.getText().isBlank();
            nameField.pseudoClassStateChanged(INVALID, !nameOk);
            okButton.setDisable(!nameOk);
        };

        nameField.textProperty().addListener((_, _, _) -> validate.run());
        validate.run();

        setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            return new Result(
                    nameField.getText().trim(),
                    textArea.getText() == null ? "" : textArea.getText());
        });
    }
}