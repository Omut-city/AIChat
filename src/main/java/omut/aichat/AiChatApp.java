package omut.aichat;

import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import omut.aichat.chat.ChatSession;
import omut.aichat.config.AppConfig;
import omut.aichat.service.LlmService;
import omut.aichat.service.OllamaLlmService;
import omut.aichat.ui.ChatView;

public class AiChatApp extends Application {

    private ChatSession session;

    @Override
    public void start(Stage stage) {
        AppConfig config = new AppConfig();
        LlmService llmService = new OllamaLlmService(config, config.defaultModel());
        session = new ChatSession(llmService);

        ChatView view = new ChatView(session);
        Parent root = view.build();

        Scene scene = new Scene(root, config.windowWidth(), config.windowHeight());
        stage.setTitle(config.windowTitle());
        stage.setScene(scene);
        stage.show();

        view.focusInput();
    }

    @Override
    public void stop() {
        if (session != null) session.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}