package com.simonepugliese.taxreportgui;

import atlantafx.base.theme.PrimerLight;
import com.simonepugliese.taxreportgui.util.ServiceManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        // 1. Pulizia Cache all'avvio
        cleanCache();

        // 2. Setup UI
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("view/MainView.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1100, 750); // Un po' più larga per i filtri

        stage.setTitle("TaxReport Manager");
        stage.setScene(scene);
        stage.show();
    }

    private void cleanCache() {
        try {
            Path cache = ServiceManager.getInstance().getCachePath();
            if (Files.exists(cache)) {
                // Cancella ricorsivamente
                Files.walk(cache)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                System.out.println("Cache pulita correttamente.");
            }
        } catch (Exception e) {
            // Ignoriamo errori di pulizia (es. file aperti)
            System.err.println("Warning: Pulizia cache parziale o fallita.");
        }
    }

    public static void main(String[] args) {
        launch();
    }
}