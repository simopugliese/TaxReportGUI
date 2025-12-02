package com.simonepugliese.taxreportgui.controller;

import com.simonepugliese.taxreportgui.util.ServiceManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.util.Callback;
import org.kordamp.ikonli.javafx.FontIcon;
import pugliesesimone.taxreport.model.*;

import java.io.File;
import java.io.FileInputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AddExpenseController {

    @FXML private TextField txtPersonName, txtFiscalCode, txtDescription;
    @FXML private ComboBox<String> comboYear;
    @FXML private ComboBox<ExpenseType> comboType;
    @FXML private DatePicker datePicker;
    @FXML private ListView<AttachmentItem> filesListView;

    // Classe helper interna per la lista
    private static class AttachmentItem {
        File file;
        DocumentType type;
        public AttachmentItem(File f, DocumentType t) { this.file = f; this.type = t; }
    }

    @FXML
    public void initialize() {
        comboYear.setItems(FXCollections.observableArrayList("2023", "2024", "2025"));
        comboYear.setValue("2025");
        comboType.setItems(FXCollections.observableArrayList(ExpenseType.values()));
        comboType.getSelectionModel().selectFirst();

        // Custom Cell Factory per la ListView
        filesListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(AttachmentItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    HBox box = new HBox(10);
                    Label lblName = new Label(item.file.getName());
                    Label lblType = new Label("[" + item.type + "]");
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

    @FXML
    public void handleBrowseFiles() {
        FileChooser fc = new FileChooser();
        List<File> files = fc.showOpenMultipleDialog(txtDescription.getScene().getWindow());
        if (files != null) {
            // Chiediamo il tipo per ogni file? Per semplicità qui mettiamo default FATTURA,
            // idealmente si apre un dialog, ma facciamo una cosa rapida:
            // Usiamo un dialog choice
            List<DocumentType> choices = List.of(DocumentType.values());
            ChoiceDialog<DocumentType> dialog = new ChoiceDialog<>(DocumentType.FATTURA, choices);
            dialog.setTitle("Tipo Documento");
            dialog.setHeaderText("Seleziona il tipo per i file caricati");
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

            if (txtPersonName.getText().isEmpty() || filesListView.getItems().isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "Dati incompleti!").show();
                return;
            }

            Person p = new Person(txtPersonName.getText(), txtFiscalCode.getText());
            ServiceManager.getInstance().getService().registerPerson(p);

            List<Attachment> attachments = new ArrayList<>();
            for (AttachmentItem item : filesListView.getItems()) {
                attachments.add(new Attachment(item.type, item.file.getName(), new FileInputStream(item.file)));
            }

            String dateStr = (datePicker.getValue() != null) ?
                    datePicker.getValue().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "";

            Expense exp = new Expense(
                    comboYear.getValue(), p, comboType.getValue(), txtDescription.getText(), dateStr
            );

            ServiceManager.getInstance().getService().registerExpense(exp, attachments);

            new Alert(Alert.AlertType.INFORMATION, "Spesa salvata!").show();
            filesListView.getItems().clear();
            txtDescription.clear();

        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Errore: " + e.getMessage()).show();
            e.printStackTrace();
        }
    }
}