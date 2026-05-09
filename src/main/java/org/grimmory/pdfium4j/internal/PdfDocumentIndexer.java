package org.grimmory.pdfium4j.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.grimmory.pdfium4j.PdfDocument;
import org.grimmory.pdfium4j.PdfPage;

/** Internal helper for PDF document full-text indexing. */
public final class PdfDocumentIndexer {

  private PdfDocumentIndexer() {}

  public static Map<Long, int[]> buildIndex(PdfDocument doc) {
    Map<Long, List<Integer>> tempIndex = new HashMap<>();
    int count = doc.pageCount();

    for (int i = 0; i < count; i++) {
      try (PdfPage p = doc.page(i)) {
        String text = p.extractText().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) continue;

        long[] hashes = TrigramTokenizer.generateTrigramHashes(text);
        for (long hash : hashes) {
          tempIndex.computeIfAbsent(hash, _ -> new ArrayList<>(8)).add(i);
        }
      }
    }

    Map<Long, int[]> textIndex = HashMap.newHashMap(tempIndex.size());
    for (Map.Entry<Long, List<Integer>> entry : tempIndex.entrySet()) {
      textIndex.put(
          entry.getKey(), entry.getValue().stream().mapToInt(Integer::intValue).toArray());
    }
    return textIndex;
  }
}
