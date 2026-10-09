package omut.aichat.ui;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import omut.aichat.config.AppSettings;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

/**
 * Modal dialog for editing all user-tunable session settings in one
 * place: Ollama base URL, sampling temperature, request timeout,
 * history depth, and maximum attachment size.
 * <p>
 * Returns an {@link AppSettings} snapshot on OK, or {@code null} if
 * cancelled. Validation for base URL happens live (the OK button is
 * disabled while the URL does not parse); numeric fields are bounded
 * by their spinners and cannot go out of range.
 * <p>
 * A Test button next to the URL pings Ollama's {@code /api/tags} and
 * reports the result inline, so a wrong address is caught before OK.
 */
public class SettingsDialog extends Dialog<AppSettings> {

    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");
    private static final int PING_TIMEOUT_MS = 2000;

    public SettingsDialog(AppSettings current, AppSettings defaults) {
        setTitle("Settings");
        setHeaderText("Session settings");

        TextField urlField = new TextField(current.baseUrl());
        HBox.setHgrow(urlField, Priority.ALWAYS);
        urlField.setMaxWidth(Double.MAX_VALUE);

        Button testButton = new Button("Test");
        Label testResult = new Label("");
        testResult.getStyleClass().add("muted");
        testResult.setMinWidth(180);

        testButton.setOnAction(e -> runTest(urlField.getText(), testButton, testResult));

        HBox urlBox = new HBox(8, urlField, testButton);
        HBox.setHgrow(urlField, Priority.ALWAYS);

        Spinner<Double> temperatureSpinner = doubleSpinner(
                0.0, 2.0, current.temperature(), 0.1);
        Spinner<Integer> timeoutSpinner = intSpinner(
                1, 120, current.requestTimeoutMinutes(), 1);
        Spinner<Integer> historySpinner = intSpinner(
                2, 1000, current.historyMaxMessages(), 1);
        Spinner<Integer> attachSpinner = intSpinner(
                1_000, 10_000_000, current.attachMaxChars(), 1_000);

        Button resetButton = new Button("Reset to defaults");
        resetButton.setOnAction(e -> {
            urlField.setText(defaults.baseUrl());
            temperatureSpinner.getValueFactory().setValue(defaults.temperature());
            timeoutSpinner.getValueFactory().setValue(defaults.requestTimeoutMinutes());
            historySpinner.getValueFactory().setValue(defaults.historyMaxMessages());
            attachSpinner.getValueFactory().setValue(defaults.attachMaxChars());
        });

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setMaxWidth(Double.MAX_VALUE);
        grid.setAlignment(javafx.geometry.Pos.CENTER);

        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setHgrow(Priority.NEVER);
        labelCol.setHalignment(javafx.geometry.HPos.LEFT);
        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        fieldCol.setFillWidth(true);
        grid.getColumnConstraints().addAll(labelCol, fieldCol);

        addRow(grid, 0, "Base URL:", urlBox);
        addRow(grid, 1, "", testResult);
        addRow(grid, 2, "Temperature (0.0 – 2.0):", temperatureSpinner);
        addRow(grid, 3, "Request timeout, minutes:", timeoutSpinner);
        addRow(grid, 4, "History limit, messages:", historySpinner);
        addRow(grid, 5, "Attach size limit, characters:", attachSpinner);

        VBox content = new VBox(12, grid, resetButton);
        content.setPadding(new Insets(6, 14, 14, 14));
        content.setAlignment(javafx.geometry.Pos.CENTER);
        content.setPrefWidth(520);

        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Node okButton = getDialogPane().lookupButton(ButtonType.OK);
        urlField.textProperty().addListener((obs, oldValue, newValue) -> {
            boolean valid = BaseUrlValidator.normalize(newValue) != null;
            okButton.setDisable(!valid);
            urlField.pseudoClassStateChanged(INVALID, !valid);
            testResult.setText("");
        });

        boolean initialValid = BaseUrlValidator.normalize(current.baseUrl()) != null;
        okButton.setDisable(!initialValid);
        urlField.pseudoClassStateChanged(INVALID, !initialValid);

        setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            String url = BaseUrlValidator.normalize(urlField.getText());
            if (url == null) return null;
            return new AppSettings(
                    url,
                    temperatureSpinner.getValue(),
                    timeoutSpinner.getValue(),
                    historySpinner.getValue(),
                    attachSpinner.getValue()
            );
        });
    }

    /**
     * Pings {@code {url}/api/tags} off the FX thread and updates the
     * result label on the FX thread. The Test button is disabled
     * while the ping is in flight so a user cannot queue a second one.
     */
    private static void runTest(String rawUrl, Button testButton, Label result) {
        String url = BaseUrlValidator.normalize(rawUrl);
        if (url == null) {
            result.setText("Invalid URL");
            result.getStyleClass().removeAll("test-ok", "test-fail");
            result.getStyleClass().add("test-fail");
            return;
        }

        testButton.setDisable(true);
        result.setText("Testing...");
        result.getStyleClass().removeAll("test-ok", "test-fail");

        CompletableFuture
                .supplyAsync(() -> ping(url))
                .whenComplete((message, error) -> Platform.runLater(() -> {
                    testButton.setDisable(false);
                    if (error != null) {
                        result.setText("Cannot connect");
                        result.getStyleClass().removeAll("test-ok", "test-fail");
                        result.getStyleClass().add("test-fail");
                    } else if (message == null) {
                        result.setText("Cannot connect");
                        result.getStyleClass().removeAll("test-ok", "test-fail");
                        result.getStyleClass().add("test-fail");
                    } else {
                        result.setText(message);
                        result.getStyleClass().removeAll("test-ok", "test-fail");
                        result.getStyleClass().add("test-ok");
                    }
                }));
    }

    /**
     * Returns "Connected (N models)" on success, null on any failure.
     * Reads only the first line of the response; the model count is
     * not worth parsing JSON for here.
     */
    private static String ping(String baseUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)
                    URI.create(baseUrl + "/api/tags").toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(PING_TIMEOUT_MS);
            connection.setReadTimeout(PING_TIMEOUT_MS);
            int code = connection.getResponseCode();
            if (code != 200) return null;

            byte[] body = connection.getInputStream().readAllBytes();
            String text = new String(body, java.nio.charset.StandardCharsets.UTF_8);
            int count = 0;
            int idx = 0;
            while ((idx = text.indexOf("\"name\"", idx)) >= 0) {
                count++;
                idx += 6;
            }
            if (count == 0) return "Connected";
            return "Connected (" + count + " models)";
        } catch (IOException | IllegalArgumentException e) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void addRow(GridPane grid, int row, String label, Node field) {
        Label l = new Label(label);
        l.setMinWidth(Region.USE_PREF_SIZE);
        grid.add(l, 0, row);
        grid.add(field, 1, row);
    }

    /**
     * Non-editable spinner. Arrows are the only way to change the
     * value — free-form typing is disabled because parsing partial
     * input in a StringConverter is a source of NPEs and it buys
     * nothing here.
     * <p>
     * Grows to fill its grid column, with a floor of 120 px so the
     * arrow buttons stay clickable when the dialog is narrow.
     */
    private static <T> Spinner<T> styledSpinner(SpinnerValueFactory<T> factory) {
        Spinner<T> spinner = new Spinner<>(factory);
        spinner.setEditable(false);
        spinner.setPrefWidth(160);
        spinner.setMinWidth(120);
        spinner.setMaxWidth(Double.MAX_VALUE);
        return spinner;
    }

    private static Spinner<Double> doubleSpinner(
            double min, double max, double initial, double step
    ) {
        return styledSpinner(new SpinnerValueFactory.DoubleSpinnerValueFactory(
                min, max, initial, step));
    }

    private static Spinner<Integer> intSpinner(
            int min, int max, int initial, int step
    ) {
        return styledSpinner(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                min, max, initial, step));
    }
}