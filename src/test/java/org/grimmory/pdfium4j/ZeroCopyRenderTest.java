package org.grimmory.pdfium4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.grimmory.pdfium4j.model.NativeBitmap;
import org.grimmory.pdfium4j.model.RenderFlags;
import org.junit.jupiter.api.Test;

class ZeroCopyRenderTest {

  @Test
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings("DLS_DEAD_LOCAL_STORE")
  void testNativeRender() throws Exception {
    Path path = Paths.get("src/test/resources/minimal.pdf");
    if (!path.toFile().exists()) return;

    try (PdfDocument doc = PdfDocument.open(path)) {
      try (PdfPage page = doc.page(0)) {
        try (NativeBitmap bitmap = page.renderNative(72, RenderFlags.DEFAULT)) {
          assertNotNull(bitmap);
          assertEquals(72, (int) (bitmap.width() / (page.size().width() / 72.0))); // Rough check

          BufferedImage img = bitmap.asBufferedImage();
          assertNotNull(img);
          assertEquals(bitmap.width(), img.getWidth());
          assertEquals(bitmap.height(), img.getHeight());

          // Verify we can access pixels without crashing
          @SuppressWarnings("unused")
          int rgb = img.getRGB(0, 0);
        }
      }
    }
  }
}
