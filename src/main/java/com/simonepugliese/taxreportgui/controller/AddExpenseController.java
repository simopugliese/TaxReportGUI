package com.simonepugliese.taxreportgui.controller;

import com.simonepugliese.taxreportgui.util.ServiceManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;
import pugliesesimone.taxreport.model.*;

import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AddExpenseController {

    @FXML private TextField txtPersonName, txtFiscalCode, txtDescription;
    @FXML private ComboBox<String> comboYear;
    @FXML private ComboBox<ExpenseType> comboType;
    @FXML private DatePicker datePicker;
    @FXML private ListView<AttachmentItem> filesListView;
    @FXML private Button btnSave;

    // Se valorizzato, siamo in modalità MODIFICA
    private Expense editingExpense;

    private static class AttachmentItem {
        File file; // Null se è un documento già esistente sul server
        DocumentType type;
        String existingName; // Usato solo per visualizzazione

        // Costruttore per NUOVI file
        public AttachmentItem(File f, DocumentType t) {
            this.file = f;
            this.type = t;
        }

        // Costruttore per file ESISTENTI
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

                    // Stile diverso per file esistenti
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

    public void setEditingExpense(Expense expense) {
        this.editingExpense = expense;

        // Popola i campi
        txtPersonName.setText(expense.getPerson().getName());
        txtFiscalCode.setText(expense.getPerson().getFiscalCode());
        txtDescription.setText(expense.getDescription());
        comboYear.setValue(expense.getYear());
        comboType.setValue(expense.getExpenseType());

        if (expense.getRawDate() != null && !expense.getRawDate().isEmpty()) {
            try {
                datePicker.setValue(LocalDate.parse(expense.getRawDate(), DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            } catch (Exception ignored) {}
        }

        // Carica documenti esistenti nella lista (solo visualizzazione)
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

            if (txtPersonName.getText().isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "Dati incompleti!").show();
                return;
            }

            // 1. Gestione Persona: Se siamo in edit, proviamo a riusare l'ID persona esistente
            Person person;
            if (editingExpense != null && editingExpense.getPerson().getFiscalCode().equals(txtFiscalCode.getText())) {
                person = new Person(editingExpense.getPerson().getId(), txtPersonName.getText(), txtFiscalCode.getText());
            } else {
                person = new Person(txtPersonName.getText(), txtFiscalCode.getText());
            }
            ServiceManager.getInstance().getService().registerPerson(person);

            // 2. Preparazione Allegati (SOLO quelli nuovi, che hanno File != null)
            List<Attachment> newAttachments = new ArrayList<>();
            for (AttachmentItem item : filesListView.getItems()) {
                if (item.file != null) {
                    newAttachments.add(new Attachment(item.type, item.file.getName(), new FileInputStream(item.file)));
                }
            }

            String dateStr = (datePicker.getValue() != null) ?
                    datePicker.getValue().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "";

            Expense expenseToSave;
            if (editingExpense != null) {
                // *** EDIT MODE *** : Usiamo il costruttore completo con ID esistente
                expenseToSave = new Expense(
                        editingExpense.getId(), // ID ORIGINALE!
                        comboYear.getValue(),
                        person,
                        comboType.getValue(),
                        txtDescription.getText(),
                        dateStr,
                        editingExpense.getExpenseState()
                );
            } else {
                // *** NEW MODE ***
                expenseToSave = new Expense(
                        comboYear.getValue(), person, comboType.getValue(), txtDescription.getText(), dateStr
                );
            }

            // Il backend farà il MERGE dei documenti nuovi con quelli vecchi
            ServiceManager.getInstance().getService().registerExpense(expenseToSave, newAttachments);

            new Alert(Alert.AlertType.INFORMATION, "Spesa salvata con successo!").show();

            editingExpense = null;
            filesListView.getItems().clear();
            txtDescription.clear();
            btnSave.setText("SALVA SPESA");

        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Errore: " + e.getMessage()).show();
            e.printStackTrace();
        }
    }
}