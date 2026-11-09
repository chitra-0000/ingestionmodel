package com.bank.ingestion.service.export;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal PDF 1.4 writer with no third-party library: A4 pages, the standard Helvetica / Helvetica-Bold /
 * Courier fonts (built into every PDF reader, nothing embedded), text, lines and filled rectangles.
 * Enough for a record sheet. Characters outside Latin-1 are written as '?'.
 */
final class SimplePdf {

    static final float WIDTH = 595.28f;
    static final float HEIGHT = 841.89f;
    static final String REGULAR = "F1";
    static final String BOLD = "F2";
    static final String MONO = "F3";

    /** Approximate Helvetica widths (1/1000 em) for 32..126; others count as 556. */
    private static final int[] HELV = {278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278, 556, 556,
            556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556, 1015, 667, 667, 722, 722, 667, 611, 778, 722, 278,
            500, 667, 556, 833, 722, 778, 667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556, 333, 556,
            556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556, 556, 556, 333, 500, 278, 556, 500, 722, 500, 500,
            500, 334, 260, 334, 584};

    private final List<StringBuilder> pages = new ArrayList<>();
    private StringBuilder page;

    SimplePdf() {
        newPage();
    }

    void newPage() {
        page = new StringBuilder(4096);
        pages.add(page);
    }

    /** Text with its baseline at (x, y) from the bottom-left corner. rgb in 0..1. */
    void text(float x, float y, String font, float size, float[] rgb, String s) {
        page.append(String.format(java.util.Locale.ROOT, "BT %.3f %.3f %.3f rg /%s %.1f Tf %.2f %.2f Td (%s) Tj ET%n",
                rgb[0], rgb[1], rgb[2], font, size, x, y, escape(s)));
    }

    void rect(float x, float y, float w, float h, float[] rgb) {
        page.append(String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f rg %.2f %.2f %.2f %.2f re f%n",
                rgb[0], rgb[1], rgb[2], x, y, w, h));
    }

    void line(float x1, float y1, float x2, float y2, float width, float[] rgb) {
        page.append(String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f RG %.2f w %.2f %.2f m %.2f %.2f l S%n",
                rgb[0], rgb[1], rgb[2], width, x1, y1, x2, y2));
    }

    /** Width of a text in points (Courier is fixed at 600). */
    static float width(String s, String font, float size) {
        float w = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (MONO.equals(font)) {
                w += 600;
            } else {
                int base = c >= 32 && c <= 126 ? HELV[c - 32] : 556;
                w += BOLD.equals(font) ? base * 1.05f : base;
            }
        }
        return w * size / 1000f;
    }

    /** Splits a text into lines no wider than maxWidth. */
    static List<String> wrap(String s, String font, float size, float maxWidth) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : (s == null ? "" : s).split(" ")) {
            String candidate = cur.length() == 0 ? word : cur + " " + word;
            if (width(candidate, font, size) <= maxWidth || cur.length() == 0) {
                cur.setLength(0);
                cur.append(candidate);
                while (width(cur.toString(), font, size) > maxWidth && cur.length() > 1) {
                    int cut = cur.length() - 1;
                    while (cut > 1 && width(cur.substring(0, cut), font, size) > maxWidth) {
                        cut--;
                    }
                    out.add(cur.substring(0, cut));
                    cur.delete(0, cut);
                }
            } else {
                out.add(cur.toString());
                cur.setLength(0);
                cur.append(word);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == ')' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c == '–' || c == '—') {
                sb.append('-');
            } else if (c < 32 || c > 255) {
                sb.append('?');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** The finished file. */
    byte[] bytes(String title) {
        List<String> objects = new ArrayList<>();
        int n = pages.size();
        // 1 catalog, 2 pages, 3-5 fonts, 6 info, then per page: page object + content stream
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < n; i++) {
            kids.append(7 + i * 2).append(" 0 R ");
        }
        objects.add("<< /Type /Pages /Kids [" + kids + "] /Count " + n + " >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>");
        objects.add("<< /Title (" + escape(title) + ") /Creator (ListHub) /Producer (ListHub) >>");
        for (int i = 0; i < n; i++) {
            objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + WIDTH + " " + HEIGHT + "] "
                    + "/Resources << /Font << /F1 3 0 R /F2 4 0 R /F3 5 0 R >> >> /Contents " + (8 + i * 2) + " 0 R >>");
            byte[] content = pages.get(i).toString().getBytes(StandardCharsets.ISO_8859_1);
            objects.add("<< /Length " + content.length + " >>\nstream\n" + new String(content, StandardCharsets.ISO_8859_1)
                    + "\nendstream");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(out, "%PDF-1.4\n%âãÏÓ\n");
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
        }
        int xref = out.size();
        StringBuilder sb = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int off : offsets) {
            sb.append(String.format("%010d 00000 n \n", off));
        }
        sb.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R /Info 6 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        write(out, sb.toString());
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String s) {
        byte[] b = s.getBytes(StandardCharsets.ISO_8859_1);
        out.write(b, 0, b.length);
    }
}
