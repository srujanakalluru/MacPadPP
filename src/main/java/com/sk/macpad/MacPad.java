package com.sk.macpad;

import com.sk.macpad.control.EditorController;
import com.sk.macpad.ui.MainFrame;

import javax.swing.SwingUtilities;

public class MacPad {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            EditorController controller = new EditorController(frame);
            frame.setController(controller);
            controller.start(args);
            frame.setVisible(true);
        });
    }
}
