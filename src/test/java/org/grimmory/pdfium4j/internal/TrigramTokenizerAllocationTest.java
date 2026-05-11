package org.grimmory.pdfium4j.internal;

import org.grimmory.pdfium4j.NoAllocationAsserter;
import org.junit.jupiter.api.Test;

class TrigramTokenizerAllocationTest {

  private final NoAllocationAsserter asserter = new NoAllocationAsserter();

  @Test
  void generateTrigramHashesEfficiency() {
    String text = "The quick brown fox jumps over the lazy dog";

    // Warmup
    for (int i = 0; i < 1000; i++) {
      if (TrigramTokenizer.generateTrigramHashes(text).length == 0) continue;
    }

    asserter.startRecording();
    long[] hashes = TrigramTokenizer.generateTrigramHashes(text);
    // Tolerance for long[] allocation
    asserter.assertNoAllocations(8016);
    if (hashes.length == 0) throw new IllegalStateException();
  }
}
