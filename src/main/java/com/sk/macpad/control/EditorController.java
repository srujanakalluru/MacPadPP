package com.sk.macpad.control;

import com.sk.macpad.model.Buffer;
import com.sk.macpad.model.Eol;
import com.sk.macpad.platform.MagnifyGesture;
import com.sk.macpad.service.FileSearchService;
import com.sk.macpad.service.SessionService;
import com.sk.macpad.service.SyntaxResolver;
import com.sk.macpad.service.TextCodec;
import com.sk.macpad.ui.MainFrame;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.image.BufferedImage;
import java.awt.print.PrinterException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Application logic: owns the open {@link Buffer}s and preferences and drives
 * the {@link MainFrame} view against the service layer (file IO, encoding,
 * search, session).
 */
public class EditorController {

    private static final String APP = "MacPad++";
    private static final Icon BOOKMARK_ICON = makeBookmarkIcon();
    private static final int AUTOSAVE_INTERVAL_MS = 5000;


    private final MainFrame frame;
    private final List<Buffer> buffers = new ArrayList<>();
    private final Deque<String> recent = new ArrayDeque<>();

    private final java.util.Map<RSyntaxTextArea, Buffer> areaToBuffer = new java.util.IdentityHashMap<>();
    private final java.util.Map<Buffer, RSyntaxTextArea> splitClones = new java.util.IdentityHashMap<>();
    private RSyntaxTextArea focusedArea;

    private boolean dark = true;
    private int fontSize = 13;
    private boolean wordWrap = false;
    private boolean showWhitespace = false;
    private boolean showEol = false;
    private boolean indentGuides = false;
    private int newCounter = 1;
    private double zoomAccum;
    private double magnifyAccum;

    private boolean suppressDirty = false;

    private String lastQuery = "";
    private boolean lastRegex;
    private boolean lastMatchCase;
    private boolean lastWholeWord;

    public EditorController(MainFrame frame) {
        this.frame = frame;
        frame.tabs().addChangeListener(e -> onTabChanged(frame.tabs()));
        frame.secondaryTabs().addChangeListener(e -> onTabChanged(frame.secondaryTabs()));
        new javax.swing.Timer(AUTOSAVE_INTERVAL_MS, e -> saveSession()).start();
    }

    private void onTabChanged(JTabbedPane pane) {
        Component comp = pane.getSelectedComponent();
        if (comp instanceof RTextScrollPane sp && sp.getTextArea() instanceof RSyntaxTextArea a) {
            focusedArea = a;
        }
        updateStatus();
        Buffer c = current();
        if (c != null) refreshTitle(c);
    }

    public void start(String[] args) {
        restoreSession();
        for (String arg : args) {
            File f = new File(arg);
            if (f.isFile()) openFile(f);
        }
        if (isEmpty()) newBuffer();
    }

    public boolean isEmpty() {
        return buffers.isEmpty();
    }

    public boolean isDark() {
        return dark;
    }

    public boolean isWrap() {
        return wordWrap;
    }

    private Buffer current() {
        if (focusedArea != null) {
            Buffer b = areaToBuffer.get(focusedArea);
            if (b != null) return b;
        }
        if (frame.tabs().getSelectedComponent() instanceof RTextScrollPane sp
                && sp.getTextArea() instanceof RSyntaxTextArea a) {
            Buffer b = areaToBuffer.get(a);
            if (b != null) return b;
        }
        return buffers.isEmpty() ? null : buffers.get(0);
    }

    private JTabbedPane paneOf(Buffer b) {
        return frame.secondaryTabs().indexOfComponent(b.getScroll()) >= 0 ? frame.secondaryTabs() : frame.tabs();
    }

    public Buffer newBuffer() {
        Buffer b = createBuffer("new " + (newCounter++));
        selectBuffer(b);
        b.getArea().requestFocusInWindow();
        updateStatus();
        return b;
    }

    private void configureArea(RSyntaxTextArea area) {
        area.setCodeFoldingEnabled(true);
        area.setAntiAliasingEnabled(true);
        area.setTabSize(4);
        area.setLineWrap(wordWrap);
        area.setWrapStyleWord(true);
        area.setWhitespaceVisible(showWhitespace);
        area.setEOLMarkersVisible(showEol);
        area.setPaintTabLines(indentGuides);
        area.setFont(area.getFont().deriveFont((float) fontSize));
        applyThemeTo(area);
        area.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                focusedArea = area;
            }
        });
        area.addCaretListener(e -> updateStatus());
        area.addMouseWheelListener(
                e -> {
                    if ((e.getModifiersEx() & Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()) == 0) return;
                    e.consume();
                    zoomAccum += e.getPreciseWheelRotation();
                    while (zoomAccum >= 1) {
                        zoom(1);
                        zoomAccum -= 1;
                    }
                    while (zoomAccum <= -1) {
                        zoom(-1);
                        zoomAccum += 1;
                    }
                });
    }

    private Buffer createBuffer(String title) {
        RSyntaxTextArea area = new RSyntaxTextArea();
        configureArea(area);
        Buffer b = new Buffer(title, area);
        buffers.add(b);
        areaToBuffer.put(area, b);
        enableBookmarks(b.getScroll());
        JTabbedPane tabs = frame.tabs();
        tabs.addTab(title, b.getScroll());
        tabs.setTabComponentAt(tabs.indexOfComponent(b.getScroll()), tabComponent(b));
        area.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                onEdit(b);
            }

            public void removeUpdate(DocumentEvent e) {
                onEdit(b);
            }

            public void changedUpdate(DocumentEvent e) { /* no-op */ }
        });
        refreshTitle(b);
        return b;
    }

    private void onEdit(Buffer b) {
        if (suppressDirty) return;
        if (!b.isDirty()) {
            b.setDirty(true);
            refreshTitle(b);
        }
        updateStatus();
    }

    private void setBufferText(Buffer b, String text) {
        suppressDirty = true;
        b.getArea().setText(text);
        suppressDirty = false;
    }

    private JComponent tabComponent(Buffer b) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.setOpaque(false);
        JLabel label = new JLabel();
        JButton close = new JButton("×");
        close.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        close.setContentAreaFilled(false);
        close.setFocusable(false);
        close.addActionListener(e -> closeTab(b));
        panel.add(label);
        panel.add(close);
        return panel;
    }

    private void selectBuffer(Buffer b) {
        JTabbedPane pane = paneOf(b);
        int idx = pane.indexOfComponent(b.getScroll());
        if (idx >= 0) pane.setSelectedIndex(idx);
    }

    public void closeCurrent() {
        Buffer b = current();
        if (b != null) closeTab(b);
    }

    public void closeTab(Buffer b) {
        if (b.isDirty()) {
            int choice = JOptionPane.showConfirmDialog(frame, "Save changes to \"" + b.displayName() + "\"?",
                    APP, JOptionPane.YES_NO_CANCEL_OPTION);
            if (choice == JOptionPane.CANCEL_OPTION) return;
            if (choice == JOptionPane.YES_OPTION && !save(b, false)) return;
        }
        if (!buffers.contains(b)) return;
        removeCloneTab(b);
        JTabbedPane pane = paneOf(b);
        int idx = pane.indexOfComponent(b.getScroll());
        if (idx >= 0) pane.remove(idx);
        buffers.remove(b);
        areaToBuffer.remove(b.getArea());
        if (frame.secondaryTabs().getTabCount() == 0) frame.showSecondView(false);
        if (b.getFile() != null) SessionService.deleteBackup(SessionService.backupNameFor(b.getFile()));
        if (buffers.isEmpty()) newBuffer();
        saveSession();
    }

    public void openDialog() {
        JFileChooser fc = new JFileChooser(currentDir());
        fc.setMultiSelectionEnabled(true);
        if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            for (File f : fc.getSelectedFiles()) openFile(f);
            saveSession();
        }
    }

    public void openFile(File file) {
        File f = canonical(file);
        for (Buffer b : buffers) {
            if (f.equals(b.getFile())) {
                selectBuffer(b);
                return;
            }
        }
        try {
            TextCodec.Decoded d = TextCodec.decode(Files.readAllBytes(f.toPath()));
            Buffer b = reusableBlankBuffer();
            if (b == null) b = createBuffer(f.getName());
            b.setFile(f);
            b.setCharset(d.charset());
            b.setBom(d.bom());
            b.setEol(d.eol());
            b.setSyntaxStyle(SyntaxResolver.forFileName(f.getName()));
            setBufferText(b, d.text());
            b.getArea().setCaretPosition(0);
            b.getArea().discardAllEdits();
            b.setDirty(false);
            selectBuffer(b);
            refreshTitle(b);
            pushRecent(f.getAbsolutePath());
            updateStatus();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Could not open:\n" + f + "\n" + ex.getMessage(),
                    APP, JOptionPane.ERROR_MESSAGE);
        }
    }

    private static File canonical(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException ignored) {
            return file;
        }
    }

    private Buffer reusableBlankBuffer() {
        Buffer b = current();
        boolean blank = b != null && b.getFile() == null && !b.isDirty()
                && b.getArea().getDocument().getLength() == 0;
        return blank ? b : null;
    }

    private boolean save(Buffer b, boolean saveAs) {
        if (b == null) return false;
        File target = b.getFile();
        if (saveAs || target == null) {
            JFileChooser fc = new JFileChooser(currentDir());
            fc.setSelectedFile(target != null ? target : new File(currentDir(), b.getTitle() + ".txt"));
            if (fc.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return false;
            target = fc.getSelectedFile();
        }
        try {
            Files.write(target.toPath(), TextCodec.encode(b.getArea().getText(), b.getCharset(), b.isBom(), b.getEol()));
            b.setFile(target);
            b.setSyntaxStyle(SyntaxResolver.forFileName(target.getName()));
            b.setDirty(false);
            refreshTitle(b);
            pushRecent(target.getAbsolutePath());
            SessionService.deleteBackup(SessionService.backupNameFor(target));
            updateStatus();
            return true;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Save failed:\n" + ex.getMessage(), APP, JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    public void saveCurrent() {
        save(current(), false);
        saveSession();
    }

    public void saveCurrentAs() {
        save(current(), true);
        saveSession();
    }

    public void saveAll() {
        for (Buffer b : buffers) {
            if (b.isDirty() || b.getFile() == null) save(b, false);
        }
        saveSession();
    }

    public void reloadFromDisk() {
        Buffer b = current();
        if (b == null || b.getFile() == null) return;
        if (b.isDirty() && JOptionPane.showConfirmDialog(frame,
                "Reload from disk? Unsaved changes will be lost.", APP,
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try {
            TextCodec.Decoded d = TextCodec.decode(Files.readAllBytes(b.getFile().toPath()));
            b.setCharset(d.charset());
            b.setBom(d.bom());
            b.setEol(d.eol());
            setBufferText(b, d.text());
            b.getArea().discardAllEdits();
            b.setDirty(false);
            refreshTitle(b);
            updateStatus();
        } catch (IOException ignored) {
            // ignore
        }
    }

    public void printCurrent() {
        Buffer b = current();
        if (b == null) return;
        try {
            b.getArea().print();
        } catch (PrinterException ignored) {
            // ignore
        }
    }

    public void undo() {
        Buffer b = current();
        if (b != null) b.getArea().undoLastAction();
    }

    public void redo() {
        Buffer b = current();
        if (b != null) b.getArea().redoLastAction();
    }

    public void selectAll() {
        Buffer b = current();
        if (b != null) b.getArea().selectAll();
    }

    public void toggleComment() {
        Buffer b = current();
        if (b == null) return;
        Action a = b.getArea().getActionMap().get(RSyntaxTextAreaEditorKit.rstaToggleCommentAction);
        if (a != null) a.actionPerformed(new ActionEvent(b.getArea(), ActionEvent.ACTION_PERFORMED, ""));
    }

    public void transformUpper() {
        transform(String::toUpperCase);
    }

    public void transformLower() {
        transform(String::toLowerCase);
    }

    private void transform(Function<String, String> fn) {
        Buffer b = current();
        if (b == null) return;
        String sel = b.getArea().getSelectedText();
        if (sel != null) b.getArea().replaceSelection(fn.apply(sel));
    }

    public boolean find(String query, boolean regex, boolean matchCase, boolean wholeWord, boolean forward) {
        RSyntaxTextArea area = activeArea();
        if (area == null || query.isEmpty()) return false;
        rememberSearch(query, regex, matchCase, wholeWord);
        SearchResult r = SearchEngine.find(area, context(query, regex, matchCase, wholeWord, forward, null));
        if (!r.wasFound()) frame.setStatus("Not found: " + query);
        return r.wasFound();
    }

    public void findNext() {
        repeatFind(true);
    }

    public void findPrevious() {
        repeatFind(false);
    }

    private void repeatFind(boolean forward) {
        RSyntaxTextArea area = activeArea();
        if (area == null || lastQuery.isEmpty()) return;
        SearchResult r =
                SearchEngine.find(area, context(lastQuery, lastRegex, lastMatchCase, lastWholeWord, forward, null));
        if (!r.wasFound()) frame.setStatus("Not found: " + lastQuery);
    }

    public void useSelectionForFind() {
        RSyntaxTextArea area = activeArea();
        if (area == null) return;
        String sel = area.getSelectedText();
        if (sel == null || sel.isEmpty()) return;
        rememberSearch(sel, false, false, false);
        frame.setStatus("Find: " + sel);
    }

    public String searchSeed() {
        RSyntaxTextArea area = activeArea();
        if (area != null) {
            String sel = area.getSelectedText();
            if (sel != null && !sel.isEmpty()) return sel;
        }
        return lastQuery;
    }

    private void rememberSearch(String query, boolean regex, boolean matchCase, boolean wholeWord) {
        lastQuery = query;
        lastRegex = regex;
        lastMatchCase = matchCase;
        lastWholeWord = wholeWord;
    }

    public void replaceNext(String query, boolean regex, boolean matchCase, boolean wholeWord, String replacement) {
        Buffer b = current();
        if (b == null || query.isEmpty()) return;
        SearchEngine.replace(b.getArea(), context(query, regex, matchCase, wholeWord, true, replacement));
    }

    public void replaceAll(String query, boolean regex, boolean matchCase, boolean wholeWord, String replacement) {
        Buffer b = current();
        if (b == null || query.isEmpty()) return;
        SearchResult r = SearchEngine.replaceAll(b.getArea(), context(query, regex, matchCase, wholeWord, true, replacement));
        frame.setStatus("Replaced " + r.getCount() + " occurrence(s)");
    }

    private SearchContext context(String query, boolean regex, boolean matchCase, boolean wholeWord,
                                  boolean forward, String replacement) {
        SearchContext c = new SearchContext();
        c.setSearchFor(query);
        c.setMatchCase(matchCase);
        c.setRegularExpression(regex);
        c.setWholeWord(wholeWord);
        c.setSearchForward(forward);
        c.setSearchWrap(true);
        if (replacement != null) c.setReplaceWith(replacement);
        return c;
    }

    public int markAll(String query, boolean regex, boolean matchCase, boolean wholeWord) {
        RSyntaxTextArea area = activeArea();
        if (area == null || query.isEmpty()) return 0;
        rememberSearch(query, regex, matchCase, wholeWord);
        SearchContext c = context(query, regex, matchCase, wholeWord, true, null);
        c.setMarkAll(true);
        SearchResult r = SearchEngine.markAll(area, c);
        frame.setStatus(r.getMarkedCount() + " match(es)");
        return r.getMarkedCount();
    }

    public void clearMarks() {
        RSyntaxTextArea area = activeArea();
        if (area != null) area.clearMarkAllHighlights();
    }

    private RSyntaxTextArea activeArea() {
        Buffer cur = current();
        if (cur == null) return null;
        if (focusedArea != null && cur.equals(areaToBuffer.get(focusedArea))) return focusedArea;
        return cur.getArea();
    }

    public void findInFiles() {
        JFileChooser fc = new JFileChooser(currentDir());
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        File root = fc.getSelectedFile();
        String query = JOptionPane.showInputDialog(frame, "Search for (regex):");
        if (query == null || query.isEmpty()) return;
        Pattern pattern;
        try {
            pattern = Pattern.compile(query);
        } catch (java.util.regex.PatternSyntaxException ex) {
            JOptionPane.showMessageDialog(frame, "Bad regex: " + ex.getMessage());
            return;
        }
        frame.showSearchResults(root, FileSearchService.search(root, pattern));
    }

    public void openAt(File file, int line) {
        openFile(file);
        Buffer b = current();
        if (b == null) return;
        SwingUtilities.invokeLater(() -> {
            moveCaretToLine(b, line);
            b.getArea().requestFocusInWindow();
        });
    }

    public void goToLine() {
        Buffer b = current();
        if (b == null) return;
        String input = JOptionPane.showInputDialog(frame, "Go to line:");
        if (input == null) return;
        try {
            moveCaretToLine(b, Math.max(1, Integer.parseInt(input.trim())));
            b.getArea().requestFocusInWindow();
        } catch (NumberFormatException ignored) {
            // ignore
        }
    }

    private void moveCaretToLine(Buffer b, int line) {
        try {
            RSyntaxTextArea area = b.getArea();
            area.setCaretPosition(area.getLineStartOffset(Math.min(line - 1, area.getLineCount() - 1)));
        } catch (BadLocationException ignored) {
            // ignore
        }
    }

    public void toggleWrap() {
        wordWrap = !wordWrap;
        for (RSyntaxTextArea a : areaToBuffer.keySet()) a.setLineWrap(wordWrap);
        saveSession();
    }

    public void zoom(int delta) {
        fontSize = delta == 0 ? 13 : Math.max(8, Math.min(40, fontSize + delta));
        for (RSyntaxTextArea a : areaToBuffer.keySet()) a.setFont(a.getFont().deriveFont((float) fontSize));
        saveSession();
    }

    public void enablePinchZoom() {
        MagnifyGesture.install(this::magnify);
    }

    public void magnify(double delta) {
        magnifyAccum += delta;
        while (magnifyAccum >= 0.1) {
            zoom(1);
            magnifyAccum -= 0.1;
        }
        while (magnifyAccum <= -0.1) {
            zoom(-1);
            magnifyAccum += 0.1;
        }
    }

    public void toggleTheme() {
        dark = !dark;
        frame.applyLookAndFeel(dark);
        for (RSyntaxTextArea a : areaToBuffer.keySet()) applyThemeTo(a);
        saveSession();
    }

    private void applyThemeTo(RSyntaxTextArea area) {
        String resource = dark ? "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"
                : "/org/fife/ui/rsyntaxtextarea/themes/default.xml";
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in != null) {
                Theme.load(in).apply(area);
                area.setFont(area.getFont().deriveFont((float) fontSize));
            }
        } catch (IOException ignored) {
            // ignore
        }
    }

    public void setLanguage(String style) {
        Buffer b = current();
        if (b == null) return;
        b.setSyntaxStyle(style);
        updateStatus();
        saveSession();
    }

    public void chooseLanguage() {
        Buffer b = current();
        if (b == null) return;
        Object[] names = SyntaxResolver.CATALOG.stream().map(SyntaxResolver.Lang::name).toArray();
        String currentName = SyntaxResolver.nameFor(b.getSyntaxStyle());
        Object choice =
                JOptionPane.showInputDialog(
                        frame, "Language:", "Set Language",
                        JOptionPane.PLAIN_MESSAGE, null, names, currentName);
        if (choice == null) return;
        for (SyntaxResolver.Lang lang : SyntaxResolver.CATALOG) {
            if (lang.name().equals(choice)) {
                setLanguage(lang.style());
                return;
            }
        }
    }

    public void setEncoding(String label) {
        Buffer b = current();
        if (b == null) return;
        b.setCharset(TextCodec.charsetForLabel(label));
        b.setBom(TextCodec.bomForLabel(label));
        b.setDirty(true);
        refreshTitle(b);
        updateStatus();
    }

    public void setEol(Eol eol) {
        Buffer b = current();
        if (b == null) return;
        b.setEol(eol);
        b.setDirty(true);
        refreshTitle(b);
        updateStatus();
    }

    public List<String> recentFiles() {
        return new ArrayList<>(recent);
    }

    public void clearRecent() {
        recent.clear();
        saveSession();
    }

    private void pushRecent(String path) {
        recent.remove(path);
        recent.addFirst(path);
        while (recent.size() > 10) recent.removeLast();
    }

    public void showAbout() {
        JOptionPane.showMessageDialog(frame,
                APP + " 1.0.0\nA Notepad++ style editor for macOS.",
                "About " + APP, JOptionPane.INFORMATION_MESSAGE);
    }

    public int fontSize() {
        return fontSize;
    }

    public void setFontSize(int size) {
        fontSize = Math.max(8, Math.min(40, size));
        for (RSyntaxTextArea a : areaToBuffer.keySet()) a.setFont(a.getFont().deriveFont((float) fontSize));
        saveSession();
    }

    public void showPreferences() {
        frame.showPreferences();
    }


    public void quit() {
        saveSession();
        frame.dispose();
    }


    public void saveSession() {
        Properties p = new Properties();
        p.setProperty("dark", String.valueOf(dark));
        p.setProperty("font", String.valueOf(fontSize));
        p.setProperty("wrap", String.valueOf(wordWrap));
        p.setProperty("whitespace", String.valueOf(showWhitespace));
        p.setProperty("eolMarkers", String.valueOf(showEol));
        int n = 0;
        for (Buffer b : buffers) {
            if (b.getFile() == null && !b.isDirty() && b.getArea().getDocument().getLength() == 0) continue;
            persistTab(p, b, n);
            n++;
        }
        p.setProperty("count", String.valueOf(n));
        p.setProperty("active", String.valueOf(frame.tabs().getSelectedIndex()));
        StringBuilder rec = new StringBuilder();
        for (String r : recent) rec.append(r).append("\n");
        p.setProperty("recent", rec.toString());
        SessionService.save(p);
    }

    private void persistTab(Properties p, Buffer b, int index) {
        String pre = "tab." + index + ".";
        if (b.getFile() != null) p.setProperty(pre + "path", b.getFile().getAbsolutePath());
        p.setProperty(pre + "caret", String.valueOf(b.getArea().getCaretPosition()));
        p.setProperty(pre + "syntax", b.getSyntaxStyle());
        p.setProperty(pre + "eol", b.getEol().name());
        p.setProperty(pre + "charset", b.getCharset().name());
        p.setProperty(pre + "bom", String.valueOf(b.isBom()));
        p.setProperty(pre + "dirty", String.valueOf(b.isDirty()));
        p.setProperty(pre + "title", b.getTitle());
        if (b.isDirty()) {
            String name = b.getFile() != null ? SessionService.backupNameFor(b.getFile()) : ("untitled" + index + ".bak");
            SessionService.writeBackup(name, b.getArea().getText());
            if (b.getFile() == null) p.setProperty(pre + "backup", name);
        }
    }

    public void restoreSession() {
        Properties p = SessionService.load();
        if (p.isEmpty()) return;
        dark = Boolean.parseBoolean(p.getProperty("dark", "false"));
        fontSize = Integer.parseInt(p.getProperty("font", "13"));
        wordWrap = Boolean.parseBoolean(p.getProperty("wrap", "false"));
        showWhitespace = Boolean.parseBoolean(p.getProperty("whitespace", "false"));
        showEol = Boolean.parseBoolean(p.getProperty("eolMarkers", "false"));
        frame.applyLookAndFeel(dark);
        for (String r : p.getProperty("recent", "").split("\n")) {
            if (!r.isBlank()) recent.addLast(r.trim());
        }
        int count = Integer.parseInt(p.getProperty("count", "0"));
        for (int i = 0; i < count; i++) {
            try {
                restoreTab(p, "tab." + i + ".");
            } catch (IOException ignored) {
                // ignore
            }
        }
        selectSavedTab(p);
    }

    private void restoreTab(Properties p, String pre) throws IOException {
        String path = p.getProperty(pre + "path");
        boolean dirty = Boolean.parseBoolean(p.getProperty(pre + "dirty", "false"));
        String backup = p.getProperty(pre + "backup");
        if (dirty && backup != null) {
            Buffer b = createBuffer(p.getProperty(pre + "title", "restored"));
            String txt = SessionService.readBackup(backup);
            setBufferText(b, txt != null ? txt : "");
            b.setDirty(true);
            refreshTitle(b);
        } else if (dirty && path != null) {
            File file = new File(path);
            Buffer b = createBuffer(file.getName());
            b.setFile(file);
            b.setSyntaxStyle(SyntaxResolver.forFileName(file.getName()));
            String txt = SessionService.readBackup(SessionService.backupNameFor(file));
            setBufferText(b, txt != null ? txt : Files.readString(file.toPath()));
            b.setDirty(true);
            refreshTitle(b);
        } else if (path != null && new File(path).isFile()) {
            openFile(new File(path));
        }
        restoreCaret(p, pre);
    }

    private void restoreCaret(Properties p, String pre) {
        Buffer cur = current();
        if (cur == null) return;
        try {
            cur.getArea().setCaretPosition(Math.min(
                    Integer.parseInt(p.getProperty(pre + "caret", "0")),
                    cur.getArea().getDocument().getLength()));
        } catch (NumberFormatException ignored) {
            // ignore
        }
    }

    private void selectSavedTab(Properties p) {
        try {
            int a = Integer.parseInt(p.getProperty("active", "0"));
            if (a >= 0 && a < frame.tabs().getTabCount()) frame.tabs().setSelectedIndex(a);
        } catch (NumberFormatException ignored) {
            // ignore
        }
    }

    // ---- line operations ----
    public void duplicateLine() {
        withArea(this::duplicateLineOn);
    }

    public void deleteLine() {
        withArea(this::deleteLineOn);
    }

    public void moveLineUp() {
        withArea(a -> moveLine(a, -1));
    }

    public void moveLineDown() {
        withArea(a -> moveLine(a, 1));
    }

    public void joinLines() {
        withArea(this::joinLinesOn);
    }

    public void sortLinesAscending() {
        transformLines(lines -> {
            lines.sort(Comparator.naturalOrder());
            return lines;
        });
    }

    public void sortLinesDescending() {
        transformLines(lines -> {
            lines.sort(Comparator.reverseOrder());
            return lines;
        });
    }

    public void removeDuplicateLines() {
        transformLines(lines -> new ArrayList<>(new LinkedHashSet<>(lines)));
    }

    public void trimTrailingWhitespace() {
        withArea(a -> replacePreservingCaret(a, Arrays.stream(a.getText().split("\n", -1))
                .map(String::stripTrailing)
                .collect(Collectors.joining("\n"))));
    }

    public void indentToSpaces() {
        withArea(a -> replacePreservingCaret(a, a.getText().replace("\t", "    ")));
    }

    public void indentToTabs() {
        withArea(a -> replacePreservingCaret(a, tabifyLeadingSpaces(a.getText())));
    }

    private void withArea(Consumer<RSyntaxTextArea> op) {
        RSyntaxTextArea area = activeArea();
        if (area != null) op.accept(area);
    }

    private void duplicateLineOn(RSyntaxTextArea area) {
        try {
            int line = area.getLineOfOffset(area.getCaretPosition());
            int start = area.getLineStartOffset(line);
            int end = area.getLineEndOffset(line);
            String text = area.getText(start, end - start);
            area.getDocument().insertString(end, text.endsWith("\n") ? text : "\n" + text, null);
        } catch (BadLocationException ignored) {
            // ignore
        }
    }

    private void deleteLineOn(RSyntaxTextArea area) {
        try {
            int line = area.getLineOfOffset(area.getCaretPosition());
            int start = area.getLineStartOffset(line);
            int end = area.getLineEndOffset(line);
            area.getDocument().remove(start, end - start);
        } catch (BadLocationException ignored) {
            // ignore
        }
    }

    private void moveLine(RSyntaxTextArea area, int direction) {
        try {
            int caretLine = area.getLineOfOffset(area.getCaretPosition());
            int target = caretLine + direction;
            if (target < 0 || target >= area.getLineCount()) return;
            List<String> lines = new ArrayList<>(Arrays.asList(area.getText().split("\n", -1)));
            if (caretLine >= lines.size() || target >= lines.size()) return;
            lines.add(target, lines.remove(caretLine));
            area.setText(String.join("\n", lines));
            area.setCaretPosition(area.getLineStartOffset(target));
        } catch (BadLocationException ignored) {
            // ignore
        }
    }

    private void joinLinesOn(RSyntaxTextArea area) {
        try {
            int firstLine = area.getLineOfOffset(area.getSelectionStart());
            int lastLine = area.getLineOfOffset(area.getSelectionEnd());
            if (lastLine == firstLine) lastLine = Math.min(firstLine + 1, area.getLineCount() - 1);
            if (lastLine <= firstLine) return;
            int start = area.getLineStartOffset(firstLine);
            int end = area.getLineEndOffset(lastLine);
            String joined = area.getText(start, end - start).replaceAll("\\s*\\n\\s*", " ").stripTrailing();
            area.getDocument().remove(start, end - start);
            area.getDocument().insertString(start, joined, null);
        } catch (BadLocationException ignored) {
            // ignore
        }
    }

    private void transformLines(UnaryOperator<List<String>> op) {
        RSyntaxTextArea area = activeArea();
        if (area == null) return;
        try {
            int firstLine;
            int lastLine;
            if (area.getSelectionStart() == area.getSelectionEnd()) {
                firstLine = 0;
                lastLine = area.getLineCount() - 1;
            } else {
                firstLine = area.getLineOfOffset(area.getSelectionStart());
                lastLine = area.getLineOfOffset(area.getSelectionEnd());
                if (lastLine > firstLine && area.getSelectionEnd() == area.getLineStartOffset(lastLine)) lastLine--;
            }
            int start = area.getLineStartOffset(firstLine);
            int end = area.getLineEndOffset(lastLine);
            if (lastLine < area.getLineCount() - 1) end--;
            List<String> lines = new ArrayList<>(Arrays.asList(area.getText(start, end - start).split("\n", -1)));
            String result = String.join("\n", op.apply(lines));
            area.getDocument().remove(start, end - start);
            area.getDocument().insertString(start, result, null);
        } catch (BadLocationException ignored) {
            // ignore
        }
    }

    private String tabifyLeadingSpaces(String text) {
        return Arrays.stream(text.split("\n", -1))
                .map(line -> {
                    int spaces = 0;
                    while (spaces < line.length() && line.charAt(spaces) == ' ') spaces++;
                    int tabs = spaces / 4;
                    return "\t".repeat(tabs) + line.substring(tabs * 4);
                })
                .collect(Collectors.joining("\n"));
    }

    private void replacePreservingCaret(RSyntaxTextArea area, String newText) {
        int caret = area.getCaretPosition();
        area.setText(newText);
        area.setCaretPosition(Math.min(caret, area.getDocument().getLength()));
    }

    // ---- view toggles ----
    public boolean isShowWhitespace() {
        return showWhitespace;
    }

    public boolean isShowEol() {
        return showEol;
    }

    public void toggleWhitespace() {
        showWhitespace = !showWhitespace;
        for (RSyntaxTextArea a : areaToBuffer.keySet()) a.setWhitespaceVisible(showWhitespace);
        saveSession();
    }

    public void toggleEol() {
        showEol = !showEol;
        for (RSyntaxTextArea a : areaToBuffer.keySet()) a.setEOLMarkersVisible(showEol);
        saveSession();
    }

    public boolean isIndentGuides() {
        return indentGuides;
    }

    public void toggleIndentGuides() {
        indentGuides = !indentGuides;
        for (RSyntaxTextArea a : areaToBuffer.keySet()) a.setPaintTabLines(indentGuides);
        saveSession();
    }

    public void cloneToOtherView() {
        Buffer b = current();
        if (b == null) return;
        if (splitClones.containsKey(b)) {
            selectCloneTab(b);
            return;
        }
        RSyntaxTextArea second = new RSyntaxTextArea((RSyntaxDocument) b.getArea().getDocument());
        configureArea(second);
        areaToBuffer.put(second, b);
        splitClones.put(b, second);
        RTextScrollPane scroll = new RTextScrollPane(second);
        enableBookmarks(scroll);
        JTabbedPane st = frame.secondaryTabs();
        st.addTab(b.displayName(), scroll);
        int idx = st.getTabCount() - 1;
        st.setTabComponentAt(idx, cloneTabComponent(b));
        frame.showSecondView(true);
        st.setSelectedIndex(idx);
        second.requestFocusInWindow();
    }

    private JComponent cloneTabComponent(Buffer b) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.setOpaque(false);
        JLabel label = new JLabel(b.displayName());
        JButton close = new JButton("×");
        close.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        close.setContentAreaFilled(false);
        close.setFocusable(false);
        close.addActionListener(e -> removeCloneTab(b));
        panel.add(label);
        panel.add(close);
        return panel;
    }

    private void selectCloneTab(Buffer b) {
        RSyntaxTextArea clone = splitClones.get(b);
        if (clone == null) return;
        int idx = indexOfArea(frame.secondaryTabs(), clone);
        if (idx >= 0) frame.secondaryTabs().setSelectedIndex(idx);
    }

    private void removeCloneTab(Buffer b) {
        RSyntaxTextArea clone = splitClones.remove(b);
        if (clone == null) return;
        areaToBuffer.remove(clone);
        if (clone.equals(focusedArea)) focusedArea = b.getArea();
        JTabbedPane st = frame.secondaryTabs();
        int idx = indexOfArea(st, clone);
        if (idx >= 0) st.remove(idx);
        if (st.getTabCount() == 0) frame.showSecondView(false);
    }

    private int indexOfArea(JTabbedPane pane, RSyntaxTextArea area) {
        for (int i = 0; i < pane.getTabCount(); i++) {
            if (pane.getComponentAt(i) instanceof RTextScrollPane sp && area.equals(sp.getTextArea())) return i;
        }
        return -1;
    }

    public void renameCurrentFile() {
        Buffer b = current();
        if (b == null) return;
        File file = b.getFile();
        if (file == null) {
            JOptionPane.showMessageDialog(frame, "Save the document before renaming it.", APP, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Object input = JOptionPane.showInputDialog(frame, "New name:", "Rename",
                JOptionPane.PLAIN_MESSAGE, null, null, file.getName());
        if (input == null) return;
        String newName = input.toString().trim();
        if (newName.isEmpty() || newName.equals(file.getName())) return;
        File target = new File(file.getParentFile(), newName);
        if (target.exists()) {
            JOptionPane.showMessageDialog(frame, "A file named \"" + newName + "\" already exists.", APP, JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            Files.move(file.toPath(), target.toPath());
            b.setFile(target);
            b.setSyntaxStyle(SyntaxResolver.forFileName(target.getName()));
            pushRecent(target.getAbsolutePath());
            refreshTitle(b);
            updateStatus();
            saveSession();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Could not rename:\n" + ex.getMessage(), APP, JOptionPane.ERROR_MESSAGE);
        }
    }

    public void deleteCurrentFile() {
        Buffer b = current();
        if (b == null) return;
        File file = b.getFile();
        if (file == null) {
            JOptionPane.showMessageDialog(frame, "This document isn't saved to a file yet.", APP, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int choice = JOptionPane.showConfirmDialog(frame, "Move \"" + file.getName() + "\" to the Trash?",
                APP, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) return;
        boolean trashed = Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)
                && Desktop.getDesktop().moveToTrash(file);
        if (!trashed) {
            JOptionPane.showMessageDialog(frame, "Could not move the file to the Trash.", APP, JOptionPane.ERROR_MESSAGE);
            return;
        }
        b.setDirty(false);
        closeTab(b);
    }

    public void moveToOtherView() {
        Buffer b = current();
        if (b == null) return;
        boolean inMain = frame.tabs().indexOfComponent(b.getScroll()) >= 0;
        JTabbedPane from = inMain ? frame.tabs() : frame.secondaryTabs();
        JTabbedPane to = inMain ? frame.secondaryTabs() : frame.tabs();
        int idx = from.indexOfComponent(b.getScroll());
        if (idx >= 0) from.remove(idx);
        to.addTab(b.displayName(), b.getScroll());
        int moved = to.indexOfComponent(b.getScroll());
        to.setTabComponentAt(moved, tabComponent(b));
        frame.showSecondView(frame.secondaryTabs().getTabCount() > 0);
        if (moved >= 0) to.setSelectedIndex(moved);
        b.getArea().requestFocusInWindow();
        refreshTitle(b);
    }

    public void toggleBookmark() {
        RSyntaxTextArea area = activeArea();
        Gutter gutter = gutterFor(area);
        if (area == null || gutter == null) return;
        try {
            gutter.toggleBookmark(area.getLineOfOffset(area.getCaretPosition()));
        } catch (BadLocationException ignored) {
            // ignore
        }
    }

    public void nextBookmark() {
        gotoBookmark(true);
    }

    public void previousBookmark() {
        gotoBookmark(false);
    }

    private void gotoBookmark(boolean forward) {
        RSyntaxTextArea area = activeArea();
        Gutter gutter = gutterFor(area);
        if (area == null || gutter == null) return;
        GutterIconInfo[] marks = gutter.getBookmarks();
        if (marks.length == 0) return;
        int[] offsets = new int[marks.length];
        for (int i = 0; i < marks.length; i++) offsets[i] = marks[i].getMarkedOffset();
        Arrays.sort(offsets);
        int caret = area.getCaretPosition();
        area.setCaretPosition(forward ? firstAfter(offsets, caret) : lastBefore(offsets, caret));
    }

    private int firstAfter(int[] sorted, int caret) {
        for (int o : sorted) {
            if (o > caret) return o;
        }
        return sorted[0];
    }

    private int lastBefore(int[] sorted, int caret) {
        for (int i = sorted.length - 1; i >= 0; i--) {
            if (sorted[i] < caret) return sorted[i];
        }
        return sorted[sorted.length - 1];
    }

    private Gutter gutterFor(RSyntaxTextArea area) {
        if (area == null) return null;
        Component ancestor = SwingUtilities.getAncestorOfClass(RTextScrollPane.class, area);
        return (ancestor instanceof RTextScrollPane scroll) ? scroll.getGutter() : null;
    }

    private void enableBookmarks(RTextScrollPane scroll) {
        scroll.setIconRowHeaderEnabled(true);
        Gutter gutter = scroll.getGutter();
        gutter.setBookmarkingEnabled(true);
        gutter.setBookmarkIcon(BOOKMARK_ICON);
    }

    private static Icon makeBookmarkIcon() {
        BufferedImage img = new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x3B82F6));
        g.fillOval(1, 1, 10, 10);
        g.dispose();
        return new ImageIcon(img);
    }

    public void goToMatchingBracket() {
        RSyntaxTextArea area = activeArea();
        if (area == null) return;
        int match = matchingBracket(area.getText(), area.getCaretPosition());
        if (match >= 0) area.setCaretPosition(match);
    }

    private int matchingBracket(String text, int pos) {
        String open = "([{";
        String close = ")]}";
        if (pos < 0 || pos >= text.length()) return -1;
        char c = text.charAt(pos);
        int openIndex = open.indexOf(c);
        int closeIndex = close.indexOf(c);
        if (openIndex >= 0) {
            int depth = 1;
            for (int i = pos + 1; i < text.length(); i++) {
                char d = text.charAt(i);
                if (d == c) depth++;
                else if (d == close.charAt(openIndex) && --depth == 0) return i;
            }
        } else if (closeIndex >= 0) {
            int depth = 1;
            for (int i = pos - 1; i >= 0; i--) {
                char d = text.charAt(i);
                if (d == c) depth++;
                else if (d == open.charAt(closeIndex) && --depth == 0) return i;
            }
        }
        return -1;
    }

    // ---- file location ----
    public void revealInFinder() {
        Buffer b = current();
        if (b == null || b.getFile() == null) return;
        try {
            new ProcessBuilder("/usr/bin/open", "-R", b.getFile().getAbsolutePath()).start();
        } catch (IOException ignored) {
            // ignore
        }
    }

    public void copyPath() {
        Buffer b = current();
        if (b == null || b.getFile() == null) return;
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(b.getFile().getAbsolutePath()), null);
    }

    private File currentDir() {
        Buffer b = current();
        if (b != null && b.getFile() != null) return b.getFile().getParentFile();
        return FileSystemView.getFileSystemView().getHomeDirectory();
    }

    private void refreshTitle(Buffer b) {
        if (!buffers.contains(b)) return;
        String shown = (b.isDirty() ? "● " : "") + b.displayName();
        JTabbedPane pane = paneOf(b);
        int idx = pane.indexOfComponent(b.getScroll());
        if (idx >= 0) setTabLabel(pane, idx, shown);
        RSyntaxTextArea clone = splitClones.get(b);
        if (clone != null) {
            int ci = indexOfArea(frame.secondaryTabs(), clone);
            if (ci >= 0) setTabLabel(frame.secondaryTabs(), ci, shown);
        }
        if (b.equals(current())) frame.setTitle(shown);
    }

    private void setTabLabel(JTabbedPane pane, int idx, String shown) {
        Component comp = pane.getTabComponentAt(idx);
        if (comp instanceof JPanel panel && panel.getComponent(0) instanceof JLabel label) label.setText(shown);
        else pane.setTitleAt(idx, shown);
    }

    private void updateStatus() {
        Buffer b = current();
        if (b == null) {
            frame.setStatus(" ");
            return;
        }
        RSyntaxTextArea area = activeArea();
        if (area == null) area = b.getArea();
        try {
            int pos = area.getCaretPosition();
            int line = area.getLineOfOffset(pos);
            int col = pos - area.getLineStartOffset(line);
            int sel = area.getSelectionEnd() - area.getSelectionStart();
            frame.setStatus(String.format("Ln %d, Col %d   %s   len %d   %s   %s   %s",
                    line + 1, col + 1, sel > 0 ? "Sel " + sel : "",
                    area.getDocument().getLength(), b.getEol().name(),
                    TextCodec.labelFor(b.getCharset(), b.isBom()),
                    SyntaxResolver.nameFor(b.getSyntaxStyle())));
        } catch (BadLocationException ignored) {
            // ignore
        }
    }
}
