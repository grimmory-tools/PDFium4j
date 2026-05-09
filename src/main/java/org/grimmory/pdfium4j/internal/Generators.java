package org.grimmory.pdfium4j.internal;

import java.lang.foreign.Linker;
import java.nio.file.attribute.FileAttribute;
import java.util.function.Supplier;

/**
 * Utility for providing shared constant instances of commonly used objects to avoid allocations.
 */
public final class Generators {

  private static final StableValue<FileAttribute<?>[]> EMPTY_FILE_ATTRIBUTES = StableValue.of();
  private static final StableValue<byte[]> EMPTY_BYTE_ARRAY = StableValue.of();
  private static final StableValue<int[]> EMPTY_INT_ARRAY = StableValue.of();
  private static final StableValue<long[]> EMPTY_LONG_ARRAY = StableValue.of();
  private static final StableValue<Linker.Option[]> NO_OPTIONS = StableValue.of();

  private Generators() {}

  /** Returns a shared zero-length {@link FileAttribute} array. */
  @SuppressWarnings("unchecked")
  public static <T> FileAttribute<T>[] emptyFileAttributes() {
    return (FileAttribute<T>[]) computeIfAbsent(EMPTY_FILE_ATTRIBUTES, () -> new FileAttribute[0]);
  }

  /** Returns a shared zero-length byte array. */
  public static byte[] emptyByteArray() {
    return computeIfAbsent(EMPTY_BYTE_ARRAY, () -> new byte[0]);
  }

  /** Returns a shared zero-length int array. */
  public static int[] emptyIntArray() {
    return computeIfAbsent(EMPTY_INT_ARRAY, () -> new int[0]);
  }

  /** Returns a shared zero-length long array. */
  public static long[] emptyLongArray() {
    return computeIfAbsent(EMPTY_LONG_ARRAY, () -> new long[0]);
  }

  /** Returns a shared zero-length {@link Linker.Option} array. */
  public static Linker.Option[] noOptions() {
    return computeIfAbsent(NO_OPTIONS, () -> new Linker.Option[0]);
  }

  private static <T> T computeIfAbsent(StableValue<T> stable, Supplier<T> supplier) {
    return stable.orElseSet(supplier);
  }
}
