package org.grimmory.pdfium4j.model;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

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
   * Creates a managed {@link BufferedImage} from the native memory.
   *
   * <p>This method performs a one-time copy of the native pixels into a managed Java {@code
   * byte[]}. The resulting image is safe to use even after this {@code NativeBitmap} is closed.
   *
   * @return a new managed BufferedImage
   */
  public BufferedImage asBufferedImage() {
    // PDFium BGRA format
    int capacity = Math.toIntExact(segment.byteSize());
    byte[] data = new byte[capacity];
    MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, 0, data, 0, capacity);

    DataBufferByte dataBuffer = new DataBufferByte(data, capacity);
    ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
    int[] nBits = {8, 8, 8, 8};
    int[] bOffs = {2, 1, 0, 3}; // BGRA to RGBA mapping for ComponentColorModel
    ComponentColorModel colorModel =
        new ComponentColorModel(
            cs, nBits, true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);

    WritableRaster raster =
        Raster.createInterleavedRaster(dataBuffer, width, height, stride, 4, bOffs, null);

    return new BufferedImage(colorModel, raster, false, null);
  }

  @Override
  public void close() {
    arena.close();
  }
}
