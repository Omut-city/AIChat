package omut.aichat;

import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import omut.aichat.chat.ChatSession;
import omut.aichat.config.AppConfig;
import omut.aichat.service.LlmService;
import omut.aichat.service.OllamaLlmService;
import omut.aichat.ui.ChatView;
import omut.aichat.ui.Theme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AiChatApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(AiChatApp.class);

    private ChatSession session;

    @Override
    public void start(Stage stage) {
        AppConfig config = new AppConfig();
        applyTheme(config.theme());
        LlmService llmService = new OllamaLlmService(config, config.defaultModel());
        session = new ChatSession(llmService);

        ChatView view = new ChatView(session);
        Parent root = view.build();

        Scene scene = new Scene(root, config.windowWidth(), config.windowHeight());
        stage.setTitle(config.windowTitle());
        stage.setScene(scene);
        stage.show();

        view.focusInput();
        log.info("AIChat started. Model: {}, base URL: {}",
                config.defaultModel(), config.getBaseUrl());
    }

    @Override
    public void stop() {
        log.info("AIChat shutting down");
        if (session != null) session.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }

    public static void applyTheme(String themeId) {
        Theme theme = Theme.fromId(themeId);
        switch (theme) {
            case NORD_LIGHT   -> Application.setUserAgentStylesheet(
                    new NordLight().getUserAgentStylesheet());
            case NORD_DARK    -> Application.setUserAgentStylesheet(
                    new NordDark().getUserAgentStylesheet());
            case PRIMER_LIGHT -> Application.setUserAgentStylesheet(
                    new PrimerLight().getUserAgentStylesheet());
            case PRIMER_DARK  -> Application.setUserAgentStylesheet(
                    new PrimerDark().getUserAgentStylesheet());
        }
    }
}