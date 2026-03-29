package org.grimmory.pdfium4j.model;

/**
 * Metadata about an image embedded within a PDF page. The image can be rendered to pixel data via
 * {@link org.grimmory.pdfium4j.PdfPage#renderEmbeddedImage(int)}.
 *
 * @param index the image's index among image objects on the page (0-based)
 * @param width image width in pixels
 * @param height image height in pixels
 * @param bitsPerPixel bits per pixel (e.g. 8, 24, 32)
 * @param horizontalDpi horizontal resolution in DPI
 * @param verticalDpi vertical resolution in DPI
 */
public record EmbeddedImage(
    int index, int width, int height, int bitsPerPixel, float horizontalDpi, float verticalDpi) {

  /** Total pixel count for this image. */
  public long pixelCount() {
    return (long) width * height;
  }
}
