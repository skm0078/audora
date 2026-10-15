// File: research/spikes/S2-pdf-extraction/java/PdfBoxBench.java
// Purpose: Page-at-a-time PdfBox 2.0.27 (PdfBox-Android's upstream base) extraction with per-page timing and heap use, JSON lines out.
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class PdfBoxBench {
    static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.toString();
    }
    public static void main(String[] a) throws Exception {
        Runtime rt = Runtime.getRuntime();
        long t0 = System.nanoTime();
        try (PDDocument doc = PDDocument.load(new File(a[0]), MemoryUsageSetting.setupTempFileOnly());
             Writer w = new OutputStreamWriter(new FileOutputStream(a[1]), StandardCharsets.UTF_8)) {
            double open = (System.nanoTime() - t0) / 1e6;
            int n = doc.getNumberOfPages();
            int max = a.length > 2 ? Math.min(n, Integer.parseInt(a[2])) : n;
            PDFTextStripper st = new PDFTextStripper();
            w.write("{\"open_ms\":" + open + ",\"pages_total\":" + n + "}\n");
            for (int p = 1; p <= max; p++) {
                long t = System.nanoTime();
                st.setStartPage(p); st.setEndPage(p);
                String text = st.getText(doc);
                double ms = (System.nanoTime() - t) / 1e6;
                long heap = rt.totalMemory() - rt.freeMemory();
                w.write("{\"page\":" + p + ",\"ms\":" + ms + ",\"heap\":" + heap + ",\"text\":\"" + esc(text) + "\"}\n");
            }
        }
    }
}
