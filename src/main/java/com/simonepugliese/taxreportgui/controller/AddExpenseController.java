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

import java.awt.Desktop;
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

    // Wrapper per gestire file Locali (Nuovi) e Remoti (Esistenti)
    private static class AttachmentItem {
        File localFile;
        Document serverDoc; // [NEW] Salviamo l'intero oggetto Document
        DocumentType type;
        String name;

        // Costruttore per File Locali (Nuovi)
        public AttachmentItem(File f, DocumentType t) {
            this.localFile = f;
            this.type = t;
            this.name = f.getName();
        }

        // Costruttore per File Server (Esistenti)
        public AttachmentItem(Document d) {
            this.serverDoc = d;
            this.type = d.getDocumentType();
            // Estraiamo il nome dal path relativo
            this.name = new File(d.getRelativePath()).getName();
        }

        @Override
        public String toString() {
            return name + " [" + type + "]";
        }
    }

    @FXML
    public void initialize() {
        comboYear.setItems(FXCollections.observableArrayList("2023", "2024", "2025"));
        comboYear.setValue("2025");
        comboType.setItems(FXCollections.observableArrayList(ExpenseType.values()));
        comboType.getSelectionModel().selectFirst();

        comboPerson.setConverter(new StringConverter<>() {
            @Override public String toString(Person p) { return p == null ? "" : p.getName(); }
            @Override public Person fromString(String string) { return null; }
        });

        loadPersons();

        // Setup Lista Allegati Custom
        filesListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(AttachmentItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setTooltip(null);
                } else {
                    HBox box = new HBox(10);
                    String prefix = (item.localFile != null) ? "🆕 " : "☁️ ";
                    Label lblName = new Label(prefix + item.name);
                    Label lblType = new Label("[" + item.type + "]");

                    if (item.localFile == null) lblName.setStyle("-fx-text-fill: blue;");

                    Button btnDel = new Button("", new FontIcon("fas-trash"));
                    btnDel.getStyleClass().add("danger");
                    btnDel.setOnAction(e -> getListView().getItems().remove(item));

                    // [NEW] Pulsante o Icona View
                    Button btnView = new Button("", new FontIcon("fas-eye"));
                    btnView.setOnAction(e -> openAttachment(item));

                    HBox.setHgrow(lblName, Priority.ALWAYS);
                    box.getChildren().addAll(lblName, lblType, btnView, btnDel);
                    setGraphic(box);

                    // Doppio Click per aprire
                    setOnMouseClicked(e -> {
                        if (e.getClickCount() == 2 && !isEmpty()) {
                            openAttachment(getItem());
                        }
                    });
                }
            }
        });
    }

    // [NEW] Logica Apertura File
    private void openAttachment(AttachmentItem item) {
        try {
            File fileToOpen;
            if (item.localFile != null) {
                fileToOpen = item.localFile;
            } else {
                // Scarica dal server (Smart Cache)
                fileToOpen = ServiceManager.getInstance().downloadDocument(item.serverDoc);
            }

            // Apre col visualizzatore di sistema
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(fileToOpen);
            }
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Errore apertura file: " + e.getMessage()).show();
            e.printStackTrace();
        }
    }

    private void loadPersons() {
        try {
            if (!ServiceManager.getInstance().isReady()) ServiceManager.getInstance().init();
            List<Person> persons = ServiceManager.getInstance().getService().getAllPersons();
            comboPerson.setItems(FXCollections.observableArrayList(persons));
        } catch (Exception e) {}
    }

    @FXML
    public void handlePersonSelection() {
        Person p = comboPerson.getValue();
        if (p != null) txtFiscalCode.setText(p.getFiscalCode());
    }

    public void setEditingExpense(Expense expense) {
        this.editingExpense = expense;

        comboPerson.getItems().stream()
                .filter(p -> p.getId().equals(expense.getPerson().getId()))
                .findFirst().ifPresent(comboPerson::setValue);

        if(comboPerson.getValue() != null) txtFiscalCode.setText(comboPerson.getValue().getFiscalCode());

        txtDescription.setText(expense.getDescription());
        comboYear.setValue(expense.getYear());
        comboType.setValue(expense.getExpenseType());

        if (expense.getRawDate() != null && !expense.getRawDate().isEmpty()) {
            try {
                datePicker.setValue(LocalDate.parse(expense.getRawDate(), DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            } catch (Exception e) {}
        }

        // Popola la lista mantenendo il riferimento al Document originale
        for (Document doc : expense.getDocuments()) {
            filesListView.getItems().add(new AttachmentItem(doc));
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

            // 1. Nuovi Allegati (FileInputStream)
            List<Attachment> newAttachments = new ArrayList<>();
            for (AttachmentItem item : filesListView.getItems()) {
                if (item.localFile != null) {
                    newAttachments.add(new Attachment(item.type, item.name, new FileInputStream(item.localFile)));
                }
            }

            // 2. Allegati Sopravvissuti (Server)
            List<Document> survivingDocs = new ArrayList<>();
            if (editingExpense != null) {
                for (AttachmentItem item : filesListView.getItems()) {
                    if (item.serverDoc != null) {
                        survivingDocs.add(item.serverDoc);
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

            // Torna alla Dashboard
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/simonepugliese/taxreportgui/view/DashboardView.fxml"));
                Parent view = loader.load();
                if (btnSave.getScene().getRoot() instanceof BorderPane mainPane) {
                    mainPane.setCenter(view);
                }
            } catch (IOException e) { e.printStackTrace(); }

        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Errore: " + e.getMessage()).show();
            e.printStackTrace();
        }
    }
}