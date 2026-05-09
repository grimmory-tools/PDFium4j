package org.grimmory.pdfium4j.model;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

/** A zero-copy representation of a rendered PDF page in native off-heap memory. */
public final class NativeBitmap implements AutoCloseable {

  private final int width;
  private final int height;
  private final int stride;
  private final MemorySegment segment;
  private final Arena arena;

  @SuppressFBWarnings("EI_EXPOSE_REP2")
  public NativeBitmap(int width, int height, int stride, MemorySegment segment, Arena arena) {
    this.width = width;
    this.height = height;
    this.stride = stride;
    this.segment = segment;
    this.arena = arena;
  }

  public int width() {
    return width;
  }

  public int height() {
    return height;
  }

  public int stride() {
    return stride;
  }

  @SuppressFBWarnings("EI_EXPOSE_REP")
  public MemorySegment segment() {
    return segment;
  }

  /**
   * Wraps the native memory segment into a {@link BufferedImage} without copying data.
   *
   * <p>The resulting image depends on the native memory staying valid. Closing this {@code
   * NativeBitmap} will invalidate the image.
   *
   * @return a BufferedImage wrapping the off-heap memory
   */
  public BufferedImage asBufferedImage() {
    // PDFium BGRA format matches Java's TYPE_4BYTE_ABGR with some adjustments
    // or we can use a custom ComponentColorModel

    ByteBuffer buffer = segment.asByteBuffer();
    return createZeroCopyBufferedImage(buffer, width, height, stride);
  }

  private static BufferedImage createZeroCopyBufferedImage(
      ByteBuffer buffer, int width, int height, int stride) {
    ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
    int[] nBits = {8, 8, 8, 8};
    int[] bOffs = {2, 1, 0, 3}; // BGRA to RGBA mapping for ComponentColorModel
    ComponentColorModel colorModel =
        new ComponentColorModel(
            cs, nBits, true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);

    WritableRaster raster =
        Raster.createInterleavedRaster(
            new DataBuffer(DataBuffer.TYPE_BYTE, buffer.capacity()) {
              @Override
              public int getElem(int bank, int i) {
                return buffer.get(i) & 0xFF;
              }

              @Override
              public void setElem(int bank, int i, int val) {
                buffer.put(i, (byte) val);
              }
            },
            width,
            height,
            stride,
            4,
            bOffs,
            null);

    return new BufferedImage(colorModel, raster, false, null);
  }

  @Override
  public void close() {
    arena.close();
  }
}
