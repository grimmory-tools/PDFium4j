package org.grimmory.pdfium4j.internal;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.grimmory.pdfium4j.PdfiumLibrary;
import org.grimmory.pdfium4j.model.MetadataTag;

/** Internal helper for PDF document metadata extraction. */
public final class PdfDocumentMetadata {

  static {
    try {
      MethodHandle mh =
          MethodHandles.lookup()
              .findStatic(
                  PdfDocumentMetadata.class,
                  "nativeMetadataCallback",
                  MethodType.methodType(
                      int.class, MemorySegment.class, MemorySegment.class, MemorySegment.class));
      ShimBindings.initializeMetadataCallback(mh);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @SuppressWarnings("unused")
  private static int nativeMetadataCallback(
      MemorySegment keyPtr, MemorySegment valPtr, MemorySegment userdata) {
    String key = keyPtr.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_8);
    String val = valPtr.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_8);
    @SuppressWarnings("unchecked")
    Map<String, String> map = RegistryHolder.METADATA_MAPS.get(userdata.address());
    if (map != null) {
      map.put(key, val);
    }
    return 0;
  }

  private static final class RegistryHolder {
    private static final Map<Long, Map<String, String>> METADATA_MAPS = new ConcurrentHashMap<>();
    private static final AtomicLong COUNTER = new AtomicLong(1);
  }

  public static Map<String, String> readAllInfoDict(
      Path path, byte[] bytes, MemorySegment segment) {
    Map<String, String> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    long id = RegistryHolder.COUNTER.getAndIncrement();
    RegistryHolder.METADATA_MAPS.put(id, result);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment ud = MemorySegment.ofAddress(id);
      if (path != null) {
        MemorySegment pathSeg = arena.allocateFrom(path.toAbsolutePath().toString());
        var _ =
            (int)
                ShimBindings.pdfium4jReadInfoDict()
                    .invokeExact(pathSeg, ShimBindings.metadataCallback(), ud);
      } else if (bytes != null) {
        MemorySegment mem = arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);
        var _ =
            (int)
                ShimBindings.pdfium4jReadInfoDictMem()
                    .invokeExact(mem, (long) bytes.length, ShimBindings.metadataCallback(), ud);
      } else if (segment != null && segment != MemorySegment.NULL) {
        // Reinterpret the segment to be safe for QPDF which might expect a larger scope than we
        // think
        // but QPDF processMemoryFile should respect the length.
        var _ =
            (int)
                ShimBindings.pdfium4jReadInfoDictMem()
                    .invokeExact(segment, segment.byteSize(), ShimBindings.metadataCallback(), ud);
      }
    } catch (Throwable t) {
      // Fallback to empty if QPDF fails - log is fine but we keep it simple here
      InternalLogger.warn("QPDF metadata enumeration failed: " + t.getMessage());
    } finally {
      RegistryHolder.METADATA_MAPS.remove(id);
    }
    return result;
  }

  public static void ensureInitialized() {
    // This method just forces the class to be loaded and the static block to run
  }

  private record MetadataHolder(Map<MetadataTag, MemorySegment> segments, MethodHandle[] handles) {
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof MetadataHolder(var s, var h))) return false;
      return Objects.equals(segments, s) && Arrays.equals(handles, h);
    }

    @Override
    public int hashCode() {
      int result = Objects.hashCode(segments);
      result = 31 * result + Arrays.hashCode(handles);
      return result;
    }

    @Override
    public String toString() {
      return "MetadataHolder[segments=" + segments + ", handles=" + Arrays.toString(handles) + "]";
    }
  }

  private static final StableValue<MetadataHolder> HOLDER = StableValue.of();

  private static MetadataHolder holder() {
    return HOLDER.orElseSet(
        () -> {
          PdfiumLibrary.ensureInitialized();
          Arena global = Arena.global();
          MetadataTag[] tags = MetadataTag.values();
          Map<MetadataTag, MemorySegment> segments = new EnumMap<>(MetadataTag.class);
          MethodHandle[] handles = new MethodHandle[tags.length];

          for (MetadataTag tag : tags) {
            MemorySegment keySeg = global.allocateFrom(tag.pdfKey(), StandardCharsets.UTF_8);
            segments.put(tag, keySeg);
            handles[tag.ordinal()] =
                MethodHandles.insertArguments(DocBindings.fpdfGetMetaText(), 1, keySeg);
          }
          return new MetadataHolder(segments, handles);
        });
  }

  private PdfDocumentMetadata() {}

  public static MemorySegment tagSegment(MetadataTag tag) {
    return holder().segments().get(tag);
  }

  public static MethodHandle getHandle(MetadataTag tag) {
    return holder().handles()[tag.ordinal()];
  }

  public static String readMetadataString(
      MemorySegment handle,
      MetadataTag tag,
      MethodHandle m,
      Map<MetadataTag, String> pending,
      MetadataFallbackProvider fallback) {
    if (pending.containsKey(tag)) {
      return pending.get(tag);
    }
    try (var _ = ScratchBuffer.acquireScope()) {
      long needed = (long) m.invokeExact(handle, MemorySegment.NULL, 0L);
      if (needed <= 2) return fallback.get(tag).orElse(null);

      MemorySegment buf =
          (needed <= 8192) ? ScratchBuffer.getMetadataBuffer() : ScratchBuffer.get(needed);
      long copied = (long) m.invokeExact(handle, buf, needed);
      if (copied <= 2) return fallback.get(tag).orElse(null);

      return FfmHelper.fromWideString(buf, copied);
    } catch (Error e) {
      throw e;
    } catch (Throwable _) {
      return fallback.get(tag).orElse(null);
    }
  }

  @FunctionalInterface
  public interface MetadataFallbackProvider {
    Optional<String> get(MetadataTag tag);
  }
}
