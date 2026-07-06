package com.sk.macpad.service;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import java.util.List;

/**
 * Maps file names to RSyntaxTextArea syntax styles and exposes the language
 * catalog shown in the Language menu.
 */
public final class SyntaxResolver {

    private SyntaxResolver() { }

    /** A selectable language: display name and its RSyntaxTextArea style constant. */
    public record Lang(String name, String style) { }

    public static final List<Lang> CATALOG = List.of(
            new Lang("Plain Text", SyntaxConstants.SYNTAX_STYLE_NONE),
            new Lang("C", SyntaxConstants.SYNTAX_STYLE_C),
            new Lang("C++", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS),
            new Lang("C#", SyntaxConstants.SYNTAX_STYLE_CSHARP),
            new Lang("CSS", SyntaxConstants.SYNTAX_STYLE_CSS),
            new Lang("Clojure", SyntaxConstants.SYNTAX_STYLE_CLOJURE),
            new Lang("Dart", SyntaxConstants.SYNTAX_STYLE_DART),
            new Lang("Dockerfile", SyntaxConstants.SYNTAX_STYLE_DOCKERFILE),
            new Lang("Groovy", SyntaxConstants.SYNTAX_STYLE_GROOVY),
            new Lang("HTML", SyntaxConstants.SYNTAX_STYLE_HTML),
            new Lang("INI", SyntaxConstants.SYNTAX_STYLE_INI),
            new Lang("JSON", SyntaxConstants.SYNTAX_STYLE_JSON),
            new Lang("Java", SyntaxConstants.SYNTAX_STYLE_JAVA),
            new Lang("JavaScript", SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT),
            new Lang("Kotlin", SyntaxConstants.SYNTAX_STYLE_KOTLIN),
            new Lang("LaTeX", SyntaxConstants.SYNTAX_STYLE_LATEX),
            new Lang("Less", SyntaxConstants.SYNTAX_STYLE_LESS),
            new Lang("Lua", SyntaxConstants.SYNTAX_STYLE_LUA),
            new Lang("Makefile", SyntaxConstants.SYNTAX_STYLE_MAKEFILE),
            new Lang("Markdown", SyntaxConstants.SYNTAX_STYLE_MARKDOWN),
            new Lang("PHP", SyntaxConstants.SYNTAX_STYLE_PHP),
            new Lang("Perl", SyntaxConstants.SYNTAX_STYLE_PERL),
            new Lang("Properties", SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE),
            new Lang("Python", SyntaxConstants.SYNTAX_STYLE_PYTHON),
            new Lang("Ruby", SyntaxConstants.SYNTAX_STYLE_RUBY),
            new Lang("SQL", SyntaxConstants.SYNTAX_STYLE_SQL),
            new Lang("Scala", SyntaxConstants.SYNTAX_STYLE_SCALA),
            new Lang("Shell", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL),
            new Lang("TypeScript", SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT),
            new Lang("Windows Batch", SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH),
            new Lang("XML", SyntaxConstants.SYNTAX_STYLE_XML),
            new Lang("YAML", SyntaxConstants.SYNTAX_STYLE_YAML),
            new Lang("ActionScript", SyntaxConstants.SYNTAX_STYLE_ACTIONSCRIPT),
            new Lang("Assembly (x86)", SyntaxConstants.SYNTAX_STYLE_ASSEMBLER_X86),
            new Lang("D", SyntaxConstants.SYNTAX_STYLE_D),
            new Lang("Delphi", SyntaxConstants.SYNTAX_STYLE_DELPHI),
            new Lang("Fortran", SyntaxConstants.SYNTAX_STYLE_FORTRAN),
            new Lang("Handlebars", SyntaxConstants.SYNTAX_STYLE_HANDLEBARS),
            new Lang("JSP", SyntaxConstants.SYNTAX_STYLE_JSP),
            new Lang("Lisp", SyntaxConstants.SYNTAX_STYLE_LISP),
            new Lang("MXML", SyntaxConstants.SYNTAX_STYLE_MXML),
            new Lang("NSIS", SyntaxConstants.SYNTAX_STYLE_NSIS),
            new Lang("SAS", SyntaxConstants.SYNTAX_STYLE_SAS),
            new Lang("Tcl", SyntaxConstants.SYNTAX_STYLE_TCL),
            new Lang("Visual Basic", SyntaxConstants.SYNTAX_STYLE_VISUAL_BASIC));

    public static String nameFor(String style) {
        for (Lang l : CATALOG) if (l.style().equals(style)) return l.name();
        return "Plain Text";
    }

    public static String forFileName(String name) {
        String n = name.toLowerCase();
        int dot = n.lastIndexOf('.');
        String ext = dot >= 0 ? n.substring(dot + 1) : n;
        return switch (ext) {
            case "c", "h" -> SyntaxConstants.SYNTAX_STYLE_C;
            case "cpp", "cc", "cxx", "hpp" -> SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS;
            case "cs" -> SyntaxConstants.SYNTAX_STYLE_CSHARP;
            case "css" -> SyntaxConstants.SYNTAX_STYLE_CSS;
            case "clj" -> SyntaxConstants.SYNTAX_STYLE_CLOJURE;
            case "dart" -> SyntaxConstants.SYNTAX_STYLE_DART;
            case "groovy", "gradle" -> SyntaxConstants.SYNTAX_STYLE_GROOVY;
            case "htm", "html" -> SyntaxConstants.SYNTAX_STYLE_HTML;
            case "ini", "cfg", "conf" -> SyntaxConstants.SYNTAX_STYLE_INI;
            case "json" -> SyntaxConstants.SYNTAX_STYLE_JSON;
            case "java" -> SyntaxConstants.SYNTAX_STYLE_JAVA;
            case "js", "mjs", "cjs" -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
            case "kt", "kts" -> SyntaxConstants.SYNTAX_STYLE_KOTLIN;
            case "tex" -> SyntaxConstants.SYNTAX_STYLE_LATEX;
            case "less" -> SyntaxConstants.SYNTAX_STYLE_LESS;
            case "lua" -> SyntaxConstants.SYNTAX_STYLE_LUA;
            case "md", "markdown" -> SyntaxConstants.SYNTAX_STYLE_MARKDOWN;
            case "php" -> SyntaxConstants.SYNTAX_STYLE_PHP;
            case "pl", "pm" -> SyntaxConstants.SYNTAX_STYLE_PERL;
            case "properties" -> SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE;
            case "py", "pyw" -> SyntaxConstants.SYNTAX_STYLE_PYTHON;
            case "rb" -> SyntaxConstants.SYNTAX_STYLE_RUBY;
            case "sql" -> SyntaxConstants.SYNTAX_STYLE_SQL;
            case "scala", "sc" -> SyntaxConstants.SYNTAX_STYLE_SCALA;
            case "sh", "bash", "zsh" -> SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL;
            case "ts", "tsx" -> SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT;
            case "bat", "cmd" -> SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH;
            case "xml", "xsd", "xsl", "pom" -> SyntaxConstants.SYNTAX_STYLE_XML;
            case "yaml", "yml" -> SyntaxConstants.SYNTAX_STYLE_YAML;
            case "as" -> SyntaxConstants.SYNTAX_STYLE_ACTIONSCRIPT;
            case "asm", "s" -> SyntaxConstants.SYNTAX_STYLE_ASSEMBLER_X86;
            case "d" -> SyntaxConstants.SYNTAX_STYLE_D;
            case "pas", "dpr" -> SyntaxConstants.SYNTAX_STYLE_DELPHI;
            case "f", "f90", "f95", "for" -> SyntaxConstants.SYNTAX_STYLE_FORTRAN;
            case "hbs" -> SyntaxConstants.SYNTAX_STYLE_HANDLEBARS;
            case "jsp" -> SyntaxConstants.SYNTAX_STYLE_JSP;
            case "lisp", "lsp", "el" -> SyntaxConstants.SYNTAX_STYLE_LISP;
            case "mxml" -> SyntaxConstants.SYNTAX_STYLE_MXML;
            case "nsi" -> SyntaxConstants.SYNTAX_STYLE_NSIS;
            case "sas" -> SyntaxConstants.SYNTAX_STYLE_SAS;
            case "tcl" -> SyntaxConstants.SYNTAX_STYLE_TCL;
            case "vb", "vbs" -> SyntaxConstants.SYNTAX_STYLE_VISUAL_BASIC;
            default -> {
                if (n.equals("makefile")) yield SyntaxConstants.SYNTAX_STYLE_MAKEFILE;
                if (n.equals("dockerfile")) yield SyntaxConstants.SYNTAX_STYLE_DOCKERFILE;
                yield SyntaxConstants.SYNTAX_STYLE_NONE;
            }
        };
    }
}
