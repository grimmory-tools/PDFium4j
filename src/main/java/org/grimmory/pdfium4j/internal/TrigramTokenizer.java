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
    int len = text.length();
    int count = len - 2;
    long[] hashes = new long[count];
    for (int i = 0; i < count; i++) {
      hashes[i] =
          ((long) text.charAt(i) << 32) | ((long) text.charAt(i + 1) << 16) | text.charAt(i + 2);
    }
    Arrays.sort(hashes);

    // Deduplicate in-place
    if (hashes.length <= 1) return hashes;
    int uniqueCount = 1;
    for (int i = 1; i < hashes.length; i++) {
      if (hashes[i] != hashes[i - 1]) {
        hashes[uniqueCount++] = hashes[i];
      }
    }
    return uniqueCount == hashes.length ? hashes : Arrays.copyOf(hashes, uniqueCount);
  }
}
