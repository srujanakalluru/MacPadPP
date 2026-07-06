package com.sk.macpad.service;

import java.io.*;
import java.nio.file.Files;
import java.util.Properties;

/**
 * Persistence of the editor session (open tabs, preferences) and per-document
 * backups of unsaved buffers, under {@code ~/Library/Application Support/MacPad++}.
 */
public final class SessionService {

    private SessionService() {
    }

    public static File appDir() {
        File dir = new File(System.getProperty("user.home"), "Library/Application Support/MacPad++");
        ensureDir(dir);
        return dir;
    }

    public static File backupDir() {
        File dir = new File(appDir(), "backups");
        ensureDir(dir);
        return dir;
    }

    private static void ensureDir(File dir) {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            // ignore
        }
    }

    private static File sessionFile() {
        return new File(appDir(), "session.properties");
    }

    public static Properties load() {
        Properties p = new Properties();
        File f = sessionFile();
        if (f.exists()) {
            try (InputStream is = new FileInputStream(f)) {
                p.load(is);
            } catch (IOException ignored) {
                // ignore
            }
        }
        return p;
    }

    public static void save(Properties p) {
        try (OutputStream os = new FileOutputStream(sessionFile())) {
            p.store(os, "MacPad++");
        } catch (IOException ignored) {
            // ignore
        }
    }

    public static String backupNameFor(File file) {
        return Integer.toHexString(file.getAbsolutePath().hashCode()) + ".bak";
    }

    public static void writeBackup(String name, String text) {
        try {
            Files.writeString(new File(backupDir(), name).toPath(), text);
        } catch (IOException ignored) {
            // ignore
        }
    }

    public static String readBackup(String name) {
        try {
            return Files.readString(new File(backupDir(), name).toPath());
        } catch (IOException e) {
            return null;
        }
    }

    public static void deleteBackup(String name) {
        File f = new File(backupDir(), name);
        if (f.exists() && !f.delete()) {
            // ignore
        }
    }
}
