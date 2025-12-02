package com.simonepugliese.taxreportgui.gui;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.StackPane;
import pugliesesimone.taxreport.AppFactory;
import pugliesesimone.taxreport.service.TaxReportService;

import java.util.Optional;

public class MainController {

    @FXML private StackPane contentArea;

    private Node settingsView;
    private Node reportView;
    private Node expenseView;

    private TaxReportService service;

    public void setViews(Node settingsView, Node reportView, Node expenseView) {
        this.settingsView = settingsView;
        this.reportView = reportView;
        this.expenseView = expenseView;
        showReport();
    }

    @FXML public void showSettings() { switchView(settingsView); }
    @FXML public void showReport() { switchView(reportView); }
    @FXML public void showAddExpense() { switchView(expenseView); }

    private void switchView(Node view) {
        if (contentArea != null && view != null) {
            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
        }
    }

    @FXML
    void handlePrintReport() {
        // 1. Chiedi l'anno all'utente
        TextInputDialog dialog = new TextInputDialog("2024");
        dialog.setTitle("Verifica Report");
        dialog.setHeaderText("Analisi Conformità Fiscale");
        dialog.setContentText("Inserisci l'anno da verificare:");

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) return;

        String year = result.get();

        // 2. Carica configurazione ed esegui
        ConfigService cfg = ConfigService.getInstance();
        try {
            this.service = AppFactory.buildAndroidService(
                    cfg.get(ConfigService.KEY_HOST, "192.168.1.x"),
                    Integer.parseInt(cfg.get(ConfigService.KEY_DB_PORT, "3306")),
                    cfg.get(ConfigService.KEY_DB_NAME, "taxreport"),
                    cfg.get(ConfigService.KEY_DB_USER, "root"),
                    cfg.get(ConfigService.KEY_DB_PASS, ""),
                    cfg.get(ConfigService.KEY_HOST, "192.168.1.x"),
                    cfg.get(ConfigService.KEY_SMB_SHARE, "TaxData"),
                    cfg.get(ConfigService.KEY_SMB_USER, "pi"),
                    cfg.get(ConfigService.KEY_SMB_PASS, "")
            );

            // 3. Esegui il check vero e proprio
            String reportSummary = service.runComplianceCheck(year);

            // 4. Mostra Risultati
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Report Completato");
            alert.setHeaderText("Risultato Verifica " + year);
            alert.setContentText(reportSummary);
            alert.showAndWait();

        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Errore Report");
            alert.setHeaderText("Impossibile generare il report");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    void handleExit() {
        System.exit(0);
    }
}