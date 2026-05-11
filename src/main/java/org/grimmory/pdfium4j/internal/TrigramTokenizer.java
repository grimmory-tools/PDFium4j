package org.grimmory.pdfium4j.internal;

import java.util.Arrays;

/** Shared utility for consistent trigram tokenization across indexing and search. */
public final class TrigramTokenizer {

  private TrigramTokenizer() {}

  /**
   * Generates a sorted, deduplicated array of packed trigram hashes from the given normalized text.
   *
   * @param text the normalized (lowercased) text to tokenize
   * @return a sorted, deduplicated array of packed trigrams (each long contains 3 chars)
   */
  public static long[] generateTrigramHashes(String text) {
    if (text == null || text.length() < 3) {
      return Generators.emptyLongArray();
    }
    return text.chars()
        .boxed()
        .gather(java.util.stream.Gatherers.windowSliding(3))
        .mapToLong(
            window -> ((long) window.get(0) << 32) | ((long) window.get(1) << 16) | window.get(2))
        .distinct()
        .sorted()
        .toArray();
  }
}
