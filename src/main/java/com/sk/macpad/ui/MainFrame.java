package com.sk.macpad.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.sk.macpad.control.EditorController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class MainFrame extends JFrame {

    private final JTabbedPane tabs = new JTabbedPane();
    private final JLabel status = new JLabel(" ");
    private EditorController controller;

    public MainFrame() {
        super("MacPad++");
        applyLookAndFeel(true);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        add(tabs, BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        add(status, BorderLayout.SOUTH);
    }

    public void setController(EditorController controller) {
        this.controller = controller;
        setJMenuBar(buildMenuBar());
    }

    public JTabbedPane tabs() { return tabs; }

    public void setStatus(String text) { status.setText(text); }

    public void applyLookAndFeel(boolean dark) {
        try {
            UIManager.setLookAndFeel(dark ? new FlatDarkLaf() : new FlatLightLaf());
            SwingUtilities.updateComponentTreeUI(this);
        } catch (UnsupportedLookAndFeelException ignored) {
            // ignore
        }
    }

    private JMenuBar buildMenuBar() {
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.add(item("New", 'N', mask, e -> controller.newBuffer()));
        file.add(item("Open…", 'O', mask, e -> controller.openDialog()));
        file.add(item("Save", 'S', mask, e -> controller.saveCurrent()));
        file.add(item("Close Tab", 'W', mask, e -> controller.closeCurrent()));
        bar.add(file);
        JMenu view = new JMenu("View");
        view.add(item("Toggle Theme", e -> controller.toggleTheme()));
        bar.add(view);
        return bar;
    }

    private JMenuItem item(String label, ActionListener a) {
        JMenuItem mi = new JMenuItem(label);
        mi.addActionListener(a);
        return mi;
    }

    private JMenuItem item(String label, char key, int mask, ActionListener a) {
        JMenuItem mi = item(label, a);
        mi.setAccelerator(KeyStroke.getKeyStroke(key, mask));
        return mi;
    }
}
