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
import java.util.concurrent.TimeUnit;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        // 1. Pulizia Cache Intelligente (non blocca l'avvio)
        new Thread(this::cleanOldCache).start();

        // 2. Setup UI
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("view/MainView.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1100, 750); // Un po' più larga per i filtri

        stage.setTitle("TaxReport Manager");
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Pulisce solo i file di cache più vecchi di 7 giorni.
     * Evita di riscaricare tutto ogni volta.
     */
    private void cleanOldCache() {
        try {
            Path cache = ServiceManager.getInstance().getCachePath();
            if (Files.exists(cache)) {
                long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);

                Files.walk(cache)
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toFile().lastModified() < cutoff)
                        .map(Path::toFile)
                        .forEach(file -> {
                            try {
                                if (file.delete()) {
                                    System.out.println("Cache scaduta rimossa: " + file.getName());
                                }
                            } catch (Exception ignored) {}
                        });
            }
        } catch (Exception e) {
            System.err.println("Warning: Manutenzione cache fallita: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch();
    }
}