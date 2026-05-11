package org.grimmory.pdfium4j;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import org.grimmory.pdfium4j.model.MetadataTag;
import org.grimmory.pdfium4j.util.AllocationTestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PdfSaveAllocationTest {

  private final NoAllocationAsserter asserter = new NoAllocationAsserter();
  private PdfDocument doc;
  private Path target;

  /** Allocation tolerance for JVM/JIT noise. */
  private static final long STEADY_STATE_TOLERANCE = 32768;

  @BeforeAll
  void setUp() throws IOException {
    asserter.verifyAllocationTrackingAvailable();
    Path source = findCorpusPdf();
    target = Files.createTempFile("pdfium4j-alloc-target-", ".pdf");

    doc = PdfDocument.open(source);
    doc.setMetadata(MetadataTag.TITLE, "Allocation Free Save");
    doc.setMetadata(MetadataTag.AUTHOR, "Edgar Allan Poe");
    doc.setMetadata(MetadataTag.SUBJECT, "Test Subject");

    // Disable logging to avoid noise
    LogManager.getLogManager().reset();
    Logger.getLogger("").setLevel(Level.OFF);

    // Aggressive Warmup
    try {
      warmup();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void warmup() throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream(1024 * 1024);
    for (int i = 0; i < 100; i++) {
      out.reset();
      doc.save(out);
    }
  }

  @AfterAll
  void tearDown() throws IOException {
    if (doc != null) {
      doc.close();
    }
    if (target != null) {
      Files.deleteIfExists(target);
    }
  }

  @Test
  void metadataSaveToOutputStreamDoesNotAllocateAfterWarmup() throws Exception {
    warmup(); // Local warmup for this thread

    ByteArrayOutputStream bos = new ByteArrayOutputStream(1024 * 1024);
    for (int i = 0; i < 10; i++) {
      bos.reset();
      asserter.startRecording();
      doc.save(bos);
      // Iteration 0 might see one-time JVM noise
      long tolerance = (i == 0) ? 160_000 : STEADY_STATE_TOLERANCE;
      asserter.assertNoAllocations(tolerance);
    }
    assertTrue(bos.size() > 0, "Native save should stream bytes to the sink");
  }

  private static Path findCorpusPdf() throws IOException {
    return AllocationTestUtils.getTestPdf(PdfSaveAllocationTest.class);
  }
}
