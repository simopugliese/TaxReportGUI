package com.simonepugliese.taxreportgui.controller;

import com.simonepugliese.taxreportgui.util.ServiceManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.control.ProgressIndicator;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;
import pugliesesimone.taxreport.model.*;
import javafx.geometry.Pos;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
        File localFile;
        Document serverDoc;
        DocumentType type;
        String name;
        private boolean downloading = false;

        public AttachmentItem(File f, DocumentType t) {
            this.localFile = f;
            this.type = t;
            this.name = f.getName();
        }

        public AttachmentItem(Document d) {
            this.serverDoc = d;
            this.type = d.getDocumentType();
            this.name = new File(d.getRelativePath()).getName();
        }

        @Override public String toString() { return name + " [" + type + "]"; }
        public boolean isDownloading() { return downloading; }
        public void setDownloading(boolean downloading) { this.downloading = downloading; }
    }

    @FXML
    public void initialize() {
        loadAvailableYears();

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
                    setOnMouseClicked(null);
                } else {
                    HBox box = new HBox(10);
                    box.setAlignment(Pos.CENTER_LEFT);

                    // Indicatore visivo
                    if (item.isDownloading()) {
                        ProgressIndicator pi = new ProgressIndicator(ProgressIndicator.INDETERMINATE_PROGRESS);
                        pi.setPrefSize(16, 16);
                        box.getChildren().add(pi);
                    } else {
                        // [FIX] Icona documento generica invece dell'occhio
                        FontIcon icon = new FontIcon("fas-file-alt");
                        icon.setStyle("-fx-fill: #666666;"); // Grigio scuro
                        box.getChildren().add(icon);
                    }

                    String prefix = (item.localFile != null) ? "🆕 " : "☁️ ";
                    Label lblName = new Label(prefix + item.name);
                    Label lblType = new Label("[" + item.type + "]");
                    if (item.localFile == null) lblName.setStyle("-fx-text-fill: #0066cc;"); // Blu per cloud

                    Button btnDel = new Button("", new FontIcon("fas-trash"));
                    btnDel.getStyleClass().add("danger");
                    btnDel.setOnAction(e -> getListView().getItems().remove(item));

                    HBox.setHgrow(lblName, Priority.ALWAYS);
                    box.getChildren().addAll(lblName, lblType, btnDel);
                    setGraphic(box);

                    // Doppio Click per aprire
                    setOnMouseClicked(e -> {
                        if (e.getClickCount() == 2 && (!isEmpty())) {
                            openAttachment(getItem());
                        }
                    });
                }
            }
        });
    }

    private void openAttachment(AttachmentItem item) {
        if (item.localFile != null) {
            openFileOnDesktop(item.localFile);
            return;
        }

        // [CRITICAL FIX] Se sta già scaricando, esci subito.
        if (item.isDownloading()) return;

        // [CRITICAL FIX] Imposta il flag SUBITO (sincrono) per bloccare chiamate successive
        item.setDownloading(true);
        filesListView.refresh(); // Aggiorna UI per mostrare lo spinner

        Task<File> downloadTask = new Task<>() {
            @Override protected File call() throws Exception {
                return ServiceManager.getInstance().downloadDocument(item.serverDoc);
            }
        };

        downloadTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                item.setDownloading(false);
                filesListView.refresh();
                openFileOnDesktop(downloadTask.getValue());
            });
        });

        downloadTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                item.setDownloading(false);
                filesListView.refresh();
                new Alert(Alert.AlertType.ERROR, "Errore download: " + downloadTask.getException().getMessage()).show();
            });
        });

        new Thread(downloadTask).start();
    }

    private void openFileOnDesktop(File file) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file);
        } catch (IOException e) {
            // Ignoriamo o logghiamo errore apertura viewer (non crasha l'app)
            System.err.println("Impossibile aprire file: " + e.getMessage());
        }
    }

    private void loadAvailableYears() {
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                if (!ServiceManager.getInstance().isReady()) ServiceManager.getInstance().init();
                List<String> years = new ArrayList<>(ServiceManager.getInstance().getMetadata().getAvailableYears());

                String current = String.valueOf(LocalDate.now().getYear());
                String next = String.valueOf(LocalDate.now().getYear() + 1);

                if (!years.contains(current)) years.add(0, current);
                if (!years.contains(next)) years.add(0, next);

                return years.stream().distinct()
                        .sorted((a, b) -> b.compareTo(a))
                        .collect(Collectors.toList());
            }
        };

        task.setOnSucceeded(e -> {
            comboYear.setItems(FXCollections.observableArrayList(task.getValue()));
            if(comboYear.getValue() == null) comboYear.getSelectionModel().selectFirst();
        });

        task.setOnFailed(e -> {
            String current = String.valueOf(LocalDate.now().getYear());
            comboYear.setItems(FXCollections.observableArrayList(current, String.valueOf(Integer.parseInt(current)+1)));
            comboYear.setValue(current);
        });

        new Thread(task).start();
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
        comboPerson.getItems().stream().filter(p -> p.getId().equals(expense.getPerson().getId())).findFirst().ifPresent(comboPerson::setValue);
        if(comboPerson.getValue() != null) txtFiscalCode.setText(comboPerson.getValue().getFiscalCode());
        txtDescription.setText(expense.getDescription());

        comboYear.setValue(expense.getYear());
        comboType.setValue(expense.getExpenseType());
        if (expense.getRawDate() != null && !expense.getRawDate().isEmpty()) {
            try { datePicker.setValue(LocalDate.parse(expense.getRawDate(), DateTimeFormatter.ofPattern("dd/MM/yyyy"))); } catch (Exception e) {}
        }
        for (Document doc : expense.getDocuments()) filesListView.getItems().add(new AttachmentItem(doc));
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
                for (File f : files) filesListView.getItems().add(new AttachmentItem(f, type));
            });
        }
    }

    @FXML
    public void handleSave() {
        try {
            if (!ServiceManager.getInstance().isReady()) ServiceManager.getInstance().init();
            if (comboPerson.getValue() == null) { new Alert(Alert.AlertType.WARNING, "Seleziona una persona!").show(); return; }

            Person person = comboPerson.getValue();
            List<Attachment> newAttachments = new ArrayList<>();
            for (AttachmentItem item : filesListView.getItems()) {
                if (item.localFile != null) newAttachments.add(new Attachment(item.type, item.name, new FileInputStream(item.localFile)));
            }
            List<Document> survivingDocs = new ArrayList<>();
            if (editingExpense != null) {
                for (AttachmentItem item : filesListView.getItems()) {
                    if (item.serverDoc != null) survivingDocs.add(item.serverDoc);
                }
            }
            String dateStr = (datePicker.getValue() != null) ? datePicker.getValue().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "";
            Expense expenseToSave;
            if (editingExpense != null) {
                expenseToSave = new Expense(editingExpense.getId(), comboYear.getValue(), person, comboType.getValue(), txtDescription.getText(), dateStr, editingExpense.getExpenseState());
                expenseToSave.setDocuments(survivingDocs);
            } else {
                expenseToSave = new Expense(comboYear.getValue(), person, comboType.getValue(), txtDescription.getText(), dateStr);
            }
            ServiceManager.getInstance().getService().registerExpense(expenseToSave, newAttachments);
            new Alert(Alert.AlertType.INFORMATION, "Spesa salvata con successo!").showAndWait();
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/simonepugliese/taxreportgui/view/DashboardView.fxml"));
                Parent view = loader.load();
                if (btnSave.getScene().getRoot() instanceof BorderPane mainPane) mainPane.setCenter(view);
            } catch (IOException e) { e.printStackTrace(); }
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Errore: " + e.getMessage()).show();
            e.printStackTrace();
        }
    }
}