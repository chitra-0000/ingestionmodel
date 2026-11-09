package com.bank.ingestion.service.export;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Record sheet PDF: the record as valid in a version, and what changed in that version.
 * Written with SimplePdf (no third-party PDF library).
 */
@Service
public class PdfService {

    private static final float[] NAVY = rgb(0x16, 0x23, 0x3A);
    private static final float[] BLUE = rgb(0x1E, 0x4E, 0x8C);
    private static final float[] GREY = rgb(0x5B, 0x65, 0x75);
    private static final float[] LINE = rgb(0xDC, 0xE0, 0xE5);
    private static final float[] HEAD = rgb(0xEE, 0xF1, 0xF5);
    private static final float[] RED = rgb(0x8C, 0x2B, 0x22);
    private static final float[] GREEN = rgb(0x24, 0x57, 0x3F);
    private static final float LEFT = 48;
    private static final float RIGHT = SimplePdf.WIDTH - 48;
    private static final float TOP = SimplePdf.HEIGHT - 48;
    private static final float BOTTOM = 60;

    private static float[] rgb(int r, int g, int b) {
        return new float[]{r / 255f, g / 255f, b / 255f};
    }

    /** Page state while writing. */
    private static final class Cursor {
        final SimplePdf pdf = new SimplePdf();
        float y = TOP;

        void need(float h) {
            if (y - h < BOTTOM) {
                pdf.newPage();
                y = TOP;
            }
        }
    }

    /**
     * @param record  rowView of RecordService (attributes grouped)
     * @param change  changeView of the version's change for this record, may be null
     */
    @SuppressWarnings("unchecked")
    public byte[] recordSheet(Map<String, Object> record, Map<String, Object> change, Integer version) {
        Cursor c = new Cursor();
        String rglId = String.valueOf(record.get("rglId"));

        c.pdf.text(LEFT, c.y - 12, SimplePdf.BOLD, 15, NAVY, "ListHub record sheet");
        String idText = rglId;
        c.pdf.text(RIGHT - SimplePdf.width(idText, SimplePdf.MONO, 11), c.y - 12, SimplePdf.MONO, 11, NAVY, idText);
        String sub = record.get("listCode") + " " + record.get("listName") + (version == null ? "" : " - version " + version);
        c.pdf.text(LEFT, c.y - 28, SimplePdf.REGULAR, 9, GREY, sub);
        if (change != null) {
            String t = String.valueOf(change.get("type"));
            c.pdf.text(RIGHT - SimplePdf.width(t, SimplePdf.REGULAR, 9), c.y - 28, SimplePdf.REGULAR, 9, GREY, t);
        }
        c.y -= 38;
        c.pdf.line(LEFT, c.y, RIGHT, c.y, 1.5f, NAVY);
        c.y -= 30;
        for (String l : SimplePdf.wrap(String.valueOf(record.get("primaryName")), SimplePdf.BOLD, 18, RIGHT - LEFT)) {
            c.need(24);
            c.pdf.text(LEFT, c.y, SimplePdf.BOLD, 18, NAVY, l);
            c.y -= 24;
        }
        c.y -= 6;

        heading(c, "RECORD");
        row(c, "Provider ID", record.get("externalRef"));
        row(c, "Type", record.get("recordType"));
        row(c, "Valid from version", record.get("validFrom"));
        if (record.get("validTo") != null) {
            row(c, "Valid until version", record.get("validTo"));
        }
        for (Map<String, Object> a : (List<Map<String, Object>>) record.get("attributes")) {
            row(c, (String) a.get("label"), String.join("; ", (List<String>) a.get("values")));
        }

        if (change != null && change.get("detail") instanceof Map) {
            Object changes = ((Map<String, Object>) change.get("detail")).get("changes");
            if (changes instanceof List && !((List<?>) changes).isEmpty()) {
                c.y -= 14;
                heading(c, "WHAT CHANGED IN THIS VERSION");
                changeTable(c, (List<Map<String, Object>>) changes);
            }
        }
        if (record.get("flags") != null) {
            c.y -= 10;
            c.need(14);
            c.pdf.text(LEFT, c.y, SimplePdf.REGULAR, 9, GREY, "Quality flags: " + record.get("flags"));
            c.y -= 14;
        }
        c.y -= 16;
        c.need(14);
        c.pdf.text(LEFT, c.y, SimplePdf.REGULAR, 8.5f, GREY, "Batch " + record.get("batchId") + ", raw record "
                + record.get("rawSeq") + ". Generated " + LocalDateTime.now().withNano(0) + " by ListHub.");
        return c.pdf.bytes("Record " + rglId);
    }

    private static void heading(Cursor c, String text) {
        c.need(20);
        c.pdf.text(LEFT, c.y, SimplePdf.BOLD, 9, BLUE, text);
        c.y -= 10;
    }

    private static void row(Cursor c, String label, Object value) {
        float labelW = 140;
        List<String> lines = SimplePdf.wrap(value == null ? "-" : String.valueOf(value), SimplePdf.REGULAR, 10,
                RIGHT - LEFT - labelW - 10);
        float h = 6 + lines.size() * 13 + 4;
        c.need(h);
        float y = c.y - 15;
        c.pdf.text(LEFT + 4, y, SimplePdf.REGULAR, 9, GREY, label);
        for (String l : lines) {
            c.pdf.text(LEFT + labelW, y, SimplePdf.REGULAR, 10, NAVY, l);
            y -= 13;
        }
        c.y -= h;
        c.pdf.line(LEFT, c.y, RIGHT, c.y, 0.5f, LINE);
    }

    private static void changeTable(Cursor c, List<Map<String, Object>> items) {
        float w1 = 120;
        float w = (RIGHT - LEFT - w1) / 2;
        c.need(22);
        c.pdf.rect(LEFT, c.y - 18, RIGHT - LEFT, 18, HEAD);
        c.pdf.text(LEFT + 5, c.y - 13, SimplePdf.BOLD, 9, NAVY, "Field");
        c.pdf.text(LEFT + w1 + 5, c.y - 13, SimplePdf.BOLD, 9, NAVY, "Before");
        c.pdf.text(LEFT + w1 + w + 5, c.y - 13, SimplePdf.BOLD, 9, NAVY, "After");
        c.y -= 18;
        for (Map<String, Object> i : items) {
            List<String> f = SimplePdf.wrap(String.valueOf(i.get("field")), SimplePdf.BOLD, 9, w1 - 10);
            List<String> b = SimplePdf.wrap(i.get("before") == null ? "-" : String.valueOf(i.get("before")), SimplePdf.REGULAR, 9, w - 10);
            List<String> a = SimplePdf.wrap(i.get("after") == null ? "-" : String.valueOf(i.get("after")), SimplePdf.REGULAR, 9, w - 10);
            int lines = Math.max(f.size(), Math.max(b.size(), a.size()));
            float h = 8 + lines * 12;
            c.need(h);
            column(c, LEFT + 5, f, SimplePdf.BOLD, NAVY);
            column(c, LEFT + w1 + 5, b, SimplePdf.REGULAR, RED);
            column(c, LEFT + w1 + w + 5, a, SimplePdf.REGULAR, GREEN);
            c.y -= h;
            c.pdf.line(LEFT, c.y, RIGHT, c.y, 0.5f, LINE);
        }
    }

    private static void column(Cursor c, float x, List<String> lines, String font, float[] color) {
        float y = c.y - 13;
        for (String l : lines) {
            c.pdf.text(x, y, font, 9, color, l);
            y -= 12;
        }
    }
}
