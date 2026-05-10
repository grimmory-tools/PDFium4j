package org.grimmory.pdfium4j;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import org.grimmory.pdfium4j.util.AllocationTestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PdfRepairAllocationTest {

  private final NoAllocationAsserter asserter = new NoAllocationAsserter();
  private MemorySegment corruptPdf;
  private Arena arena;

  /** Allocation tolerance for JVM/JIT noise. */
  private static final long STEADY_STATE_TOLERANCE = 65536;

  @BeforeAll
  void setUp() throws IOException {
    asserter.verifyAllocationTrackingAvailable();
    arena = Arena.ofShared();

    Path corpusPdf = findCorpusPdf();
    byte[] data = Files.readAllBytes(corpusPdf);

    // Truncate just enough to break the startxref (usually last ~10 bytes)
    int truncatedLen = data.length - 10;
    corruptPdf = arena.allocate(truncatedLen);
    MemorySegment.copy(MemorySegment.ofArray(data), 0, corruptPdf, 0, truncatedLen);

    // Disable logging to avoid noise
    LogManager.getLogManager().reset();
    Logger.getLogger("").setLevel(Level.OFF);
  }

  static boolean pdfiumAvailable() {
    try {
      PdfiumLibrary.initialize();
      return true;
    } catch (Throwable _) {
      return false;
    }
  }

  @Test
  @EnabledIf("pdfiumAvailable")
  public void repairDoesNotAllocateAfterWarmup() throws IOException {
    // Pre-allocate a large enough buffer to avoid resizing during test
    ByteArrayOutputStream out = new ByteArrayOutputStream(1024 * 1024);

    // Warmup
    for (int i = 0; i < 100; i++) {
      out.reset();
      PdfSaver.SaveParams params =
          new PdfSaver.SaveParams(
              MemorySegment.NULL,
              Map.of(),
              Map.of(),
              _ -> null,
              false,
              null,
              null,
              null,
              null,
              corruptPdf,
              out);
      PdfSaver.save(params);
    }

    asserter.startRecording();
    out.reset();
    PdfSaver.SaveParams params =
        new PdfSaver.SaveParams(
            MemorySegment.NULL,
            Map.of(),
            Map.of(),
            _ -> null,
            false,
            null,
            null,
            null,
            null,
            corruptPdf,
            out);
    PdfSaver.save(params);

    asserter.assertNoAllocations(STEADY_STATE_TOLERANCE);
    assertTrue(out.size() > 0, "Repair should produce output");
  }

  @AfterAll
  void tearDown() {
    if (arena != null) {
      arena.close();
    }
  }

  private static Path findCorpusPdf() throws IOException {
    return AllocationTestUtils.getTestPdf(PdfRepairAllocationTest.class);
  }
}
