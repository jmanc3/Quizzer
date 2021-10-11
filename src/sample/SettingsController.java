package sample;

import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.VBox;
import net.harawata.appdirs.AppDirs;
import net.harawata.appdirs.AppDirsFactory;

import java.io.*;
import java.net.URL;
import java.nio.Buffer;
import java.util.ResourceBundle;

public class SettingsController implements Initializable {
    public CheckBox copyDetectionCheck;
    public CheckBox autoSearchCheck;
    public VBox settingsVbox;
    public CheckBox pasteCheck;
    public CheckBox clearCheck;
    public CheckBox sequentialCheck;

    public void updateSettings() {
        AppDirs appDirs = AppDirsFactory.getInstance();
        String userDataDir = appDirs.getUserDataDir("Quizlet", "1", "jmanc3");

        File settingsFolder = new File(userDataDir);
        if (settingsFolder.exists()) {
            File settingsFile = new File(userDataDir + File.separator + "settings.txt");

            try {
                FileWriter fileWriter = new FileWriter(settingsFile);
                fileWriter.write(String.format("copy %b\n", copyDetectionCheck.isSelected()));
                fileWriter.write(String.format("auto %b\n", autoSearchCheck.isSelected()));
                fileWriter.write(String.format("paste %b\n", pasteCheck.isSelected()));
                fileWriter.write(String.format("clear %b\n", clearCheck.isSelected()));
                fileWriter.write(String.format("sequential %b\n", sequentialCheck.isSelected()));
                fileWriter.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            if (settingsFolder.mkdirs()) {
                File settingsFile = new File(userDataDir + File.separator + "settings.txt");

                try {
                    FileWriter fileWriter = new FileWriter(settingsFile);
                    fileWriter.write(String.format("copy %b\n", copyDetectionCheck.isSelected()));
                    fileWriter.write(String.format("auto %b\n", autoSearchCheck.isSelected()));
                    fileWriter.write(String.format("paste %b\n", pasteCheck.isSelected()));
                    fileWriter.write(String.format("clear %b\n", clearCheck.isSelected()));
                    fileWriter.write(String.format("sequential %b\n", sequentialCheck.isSelected()));
                    fileWriter.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("Wasn't able to create data folder");
            }
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        autoSearchCheck.disableProperty().bind(copyDetectionCheck.selectedProperty().not());

        autoSearchCheck.selectedProperty().addListener((observable, oldValue, newValue) -> {
            updateSettings();
        });
        copyDetectionCheck.selectedProperty().addListener((observable, oldValue, newValue) -> {
            updateSettings();
        });
        clearCheck.selectedProperty().addListener((observable, oldValue, newValue) -> {
            updateSettings();
        });
        pasteCheck.selectedProperty().addListener((observable, oldValue, newValue) -> {
            updateSettings();
        });
        sequentialCheck.selectedProperty().addListener((observable, oldValue, newValue) -> {
            updateSettings();
        });


        AppDirs appDirs = AppDirsFactory.getInstance();
        String userDataDir = appDirs.getUserDataDir("Quizlet", "1", "jmanc3");

        File settingsFolder = new File(userDataDir);
        if (settingsFolder.exists()) {
            File settingsFile = new File(userDataDir + File.separator + "settings.txt");

            try {
                BufferedReader br = new BufferedReader(new FileReader(settingsFile));

                String line;
                while ((line = br.readLine()) != null) {
                    String[] s = line.split(" ");
                    if (s[0].equals("auto")) {
                        autoSearchCheck.setSelected(Boolean.parseBoolean(s[1]));
                    } else if (s[0].equals("copy")) {
                        copyDetectionCheck.setSelected(Boolean.parseBoolean(s[1]));
                    } else if (s[0].equals("clear")) {
                        clearCheck.setSelected(Boolean.parseBoolean(s[1]));
                    } else if (s[0].equals("paste")) {
                        pasteCheck.setSelected(Boolean.parseBoolean(s[1]));
                    } else if (s[0].equals("sequential")) {
                        sequentialCheck.setSelected(Boolean.parseBoolean(s[1]));
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            if (settingsFolder.mkdirs()) {
                File settingsFile = new File(userDataDir + File.separator + "settings.txt");

                try {
                    FileWriter fileWriter = new FileWriter(settingsFile);
                    fileWriter.write(String.format("copy %b\n", copyDetectionCheck.isSelected()));
                    fileWriter.write(String.format("auto %b\n", autoSearchCheck.isSelected()));
                    fileWriter.write(String.format("paste %b\n", pasteCheck.isSelected()));
                    fileWriter.write(String.format("clear %b\n", clearCheck.isSelected()));
                    fileWriter.write(String.format("sequential %b\n", sequentialCheck.isSelected()));
                    fileWriter.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("Wasn't able to create data folder");
            }
        }
    }
}
