package org.grimmory.pdfium4j;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.grimmory.pdfium4j.internal.NoAllocationPathProbe;
import org.grimmory.pdfium4j.util.AllocationTestUtils;
import org.grimmory.pdfium4j.model.MetadataTag;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PdfOpenReadAllocationTest {

  private static final int WARMUP_ITERATIONS = 400;
  private static final String TITLE = "Allocation Free Read";

  private final NoAllocationAsserter asserter = new NoAllocationAsserter();

  private NoAllocationPathProbe openProbe;
  private PdfDocument metadataDoc;

  /** Allocation tolerance for JVM/JIT noise. */
  private static final long STEADY_STATE_TOLERANCE = 256;

  static boolean pdfiumAvailable() {
    try {
      PdfiumLibrary.initialize();
      return true;
    } catch (Throwable _) {
      return false;
    }
  }

  @BeforeAll
  void setUp() {
    asserter.verifyAllocationTrackingAvailable();

    Path probeSource = findCorpusPdf();
    openProbe = PdfDocument.noAllocationPathProbe(probeSource);

    metadataDoc = PdfDocument.open(probeSource);
    metadataDoc.setMetadata(MetadataTag.TITLE, TITLE);
  }

  @AfterAll
  void tearDown() {
    if (metadataDoc != null) {
      metadataDoc.close();
    }
    if (openProbe != null) {
      openProbe.close();
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void pathOpenProbeDoesNotAllocateAfterWarmup() {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment trailerBuffer =
          arena.allocate(32L * JAVA_INT.byteSize(), JAVA_INT.byteAlignment());
      int[] output = new int[3];

      for (int i = 0; i < 2000; i++) {
        openProbe.inspect(output, trailerBuffer);
      }

      asserter.startRecording();
      openProbe.inspect(output, trailerBuffer);
      asserter.assertNoAllocations(STEADY_STATE_TOLERANCE);

      assertTrue(output[0] > 0, "PDF should have pages");
      assertEquals(1, output[1]); // Xref health
      assertTrue(output[2] > 0, "Expected at least one trailer end offset");
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  void metadataProbeDoesNotAllocateAfterWarmup() {
    try (Arena arena = Arena.ofConfined()) {
      int needed = metadataDoc.probeMetadataUtf16ByteLength();
      int expectedMinimum = (TITLE.length() * 2) + 2;
      assertTrue(
          needed >= expectedMinimum,
          "Expected at least " + expectedMinimum + " bytes for metadata, but got " + needed);

      MemorySegment metadataBuffer = arena.allocate(needed, 2);
      for (int i = 0; i < 2000; i++) {
        metadataDoc.readMetadataUtf16(metadataBuffer);
      }

      asserter.startRecording();
      int copied = metadataDoc.readMetadataUtf16(metadataBuffer);
      asserter.assertNoAllocations(STEADY_STATE_TOLERANCE);

      assertEquals(needed, copied);
      String title =
          new String(
              metadataBuffer.asSlice(0, copied - 2).toArray(JAVA_BYTE), StandardCharsets.UTF_16LE);
      assertEquals(TITLE, title);
    }
  }

  private static Path findCorpusPdf() {
    try {
      return AllocationTestUtils.getTestPdf(PdfOpenReadAllocationTest.class);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to find test PDF", e);
    }
  }
}
