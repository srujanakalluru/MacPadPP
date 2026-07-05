package com.sk.macpad.control;

import com.sk.macpad.model.Buffer;
import com.sk.macpad.ui.MainFrame;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class EditorController {

    private static final String APP = "MacPad++";

    private final MainFrame frame;
    private final List<Buffer> buffers = new ArrayList<>();
    private boolean dark = true;
    private int newCounter = 1;
    private boolean suppressDirty = false;

    public EditorController(MainFrame frame) {
        this.frame = frame;
        frame.tabs().addChangeListener(e -> { updateStatus(); refreshTitle(current()); });
    }

    public void start() {
        newBuffer();
    }

    public boolean isDark() { return dark; }

    private Buffer current() {
        int i = frame.tabs().getSelectedIndex();
        return (i >= 0 && i < buffers.size()) ? buffers.get(i) : null;
    }

    public Buffer newBuffer() {
        RSyntaxTextArea area = new RSyntaxTextArea();
        area.setCodeFoldingEnabled(true);
        Buffer b = new Buffer("new " + (newCounter++), area);
        buffers.add(b);
        JTabbedPane tabs = frame.tabs();
        tabs.addTab(b.getTitle(), b.getScroll());
        area.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { onEdit(b); }
            public void removeUpdate(DocumentEvent e) { onEdit(b); }
            public void changedUpdate(DocumentEvent e) { /* no-op */ }
        });
        area.addCaretListener(e -> updateStatus());
        tabs.setSelectedComponent(b.getScroll());
        refreshTitle(b);
        updateStatus();
        return b;
    }

    private void onEdit(Buffer b) {
        if (suppressDirty) return;
        if (!b.isDirty()) { b.setDirty(true); refreshTitle(b); }
        updateStatus();
    }

    public void openDialog() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) openFile(fc.getSelectedFile());
    }

    public void openFile(File file) {
        try {
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            Buffer b = newBuffer();
            b.setFile(file);
            suppressDirty = true;
            b.getArea().setText(text);
            suppressDirty = false;
            b.getArea().setCaretPosition(0);
            b.setDirty(false);
            refreshTitle(b);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Could not open:\n" + ex.getMessage(), APP, JOptionPane.ERROR_MESSAGE);
        }
    }

    public void saveCurrent() {
        Buffer b = current();
        if (b == null) return;
        File target = b.getFile();
        if (target == null) {
            JFileChooser fc = new JFileChooser();
            if (fc.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
            target = fc.getSelectedFile();
        }
        try {
            Files.writeString(target.toPath(), b.getArea().getText(), StandardCharsets.UTF_8);
            b.setFile(target);
            b.setDirty(false);
            refreshTitle(b);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Save failed:\n" + ex.getMessage(), APP, JOptionPane.ERROR_MESSAGE);
        }
    }

    public void closeCurrent() {
        Buffer b = current();
        if (b == null) return;
        int idx = buffers.indexOf(b);
        buffers.remove(idx);
        frame.tabs().remove(idx);
        if (buffers.isEmpty()) newBuffer();
    }

    public void toggleTheme() {
        dark = !dark;
        frame.applyLookAndFeel(dark);
    }

    private void refreshTitle(Buffer b) {
        if (b == null) return;
        int idx = buffers.indexOf(b);
        if (idx < 0) return;
        String shown = (b.isDirty() ? "* " : "") + b.displayName();
        frame.tabs().setTitleAt(idx, shown);
        frame.setTitle(shown + " - " + APP);
    }

    private void updateStatus() {
        Buffer b = current();
        if (b == null) { frame.setStatus(" "); return; }
        RSyntaxTextArea area = b.getArea();
        frame.setStatus("Len " + area.getDocument().getLength() + "   Pos " + area.getCaretPosition());
    }
}
