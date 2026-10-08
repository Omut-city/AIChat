package omut.aichat.ui;

import javafx.application.HostServices;
import javafx.scene.control.Alert;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import omut.aichat.config.AppSettings;

/**
 * Factory for the modal dialogs used by the chat window.
 * <p>
 * Every dialog in the app goes through here: owner wiring, showAndWait,
 * and the small "About" alert are all hidden behind one method per
 * dialog. {@link ChatView} keeps only the business decision of what to
 * do with the returned value, never the mechanics of constructing the
 * dialog.
 * <p>
 * All methods block until the user closes the dialog and must therefore
 * be called on the JavaFX Application Thread.
 */
public final class ChatDialogs {

    private static final String PROJECT_URL = "https://github.com/Omut-city/AIChat";

    private ChatDialogs() {}

    /** Returns the edited text, or {@code null} if the user cancelled. */
    public static String editMessage(Window owner, String currentText) {
        EditMessageDialog dialog = new EditMessageDialog(currentText);
        dialog.initOwner(owner);
        return dialog.showAndWait().orElse(null);
    }

    /** Returns the edited system prompt, or {@code null} if cancelled. */
    public static String editSystemPrompt(Window owner, String current, String fallback) {
        SystemPromptDialog dialog = new SystemPromptDialog(current, fallback);
        dialog.initOwner(owner);
        return dialog.showAndWait().orElse(null);
    }

    /** Returns the edited settings, or {@code null} if cancelled. */
    public static AppSettings editSettings(
            Window owner,
            AppSettings current,
            AppSettings defaults
    ) {
        SettingsDialog dialog = new SettingsDialog(current, defaults);
        dialog.initOwner(owner);
        return dialog.showAndWait().orElse(null);
    }

    /** Shows the About dialog. Blocks until the user closes it. */
    public static void showAbout(Window owner, HostServices hostServices) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About AIChat");
        alert.setHeaderText("AIChat — Local Offline LLM");
        alert.initOwner(owner);

        Hyperlink link = new Hyperlink(PROJECT_URL);
        link.setOnAction(_ -> hostServices.showDocument(PROJECT_URL));

        VBox content = new VBox(8,
                new Label("A simple offline chat with local LLMs."),
                new Label("Built with Java 25, JavaFX 25, Ollama and LangChain4j."),
                link
        );
        alert.getDialogPane().setContent(content);

        alert.showAndWait();
    }
}