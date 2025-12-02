package com.simonepugliese.taxreportgui.controller;

import com.simonepugliese.taxreportgui.util.ServiceManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;
import pugliesesimone.taxreport.model.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AddExpenseController {

    @FXML private ComboBox<Person> comboPerson;
    @FXML private TextField txtFiscalCode;
    @FXML private TextField txtDescription;
    @FXML private ComboBox<String> comboYear;
    @FXML private ComboBox<ExpenseType> comboType;
    @FXML private DatePicker datePicker;
    @FXML private ListView<AttachmentItem> filesListView;
    @FXML private Button btnSave;

    private Expense editingExpense;

    private static class AttachmentItem {
        File file;
        DocumentType type;
        String existingName;

        public AttachmentItem(File f, DocumentType t) {
            this.file = f;
            this.type = t;
        }

        public AttachmentItem(String name, DocumentType t) {
            this.existingName = name;
            this.type = t;
            this.file = null;
        }

        @Override
        public String toString() {
            return (file != null ? file.getName() : existingName + " (Server)") + " [" + type + "]";
        }
    }

    @FXML
    public void initialize() {
        comboYear.setItems(FXCollections.observableArrayList("2023", "2024", "2025"));
        comboYear.setValue("2025");
        comboType.setItems(FXCollections.observableArrayList(ExpenseType.values()));
        comboType.getSelectionModel().selectFirst();

        // Setup Combo Persone
        comboPerson.setConverter(new StringConverter<>() {
            @Override
            public String toString(Person p) { return p == null ? "" : p.getName(); }
            @Override
            public Person fromString(String string) { return null; }
        });

        loadPersons();

        filesListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(AttachmentItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    HBox box = new HBox(10);
                    String labelText = item.file != null ? item.file.getName() : item.existingName + " (Cloud)";
                    Label lblName = new Label(labelText);
                    Label lblType = new Label("[" + item.type + "]");

                    if (item.file == null) {
                        lblName.setStyle("-fx-text-fill: blue;");
                    }

                    Button btnDel = new Button("", new FontIcon("fas-trash"));
                    btnDel.getStyleClass().add("danger");
                    btnDel.setOnAction(e -> getListView().getItems().remove(item));

                    HBox.setHgrow(lblName, Priority.ALWAYS);
                    box.getChildren().addAll(lblName, lblType, btnDel);
                    setGraphic(box);
                }
            }
        });
    }

    private void loadPersons() {
        try {
            if (!ServiceManager.getInstance().isReady()) ServiceManager.getInstance().init();
            List<Person> persons = ServiceManager.getInstance().getService().getAllPersons();
            comboPerson.setItems(FXCollections.observableArrayList(persons));
        } catch (Exception e) {
            // Ignoriamo errori di connessione qui
        }
    }

    @FXML
    public void handlePersonSelection() {
        Person p = comboPerson.getValue();
        if (p != null) {
            txtFiscalCode.setText(p.getFiscalCode());
        }
    }

    public void setEditingExpense(Expense expense) {
        this.editingExpense = expense;

        if (expense.getPerson() != null) {
            comboPerson.getItems().stream()
                    .filter(p -> p.getId().equals(expense.getPerson().getId()))
                    .findFirst()
                    .ifPresent(p -> {
                        comboPerson.setValue(p);
                        txtFiscalCode.setText(p.getFiscalCode());
                    });
        }

        txtDescription.setText(expense.getDescription());
        comboYear.setValue(expense.getYear());
        comboType.setValue(expense.getExpenseType());

        if (expense.getRawDate() != null && !expense.getRawDate().isEmpty()) {
            try {
                datePicker.setValue(LocalDate.parse(expense.getRawDate(), DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            } catch (Exception e) {
                System.err.println("Formato data non valido: " + expense.getRawDate());
            }
        }

        for (Document doc : expense.getDocuments()) {
            String name = new File(doc.getRelativePath()).getName();
            filesListView.getItems().add(new AttachmentItem(name, doc.getDocumentType()));
        }

        btnSave.setText("AGGIORNA SPESA");
    }

    @FXML
    public void handleBrowseFiles() {
        FileChooser fc = new FileChooser();
        List<File> files = fc.showOpenMultipleDialog(txtDescription.getScene().getWindow());
        if (files != null) {
            ChoiceDialog<DocumentType> dialog = new ChoiceDialog<>(DocumentType.FATTURA, List.of(DocumentType.values()));
            dialog.setTitle("Tipo Documento");
            dialog.setHeaderText("Tipo documenti caricati:");
            dialog.setContentText("Tipo:");

            dialog.showAndWait().ifPresent(type -> {
                for (File f : files) {
                    filesListView.getItems().add(new AttachmentItem(f, type));
                }
            });
        }
    }

    @FXML
    public void handleSave() {
        try {
            if (!ServiceManager.getInstance().isReady()) ServiceManager.getInstance().init();

            if (comboPerson.getValue() == null) {
                new Alert(Alert.AlertType.WARNING, "Seleziona una persona!").show();
                return;
            }

            Person person = comboPerson.getValue();

            // Preparazione Allegati NUOVI
            List<Attachment> newAttachments = new ArrayList<>();
            for (AttachmentItem item : filesListView.getItems()) {
                if (item.file != null) {
                    newAttachments.add(new Attachment(item.type, item.file.getName(), new FileInputStream(item.file)));
                }
            }

            // Calcolo documenti SOPRAVVISSUTI
            List<Document> survivingDocs = new ArrayList<>();
            if (editingExpense != null) {
                for (AttachmentItem item : filesListView.getItems()) {
                    if (item.file == null) {
                        editingExpense.getDocuments().stream()
                                .filter(d -> d.getDocumentType() == item.type && d.getRelativePath().endsWith(item.existingName))
                                .findFirst()
                                .ifPresent(survivingDocs::add);
                    }
                }
            }

            String dateStr = (datePicker.getValue() != null) ?
                    datePicker.getValue().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "";

            Expense expenseToSave;
            if (editingExpense != null) {
                expenseToSave = new Expense(
                        editingExpense.getId(),
                        comboYear.getValue(),
                        person,
                        comboType.getValue(),
                        txtDescription.getText(),
                        dateStr,
                        editingExpense.getExpenseState()
                );
                expenseToSave.setDocuments(survivingDocs);
            } else {
                expenseToSave = new Expense(
                        comboYear.getValue(), person, comboType.getValue(), txtDescription.getText(), dateStr
                );
            }

            ServiceManager.getInstance().getService().registerExpense(expenseToSave, newAttachments);

            new Alert(Alert.AlertType.INFORMATION, "Spesa salvata con successo!").showAndWait();

            // --- FIX NAVIGAZIONE: Torna alla Dashboard ---
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/simonepugliese/taxreportgui/view/DashboardView.fxml"));
                Parent view = loader.load();

                // Risaliamo al BorderPane principale e settiamo la Dashboard al centro
                if (btnSave.getScene().getRoot() instanceof BorderPane mainPane) {
                    mainPane.setCenter(view);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Errore: " + e.getMessage()).show();
            e.printStackTrace();
        }
    }
}