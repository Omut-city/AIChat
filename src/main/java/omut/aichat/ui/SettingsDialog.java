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
import omut.aichat.utils.UrlParser;

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

        Button addServerButton = new Button("Add");
        Button editServerButton = new Button("Edit");
        Button removeServerButton = new Button("Remove");

        addServerButton.setOnAction(e -> onAddServer(serverCombo));
        editServerButton.setOnAction(e -> onEditServer(serverCombo));
        removeServerButton.setOnAction(e -> onRemoveServer(serverCombo));

        serverCombo.valueProperty().addListener((_, _, sel) -> {
            boolean isLocal = sel != null && AppConfig.LOCAL_SERVER_ID.equals(sel.id());
            removeServerButton.setDisable(isLocal || sel == null);
            editServerButton.setDisable(sel == null);
        });
        if (serverCombo.getValue() != null) {
            boolean isLocal = AppConfig.LOCAL_SERVER_ID.equals(serverCombo.getValue().id());
            removeServerButton.setDisable(isLocal);
        }

        VBox serverBox = new VBox(6,
                new HBox(serverCombo),
                new HBox(8, addServerButton, editServerButton, removeServerButton));
        serverBox.setAlignment(Pos.CENTER_LEFT);

        ComboBox<PromptItem> promptCombo = new ComboBox<>();
        promptCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(promptCombo, Priority.ALWAYS);
        populatePrompts(promptCombo);

        Button addPromptButton = new Button("Add");
        Button editPromptButton = new Button("Edit");
        Button removePromptButton = new Button("Remove");

        addPromptButton.setOnAction(e -> onAddPrompt(promptCombo));
        editPromptButton.setOnAction(e -> onEditPrompt(promptCombo));
        removePromptButton.setOnAction(e -> onRemovePrompt(promptCombo));

        promptCombo.valueProperty().addListener((_, _, sel) -> {
            boolean isDefault = sel != null
                    && AppConfig.DEFAULT_PROMPT_ID.equals(sel.id());
            removePromptButton.setDisable(isDefault || sel == null);
            editPromptButton.setDisable(sel == null);
        });
        if (promptCombo.getValue() != null) {
            boolean isDefault = AppConfig.DEFAULT_PROMPT_ID.equals(
                    promptCombo.getValue().id());
            removePromptButton.setDisable(isDefault);
        }

        VBox promptBox = new VBox(6,
                new HBox(promptCombo),
                new HBox(8, addPromptButton, editPromptButton, removePromptButton));
        promptBox.setAlignment(Pos.CENTER_LEFT);

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

        addRow(grid, 0, "Active prompt:", promptBox);
        addRow(grid, 1, "Active server:", serverBox);
        addRow(grid, 2, "Temperature (0.0 – 2.0):", temperatureSpinner);
        addRow(grid, 3, "Request timeout, minutes:", timeoutSpinner);
        addRow(grid, 4, "History limit, messages:", historySpinner);
        addRow(grid, 5, "Attach size limit, characters:", attachSpinner);

        VBox content = new VBox(12, grid, resetButton);
        content.setPadding(new Insets(6, 14, 14, 14));
        content.setAlignment(Pos.CENTER);
        content.setPrefWidth(520);

        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        setResultConverter(button -> {
            if (button != ButtonType.OK) return null;

            ServerItem selectedServer = serverCombo.getValue();
            if (selectedServer != null) {
                config.setActiveServer(selectedServer.id());
            }
            PromptItem selectedPrompt = promptCombo.getValue();
            if (selectedPrompt != null) {
                config.setActivePrompt(selectedPrompt.id());
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
        selectServerById(combo, newId);
    }

    private void onEditServer(ComboBox<ServerItem> combo) {
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
        selectServerById(combo, sel.id());
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
        selectServerById(combo, config.activeServerId());
    }

    private void populateServers(ComboBox<ServerItem> combo) {
        combo.getItems().clear();
        for (String id : config.serverIds()) {
            combo.getItems().add(new ServerItem(id, config.serverName(id)));
        }
        selectServerById(combo, config.activeServerId());
    }

    private void selectServerById(ComboBox<ServerItem> combo, String id) {
        for (ServerItem item : combo.getItems()) {
            if (item.id().equals(id)) {
                combo.setValue(item);
                return;
            }
        }
    }

    private void onAddPrompt(ComboBox<PromptItem> combo) {
        PromptEditDialog.Result result = ChatDialogs.editPrompt(
                getOwner(),
                "Add prompt",
                "New prompt",
                null);
        if (result == null) return;
        String newId = config.addPrompt(result.name(), result.text());
        populatePrompts(combo);
        selectPromptById(combo, newId);
    }

    private void onEditPrompt(ComboBox<PromptItem> combo) {
        PromptItem sel = combo.getValue();
        if (sel == null) return;

        String currentName = config.promptName(sel.id());
        String currentText = config.promptText(sel.id());
        if (currentText == null) currentText = "";

        PromptEditDialog.Result result = ChatDialogs.editPrompt(
                getOwner(),
                "Edit prompt",
                "Edit '" + currentName + "'",
                new PromptEditDialog.Result(currentName, currentText));
        if (result == null) return;

        config.updatePrompt(sel.id(), result.name(), result.text());
        populatePrompts(combo);
        selectPromptById(combo, sel.id());
    }

    private void onRemovePrompt(ComboBox<PromptItem> combo) {
        PromptItem sel = combo.getValue();
        if (sel == null) return;
        if (AppConfig.DEFAULT_PROMPT_ID.equals(sel.id())) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove prompt");
        confirm.setHeaderText("Remove '" + config.promptName(sel.id()) + "'?");
        confirm.initOwner(getOwner());

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        config.removePrompt(sel.id());
        populatePrompts(combo);
        selectPromptById(combo, config.activePromptId());
    }

    private void populatePrompts(ComboBox<PromptItem> combo) {
        combo.getItems().clear();
        for (String id : config.promptIds()) {
            combo.getItems().add(new PromptItem(id, config.promptName(id)));
        }
        selectPromptById(combo, config.activePromptId());
    }

    private void selectPromptById(ComboBox<PromptItem> combo, String id) {
        for (PromptItem item : combo.getItems()) {
            if (item.id().equals(id)) {
                combo.setValue(item);
                return;
            }
        }
    }

    private String hostOf(String serverId) {
        String url = config.serverBaseUrl(serverId);
        if (url == null) return "";
        UrlParser.Parsed parts = UrlParser.parse(url);
        return parts != null ? parts.host() : "";
    }

    private int portOf(String serverId) {
        String url = config.serverBaseUrl(serverId);
        if (url == null) return UrlParser.DEFAULT_PORT;
        UrlParser.Parsed parts = UrlParser.parse(url);
        return parts != null ? parts.port() : UrlParser.DEFAULT_PORT;
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
}