package com.simonepugliese.taxreportgui.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import java.io.IOException;

public class MainController {

    @FXML private BorderPane mainPane;

    @FXML
    public void initialize() {
        showDashboard();
    }

    @FXML
    void showDashboard() { loadView("/com/simonepugliese/taxreportgui/view/DashboardView.fxml"); }

    @FXML
    void showAddExpense() { loadView("/com/simonepugliese/taxreportgui/view/AddExpenseView.fxml"); }

    @FXML
    void showSettings() { loadView("/com/simonepugliese/taxreportgui/view/SettingsView.fxml"); }

    private void loadView(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();
            mainPane.setCenter(view);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}