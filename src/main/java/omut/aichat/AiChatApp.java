package omut.aichat;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class AiChatApp extends Application {

    @Override
    public void start(Stage stage) {
        Label label = new Label("AIChat — заготовка работает");
        Scene scene = new Scene(new StackPane(label), 400, 200);
        stage.setTitle("AIChat");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}