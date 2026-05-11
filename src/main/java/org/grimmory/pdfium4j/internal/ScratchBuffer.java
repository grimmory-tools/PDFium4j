package org.grimmory.pdfium4j.internal;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A thread-local scratch buffer for temporary native allocations.
 *
 * <p>PDFium operations often require passing small memory segments for strings or structs.
 * Re-allocating an {@link Arena} per call would create significant GC pressure and native
 * bookkeeping overhead. This class maintains a reusable thread-local slab to achieve
 * near-zero-allocation performance for these hot paths.
 *
 * <p><strong>Safety:</strong> Returned segments can be overwritten by subsequent calls and should
 * be treated as short-lived temporaries. Growth is non-destructive: previously returned segments
 * remain valid until {@link #release()} is invoked.
 */
public final class ScratchBuffer {

  private static final long INITIAL_SIZE = 4096;
  private static final long STEADY_STATE_SIZE =
      Long.getLong("pdfium4j.scratch.steadyStateSize", 16L * 1024L * 1024L);
  private static final long MAX_SIZE =
      Long.getLong("pdfium4j.scratch.maxSize", 256L * 1024L * 1024L);
  private static final long WARN_THRESHOLD =
      Long.getLong("pdfium4j.scratch.warnThreshold", 32L * 1024L * 1024L);

  private static final ScopedValue<State> STATE = ScopedValue.newInstance();
  private static final ThreadLocal<State> BRIDGE = new ThreadLocal<>();

  private ScratchBuffer() {}

  /**
   * Get a thread-local memory segment of at least the specified size. The contents of the segment
   * are undefined and must be overwritten by the caller.
   *
   * <p><strong>WARNING:</strong> Do not rely on contents surviving across subsequent {@code
   * ScratchBuffer.get()} calls.
   *
   * @param minBytes minimum required size in bytes
   * @return a memory segment of at least minBytes, 8-byte aligned
   * @throws IllegalArgumentException if minBytes exceeds the safety limit or is negative
   */
  public static MemorySegment get(long minBytes) {
    if (minBytes < 0 || minBytes > MAX_SIZE) {
      throw new IllegalArgumentException("Invalid scratch buffer size: " + minBytes);
    }
    State s = getOrCreateState();
    if (s.useCount <= 0) {
      throw new IllegalStateException("ScratchBuffer.get() called without active acquire()");
    }
    return s.getSegment(minBytes);
  }

  /** Returns a scratch segment containing a null-terminated UTF-8 string. */
  public static MemorySegment getUtf8(String s) {
    long len = FfmHelper.utf8ByteLengthWithNull(s);
    MemorySegment seg = get(len);
    return FfmHelper.writeUtf8String(seg, s);
  }

  /**
   * Allocates one scratch segment containing a UTF-8 key and a wide-string value buffer.
   *
   * @param key UTF-8 key to write including null terminator
   * @param valueBytes value buffer size in bytes
   */
  public static KeyValueSlots utf8KeyAndWideValue(String key, long valueBytes) {
    if (key == null) {
      throw new NullPointerException("key must not be null");
    }
    if (valueBytes < 0) {
      throw new IllegalArgumentException("Invalid value size: " + valueBytes);
    }
    long keyUpperBoundBytes = Math.addExact(Math.multiplyExact((long) key.length(), 4), 1);
    long total = Math.addExact(keyUpperBoundBytes, valueBytes);
    MemorySegment scratch = get(total);
    MemorySegment keySeg = FfmHelper.writeUtf8String(scratch, key);
    MemorySegment valueSeg = scratch.asSlice(keySeg.byteSize(), valueBytes);
    return keyAndWideValue(keySeg, valueSeg);
  }

  /** Wrap existing key and value segments into the reusable thread-local slot holder. */
  public static KeyValueSlots keyAndWideValue(MemorySegment keySeg, MemorySegment valueSeg) {
    State s = getOrCreateState();
    if (s.useCount <= 0) {
      throw new IllegalStateException(
          "ScratchBuffer.keyAndWideValue() called without active acquire()");
    }
    s.keyValueSlots.keySeg = keySeg;
    s.keyValueSlots.valueSeg = valueSeg;
    return s.keyValueSlots;
  }

  /** Acquire a usage slot for managed lifecycle owners. */
  public static void acquire() {
    getOrCreateState().useCount++;
  }

  /**
   * Acquires the scratch buffer and returns a zero-allocation AutoCloseable scope.
   *
   * <p>Usage:
   *
   * <pre>{@code
   * try (var _ = ScratchBuffer.acquireScope()) {
   *     // use ScratchBuffer.get(...) safely
   * }
   * }</pre>
   */
  public static Scope acquireScope() {
    acquire();
    return Scope.INSTANCE;
  }

  /**
   * A stateless, zero-allocation token used exclusively to hook into Java's try-with-resources
   * mechanism.
   */
  public static final class Scope implements AutoCloseable {
    private static final Scope INSTANCE = new Scope();

    private Scope() {}

    @Override
    public void close() {
      release();
    }
  }

  /** Release and close all thread-local scratch state for the current thread. */
  public static void release() {
    State s = getOrCreateState();
    if (s.useCount <= 0) return;
    s.useCount--;

    if (s.useCount == 0 && s.segment.byteSize() > STEADY_STATE_SIZE) {
      purge();
    }
  }

  /** Returns a clamped size that fits within the maximum scratch buffer limits. */
  public static long probeSize(long size) {
    return Math.min(Math.max(0, size), MAX_SIZE);
  }

  /**
   * Executes the given action within a managed scratch buffer scope using ScopedValues.
   *
   * @param action the action to run
   */
  public static void withScratch(Runnable action) {
    State s = new State();
    s.useCount = 1;
    try {
      ScopedValue.where(STATE, s).run(action);
    } finally {
      s.close();
    }
  }

  /**
   * Executes the given task within a managed scratch buffer scope and returns its result.
   *
   * @param action the task to run
   * @param <T> the result type
   * @return the task result
   * @throws Exception if the task fails
   */
  public static <T> T callWithScratch(java.util.concurrent.Callable<T> action) throws Exception {
    State s = new State();
    s.useCount = 1;
    try {
      return ScopedValue.where(STATE, s).call(() -> action.call());
    } finally {
      s.close();
    }
  }

  /** Clear all thread-local buffers and close their arenas. */
  public static void purge() {
    if (STATE.isBound()) {
      State s = STATE.get();
      s.useCount = 0;
      s.close();
    }
    State s = BRIDGE.get();
    if (s != null) {
      s.useCount = 0;
      s.close();
      BRIDGE.remove();
    }
  }

  /** Returns a thread-local byte array for temporary UTF-16LE decode staging. */
  public static byte[] getByteArray(int minBytes) {
    State s = getOrCreateState();
    if (s.useCount <= 0) {
      throw new IllegalStateException(
          "ScratchBuffer.getByteArray() called without active acquire()");
    }
    return s.getByteArray(minBytes);
  }

  /** Returns a dedicated 8KB scratch segment for metadata reads. */
  public static MemorySegment getMetadataBuffer() {
    State s = getOrCreateState();
    if (s.useCount <= 0) {
      throw new IllegalStateException(
          "ScratchBuffer.getMetadataBuffer() called without active acquire()");
    }
    return s.getMetadataBuffer();
  }

  static long currentCapacity() {
    if (STATE.isBound()) return STATE.get().segment.byteSize();
    State s = BRIDGE.get();
    return s == null ? 0 : s.segment.byteSize();
  }

  private static State getOrCreateState() {
    if (STATE.isBound()) {
      return STATE.get();
    }
    State s = BRIDGE.get();
    if (s == null) {
      s = new State();
      BRIDGE.set(s);
    }
    return s;
  }

  /**
   * Transient holder backed by the current thread's scratch state.
   *
   * <p>Instances are reused. Callers must consume the returned segments immediately and must not
   * retain this holder or its segments across subsequent {@link ScratchBuffer} calls or across
   * {@link #release()}.
   */
  public static final class KeyValueSlots {
    private MemorySegment keySeg;
    private MemorySegment valueSeg;

    /**
     * Returns the current key segment view.
     *
     * <p>The returned segment is transient scratch state and must not be retained.
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public MemorySegment keySeg() {
      return keySeg;
    }

    /**
     * Returns the current value segment view.
     *
     * <p>The returned segment is transient scratch state and must not be retained.
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public MemorySegment valueSeg() {
      return valueSeg;
    }
  }

  private static final class State {
    int useCount;
    private final List<Arena> arenas;
    private Arena arena;
    private MemorySegment segment;
    private MemorySegment steadySegment;
    private MemorySegment largestSegment;
    private byte[] byteArray;
    private final MemorySegment metadataBuffer;
    private final KeyValueSlots keyValueSlots;

    /**
     * State is only valid between construction and the {@link #close()} call. Subsequent accesses
     * are protected by the binding lifecycle or {@link BRIDGE#remove()}.
     */
    State() {
      this.arenas = new ArrayList<>(2);
      this.arena = Arena.ofConfined();
      this.arenas.add(this.arena);
      this.segment = arena.allocate(INITIAL_SIZE, 8);
      this.steadySegment = segment;
      this.largestSegment = segment;
      this.byteArray = new byte[2048];
      this.metadataBuffer = arena.allocate(8192, 8);
      this.keyValueSlots = new KeyValueSlots();
    }

    byte[] getByteArray(int minBytes) {
      if (byteArray.length < minBytes) {
        byteArray = new byte[Math.max(byteArray.length * 2, minBytes)];
      }
      return byteArray;
    }

    MemorySegment getMetadataBuffer() {
      return metadataBuffer;
    }

    MemorySegment getSegment(long minBytes) {
      // Revert to steady segment if it fits, to avoid keeping oversized buffers.
      // We check against steadySegment.byteSize() to avoid a "revert-then-grow" loop
      // if minBytes is between the initial slab size and STEADY_STATE_SIZE.
      if (segment.byteSize() > STEADY_STATE_SIZE
          && steadySegment != null
          && minBytes <= steadySegment.byteSize()) {
        segment = steadySegment;
      }

      if (segment.byteSize() < minBytes) {
        if (largestSegment != null && largestSegment.byteSize() >= minBytes) {
          segment = largestSegment;
          return segment;
        }
        if (minBytes > WARN_THRESHOLD) {
          // Large scratch allocations are rare and might indicate a logic error or
          // extremely large metadata.
          InternalLogger.warn("Large scratch allocation requested: " + minBytes + " bytes");
        }
        long doubled = Math.max(segment.byteSize() * 2, minBytes);
        long newSize = Math.min(doubled, MAX_SIZE);
        allocateArena(newSize);
      }
      return segment;
    }

    private void allocateArena(long size) {
      this.arena = Arena.ofConfined();
      this.arenas.add(this.arena);
      this.segment = arena.allocate(size, 8);
      if (size <= STEADY_STATE_SIZE) {
        this.steadySegment = this.segment;
      } else {
        this.largestSegment = this.segment;
      }
    }

    @SuppressWarnings("PMD.EmptyCatchBlock")
    private void close() {
      for (Arena a : arenas) {
        try {
          a.close();
        } catch (Exception _) {
          // ignored
        }
      }
      arenas.clear();
      largestSegment = null;
      keyValueSlots.keySeg = null;
      keyValueSlots.valueSeg = null;
    }
  }

  private static final class SegmentInputStream extends InputStream {
    private MemorySegment segment;
    private long pos;
    private final long size;

    SegmentInputStream(MemorySegment segment, long size) {
      this.segment = segment;
      this.size = size;
      this.pos = 0;
      acquire();
    }

    @Override
    public int read() {
      if (segment == null || pos >= size) return -1;
      int i = segment.get(ValueLayout.JAVA_BYTE, pos) & 0xFF;
      pos++;
      return i;
    }

    @Override
    public int read(byte[] b, int off, int len) {
      Objects.checkFromIndexSize(off, len, b.length);
      if (len == 0) return 0;
      if (segment == null || pos >= size) return -1;
      long n = Math.min(len, size - pos);
      MemorySegment.copy(segment, pos, MemorySegment.ofArray(b), off, n);
      pos += n;
      return (int) n;
    }

    @Override
    public int available() {
      return segment == null ? 0 : (int) Math.min(Integer.MAX_VALUE, size - pos);
    }

    @Override
    public void close() {
      if (segment != null) {
        segment = null;
        release();
      }
    }
  }
}
