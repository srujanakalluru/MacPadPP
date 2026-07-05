package com.sk.macpad.model;

/** A line-ending style and its byte sequence. */
public enum Eol {
    LF("\n"),
    CRLF("\r\n"),
    CR("\r");

    private final String sequence;

    Eol(String sequence) {
        this.sequence = sequence;
    }

    public String sequence() {
        return sequence;
    }
}
