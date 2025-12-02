package com.simonepugliese.taxreportgui.controller;

import com.simonepugliese.taxreportgui.gui.ConfigService;
import com.simonepugliese.taxreportgui.util.ServiceManager;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class SettingsController {

    @FXML private TextField txtHost, txtDbPort, txtDbName, txtDbUser, txtSmbShare, txtSmbUser;
    @FXML private PasswordField txtDbPass, txtSmbPass;

    @FXML
    public void initialize() {
        ConfigService cfg = ConfigService.getInstance();
        txtHost.setText(cfg.get(ConfigService.KEY_HOST, ""));
        txtDbPort.setText(cfg.get(ConfigService.KEY_DB_PORT, "3306"));
        txtDbName.setText(cfg.get(ConfigService.KEY_DB_NAME, "taxreport"));
        txtDbUser.setText(cfg.get(ConfigService.KEY_DB_USER, ""));
        txtDbPass.setText(cfg.get(ConfigService.KEY_DB_PASS, ""));
        txtSmbShare.setText(cfg.get(ConfigService.KEY_SMB_SHARE, "TaxData"));
        txtSmbUser.setText(cfg.get(ConfigService.KEY_SMB_USER, ""));
        txtSmbPass.setText(cfg.get(ConfigService.KEY_SMB_PASS, ""));
    }

    @FXML
    public void handleSave() {
        ConfigService cfg = ConfigService.getInstance();
        cfg.set(ConfigService.KEY_HOST, txtHost.getText());
        cfg.set(ConfigService.KEY_DB_PORT, txtDbPort.getText());
        cfg.set(ConfigService.KEY_DB_NAME, txtDbName.getText());
        cfg.set(ConfigService.KEY_DB_USER, txtDbUser.getText());
        cfg.set(ConfigService.KEY_DB_PASS, txtDbPass.getText());
        cfg.set(ConfigService.KEY_SMB_SHARE, txtSmbShare.getText());
        cfg.set(ConfigService.KEY_SMB_USER, txtSmbUser.getText());
        cfg.set(ConfigService.KEY_SMB_PASS, txtSmbPass.getText());

        cfg.save();

        try {
            // Tentiamo di inizializzare subito per verificare la connessione
            ServiceManager.getInstance().init();
            new Alert(Alert.AlertType.INFORMATION, "Configurazione salvata e connessione OK!").show();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Config salvata ma connessione fallita: " + e.getMessage()).show();
        }
    }
}