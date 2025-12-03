package com.simonepugliese.taxreportgui.controller;

import com.simonepugliese.taxreportgui.util.ServiceManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
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

    // DATA MODEL REATTIVO (Best Practice JavaFX)
    private final ObservableList<Expense> masterData = FXCollections.observableArrayList();
    private FilteredList<Expense> filteredData;

    // Dati di supporto
    private List<Person> allPersons = new ArrayList<>();
    private Set<String> selectedPersonIds = new HashSet<>();
    private Set<ExpenseType> selectedCategories = new HashSet<>();

    private boolean isUpdating = false;

    @FXML
    public void initialize() {
        setupTable();
        // Load iniziale posticipato per permettere alla UI di apparire
        Platform.runLater(this::loadData);
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
        // Logica Pura: Aggiorniamo il predicato della FilteredList.
        // La TableView si aggiornerà istantaneamente da sola.
        filteredData.setPredicate(expense -> {
            boolean personMatch = selectedPersonIds.isEmpty() || selectedPersonIds.contains(expense.getPerson().getId().toString());
            boolean typeMatch = selectedCategories.isEmpty() || selectedCategories.contains(expense.getExpenseType());
            return personMatch && typeMatch;
        });

        updateButtonsState();
        updateUiStats();
    }

    private void updateButtonsState() {
        btnFilterPerson.setText(selectedPersonIds.isEmpty() ? "Persone" : "Persone (" + selectedPersonIds.size() + ")");
        btnFilterType.setText(selectedCategories.isEmpty() ? "Categorie" : "Categorie (" + selectedCategories.size() + ")");

        btnFilterPerson.setStyle(selectedPersonIds.isEmpty() ? "" : "-fx-base: #e3f2fd; -fx-text-fill: #0d47a1;");
        btnFilterType.setStyle(selectedCategories.isEmpty() ? "" : "-fx-base: #e3f2fd; -fx-text-fill: #0d47a1;");
    }

    private void updateUiStats() {
        // Calcoliamo le statistiche sui dati FILTRATI
        int total = filteredData.size();
        long completed = filteredData.stream().filter(e -> e.getExpenseState() == ExpenseState.COMPLETED).count();
        long partial = total - completed;

        lblTotal.setText("Visualizzate: " + total);
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

        ButtonType applyButtonType = new ButtonType("Applica", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyButtonType, ButtonType.CANCEL);

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
            if (dialogButton == applyButtonType) return tempSelection;
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

        // Setup FilteredList e SortedList
        filteredData = new FilteredList<>(masterData, p -> true);
        SortedList<Expense> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(expenseTable.comparatorProperty());

        expenseTable.setItems(sortedData);

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
        yearCombo.setDisable(true);
        expenseTable.setPlaceholder(new ProgressIndicator());

        Task<LoadResult> loadTask = new Task<>() {
            @Override
            protected LoadResult call() throws Exception {
                if (!ServiceManager.getInstance().isReady()) ServiceManager.getInstance().init();

                // 1. Recupera Anni
                List<String> availableYears = ServiceManager.getInstance().getMetadata().getAvailableYears();
                if (availableYears.isEmpty()) {
                    availableYears.add(String.valueOf(LocalDate.now().getYear()));
                }

                String yearToLoad = (selectedYear != null && availableYears.contains(selectedYear))
                        ? selectedYear : availableYears.get(0);

                // 2. Carica Dati Pesanti (IO)
                List<Expense> expenses = ServiceManager.getInstance().getMetadata().findByYear(yearToLoad);
                List<Person> persons = ServiceManager.getInstance().getService().getAllPersons();

                return new LoadResult(availableYears, yearToLoad, expenses, persons);
            }
        };

        loadTask.setOnSucceeded(e -> {
            LoadResult result = loadTask.getValue();

            // 3. Aggiornamento UI (Tutto insieme, niente sleep)
            isUpdating = true;
            try {
                yearCombo.setItems(FXCollections.observableArrayList(result.years));
                yearCombo.setValue(result.loadedYear);

                allPersons = result.persons;

                // Aggiorna Master Data -> Triggera FilteredList -> Triggera UI
                masterData.setAll(result.expenses);

                // Riapplica filtri e statistiche
                applyFilters();

            } finally {
                isUpdating = false;
                yearCombo.setDisable(false);
                expenseTable.setPlaceholder(new Label("Nessuna spesa da visualizzare."));
            }
        });

        loadTask.setOnFailed(e -> {
            yearCombo.setDisable(false);
            expenseTable.setPlaceholder(new Label("Errore caricamento dati."));
            new Alert(Alert.AlertType.ERROR, "Errore caricamento: " + loadTask.getException().getMessage()).show();
            loadTask.getException().printStackTrace();
        });

        new Thread(loadTask).start();
    }

    // Record di supporto per passare dati dal background thread
    private record LoadResult(List<String> years, String loadedYear, List<Expense> expenses, List<Person> persons) {}

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
        if (!ServiceManager.getInstance().isReady()) return;
        String year = yearCombo.getValue();

        Task<String> complianceTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                // Check parallelo (nuova implementazione service)
                return ServiceManager.getInstance().getService().runComplianceCheck(year);
            }
        };

        complianceTask.setOnRunning(e -> {
            lblTotal.setText("Verifica in corso...");
            btnFilterPerson.setDisable(true);
            btnFilterType.setDisable(true);
        });

        complianceTask.setOnSucceeded(e -> {
            btnFilterPerson.setDisable(false);
            btnFilterType.setDisable(false);
            loadData(); // Ricarica dati aggiornati
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