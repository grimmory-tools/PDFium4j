package org.grimmory.pdfium4j.model;

import java.awt.image.BufferedImage;

/**
 * Result of rendering a PDF page: raw pixel data plus dimensions.
 *
 * <p>Pixel data is in BGRA byte order (PDFium native format with {@code FPDF_REVERSE_BYTE_ORDER}
 * flag, which gives RGBA).
 *
 * @param width image width in pixels
 * @param height image height in pixels
 * @param rgba raw RGBA pixel data (4 bytes per pixel)
 */
public record RenderResult(int width, int height, byte[] rgba) {

  public RenderResult {
    rgba = rgba.clone();
  }

  @Override
  public byte[] rgba() {
    return rgba.clone();
  }

  /**
   * Convert to a {@link BufferedImage} for use with ImageIO or Graphics2D.
   *
   * @throws IllegalStateException if the rgba array length doesn't match dimensions
   */
  public BufferedImage toBufferedImage() {
    int expectedBytes = width * height * 4;
    if (rgba.length < expectedBytes) {
      throw new IllegalStateException(
          "RGBA buffer too small: expected "
              + expectedBytes
              + " bytes for "
              + width
              + "x"
              + height
              + ", got "
              + rgba.length);
    }
    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    int[] pixels = new int[width * height];
    for (int i = 0; i < pixels.length; i++) {
      int r = rgba[i * 4] & 0xFF;
      int g = rgba[i * 4 + 1] & 0xFF;
      int b = rgba[i * 4 + 2] & 0xFF;
      int a = rgba[i * 4 + 3] & 0xFF;
      pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }
    img.setRGB(0, 0, width, height, pixels, 0, width);
    return img;
  }
}
