package omut.aichat.ui;

import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class SettingsDialog extends Dialog<String> {

    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");

    public SettingsDialog(String currentBaseUrl, String defaultBaseUrl) {
        setTitle("Settings");
        setHeaderText("Ollama server URL");

        TextField urlField = new TextField(currentBaseUrl);
        HBox.setHgrow(urlField, Priority.ALWAYS);

        Button resetButton = new Button("Reset");
        resetButton.setOnAction(e -> urlField.setText(defaultBaseUrl));

        HBox urlBox = new HBox(8, urlField, resetButton);

        VBox content = new VBox(10,
                new Label("Base URL (e.g. http://127.0.0.1:11434):"),
                urlBox
        );
        content.setPadding(new Insets(10));

        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Node okButton = getDialogPane().lookupButton(ButtonType.OK);
        urlField.textProperty().addListener((obs, oldValue, newValue) -> {
            boolean valid = BaseUrlValidator.normalize(newValue) != null;
            okButton.setDisable(!valid);
            urlField.pseudoClassStateChanged(INVALID, !valid);
        });

        boolean initialValid = BaseUrlValidator.normalize(currentBaseUrl) != null;
        okButton.setDisable(!initialValid);
        urlField.pseudoClassStateChanged(INVALID, !initialValid);

        setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            return BaseUrlValidator.normalize(urlField.getText());
        });
    }

}