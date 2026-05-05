package org.grimmory.pdfium4j.internal;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.foreign.MemorySegment;
import org.junit.jupiter.api.Test;

class FfmHelperTest {

  @Test
  void writeUtf8StringUsesActualUtf8Size() {
    MemorySegment seg = MemorySegment.ofArray(new byte[8]);
    MemorySegment written = FfmHelper.writeUtf8String(seg, "Ü");
    assertEquals(3, written.byteSize());
    assertEquals(0, written.get(JAVA_BYTE, 2));
  }

  @Test
  void writeUtf8StringAllowsNonAsciiWhenUtf8BytesFit() {
    MemorySegment seg = MemorySegment.ofArray(new byte[3]);
    MemorySegment written = FfmHelper.writeUtf8String(seg, "é");
    assertEquals(3, written.byteSize());
  }

  @Test
  void writeUtf8StringThrowsWhenSegmentTooSmall() {
    MemorySegment seg = MemorySegment.ofArray(new byte[2]);
    assertThrows(IllegalArgumentException.class, () -> FfmHelper.writeUtf8String(seg, "hello"));
  }

  @Test
  void normalizeWideByteLengthClampsAndKeepsEven() {
    MemorySegment seg = MemorySegment.ofArray(new byte[10]);
    seg.set(JAVA_BYTE, 8, (byte) 0);
    seg.set(JAVA_BYTE, 9, (byte) 0);
    assertEquals(10, FfmHelper.normalizeWideByteLength(seg, 11, 10));
    assertEquals(0, FfmHelper.normalizeWideByteLength(seg, 2, 10));
    assertEquals(0, FfmHelper.normalizeWideByteLength(seg, 100, 2));
    assertEquals(8, FfmHelper.normalizeWideByteLength(seg, 8, 8));
  }

  @Test
  void normalizeWideByteLengthFindsTerminatorWhenReportedExcludesIt() {
    byte[] bytes = new byte[12];
    java.util.Arrays.fill(bytes, (byte) 1);
    MemorySegment seg = MemorySegment.ofArray(bytes);
    seg.set(JAVA_BYTE, 10, (byte) 0);
    seg.set(JAVA_BYTE, 11, (byte) 0);
    assertEquals(12, FfmHelper.normalizeWideByteLength(seg, 10, 12));
  }

  @Test
  void normalizeWideByteLengthRejectsMissingTerminator() {
    byte[] bytes = new byte[12];
    java.util.Arrays.fill(bytes, (byte) 1);
    MemorySegment seg = MemorySegment.ofArray(bytes);
    assertEquals(0, FfmHelper.normalizeWideByteLength(seg, 10, 12));
  }
}
