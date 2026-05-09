package org.grimmory.pdfium4j.internal;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.grimmory.pdfium4j.PdfiumLibrary;
import org.grimmory.pdfium4j.model.MetadataTag;

/** Internal helper for PDF document metadata extraction. */
public final class PdfDocumentMetadata {

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
    } catch (Throwable _) {
      return fallback.get(tag).orElse(null);
    }
  }

  @FunctionalInterface
  public interface MetadataFallbackProvider {
    Optional<String> get(MetadataTag tag);
  }
}
