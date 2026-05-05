package org.grimmory.pdfium4j.internal;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

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
  private static final long STEADY_STATE_SIZE = 64L * 1024L;
  private static final long MAX_SIZE = 1024L * 1024L * 128L; // 128MB safety limit

  private static final ThreadLocal<State> STATE = new ThreadLocal<>();
  private static final ThreadLocal<int[]> USE_COUNT = ThreadLocal.withInitial(() -> new int[] {0});

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
    if (USE_COUNT.get()[0] <= 0) {
      throw new IllegalStateException("ScratchBuffer.get() called without active acquire()");
    }
    State s = getOrCreateState();
    return s.getSegment(minBytes);
  }

  /** Returns a probe buffer for two-phase UTF-8 key APIs. */
  public static MemorySegment utf8ProbeBuffer(String key) {
    return get(probeSize(FfmHelper.utf8ByteLengthWithNull(key)));
  }

  static long probeSize(long keyBytes) {
    if (keyBytes < 0) {
      throw new IllegalArgumentException("Invalid UTF-8 probe size: " + keyBytes);
    }
    if (keyBytes >= MAX_SIZE - 1024) {
      return MAX_SIZE;
    }
    return keyBytes + 1024;
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
    if (USE_COUNT.get()[0] <= 0) {
      throw new IllegalStateException(
          "ScratchBuffer.keyAndWideValue() called without active acquire()");
    }
    State s = getOrCreateState();
    s.keyValueSlots.keySeg = keySeg;
    s.keyValueSlots.valueSeg = valueSeg;
    return s.keyValueSlots;
  }

  /**
   * Acquire a usage slot for managed lifecycle owners (for example, PdfDocument instances).
   *
   * <p>Uses a thread-local primitive array to avoid boxing allocations on increment/decrement.
   */
  public static void acquire() {
    USE_COUNT.get()[0]++;
  }

  /**
   * Acquires the scratch buffer and returns a zero-allocation AutoCloseable scope.
   *
   * <p>Usage:
   *
   * <pre>{@code
   * try (var scope = ScratchBuffer.acquireScope()) {
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
    int[] countRef = USE_COUNT.get();
    int count = countRef[0];
    if (count <= 0) return;
    count--;
    countRef[0] = count;
    if (count == 0) {
      State s = STATE.get();
      if (s != null) {
        s.close();
        STATE.remove();
      }
      USE_COUNT.remove();
    }
  }

  /** Returns a thread-local char array for temporary string construction. */
  public static char[] getCharArray(int minChars) {
    if (USE_COUNT.get()[0] <= 0) {
      throw new IllegalStateException(
          "ScratchBuffer.getCharArray() called without active acquire()");
    }
    State s = getOrCreateState();
    return s.getCharArray(minChars);
  }

  /** Returns a thread-local byte array for temporary UTF-16LE decode staging. */
  public static byte[] getByteArray(int minBytes) {
    if (USE_COUNT.get()[0] <= 0) {
      throw new IllegalStateException(
          "ScratchBuffer.getByteArray() called without active acquire()");
    }
    State s = getOrCreateState();
    return s.getByteArray(minBytes);
  }

  /** Returns a dedicated scratch slab for visitor loops that must survive nested get() calls. */
  public static MemorySegment getLoopScratch(long minBytes) {
    if (USE_COUNT.get()[0] <= 0) {
      throw new IllegalStateException(
          "ScratchBuffer.getLoopScratch() called without active acquire()");
    }
    State s = getOrCreateState();
    return s.getLoopScratch(minBytes);
  }

  static long currentCapacity() {
    State s = STATE.get();
    return s == null ? 0 : s.segment.byteSize();
  }

  private static State getOrCreateState() {
    State s = STATE.get();
    if (s == null) {
      s = new State();
      STATE.set(s);
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
    private final List<Arena> arenas;
    private Arena arena;
    private Arena loopArena;
    private MemorySegment segment;
    private MemorySegment steadySegment;
    private MemorySegment largestSegment;
    private MemorySegment loopScratch;
    private char[] charArray;
    private byte[] byteArray;
    private final KeyValueSlots keyValueSlots;

    /**
     * State is only valid between construction and the {@link #close()} call. Subsequent accesses
     * are protected by the {@link ThreadLocal#remove()} in {@link ScratchBuffer#release()}.
     */
    State() {
      this.arenas = new ArrayList<>(2);
      this.arena = Arena.ofConfined();
      this.arenas.add(this.arena);
      this.segment = arena.allocate(INITIAL_SIZE, 8);
      this.steadySegment = segment;
      this.largestSegment = segment;
      this.loopArena = Arena.ofConfined();
      this.arenas.add(this.loopArena);
      this.loopScratch = loopArena.allocate(64, 8);
      this.charArray = new char[1024];
      this.byteArray = new byte[2048];
      this.keyValueSlots = new KeyValueSlots();
    }

    char[] getCharArray(int minChars) {
      if (charArray.length < minChars) {
        charArray = new char[Math.max(charArray.length * 2, minChars)];
      }
      return charArray;
    }

    byte[] getByteArray(int minBytes) {
      if (byteArray.length < minBytes) {
        byteArray = new byte[Math.max(byteArray.length * 2, minBytes)];
      }
      return byteArray;
    }

    MemorySegment getLoopScratch(long minBytes) {
      if (minBytes < 0 || minBytes > MAX_SIZE) {
        throw new IllegalArgumentException("Invalid loop scratch size: " + minBytes);
      }
      if (loopScratch.byteSize() < minBytes) {
        this.loopArena = Arena.ofConfined();
        this.arenas.add(this.loopArena);
        this.loopScratch = loopArena.allocate(minBytes, 8);
      }
      return loopScratch;
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
        if (minBytes > 1024 * 1024) {
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
}
