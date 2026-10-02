package omut.aichat;

import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import omut.aichat.controller.ChatController;
import omut.aichat.service.LocalLlmService;

public class AiChatApp extends Application {

    @Override
    public void start(Stage stage) {
        LocalLlmService llmService = new LocalLlmService();
        ChatController controller = new ChatController(llmService);

        Parent root = controller.buildView();
        Scene scene = new Scene(root, 640, 540);

        stage.setTitle("AIChat - Local Offline LLM");
        stage.setScene(scene);
        stage.show();

        controller.focusInput();
    }

    public static void main(String[] args) {
        launch(args);
    }
}