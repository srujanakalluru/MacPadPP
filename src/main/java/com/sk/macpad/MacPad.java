package com.sk.macpad;

import com.sk.macpad.control.EditorController;
import com.sk.macpad.platform.MacIntegration;
import com.sk.macpad.ui.MainFrame;

import javax.swing.SwingUtilities;

/** Application entry point and composition root: wires the layers together and starts the app. */
public class MacPad {

    public static void main(String[] args) {
        MacIntegration.configureSystemProperties();
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            EditorController controller = new EditorController(frame);
            frame.setController(controller);
            MacIntegration.install(controller);
            controller.start(args);
            frame.setVisible(true);
        });
    }
}
