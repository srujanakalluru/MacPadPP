package com.sk.macpad.service;

import com.sk.macpad.model.Eol;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Encoding and line-ending logic: detect an encoding from raw bytes, decode to
 * text (normalized to {@code \n}), and re-encode with a chosen charset, BOM and
 * line ending. Pure logic - no UI.
 */
public final class TextCodec {

    private TextCodec() {
    }

    /**
     * Encoding labels offered in the Encoding menu.
     */
    public static final List<String> ENCODINGS = List.of(
            "UTF-8", "UTF-8 BOM", "UTF-16LE", "UTF-16BE", "ISO-8859-1", "windows-1252");

    /**
     * Result of decoding: normalized text plus the detected encoding and EOL.
     */
    public record Decoded(String text, Charset charset, boolean bom, Eol eol) {
    }

    public static Charset charsetForLabel(String label) {
        return Charset.forName(label.replace(" BOM", ""));
    }

    public static boolean bomForLabel(String label) {
        return label.endsWith("BOM");
    }

    public static String labelFor(Charset charset, boolean bom) {
        if (charset.equals(StandardCharsets.UTF_8)) return bom ? "UTF-8 BOM" : "UTF-8";
        if (charset.equals(StandardCharsets.UTF_16LE)) return "UTF-16LE";
        if (charset.equals(StandardCharsets.UTF_16BE)) return "UTF-16BE";
        return charset.name();
    }

    public static Decoded decode(byte[] b) {
        Charset charset;
        boolean bom = false;
        int skip = 0;
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) {
            charset = StandardCharsets.UTF_8;
            bom = true;
            skip = 3;
        } else if (b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) {
            charset = StandardCharsets.UTF_16LE;
            bom = true;
            skip = 2;
        } else if (b.length >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF) {
            charset = StandardCharsets.UTF_16BE;
            bom = true;
            skip = 2;
        } else {
            charset = looksUtf8(b) ? StandardCharsets.UTF_8 : Charset.forName("windows-1252");
        }
        String text = new String(b, skip, b.length - skip, charset);
        Eol eol = detectEol(text);
        text = text.replace("\r\n", "\n").replace("\r", "\n");
        return new Decoded(text, charset, bom, eol);
    }

    public static byte[] encode(String text, Charset charset, boolean bom, Eol eol) {
        String s = text;
        if (eol == Eol.CRLF) s = s.replace("\n", "\r\n");
        else if (eol == Eol.CR) s = s.replace("\n", "\r");
        byte[] body = s.getBytes(charset);
        if (!bom) return body;

        byte[] mark;
        if (charset.equals(StandardCharsets.UTF_8)) mark = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        else if (charset.equals(StandardCharsets.UTF_16LE)) mark = new byte[]{(byte) 0xFF, (byte) 0xFE};
        else if (charset.equals(StandardCharsets.UTF_16BE)) mark = new byte[]{(byte) 0xFE, (byte) 0xFF};
        else return body;

        byte[] out = new byte[mark.length + body.length];
        System.arraycopy(mark, 0, out, 0, mark.length);
        System.arraycopy(body, 0, out, mark.length, body.length);
        return out;
    }

    private static Eol detectEol(String text) {
        int crlf = 0;
        int cr = 0;
        int lf = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\r') {
                boolean followedByLf = i + 1 < text.length() && text.charAt(i + 1) == '\n';
                if (followedByLf) {
                    crlf++;
                    i++;
                } else {
                    cr++;
                }
            } else if (c == '\n') {
                lf++;
            }
            i++;
        }
        if (crlf >= lf && crlf >= cr && crlf > 0) return Eol.CRLF;
        if (cr > lf && cr > crlf) return Eol.CR;
        return Eol.LF;
    }

    private static boolean looksUtf8(byte[] b) {
        int limit = Math.min(b.length, 65536);
        int i = 0;
        while (i < limit) {
            int cont = continuationBytes(b[i] & 0xFF);
            if (cont < 0 || !validContinuation(b, i, limit, cont)) return false;
            i += cont + 1;
        }
        return true;
    }

    private static int continuationBytes(int leadByte) {
        if (leadByte < 0x80) return 0;
        if (leadByte >= 0xC2 && leadByte <= 0xDF) return 1;
        if (leadByte >= 0xE0 && leadByte <= 0xEF) return 2;
        if (leadByte >= 0xF0 && leadByte <= 0xF4) return 3;
        return -1;
    }

    private static boolean validContinuation(byte[] b, int start, int limit, int count) {
        if (start + count >= limit) return false;
        for (int k = 1; k <= count; k++) {
            if ((b[start + k] & 0xC0) != 0x80) return false;
        }
        return true;
    }
}
