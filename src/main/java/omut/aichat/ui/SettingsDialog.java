package omut.aichat.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.net.URI;

public class SettingsDialog extends Dialog<String> {

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
            boolean valid = normalizeBaseUrl(newValue) != null;
            okButton.setDisable(!valid);
            urlField.setStyle(valid
                    ? ""
                    : "-fx-border-color: crimson; -fx-border-width: 1;");
        });

        boolean initialValid = normalizeBaseUrl(currentBaseUrl) != null;
        okButton.setDisable(!initialValid);

        setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            return normalizeBaseUrl(urlField.getText());
        });
    }

    private String normalizeBaseUrl(String raw) {
        if (raw == null || raw.isBlank()) return null;

        String url = raw.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }

        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            return url;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}