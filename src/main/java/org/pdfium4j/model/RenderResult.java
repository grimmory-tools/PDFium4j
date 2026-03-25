package org.pdfium4j.model;

import java.awt.image.BufferedImage;

/**
 * Result of rendering a PDF page: raw pixel data plus dimensions.
 *
 * <p>Pixel data is in BGRA byte order (PDFium native format with
 * {@code FPDF_REVERSE_BYTE_ORDER} flag, which gives RGBA).
 *
 * @param width  image width in pixels
 * @param height image height in pixels
 * @param rgba   raw RGBA pixel data (4 bytes per pixel)
 */
public record RenderResult(int width, int height, byte[] rgba) {

    private static final int BYTES_PER_PIXEL = 4; // RGBA

    /**
     * Convert to a {@link BufferedImage} for use with ImageIO or Graphics2D.
     *
     * @throws IllegalStateException if the rgba array length doesn't match dimensions
     */
    public BufferedImage toBufferedImage() {
        int expectedBytes = width * height * BYTES_PER_PIXEL;
        if (rgba.length < expectedBytes) {
            throw new IllegalStateException(
                    "RGBA buffer too small: expected " + expectedBytes + " bytes for "
                            + width + "x" + height + ", got " + rgba.length);
        }
        var img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var pixels = new int[width * height];
        for (int i = 0; i < pixels.length; i++) {
            int off = i * BYTES_PER_PIXEL;
            int r = rgba[off]     & 0xFF;
            int g = rgba[off + 1] & 0xFF;
            int b = rgba[off + 2] & 0xFF;
            int a = rgba[off + 3] & 0xFF;
            pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        img.setRGB(0, 0, width, height, pixels, 0, width);
        return img;
    }
}
