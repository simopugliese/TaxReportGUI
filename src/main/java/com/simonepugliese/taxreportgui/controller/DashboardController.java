package com.simonepugliese.taxreportgui.controller;

import com.simonepugliese.taxreportgui.util.ServiceManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import pugliesesimone.taxreport.model.Expense;
import pugliesesimone.taxreport.model.ExpenseState;

import java.io.IOException;
import java.util.List;

public class DashboardController {

    @FXML private ComboBox<String> yearCombo;
    @FXML private PieChart statusChart;
    @FXML private Label lblTotal, lblCompliant, lblPartial;
    @FXML private TableView<Expense> expenseTable;
    @FXML private TableColumn<Expense, String> colDate, colType, colDesc, colPerson, colState;

    @FXML
    public void initialize() {
        yearCombo.setItems(FXCollections.observableArrayList("2023", "2024", "2025"));
        yearCombo.setValue("2025");

        setupTable();
        loadData();
    }

    private void setupTable() {
        colDate.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getRawDate()));
        colType.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getExpenseType().name()));
        colDesc.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getDescription()));
        colPerson.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getPerson().getName()));
        colState.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getExpenseState().name()));

        colState.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(item);
                if (item == null || empty) {
                    setStyle("");
                } else if ("COMPLETED".equals(item)) {
                    setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                } else if ("PARTIAL".equals(item) || "INITIAL".equals(item)) {
                    setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
                } else {
                    setStyle("-fx-text-fill: red;");
                }
            }
        });
    }

    @FXML
    public void handleEdit() {
        Expense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.WARNING, "Seleziona una spesa dalla tabella!").show();
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/simonepugliese/taxreportgui/view/AddExpenseView.fxml"));
            Parent view = loader.load();

            // Passiamo la spesa al controller
            AddExpenseController controller = loader.getController();
            controller.setEditingExpense(selected);

            // Hack veloce: risaliamo al BorderPane principale per cambiare vista
            // In un'app più grande useresti un EventBus o un NavigationService
            BorderPane mainPane = (BorderPane) expenseTable.getScene().getRoot();
            mainPane.setCenter(view);

        } catch (IOException e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Errore apertura vista modifica: " + e.getMessage()).show();
        }
    }

    @FXML
    public void loadData() {
        try {
            if (!ServiceManager.getInstance().isReady()) {
                ServiceManager.getInstance().init();
            }
            String year = yearCombo.getValue();
            List<Expense> expenses = ServiceManager.getInstance().getMetadata().findByYear(year);
            updateCharts(expenses);
            expenseTable.setItems(FXCollections.observableArrayList(expenses));
        } catch (Exception e) {
            System.err.println("Impossibile caricare dati dashboard: " + e.getMessage());
        }
    }

    @FXML
    public void handleRefresh() {
        try {
            if (!ServiceManager.getInstance().isReady()) return;
            ServiceManager.getInstance().getService().runComplianceCheck(yearCombo.getValue());
            loadData();
            new Alert(Alert.AlertType.INFORMATION, "Verifica completata!").show();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Errore verifica: " + e.getMessage()).show();
        }
    }

    private void updateCharts(List<Expense> expenses) {
        long completed = expenses.stream().filter(e -> e.getExpenseState() == ExpenseState.COMPLETED).count();
        long partial = expenses.stream().filter(e -> e.getExpenseState() != ExpenseState.COMPLETED).count();

        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList(
                new PieChart.Data("Completate", completed),
                new PieChart.Data("Incomplete", partial)
        );
        statusChart.setData(pieData);
        lblTotal.setText("Totale Spese: " + expenses.size());
        lblCompliant.setText("Completate: " + completed);
        lblPartial.setText("Da completare: " + partial);
    }
}