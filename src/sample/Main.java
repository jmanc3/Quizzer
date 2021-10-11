package sample;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setMinWidth(650);
        primaryStage.setMinHeight(400);
        FXMLLoader settingsLoader = new FXMLLoader();
        Parent settingsRoot = settingsLoader.load(getClass().getResource("settings.fxml").openStream());
        SettingsController settingsController = (SettingsController) settingsLoader.getController();

        FXMLLoader mainLoader = new FXMLLoader();
        Parent mainRoot = mainLoader.load(getClass().getResource("main.fxml").openStream());
        MainController mainController = mainLoader.getController();

        mainController.stackpane.getChildren().add(settingsRoot);
        mainController.settingsRoot = settingsRoot;
        mainController.settingsController = settingsController;
        settingsRoot.setVisible(false);

        primaryStage.setTitle("Quizlet");
        Scene scene = new Scene(mainRoot);
        scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();

        KeyCombination keyCode = new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN);
        scene.getAccelerators().put(keyCode, () -> mainController.queryTextField.requestFocus());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
