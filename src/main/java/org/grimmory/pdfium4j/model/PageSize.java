package org.grimmory.pdfium4j.model;

/** PDF page dimensions in points (1 pt = 1/72 inch). */
public record PageSize(float width, float height) {

  public static final PageSize A4 = new PageSize(595, 842);

    /** Width in pixels at the given DPI. Clamped to avoid overflow. */
  public int widthPixels(int dpi) {
    return safePixels(width, dpi);
  }

  /** Height in pixels at the given DPI. Clamped to avoid overflow. */
  public int heightPixels(int dpi) {
    return safePixels(height, dpi);
  }

  private static int safePixels(float points, int dpi) {
    long px = Math.round((double) points * dpi / 72.0);
    return Math.clamp(px, 0, Integer.MAX_VALUE);
  }
}
