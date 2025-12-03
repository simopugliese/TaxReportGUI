package com.simonepugliese.taxreportgui.util;

import com.simonepugliese.taxreportgui.gui.ConfigService;
import pugliesesimone.taxreport.metadata.MariaDbMetadata;
import pugliesesimone.taxreport.metadata.MetadataInterface;
import pugliesesimone.taxreport.model.Document;
import pugliesesimone.taxreport.service.TaxReportService;
import pugliesesimone.taxreport.storage.SmbStorage;
import pugliesesimone.taxreport.storage.StorageInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ServiceManager {

    private static ServiceManager instance;

    private TaxReportService taxReportService;
    private MetadataInterface metadata;
    private StorageInterface storage; // [NEW] Riferimento diretto allo storage

    // Cartella Cache Locale
    private final Path cachePath;

    private ServiceManager() {
        // Definiamo una cartella temporanea per l'app
        String tempDir = System.getProperty("java.io.tmpdir");
        this.cachePath = Paths.get(tempDir, "TaxReportCache");
    }

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

        // 1. Metadata (DB)
        this.metadata = new MariaDbMetadata(
                host,
                Integer.parseInt(cfg.get(ConfigService.KEY_DB_PORT, "3306")),
                cfg.get(ConfigService.KEY_DB_NAME, "taxreport"),
                cfg.get(ConfigService.KEY_DB_USER, "root"),
                cfg.get(ConfigService.KEY_DB_PASS, "")
        );

        // 2. Storage (SMB)
        this.storage = new SmbStorage(
                host,
                cfg.get(ConfigService.KEY_SMB_SHARE, "TaxData"),
                cfg.get(ConfigService.KEY_SMB_USER, "pi"),
                cfg.get(ConfigService.KEY_SMB_PASS, "")
        );

        // 3. Service
        this.taxReportService = new TaxReportService(storage, metadata);

        // 4. Init Cache Dir
        if (!Files.exists(cachePath)) {
            Files.createDirectories(cachePath);
        }
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

    // --- NUOVE FUNZIONALITÀ CACHE ---

    public Path getCachePath() {
        return cachePath;
    }

    /**
     * Scarica un documento in cache (Smart Caching).
     * Se il file esiste già, ritorna quello senza scaricare.
     */
    public File downloadDocument(Document doc) throws Exception {
        if (!isReady()) init();

        // Ricaviamo il nome file dal path relativo
        File remoteFile = new File(doc.getRelativePath());
        String filename = remoteFile.getName();
        String parentPath = remoteFile.getParent() != null ? remoteFile.getParent() : "";

        // File di destinazione
        File localFile = cachePath.resolve(filename).toFile();

        // SMART CACHE: Se esiste e ha contenuto, usalo
        if (localFile.exists() && localFile.length() > 0) {
            System.out.println("Cache Hit: " + filename);
            return localFile;
        }

        System.out.println("Downloading: " + filename + " from " + parentPath);
        // Download
        try (InputStream is = storage.loadFile(parentPath, filename);
             FileOutputStream fos = new FileOutputStream(localFile)) {
            is.transferTo(fos);
        }

        return localFile;
    }
}