package com.simonepugliese.taxreportgui.controller;

import com.simonepugliese.taxreportgui.util.ServiceManager;
import javafx.application.Platform;
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
import java.time.LocalDate;
import java.util.ArrayList;
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

    private boolean isUpdating = false;

    @FXML
    public void initialize() {
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
        if (isUpdating) return;

        String selectedYear = yearCombo.getValue();

        Task<Void> loadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (!ServiceManager.getInstance().isReady()) ServiceManager.getInstance().init();

                List<String> availableYears = ServiceManager.getInstance().getMetadata().getAvailableYears();

                if (availableYears.isEmpty()) {
                    availableYears.add(String.valueOf(LocalDate.now().getYear()));
                }

                final List<String> finalYears = availableYears;

                Platform.runLater(() -> {
                    isUpdating = true;
                    try {
                        String current = yearCombo.getValue();
                        yearCombo.setItems(FXCollections.observableArrayList(finalYears));
                        if (current == null || !finalYears.contains(current)) {
                            yearCombo.getSelectionModel().selectFirst();
                        } else {
                            yearCombo.setValue(current);
                        }
                    } finally {
                        isUpdating = false;
                    }
                });

                String yearToLoad = (selectedYear != null && finalYears.contains(selectedYear))
                        ? selectedYear : finalYears.get(0);

                allExpenses = ServiceManager.getInstance().getMetadata().findByYear(yearToLoad);
                allPersons = ServiceManager.getInstance().getService().getAllPersons();
                return null;
            }
        };

        loadTask.setOnRunning(e -> expenseTable.setPlaceholder(new ProgressIndicator()));

        loadTask.setOnSucceeded(e -> {
            expenseTable.setPlaceholder(new Label("Nessuna spesa da visualizzare."));
            applyFilters();
        });

        loadTask.setOnFailed(e -> {
            expenseTable.setPlaceholder(new Label("Errore caricamento dati."));
            new Alert(Alert.AlertType.ERROR, "Errore caricamento: " + loadTask.getException().getMessage()).show();
            loadTask.getException().printStackTrace();
        });

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

    // [MODIFICATO] Esegue il check in background e SENZA mostrare popup di successo
    @FXML
    public void handleRefresh() {
        if (!ServiceManager.getInstance().isReady()) return;

        String year = yearCombo.getValue();

        Task<String> complianceTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                // Esegue il check pesante in background
                return ServiceManager.getInstance().getService().runComplianceCheck(year);
            }
        };

        complianceTask.setOnRunning(e -> {
            lblTotal.setText("Verifica in corso...");
            btnFilterPerson.setDisable(true);
            btnFilterType.setDisable(true);
        });

        complianceTask.setOnSucceeded(e -> {
            // Riabilita UI
            btnFilterPerson.setDisable(false);
            btnFilterType.setDisable(false);

            // [MODIFICA] Niente Alert! Ricarica solo i dati silenziosamente.
            loadData();
        });

        complianceTask.setOnFailed(e -> {
            btnFilterPerson.setDisable(false);
            btnFilterType.setDisable(false);
            new Alert(Alert.AlertType.ERROR, "Errore verifica: " + complianceTask.getException().getMessage()).show();
            complianceTask.getException().printStackTrace();
        });

        new Thread(complianceTask).start();
    }
}