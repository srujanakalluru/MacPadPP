package com.sk.macpad.platform;

import com.sk.macpad.control.EditorController;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * macOS desktop integration: routes Finder "Open With" / double-click events and
 * the About/Quit menu actions from {@link java.awt.Desktop} to the controller.
 */
public final class MacIntegration {

    private MacIntegration() {
    }

    public static void configureSystemProperties() {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "MacPad++");
    }

    public static void install(EditorController controller) {
        if (!Desktop.isDesktopSupported()) return;
        Desktop desktop = Desktop.getDesktop();
        try {
            desktop.setOpenFileHandler(event -> {
                List<File> files = event.getFiles();
                SwingUtilities.invokeLater(() -> {
                    for (File f : files) controller.openFile(f);
                });
            });
        } catch (UnsupportedOperationException ignored) {
            // ignore
        }
        try {
            desktop.setAboutHandler(event -> controller.showAbout());
        } catch (UnsupportedOperationException ignored) {
            // ignore
        }
        try {
            desktop.setQuitHandler((event, response) -> {
                controller.saveSession();
                if (controller.confirmCloseAll()) response.performQuit();
                else response.cancelQuit();
            });
        } catch (UnsupportedOperationException ignored) {
            // ignore
        }
    }
}
