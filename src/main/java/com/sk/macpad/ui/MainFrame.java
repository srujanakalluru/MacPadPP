package com.sk.macpad.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.sk.macpad.control.EditorController;
import com.sk.macpad.model.Eol;
import com.sk.macpad.service.FileSearchService;
import com.sk.macpad.service.SyntaxResolver;
import com.sk.macpad.service.TextCodec;
import org.fife.ui.rtextarea.RTextArea;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * The Swing view: window, tab pane, status bar, menus and dialogs. All behaviour
 * is delegated to the {@link EditorController}.
 */
public class MainFrame extends JFrame {

    private final JTabbedPane tabs = new JTabbedPane();
    private final JTabbedPane secondaryTabs = new JTabbedPane();
    private final JSplitPane viewSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tabs, secondaryTabs);
    private Component center;
    private final JLabel status = new JLabel(" ");
    private EditorController controller;

    public MainFrame() {
        super("MacPad++");
        applyLookAndFeel(true);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setSize(1150, 780);
        setLocationRelativeTo(null);
        getRootPane().putClientProperty("apple.awt.fullscreenable", Boolean.TRUE);

        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        secondaryTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        viewSplit.setResizeWeight(0.5);
        center = tabs;
        add(center, BorderLayout.CENTER);

        status.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        add(status, BorderLayout.SOUTH);
    }

    public void setController(EditorController controller) {
        this.controller = controller;
        setJMenuBar(buildMenuBar());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                controller.quit();
            }
        });
        tabs.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeTabPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeTabPopup(e);
            }
        });
        new DropTarget(tabs, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent e) {
                try {
                    e.acceptDrop(DnDConstants.ACTION_COPY);
                    List<?> files = (List<?>) e.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    for (Object o : files) {
                        if (o instanceof File f && f.isFile()) controller.openFile(f);
                    }
                } catch (UnsupportedFlavorException | IOException ignored) {
                    // ignore
                }
            }
        });
    }

    public JTabbedPane tabs() {
        return tabs;
    }

    public JTabbedPane secondaryTabs() {
        return secondaryTabs;
    }

    public void showSecondView(boolean show) {
        remove(center);
        if (show) {
            viewSplit.setLeftComponent(tabs);
            viewSplit.setRightComponent(secondaryTabs);
            center = viewSplit;
        } else {
            center = tabs;
        }
        add(center, BorderLayout.CENTER);
        revalidate();
        repaint();
        if (show) SwingUtilities.invokeLater(() -> viewSplit.setDividerLocation(0.5));
    }

    public void setStatus(String text) {
        status.setText(text);
    }

    public void applyLookAndFeel(boolean dark) {
        try {
            UIManager.setLookAndFeel(dark ? new FlatDarkLaf() : new FlatLightLaf());
            SwingUtilities.updateComponentTreeUI(this);
        } catch (UnsupportedLookAndFeelException ignored) {
            // ignore
        }
    }

    public void showSearchResults(File root, List<FileSearchService.Match> matches) {
        DefaultListModel<FileSearchService.Match> model = new DefaultListModel<>();
        matches.forEach(model::addElement);
        JList<FileSearchService.Match> list = new JList<>(model);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    FileSearchService.Match m = list.getSelectedValue();
                    if (m != null) controller.openAt(m.file(), m.line());
                }
            }
        });
        JDialog dialog = new JDialog(this, matches.size() + " matches in " + root.getName(), false);
        dialog.add(new JScrollPane(list));
        dialog.setSize(700, 420);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // ---- tab context menu ----
    private void maybeTabPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        int idx = tabs.indexAtLocation(e.getX(), e.getY());
        if (idx < 0) return;
        tabs.setSelectedIndex(idx);
        JPopupMenu popup = new JPopupMenu();
        popup.add(item("Close", ev -> controller.closeCurrent()));
        popup.addSeparator();
        popup.add(item("Clone to Other View", ev -> controller.cloneToOtherView()));
        popup.add(item("Move to Other View", ev -> controller.moveToOtherView()));
        popup.addSeparator();
        popup.add(item("Copy Path", ev -> controller.copyPath()));
        popup.add(item("Reveal in Finder", ev -> controller.revealInFinder()));
        popup.show(tabs, e.getX(), e.getY());
    }

    // ---- find / replace ----
    private void showFindDialog(boolean replace) {
        JDialog dialog = new JDialog(this, replace ? "Replace" : "Find", false);
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        JTextField find = new JTextField(28);
        JTextField repl = new JTextField(28);
        JCheckBox matchCase = new JCheckBox("Match case");
        JCheckBox whole = new JCheckBox("Whole word");
        JCheckBox regex = new JCheckBox("Regex");
        JCheckBox extended = new JCheckBox("Extended (\\n, \\t)");
        JCheckBox searchUp = new JCheckBox("Search up");

        c.gridx = 0;
        c.gridy = 0;
        panel.add(new JLabel("Find:"), c);
        c.gridx = 1;
        c.gridy = 0;
        panel.add(find, c);
        if (replace) {
            c.gridx = 0;
            c.gridy = 1;
            panel.add(new JLabel("Replace:"), c);
            c.gridx = 1;
            c.gridy = 1;
            panel.add(repl, c);
        }
        JPanel opts = new JPanel(new FlowLayout(FlowLayout.LEFT));
        opts.add(matchCase);
        opts.add(whole);
        opts.add(regex);
        opts.add(extended);
        opts.add(searchUp);
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        panel.add(opts, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton findNext = new JButton("Find Next");
        JButton count = new JButton("Count");
        JButton markAll = new JButton("Mark All");
        JButton clearMarks = new JButton("Clear Marks");
        buttons.add(count);
        buttons.add(markAll);
        buttons.add(clearMarks);
        buttons.add(findNext);
        findNext.addActionListener(e -> controller.find(query(find, extended, regex),
                regex.isSelected(), matchCase.isSelected(), whole.isSelected(), !searchUp.isSelected()));
        find.addActionListener(e -> controller.find(query(find, extended, regex),
                regex.isSelected(), matchCase.isSelected(), whole.isSelected(), !searchUp.isSelected()));
        count.addActionListener(e -> controller.markAll(query(find, extended, regex),
                regex.isSelected(), matchCase.isSelected(), whole.isSelected()));
        markAll.addActionListener(e -> controller.markAll(query(find, extended, regex),
                regex.isSelected(), matchCase.isSelected(), whole.isSelected()));
        clearMarks.addActionListener(e -> controller.clearMarks());
        if (replace) {
            JButton replaceOne = new JButton("Replace");
            JButton replaceAll = new JButton("Replace All");
            buttons.add(replaceOne);
            buttons.add(replaceAll);
            replaceOne.addActionListener(e -> controller.replaceNext(query(find, extended, regex),
                    regex.isSelected(), matchCase.isSelected(), whole.isSelected(), query(repl, extended, regex)));
            replaceAll.addActionListener(e -> controller.replaceAll(query(find, extended, regex),
                    regex.isSelected(), matchCase.isSelected(), whole.isSelected(), query(repl, extended, regex)));
        }
        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 2;
        panel.add(buttons, c);

        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private String query(JTextField field, JCheckBox extended, JCheckBox regex) {
        String s = field.getText();
        return (extended.isSelected() && !regex.isSelected()) ? unescape(s) : s;
    }

    private String unescape(String s) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> out.append('\r');
                    case '\\' -> out.append('\\');
                    default -> out.append('\\').append(next);
                }
                i += 2;
            } else {
                out.append(ch);
                i++;
            }
        }
        return out.toString();
    }

    // ---- menus ----
    private JMenuBar buildMenuBar() {
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        int shift = InputEvent.SHIFT_DOWN_MASK;
        int alt = InputEvent.ALT_DOWN_MASK;
        JMenuBar bar = new JMenuBar();
        bar.add(fileMenu(mask, shift));
        bar.add(editMenu(mask, shift, alt));
        bar.add(searchMenu(mask, shift));
        bar.add(viewMenu(mask));
        bar.add(languageMenu(mask, shift));
        bar.add(encodingMenu());
        JMenu help = new JMenu("Help");
        help.add(item("About MacPad++", e -> controller.showAbout()));
        help.add(item("Keyboard Shortcuts", e -> controller.showShortcuts()));
        bar.add(help);
        return bar;
    }

    private JMenu fileMenu(int mask, int shift) {
        JMenu file = new JMenu("File");
        file.add(item("New", 'N', mask, e -> controller.newBuffer()));
        file.add(item("Open…", 'O', mask, e -> controller.openDialog()));
        JMenu recent = new JMenu("Open Recent");
        recent.addMenuListener(new RecentMenuListener(recent));
        file.add(recent);
        file.add(item("Reload from Disk", e -> controller.reloadFromDisk()));
        file.addSeparator();
        file.add(item("Save", 'S', mask, e -> controller.saveCurrent()));
        file.add(item("Save As…", 'S', mask | shift, e -> controller.saveCurrentAs()));
        file.add(item("Save All", e -> controller.saveAll()));
        file.addSeparator();
        file.add(item("Print…", 'P', mask, e -> controller.printCurrent()));
        file.addSeparator();
        file.add(item("Reveal in Finder", e -> controller.revealInFinder()));
        file.add(item("Copy Full Path", e -> controller.copyPath()));
        file.add(item("Rename…", e -> controller.renameCurrentFile()));
        file.add(item("Move to Trash", e -> controller.deleteCurrentFile()));
        file.addSeparator();
        file.add(item("Close Tab", 'W', mask, e -> controller.closeCurrent()));
        return file;
    }

    private JMenu editMenu(int mask, int shift, int alt) {
        JMenu edit = new JMenu("Edit");
        edit.add(item("Undo", 'Z', mask, e -> controller.undo()));
        edit.add(item("Redo", 'Z', mask | shift, e -> controller.redo()));
        edit.addSeparator();
        edit.add(actionItem("Cut", 'X', mask, RTextArea.getAction(RTextArea.CUT_ACTION)));
        edit.add(actionItem("Copy", 'C', mask, RTextArea.getAction(RTextArea.COPY_ACTION)));
        edit.add(actionItem("Paste", 'V', mask, RTextArea.getAction(RTextArea.PASTE_ACTION)));
        edit.add(item("Select All", 'A', mask, e -> controller.selectAll()));
        edit.addSeparator();

        JMenu lines = new JMenu("Line Operations");
        lines.add(item("Duplicate Line", 'D', mask, e -> controller.duplicateLine()));
        lines.add(item("Delete Line", 'K', mask | shift, e -> controller.deleteLine()));
        lines.add(itemKs("Move Line Up", KeyStroke.getKeyStroke(KeyEvent.VK_UP, alt), e -> controller.moveLineUp()));
        lines.add(itemKs("Move Line Down", KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, alt), e -> controller.moveLineDown()));
        lines.add(item("Join Lines", 'J', mask, e -> controller.joinLines()));
        lines.addSeparator();
        lines.add(item("Sort Lines Ascending", e -> controller.sortLinesAscending()));
        lines.add(item("Sort Lines Descending", e -> controller.sortLinesDescending()));
        lines.add(item("Remove Duplicate Lines", e -> controller.removeDuplicateLines()));
        lines.add(item("Trim Trailing Whitespace", e -> controller.trimTrailingWhitespace()));
        edit.add(lines);

        JMenu convertCase = new JMenu("Convert Case");
        convertCase.add(item("UPPERCASE", e -> controller.transformUpper()));
        convertCase.add(item("lowercase", e -> controller.transformLower()));
        edit.add(convertCase);

        JMenu indent = new JMenu("Convert Indentation");
        indent.add(item("Tabs to Spaces", e -> controller.indentToSpaces()));
        indent.add(item("Spaces to Tabs", e -> controller.indentToTabs()));
        edit.add(indent);

        edit.addSeparator();
        edit.add(item("Toggle Comment", '/', mask, e -> controller.toggleComment()));
        return edit;
    }

    private JMenu searchMenu(int mask, int shift) {
        JMenu search = new JMenu("Search");
        search.add(item("Find…", 'F', mask, e -> showFindDialog(false)));
        search.add(item("Replace…", 'F', mask | shift, e -> showFindDialog(true)));
        search.addSeparator();
        search.add(item("Find in Files…", e -> controller.findInFiles()));
        search.add(item("Go to Line…", 'L', mask, e -> controller.goToLine()));
        search.add(item("Go to Matching Bracket", 'B', mask, e -> controller.goToMatchingBracket()));
        search.add(item("Clear Marks", e -> controller.clearMarks()));
        search.addSeparator();
        JMenu bookmarks = new JMenu("Bookmarks");
        bookmarks.add(itemKs("Toggle Bookmark", KeyStroke.getKeyStroke(KeyEvent.VK_F2, mask), e -> controller.toggleBookmark()));
        bookmarks.add(itemKs("Next Bookmark", KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), e -> controller.nextBookmark()));
        bookmarks.add(itemKs("Previous Bookmark", KeyStroke.getKeyStroke(KeyEvent.VK_F2, shift), e -> controller.previousBookmark()));
        search.add(bookmarks);
        return search;
    }

    private JMenu viewMenu(int mask) {
        JMenu view = new JMenu("View");
        view.add(checkItem("Word Wrap", controller.isWrap(), e -> controller.toggleWrap()));
        view.add(checkItem("Show Whitespace", controller.isShowWhitespace(), e -> controller.toggleWhitespace()));
        view.add(checkItem("Show End of Line", controller.isShowEol(), e -> controller.toggleEol()));
        view.add(checkItem("Show Indent Guides", controller.isIndentGuides(), e -> controller.toggleIndentGuides()));
        view.addSeparator();
        view.add(item("Clone to Other View", '\\', mask, e -> controller.cloneToOtherView()));
        view.add(item("Move to Other View", e -> controller.moveToOtherView()));
        view.addSeparator();
        view.add(item("Zoom In", '=', mask, e -> controller.zoom(1)));
        view.add(item("Zoom Out", '-', mask, e -> controller.zoom(-1)));
        view.add(item("Reset Zoom", '0', mask, e -> controller.zoom(0)));
        view.addSeparator();
        view.add(item("Toggle Dark/Light Theme", e -> controller.toggleTheme()));
        return view;
    }

    private JMenu languageMenu(int mask, int shift) {
        JMenu language = new JMenu("Language");
        language.add(item("Set Language…", 'L', mask | shift, e -> controller.chooseLanguage()));
        language.addSeparator();
        for (SyntaxResolver.Lang lang : SyntaxResolver.CATALOG) {
            language.add(item(lang.name(), e -> controller.setLanguage(lang.style())));
        }
        return language;
    }

    private JMenu encodingMenu() {
        JMenu encoding = new JMenu("Encoding");
        JMenu convert = new JMenu("Convert To");
        for (String enc : TextCodec.ENCODINGS) {
            convert.add(item(enc, e -> controller.setEncoding(enc)));
        }
        encoding.add(convert);
        JMenu lineEndings = new JMenu("Line Endings");
        for (Eol eol : Eol.values()) {
            lineEndings.add(item(eol.name(), e -> controller.setEol(eol)));
        }
        encoding.add(lineEndings);
        return encoding;
    }

    private class RecentMenuListener implements javax.swing.event.MenuListener {
        private final JMenu menu;

        RecentMenuListener(JMenu menu) {
            this.menu = menu;
        }

        @Override
        public void menuSelected(javax.swing.event.MenuEvent e) {
            menu.removeAll();
            List<String> paths = controller.recentFiles();
            for (String path : paths) menu.add(item(path, ev -> controller.openFile(new File(path))));
            if (paths.isEmpty()) {
                JMenuItem empty = new JMenuItem("(empty)");
                empty.setEnabled(false);
                menu.add(empty);
            } else {
                menu.add(item("Clear Recent", ev -> controller.clearRecent()));
            }
        }

        @Override
        public void menuDeselected(javax.swing.event.MenuEvent e) { /* no-op */ }

        @Override
        public void menuCanceled(javax.swing.event.MenuEvent e) { /* no-op */ }
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

    private JMenuItem itemKs(String label, KeyStroke ks, ActionListener a) {
        JMenuItem mi = item(label, a);
        mi.setAccelerator(ks);
        return mi;
    }

    private JCheckBoxMenuItem checkItem(String label, boolean selected, ActionListener a) {
        JCheckBoxMenuItem mi = new JCheckBoxMenuItem(label, selected);
        mi.addActionListener(a);
        return mi;
    }

    private JMenuItem actionItem(String label, char key, int mask, Action a) {
        JMenuItem mi = new JMenuItem(a);
        mi.setText(label);
        mi.setAccelerator(KeyStroke.getKeyStroke(key, mask));
        return mi;
    }
}
