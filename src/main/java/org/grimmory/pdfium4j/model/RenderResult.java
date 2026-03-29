package org.grimmory.pdfium4j.model;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

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
    long expectedBytes = (long) width * height * 4;
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

  /**
   * Encode this render result as JPEG bytes with the specified quality.
   *
   * @param quality JPEG quality from 0.0 (worst) to 1.0 (best)
   * @return JPEG-encoded bytes
   * @throws UncheckedIOException if encoding fails
   */
  public byte[] toJpegBytes(float quality) {
    if (quality < 0f || quality > 1f) {
      throw new IllegalArgumentException(
          "JPEG quality must be between 0.0 and 1.0, got: " + quality);
    }
    BufferedImage img = toBufferedImage();
    try {
      // Convert ARGB to RGB (JPEG doesn't support alpha)
      BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = rgb.createGraphics();
      try {
        g.drawImage(img, 0, 0, null);
      } finally {
        g.dispose();
      }
      img.flush();

      ImageWriter writer = ImageIO.getImageWritersByFormatName("JPEG").next();
      try {
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
          writer.setOutput(ios);
          writer.write(null, new IIOImage(rgb, null, null), param);
        }
        return baos.toByteArray();
      } finally {
        writer.dispose();
        rgb.flush();
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to encode JPEG", e);
    }
  }

  /**
   * Encode this render result as JPEG bytes with default quality (0.85).
   *
   * @return JPEG-encoded bytes
   * @throws UncheckedIOException if encoding fails
   */
  public byte[] toJpegBytes() {
    return toJpegBytes(0.85f);
  }

  /**
   * Encode this render result as PNG bytes (lossless, with alpha).
   *
   * @return PNG-encoded bytes
   * @throws UncheckedIOException if encoding fails
   */
  public byte[] toPngBytes() {
    BufferedImage img = toBufferedImage();
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ImageIO.write(img, "PNG", baos);
      return baos.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to encode PNG", e);
    } finally {
      img.flush();
    }
  }
}
