package omut.aichat.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import omut.aichat.config.AppConfig;
import omut.aichat.config.AppSettings;

import java.util.List;

/**
 * Modal dialog for editing all user-tunable session settings in one
 * place: sampling temperature, request timeout, history depth, and
 * maximum attachment size — plus the list of named Ollama servers.
 * <p>
 * Returns an {@link AppSettings} snapshot on OK, or {@code null} if
 * cancelled. Numeric fields are bounded by their spinners and cannot
 * go out of range.
 * <p>
 * The Base URL is deliberately not editable here: connection data
 * belongs to the named-server model, and server CRUD goes through
 * {@link ServerEditDialog}. This dialog only lets the user pick the
 * active server from the existing list. The returned
 * {@link AppSettings#baseUrl()} mirrors the active server's derived
 * URL so downstream code that still reads the field keeps working.
 */
public class SettingsDialog extends Dialog<AppSettings> {

    private final AppConfig config;

    public SettingsDialog(AppSettings current, AppSettings defaults, AppConfig config) {
        this.config = config;

        setTitle("Settings");
        setHeaderText("Session settings");

        Spinner<Double> temperatureSpinner = doubleSpinner(
                0.0, 2.0, current.temperature(), 0.1);
        Spinner<Integer> timeoutSpinner = intSpinner(
                1, 120, current.requestTimeoutMinutes(), 1);
        Spinner<Integer> historySpinner = intSpinner(
                2, 1000, current.historyMaxMessages(), 1);
        Spinner<Integer> attachSpinner = intSpinner(
                1_000, 10_000_000, current.attachMaxChars(), 1_000);

        ComboBox<ServerItem> serverCombo = new ComboBox<>();
        serverCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(serverCombo, Priority.ALWAYS);
        populateServers(serverCombo);

        Button addButton = new Button("Add");
        Button editButton = new Button("Edit");
        Button removeButton = new Button("Remove");

        addButton.setOnAction(e -> onAddServer(serverCombo));
        editButton.setOnAction(e -> onRenameServer(serverCombo));
        removeButton.setOnAction(e -> onRemoveServer(serverCombo));

        serverCombo.valueProperty().addListener((_, _, sel) -> {
            boolean isLocal = sel != null && AppConfig.LOCAL_SERVER_ID.equals(sel.id());
            removeButton.setDisable(isLocal || sel == null);
            editButton.setDisable(sel == null);
        });
        if (serverCombo.getValue() != null) {
            boolean isLocal = AppConfig.LOCAL_SERVER_ID.equals(serverCombo.getValue().id());
            removeButton.setDisable(isLocal);
        }

        HBox comboRow = new HBox(serverCombo);
        comboRow.setAlignment(Pos.CENTER_LEFT);

        HBox buttonRow = new HBox(8, addButton, editButton, removeButton);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        VBox serverBox = new VBox(6, comboRow, buttonRow);
        serverBox.setAlignment(Pos.CENTER_LEFT);

        Button resetButton = new Button("Reset to defaults");
        resetButton.setOnAction(e -> {
            temperatureSpinner.getValueFactory().setValue(defaults.temperature());
            timeoutSpinner.getValueFactory().setValue(defaults.requestTimeoutMinutes());
            historySpinner.getValueFactory().setValue(defaults.historyMaxMessages());
            attachSpinner.getValueFactory().setValue(defaults.attachMaxChars());
        });

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setMaxWidth(Double.MAX_VALUE);
        grid.setAlignment(Pos.CENTER);

        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setHgrow(Priority.NEVER);
        labelCol.setHalignment(javafx.geometry.HPos.LEFT);
        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        fieldCol.setFillWidth(true);
        grid.getColumnConstraints().addAll(labelCol, fieldCol);

        addRow(grid, 0, "Active server:", serverBox);
        addRow(grid, 1, "Temperature (0.0 – 2.0):", temperatureSpinner);
        addRow(grid, 2, "Request timeout, minutes:", timeoutSpinner);
        addRow(grid, 3, "History limit, messages:", historySpinner);
        addRow(grid, 4, "Attach size limit, characters:", attachSpinner);

        HBox resetRow = new HBox(resetButton);
        resetRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(12, grid, resetRow);
        content.setPadding(new Insets(6, 14, 14, 14));
        content.setAlignment(Pos.CENTER);
        content.setPrefWidth(480);

        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        setResultConverter(button -> {
            if (button != ButtonType.OK) return null;

            ServerItem selected = serverCombo.getValue();
            if (selected != null) {
                config.setActiveServer(selected.id());
            }
            return new AppSettings(
                    config.getBaseUrl(),
                    temperatureSpinner.getValue(),
                    timeoutSpinner.getValue(),
                    historySpinner.getValue(),
                    attachSpinner.getValue()
            );
        });
    }

     private void onAddServer(ComboBox<ServerItem> combo) {
        ServerEditDialog.Result result = ChatDialogs.editServer(
                getOwner(),
                "Add server",
                "New Ollama server",
                null);
        if (result == null) return;
        String newId = config.addServer(result.name(), result.host(), result.port());
        populateServers(combo);
        selectById(combo, newId);
    }

    private void onRenameServer(ComboBox<ServerItem> combo) {
        ServerItem sel = combo.getValue();
        if (sel == null) return;

        String currentName = config.serverName(sel.id());
        String host = hostOf(sel.id());
        int port = portOf(sel.id());

        ServerEditDialog.Result result = ChatDialogs.editServer(
                getOwner(),
                "Edit server",
                "Edit '" + currentName + "'",
                new ServerEditDialog.Result(currentName, host, port));
        if (result == null) return;

        config.updateServer(sel.id(), result.name(), result.host(), result.port());
        populateServers(combo);
        selectById(combo, sel.id());
    }

    private void onRemoveServer(ComboBox<ServerItem> combo) {
        ServerItem sel = combo.getValue();
        if (sel == null) return;
        if (AppConfig.LOCAL_SERVER_ID.equals(sel.id())) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove server");
        confirm.setHeaderText("Remove '" + config.serverName(sel.id()) + "'?");
        confirm.initOwner(getOwner());

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        config.removeServer(sel.id());
        populateServers(combo);
        selectById(combo, config.activeServerId());
    }

    private void populateServers(ComboBox<ServerItem> combo) {
        combo.getItems().clear();
        for (String id : config.serverIds()) {
            combo.getItems().add(new ServerItem(id, config.serverName(id)));
        }
        selectById(combo, config.activeServerId());
    }

    private void selectById(ComboBox<ServerItem> combo, String id) {
        for (ServerItem item : combo.getItems()) {
            if (item.id().equals(id)) {
                combo.setValue(item);
                return;
            }
        }
    }

    private String hostOf(String serverId) {
        String url = config.serverBaseUrl(serverId);
        if (url == null) return "";
        BaseUrlValidator.Parts parts = BaseUrlValidator.split(url);
        return parts != null ? parts.host() : "";
    }

    private int portOf(String serverId) {
        String url = config.serverBaseUrl(serverId);
        if (url == null) return 11434;
        BaseUrlValidator.Parts parts = BaseUrlValidator.split(url);
        return parts != null ? parts.port() : 11434;
    }

    private static void addRow(GridPane grid, int row, String label, Node field) {
        Label l = new Label(label);
        l.setMinWidth(Region.USE_PREF_SIZE);
        l.setAlignment(Pos.TOP_LEFT);
        l.setPadding(new Insets(6, 0, 0, 0));
        grid.add(l, 0, row);
        grid.add(field, 1, row);
    }

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

    /**
     * One row in the server ComboBox. {@code id} is what
     * {@link AppConfig} uses; {@code name} is what the user sees.
     */
    private record ServerItem(String id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }
}