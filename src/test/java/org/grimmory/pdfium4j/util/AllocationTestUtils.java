package org.grimmory.pdfium4j.util;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AllocationTestUtils {

  private AllocationTestUtils() {}

  public static Path getTestPdf(Class<?> testClass) throws IOException {
    // Try external corpus first (developer local environment)
    Path projectRoot = Path.of("").toAbsolutePath();
    Path corpusPdf =
        projectRoot.resolve("corpus").resolve("gutenberg/1063_The Cask of Amontillado.pdf");
    if (Files.exists(corpusPdf)) {
      return corpusPdf;
    }

    // Fallback for CI: use minimal.pdf from resources
    var resource = testClass.getResource("/minimal.pdf");
    if (resource != null) {
      try {
        return Path.of(resource.toURI());
      } catch (URISyntaxException e) {
        throw new IOException("Failed to resolve minimal.pdf URI", e);
      }
    }

    throw new IOException("Could not find any test PDF (corpus or minimal.pdf)");
  }
}
