package org.grimmory.pdfium4j.internal;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.OutputStream;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * An OutputStream that writes directly to a MemorySegment.
 *
 * <p>If the segment is exhausted, it will attempt to grow using {@link ScratchBuffer}.
 */
public final class SegmentOutputStream extends OutputStream {

  private MemorySegment segment;
  private long pos;

  @SuppressFBWarnings("EI_EXPOSE_REP2")
  public SegmentOutputStream(MemorySegment segment) {
    this.segment = segment;
    this.pos = 0;
  }

  @Override
  public void write(int b) {
    ensureCapacity(1);
    segment.set(ValueLayout.JAVA_BYTE, pos++, (byte) b);
  }

  @Override
  public void write(@NonNull byte[] b, int off, int len) {
    if (len <= 0) return;
    ensureCapacity(len);
    MemorySegment.copy(b, off, segment, ValueLayout.JAVA_BYTE, pos, len);
    pos += len;
  }

  public long size() {
    return pos;
  }

  @SuppressFBWarnings("EI_EXPOSE_REP")
  public MemorySegment segment() {
    return segment;
  }

  private void ensureCapacity(long needed) {
    if (pos + needed > segment.byteSize()) {
      long newSize = Math.max(segment.byteSize() * 2, pos + needed);
      MemorySegment newSeg = ScratchBuffer.get(newSize);
      MemorySegment.copy(segment, 0, newSeg, 0, pos);
      segment = newSeg;
    }
  }
}
