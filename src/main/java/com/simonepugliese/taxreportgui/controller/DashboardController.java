package com.simonepugliese.taxreportgui.controller;

import com.simonepugliese.taxreportgui.util.ServiceManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import pugliesesimone.taxreport.model.Expense;
import pugliesesimone.taxreport.model.ExpenseState;
import pugliesesimone.taxreport.model.ExpenseType;
import pugliesesimone.taxreport.model.Person;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DashboardController {

    @FXML private ComboBox<String> yearCombo;
    @FXML private PieChart statusChart;
    @FXML private Label lblTotal, lblCompliant, lblPartial;
    @FXML private TableView<Expense> expenseTable;
    @FXML private TableColumn<Expense, String> colDate, colType, colDesc, colPerson, colState;
    @FXML private Button btnFilterPerson, btnFilterType;

    private List<Expense> allExpenses = List.of();
    private List<Person> allPersons = List.of();

    private Set<String> selectedPersonIds = new HashSet<>();
    private Set<ExpenseType> selectedCategories = new HashSet<>();

    // [NEW] Indicatore di loading globale (potrebbe essere in FXML, ma qui è gestito implicitamente)

    @FXML
    public void initialize() {
        yearCombo.setItems(FXCollections.observableArrayList("2023", "2024", "2025"));
        yearCombo.setValue("2025");

        setupTable();
        loadData();
    }

    // --- FILTRI ---

    @FXML
    public void filterAll() {
        selectedPersonIds.clear();
        selectedCategories.clear();
        applyFilters();
    }

    @FXML
    public void filterPerson() {
        showMultiSelectDialog("Filtra Persone", allPersons,
                p -> p.getId().toString(),
                Person::getName,
                selectedPersonIds,
                newSelection -> {
                    selectedPersonIds = newSelection;
                    applyFilters();
                });
    }

    @FXML
    public void filterCategory() {
        showMultiSelectDialog("Filtra Categorie", List.of(ExpenseType.values()),
                Enum::name,
                Enum::name,
                selectedCategories.stream().map(Enum::name).collect(Collectors.toSet()),
                newSelection -> {
                    selectedCategories = newSelection.stream()
                            .map(ExpenseType::valueOf)
                            .collect(Collectors.toSet());
                    applyFilters();
                });
    }

    private void applyFilters() {
        List<Expense> filtered = allExpenses.stream()
                .filter(e -> selectedPersonIds.isEmpty() || selectedPersonIds.contains(e.getPerson().getId().toString()))
                .filter(e -> selectedCategories.isEmpty() || selectedCategories.contains(e.getExpenseType()))
                .collect(Collectors.toList());

        updateUi(filtered);
        updateButtonsState();
    }

    private void updateButtonsState() {
        btnFilterPerson.setText(selectedPersonIds.isEmpty() ? "Persone" : "Persone (" + selectedPersonIds.size() + ")");
        btnFilterType.setText(selectedCategories.isEmpty() ? "Categorie" : "Categorie (" + selectedCategories.size() + ")");

        btnFilterPerson.setStyle(selectedPersonIds.isEmpty() ? "" : "-fx-base: #e3f2fd; -fx-text-fill: #0d47a1;");
        btnFilterType.setStyle(selectedCategories.isEmpty() ? "" : "-fx-base: #e3f2fd; -fx-text-fill: #0d47a1;");
    }

    private void updateUi(List<Expense> expenses) {
        expenseTable.setItems(FXCollections.observableArrayList(expenses));

        long completed = expenses.stream().filter(e -> e.getExpenseState() == ExpenseState.COMPLETED).count();
        long partial = expenses.size() - completed;

        lblTotal.setText("Visualizzate: " + expenses.size());
        lblCompliant.setText("Completate: " + completed);
        lblPartial.setText("Da completare: " + partial);

        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList(
                new PieChart.Data("Completate", completed),
                new PieChart.Data("Incomplete", partial)
        );
        statusChart.setData(pieData);
    }

    private <T> void showMultiSelectDialog(String title, List<T> items,
                                           java.util.function.Function<T, String> idMapper,
                                           java.util.function.Function<T, String> labelMapper,
                                           Set<String> currentSelection,
                                           java.util.function.Consumer<Set<String>> onConfirm) {

        Dialog<Set<String>> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText("Seleziona gli elementi da visualizzare:");

        ButtonType loginButtonType = new ButtonType("Applica", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        Set<String> tempSelection = new HashSet<>(currentSelection);

        for (T item : items) {
            String id = idMapper.apply(item);
            CheckBox cb = new CheckBox(labelMapper.apply(item));
            cb.setSelected(tempSelection.contains(id));
            cb.selectedProperty().addListener((obs, old, isSelected) -> {
                if (isSelected) tempSelection.add(id); else tempSelection.remove(id);
            });
            content.getChildren().add(cb);
        }

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(300);
        dialog.getDialogPane().setContent(scroll);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) return tempSelection;
            return null;
        });

        dialog.showAndWait().ifPresent(onConfirm);
    }

    // --- SETUP & LOAD (con Task) ---
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
                } else if ("COMPLETED".equals(item)) setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                else setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
            }
        });

        expenseTable.setRowFactory(tv -> {
            TableRow<Expense> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty()) ) {
                    handleEdit();
                }
            });
            return row ;
        });
    }

    @FXML
    public void loadData() {
        String year = yearCombo.getValue();

        // [CORE FIX] Esegui il caricamento dati su un Task in background
        Task<Void> loadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (!ServiceManager.getInstance().isReady()) ServiceManager.getInstance().init();
                allExpenses = ServiceManager.getInstance().getMetadata().findByYear(year);
                allPersons = ServiceManager.getInstance().getService().getAllPersons();
                return null;
            }
        };

        loadTask.setOnRunning(e -> {
            // Qui puoi mostrare un ProgressIndicator nella UI se vuoi
            expenseTable.setPlaceholder(new ProgressIndicator());
        });

        loadTask.setOnSucceeded(e -> {
            expenseTable.setPlaceholder(new Label("Nessuna spesa da visualizzare."));
            // Aggiorna la UI sul thread JavaFX
            applyFilters();
        });

        loadTask.setOnFailed(e -> {
            expenseTable.setPlaceholder(new Label("Errore caricamento dati."));
            new Alert(Alert.AlertType.ERROR, "Errore caricamento dati: " + loadTask.getException().getMessage()).show();
            loadTask.getException().printStackTrace();
        });

        // Avvia il Task
        new Thread(loadTask).start();
    }

    @FXML
    public void handleEdit() {
        Expense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/simonepugliese/taxreportgui/view/AddExpenseView.fxml"));
            Parent view = loader.load();
            AddExpenseController controller = loader.getController();
            controller.setEditingExpense(selected);
            BorderPane mainPane = (BorderPane) expenseTable.getScene().getRoot();
            mainPane.setCenter(view);
        } catch (IOException e) { e.printStackTrace(); }
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
}