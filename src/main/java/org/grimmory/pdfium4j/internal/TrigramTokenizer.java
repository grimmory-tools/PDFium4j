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
    int n = text.length() - 2;
    long[] tmp = new long[n];
    for (int i = 0; i < n; i++) {
      tmp[i] =
          ((long) text.charAt(i) << 32) | ((long) text.charAt(i + 1) << 16) | text.charAt(i + 2);
    }
    Arrays.sort(tmp);
    int unique = 1;
    for (int i = 1; i < tmp.length; i++) {
      if (tmp[i] != tmp[unique - 1]) {
        tmp[unique++] = tmp[i];
      }
    }
    return unique == tmp.length ? tmp : Arrays.copyOf(tmp, unique);
  }
}
