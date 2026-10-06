package omut.aichat.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;

/**
 * Builds the JavaFX scene graph for the chat window.
 * Holds references to all interactive widgets so the controller
 * can subscribe to their events and update them.
 */
public class ChatViewBuilder {

    public final WebView chatView = new WebView();
    public final TextField inputField = new TextField();
    public final Button attachButton = new Button("Attach");
    public final Button sendButton = new Button("Send");
    public final Button stopButton = new Button("Stop");
    public final Button regenerateButton = new Button("Regenerate");
    public final Button clearButton = new Button("Clear");
    public final Button checkButton = new Button("Check");
    public final Button settingsButton = new Button("Settings");
    public final Button promptButton = new Button("Prompt");
    public final Button saveButton = new Button("Save");
    public final Button copyLastButton = new Button("Copy");
    public final ComboBox<String> modelSelector = new ComboBox<>();
    public final Label statusLabel = new Label("Status: unknown");
    public final Label typingLabel = new Label("");
    public final Label lastResponseLabel = new Label("");
    public final Label speedLabel = new Label("");
    public final Label noticeLabel = new Label("");

    public final Menu viewMenu = new Menu("View");
    public final MenuBar menuBar = new MenuBar();
    public final MenuItem exitMenuItem = new MenuItem("Exit");
    public final MenuItem aboutMenuItem = new MenuItem("About AIChat");
    public final MenuItem refreshModelsMenuItem = new MenuItem("Refresh models");

    public final ToggleGroup themeGroup = new ToggleGroup();
    public final RadioMenuItem nordLightItem = new RadioMenuItem("Nord Light");
    public final RadioMenuItem nordDarkItem  = new RadioMenuItem("Nord Dark");
    public final RadioMenuItem primerLightItem = new RadioMenuItem("Primer Light");
    public final RadioMenuItem primerDarkItem  = new RadioMenuItem("Primer Dark");

    public Parent build() {

        Menu fileMenu = new Menu("File");
        fileMenu.getItems().add(exitMenuItem);

        Menu helpMenu = new Menu("Help");
        helpMenu.getItems().add(aboutMenuItem);

        nordLightItem.setToggleGroup(themeGroup);
        nordDarkItem.setToggleGroup(themeGroup);
        primerLightItem.setToggleGroup(themeGroup);
        primerDarkItem.setToggleGroup(themeGroup);
        nordLightItem.setUserData("NordLight");
        nordDarkItem.setUserData("NordDark");
        primerLightItem.setUserData("PrimerLight");
        primerDarkItem.setUserData("PrimerDark");

        viewMenu.getItems().addAll(
                nordLightItem, nordDarkItem,
                new SeparatorMenuItem(),
                primerLightItem, primerDarkItem,
                new SeparatorMenuItem(),
                refreshModelsMenuItem
        );

        menuBar.getMenus().addAll(fileMenu, viewMenu, helpMenu);

        modelSelector.setPromptText("Model");
        modelSelector.setDisable(true);
        modelSelector.setPrefWidth(200);

        Region toolbarSpacer = new Region();
        HBox.setHgrow(toolbarSpacer, Priority.ALWAYS);

        ToolBar toolBar = new ToolBar(
                modelSelector,
                new Separator(),
                statusLabel,
                toolbarSpacer,
                copyLastButton,
                saveButton,
                checkButton,
                promptButton,
                settingsButton
        );

        chatView.setContextMenuEnabled(false);
        VBox.setVgrow(chatView, Priority.ALWAYS);

        inputField.setPromptText("Type a message and press Enter...");
        HBox.setHgrow(inputField, Priority.ALWAYS);
        attachButton.setDisable(true);
        stopButton.setDisable(true);
        regenerateButton.setDisable(true);

        HBox inputBox = new HBox(
                8,
                attachButton,
                inputField,
                sendButton,
                stopButton,
                regenerateButton,
                clearButton
        );
        inputBox.setAlignment(Pos.CENTER_LEFT);
        inputBox.setPadding(new Insets(10));

        noticeLabel.setTextFill(Color.DARKSLATEGRAY);
        statusLabel.setTextFill(Color.GRAY);
        typingLabel.setTextFill(Color.DARKSLATEGRAY);
        lastResponseLabel.setTextFill(Color.DARKSLATEGRAY);
        speedLabel.setTextFill(Color.DARKSLATEGRAY);

        Region statusSpacer = new Region();
        HBox.setHgrow(statusSpacer, Priority.ALWAYS);

        HBox statusLine = new HBox(
                10,
                lastResponseLabel,
                speedLabel,
                statusSpacer,
                noticeLabel,
                typingLabel
        );
        statusLine.setAlignment(Pos.CENTER_LEFT);
        statusLine.setPadding(new Insets(0, 10, 8, 10));

        VBox root = new VBox(menuBar, toolBar, chatView, inputBox, statusLine);
        return root;
    }
}