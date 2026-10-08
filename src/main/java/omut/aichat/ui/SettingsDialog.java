package omut.aichat.ui;

import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import omut.aichat.config.AppSettings;

/**
 * Modal dialog for editing all user-tunable session settings in one
 * place: Ollama base URL, sampling temperature, request timeout,
 * history depth, and maximum attachment size.
 * <p>
 * Returns an {@link AppSettings} snapshot on OK, or {@code null} if
 * cancelled. Validation for base URL happens live (the OK button is
 * disabled while the URL does not parse); numeric fields are bounded
 * by their spinners and cannot go out of range.
 */
public class SettingsDialog extends Dialog<AppSettings> {

    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");

    public SettingsDialog(AppSettings current, AppSettings defaults) {
        setTitle("Settings");
        setHeaderText("Session settings");

        TextField urlField = new TextField(current.baseUrl());
        GridPane.setHgrow(urlField, Priority.ALWAYS);
        urlField.setMaxWidth(Double.MAX_VALUE);

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

        addRow(grid, 0, "Base URL:", urlField);
        addRow(grid, 1, "Temperature (0.0 – 2.0):", temperatureSpinner);
        addRow(grid, 2, "Request timeout, minutes:", timeoutSpinner);
        addRow(grid, 3, "History limit, messages:", historySpinner);
        addRow(grid, 4, "Attach size limit, characters:", attachSpinner);

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
     * The width is fixed via min/max (not pref), otherwise GridPane
     * stretches the spinner to the column width and the arrow buttons
     * collapse to a couple of pixels.
     */
    private static <T> Spinner<T> styledSpinner(SpinnerValueFactory<T> factory) {
        Spinner<T> spinner = new Spinner<>(factory);
        spinner.setEditable(false);
        spinner.setPrefWidth(160);                       // было 140 — базовый размер
        spinner.setMinWidth(120);                        // не схлопываться
        spinner.setMaxWidth(Double.MAX_VALUE);           // растягиваться
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