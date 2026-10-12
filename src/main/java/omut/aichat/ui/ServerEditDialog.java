package omut.aichat.ui;

import javafx.application.Platform;
import javafx.css.PseudoClass;
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

import java.util.concurrent.CompletableFuture;

/**
 * Modal dialog for adding or editing a named Ollama server. Fields:
 * display name, host, port. A Test button pings the {@code /api/tags}
 * endpoint of the entered host/port and reports the result inline.
 * <p>
 * Returns a {@link Result} snapshot on OK, or {@code null} if
 * cancelled. Validation happens live: OK is disabled until name and
 * host are non-blank and port is within range.
 * <p>
 * The dialog is deliberately unaware of the server id and the
 * {@code AppConfig} model. Callers decide what to do with the
 * {@code Result}.
 * <p>
 * Protocol is fixed at {@code "http"} for now — the data model
 * supports {@code "https"} but the dialog does not expose it yet.
 * When the HTTPS toggle lands, it becomes a field here and the
 * single constant below is replaced by the checkbox value.
 */
public class ServerEditDialog extends Dialog<ServerEditDialog.Result> {

    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");
    private static final String PROTOCOL = "http";

    /** Immutable snapshot of the edited fields. */
    public record Result(String name, String host, int port) {}

    /**
     * @param title      window title, e.g. "Add server" / "Edit server"
     * @param headerText header shown under the title bar
     * @param initial    values to pre-fill, or {@code null} for a new server
     */
    public ServerEditDialog(String title, String headerText, Result initial) {
        setTitle(title);
        setHeaderText(headerText);

        TextField nameField = new TextField(initial != null ? initial.name() : "");
        nameField.setPromptText("Local");

        TextField hostField = new TextField(initial != null ? initial.host() : "");
        hostField.setPromptText("127.0.0.1");

        TextField portField = new TextField(
                initial != null ? Integer.toString(initial.port()) : "11434");
        portField.setPrefColumnCount(6);
        portField.setPromptText("11434");

        Button testButton = new Button("Test");
        Label testResult = new Label("");
        testResult.getStyleClass().add("muted");
        testResult.setMinWidth(180);

        testButton.setOnAction(e -> runTest(
                hostField.getText(), portField.getText(), testButton, testResult));

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setMaxWidth(Double.MAX_VALUE);
        grid.setAlignment(Pos.CENTER);

        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setHgrow(Priority.NEVER);
        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        fieldCol.setFillWidth(true);
        grid.getColumnConstraints().addAll(labelCol, fieldCol);

        addRow(grid, 0, "Name:", nameField);
        addRow(grid, 1, "Host:", hostField);

        HBox portBox = new HBox(8, portField, testButton);
        portBox.setAlignment(Pos.CENTER_LEFT);
        addRow(grid, 2, "Port:", portBox);

        addRow(grid, 3, "", testResult);

        VBox content = new VBox(12, grid);
        content.setPadding(new Insets(6, 14, 14, 14));
        content.setAlignment(Pos.CENTER);
        content.setPrefWidth(460);

        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Node okButton = getDialogPane().lookupButton(ButtonType.OK);

        Runnable validate = () -> {
            boolean nameOk = !nameField.getText().isBlank();
            boolean hostOk = !hostField.getText().isBlank();
            int port = parsePort(portField.getText());
            boolean portOk = port > 0;

            nameField.pseudoClassStateChanged(INVALID, !nameOk);
            hostField.pseudoClassStateChanged(INVALID, !hostOk);
            portField.pseudoClassStateChanged(INVALID, !portOk);

            okButton.setDisable(!(nameOk && hostOk && portOk));
        };

        nameField.textProperty().addListener((_, _, _) -> validate.run());
        hostField.textProperty().addListener((_, _, _) -> validate.run());
        portField.textProperty().addListener((_, _, _) -> validate.run());

        validate.run();

        setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            int port = parsePort(portField.getText());
            if (port <= 0) return null;
            return new Result(
                    nameField.getText().trim(),
                    hostField.getText().trim(),
                    port);
        });
    }

    private static void runTest(
            String host, String portText, Button testButton, Label result
    ) {
        String trimmedHost = host == null ? "" : host.trim();
        int port = parsePort(portText);
        if (trimmedHost.isEmpty() || port <= 0) {
            result.setText("Invalid host or port");
            result.getStyleClass().removeAll("test-ok", "test-fail");
            result.getStyleClass().add("test-fail");
            return;
        }

        testButton.setDisable(true);
        result.setText("Testing...");
        result.getStyleClass().removeAll("test-ok", "test-fail");

        CompletableFuture
                .supplyAsync(() -> OllamaPing.ping(PROTOCOL, trimmedHost, port))
                .whenComplete((message, error) -> Platform.runLater(() -> {
                    testButton.setDisable(false);
                    if (error != null || message == null) {
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

    private static int parsePort(String raw) {
        if (raw == null || raw.isBlank()) return -1;
        try {
            int port = Integer.parseInt(raw.trim());
            if (port < 1 || port > 65535) return -1;
            return port;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void addRow(GridPane grid, int row, String label, Node field) {
        Label l = new Label(label);
        l.setMinWidth(Region.USE_PREF_SIZE);
        grid.add(l, 0, row);
        grid.add(field, 1, row);
    }
}