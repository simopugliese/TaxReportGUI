package com.simonepugliese.taxreportgui.gui;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public class ConfigService {
    private static final String CONFIG_FILE = ".taxreport_config.properties";
    private final Path configPath;
    private final Properties props = new Properties();
    private static ConfigService instance;

    // Keys
    public static final String KEY_HOST = "raspberry.host";
    public static final String KEY_DB_PORT = "db.port";
    public static final String KEY_DB_NAME = "db.name";
    public static final String KEY_DB_USER = "db.user";
    public static final String KEY_DB_PASS = "db.pass";
    public static final String KEY_SMB_SHARE = "smb.share";
    public static final String KEY_SMB_USER = "smb.user";
    public static final String KEY_SMB_PASS = "smb.pass";

    private ConfigService() {
        this.configPath = Paths.get(System.getProperty("user.home"), CONFIG_FILE);
        load();
    }

    public static synchronized ConfigService getInstance() {
        if (instance == null) instance = new ConfigService();
        return instance;
    }

    private void load() {
        if (!Files.exists(configPath)) return;
        try (InputStream is = Files.newInputStream(configPath)) {
            props.load(is);
        } catch (IOException e) { e.printStackTrace(); }
    }

    public void save() {
        try (OutputStream os = Files.newOutputStream(configPath)) {
            props.store(os, "TaxReport GUI Config");
        } catch (IOException e) { e.printStackTrace(); }
    }

    public String get(String key, String def) { return props.getProperty(key, def); }
    public void set(String key, String val) { props.setProperty(key, val); }
}