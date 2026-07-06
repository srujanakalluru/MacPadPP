package com.sk.macpad.model;

import lombok.Getter;
import lombok.Setter;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * One open document: the editor component plus its file and edit state.
 */
@Getter
public class Buffer {

    private final RSyntaxTextArea area;
    private final RTextScrollPane scroll;

    @Setter
    private File file;
    @Setter
    private Charset charset = StandardCharsets.UTF_8;
    @Setter
    private boolean bom;
    @Setter
    private Eol eol = Eol.LF;
    private String syntaxStyle = SyntaxConstants.SYNTAX_STYLE_NONE;
    @Setter
    private boolean dirty;
    @Setter
    private String title;

    public Buffer(String title, RSyntaxTextArea area) {
        this.title = title;
        this.area = area;
        this.scroll = new RTextScrollPane(area);
    }

    public void setSyntaxStyle(String syntaxStyle) {
        this.syntaxStyle = syntaxStyle;
        area.setSyntaxEditingStyle(syntaxStyle);
    }

    public String displayName() {
        return file != null ? file.getName() : title;
    }
}
