package com.simonepugliese.taxreportgui.util;

import com.simonepugliese.taxreportgui.gui.ConfigService;
import pugliesesimone.taxreport.metadata.MariaDbMetadata;
import pugliesesimone.taxreport.metadata.MetadataInterface;
import pugliesesimone.taxreport.service.TaxReportService;
import pugliesesimone.taxreport.storage.SmbStorage;
import pugliesesimone.taxreport.storage.StorageInterface;

public class ServiceManager {

    private static ServiceManager instance;

    private TaxReportService taxReportService;
    private MetadataInterface metadata; // Ci serve per leggere i dati grezzi!

    private ServiceManager() {}

    public static synchronized ServiceManager getInstance() {
        if (instance == null) {
            instance = new ServiceManager();
        }
        return instance;
    }

    public void init() throws Exception {
        ConfigService cfg = ConfigService.getInstance();

        String host = cfg.get(ConfigService.KEY_HOST, "");
        if (host.isEmpty()) throw new IllegalStateException("Configurazione mancante. Vai in Impostazioni.");

        // 1. Inizializziamo Metadata (DB)
        this.metadata = new MariaDbMetadata(
                host,
                Integer.parseInt(cfg.get(ConfigService.KEY_DB_PORT, "3306")),
                cfg.get(ConfigService.KEY_DB_NAME, "taxreport"),
                cfg.get(ConfigService.KEY_DB_USER, "root"),
                cfg.get(ConfigService.KEY_DB_PASS, "")
        );

        // 2. Inizializziamo Storage (SMB)
        StorageInterface storage = new SmbStorage(
                host, // Assumiamo SMB sullo stesso host del DB
                cfg.get(ConfigService.KEY_SMB_SHARE, "TaxData"),
                cfg.get(ConfigService.KEY_SMB_USER, "pi"),
                cfg.get(ConfigService.KEY_SMB_PASS, "")
        );

        // 3. Creiamo il Service Backend originale
        this.taxReportService = new TaxReportService(storage, metadata);
    }

    public TaxReportService getService() {
        if (taxReportService == null) throw new IllegalStateException("Servizio non inizializzato");
        return taxReportService;
    }

    public MetadataInterface getMetadata() {
        if (metadata == null) throw new IllegalStateException("Metadata non inizializzato");
        return metadata;
    }

    public boolean isReady() {
        return taxReportService != null;
    }
}