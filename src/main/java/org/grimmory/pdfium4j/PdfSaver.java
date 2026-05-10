package org.grimmory.pdfium4j;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.grimmory.pdfium4j.internal.ShimBindings;
import org.grimmory.pdfium4j.internal.XmpUpdate;
import org.grimmory.pdfium4j.model.MetadataTag;
import org.grimmory.pdfium4j.model.XmpMetadata;

/** Handles saving PDF documents via QPDF. */
final class PdfSaver {
  private static final XmpMetadataWriter XMP_WRITER = new XmpMetadataWriter();

  static {
    try {
      MethodHandle mh =
          MethodHandles.lookup()
              .findStatic(
                  PdfSaver.class,
                  "writeBlockCallback",
                  MethodType.methodType(
                      int.class, MemorySegment.class, MemorySegment.class, long.class));
      ShimBindings.initializeWriteBlockCallback(mh);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  /** Parameters for saving a PDF document. */
  record SaveParams(
      MemorySegment docHandle,
      Map<MetadataTag, String> pendingMetadata,
      Map<String, String> pendingCustomMetadata,
      MetadataProvider nativeMetadata,
      boolean hasInfoUpdate,
      XmpUpdate pendingXmp,
      Path sourcePath,
      Path targetPath,
      byte[] sourceBytes,
      MemorySegment sourceSegment,
      OutputStream out) {}

  @FunctionalInterface
  interface MetadataProvider {
    String get(MetadataTag tag);
  }

  private PdfSaver() {}

  public static void save(SaveParams params) throws IOException {
    if (params.targetPath() != null) {
      // File-to-file save
      Path source = params.sourcePath();
      Path tempSource = null;
      try {
        if (source == null) {
          tempSource = Files.createTempFile("pdfium4j-src-", ".pdf");
          try {
            if (tempSource.getFileSystem().supportedFileAttributeViews().contains("posix")) {
              Files.setPosixFilePermissions(
                  tempSource, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            }
          } catch (UnsupportedOperationException | IOException ignored) {
            // Ignored if POSIX is not supported
          }
          if (params.sourceBytes() != null) {
            Files.write(tempSource, params.sourceBytes());
          } else if (params.sourceSegment() != MemorySegment.NULL) {
            try (FileChannel fc = FileChannel.open(tempSource, StandardOpenOption.WRITE)) {
              fc.write(params.sourceSegment().asByteBuffer());
            }
          }
          source = tempSource;
        }
        saveNative(
            source.toString(),
            params.targetPath().toString(),
            params.pendingXmp(),
            params.pendingMetadata(),
            params.pendingCustomMetadata());
      } finally {
        if (tempSource != null) Files.deleteIfExists(tempSource);
      }
    } else if (params.out() != null) {
      // Stream-based save (no temp files if we have bytes/segment)
      if (params.sourceBytes() != null) {
        try (Arena arena = Arena.ofConfined()) {
          MemorySegment nativeSeg = arena.allocateFrom(JAVA_BYTE, params.sourceBytes());
          saveFromMemory(
              nativeSeg,
              params.out(),
              params.pendingXmp(),
              params.pendingMetadata(),
              params.pendingCustomMetadata());
        }
      } else if (params.sourceSegment() != MemorySegment.NULL) {
        saveFromMemory(
            params.sourceSegment(),
            params.out(),
            params.pendingXmp(),
            params.pendingMetadata(),
            params.pendingCustomMetadata());
      } else if (params.sourcePath() != null) {
        // Map file to memory to avoid temp copy
        try (Arena arena = Arena.ofConfined();
            FileChannel fc = FileChannel.open(params.sourcePath(), StandardOpenOption.READ)) {
          MemorySegment segment = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size(), arena);
          saveFromMemory(
              segment,
              params.out(),
              params.pendingXmp(),
              params.pendingMetadata(),
              params.pendingCustomMetadata());
        }
      }
    }
  }

  private static void saveFromMemory(
      MemorySegment src,
      OutputStream out,
      XmpUpdate xmp,
      Map<MetadataTag, String> metadata,
      Map<String, String> customMetadata)
      throws IOException {
    try (Arena arena = Arena.ofConfined()) {
      XmpResult xmpRes = prepareXmp(arena, xmp);
      MetaResult metaRes = prepareMetadataPairs(arena, metadata, customMetadata);

      var writeContext = new WriteContext(out);
      MemorySegment callback = ShimBindings.writeBlockCallback();

      int rc;
      try {
        rc =
            (int)
                ShimBindings.pdfium4jSaveWithMetadataMem()
                    .invokeExact(
                        src,
                        src.byteSize(),
                        callback,
                        writeContext.pointer(),
                        xmpRes.segment(),
                        xmpRes.length(),
                        metaRes.segment(),
                        metaRes.count());
      } catch (Throwable t) {
        throw new IOException("Native save failed", t);
      }

      if (rc != 0) {
        throw mapErrorCode(rc);
      }
    }
  }

  private static void saveNative(
      String src,
      String dst,
      XmpUpdate xmp,
      Map<MetadataTag, String> metadata,
      Map<String, String> customMetadata)
      throws IOException {
    try (Arena arena = Arena.ofConfined()) {
      XmpResult xmpRes = prepareXmp(arena, xmp);
      MetaResult metaRes = prepareMetadataPairs(arena, metadata, customMetadata);

      int rc;
      try {
        rc =
            (int)
                ShimBindings.pdfium4jSaveWithMetadata()
                    .invokeExact(
                        arena.allocateFrom(src),
                        arena.allocateFrom(dst),
                        xmpRes.segment(),
                        xmpRes.length(),
                        metaRes.segment(),
                        metaRes.count());
      } catch (Throwable t) {
        throw new IOException("Native save failed", t);
      }

      if (rc != 0) {
        throw mapErrorCode(rc);
      }
    }
  }

  private record XmpResult(MemorySegment segment, int length) {}

  private record MetaResult(MemorySegment segment, int count) {}

  private static XmpResult prepareXmp(Arena arena, XmpUpdate xmp) throws IOException {
    if (xmp == null) {
      return new XmpResult(MemorySegment.NULL, 0);
    }
    String xmpStr =
        switch (xmp) {
          case XmpUpdate.Raw(String r) -> r;
          case XmpUpdate.Structured(XmpMetadata m) -> {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            XMP_WRITER.write(m, baos);
            yield baos.toString(StandardCharsets.UTF_8);
          }
        };
    MemorySegment seg = arena.allocateFrom(xmpStr);
    return new XmpResult(seg, (int) seg.byteSize() - 1);
  }

  private static MetaResult prepareMetadataPairs(
      Arena arena, Map<MetadataTag, String> metadata, Map<String, String> customMetadata) {
    int totalMetaSize = metadata.size() + customMetadata.size();
    if (totalMetaSize == 0) {
      return new MetaResult(MemorySegment.NULL, 0);
    }
    MemorySegment metaPairs = arena.allocate(ValueLayout.ADDRESS, (long) totalMetaSize * 2);
    int i = 0;
    for (Map.Entry<MetadataTag, String> entry : metadata.entrySet()) {
      metaPairs.setAtIndex(ValueLayout.ADDRESS, i++, arena.allocateFrom(entry.getKey().pdfKey()));
      metaPairs.setAtIndex(ValueLayout.ADDRESS, i++, arena.allocateFrom(entry.getValue()));
    }
    for (Map.Entry<String, String> entry : customMetadata.entrySet()) {
      metaPairs.setAtIndex(ValueLayout.ADDRESS, i++, arena.allocateFrom(entry.getKey()));
      metaPairs.setAtIndex(ValueLayout.ADDRESS, i++, arena.allocateFrom(entry.getValue()));
    }
    return new MetaResult(metaPairs, totalMetaSize);
  }

  private static final class WriteContext {
    private final OutputStream out;
    private final MemorySegment pointer;

    WriteContext(OutputStream out) {
      this.out = out;
      // Using identity hash code as a unique identifier for this context
      this.pointer = MemorySegment.ofAddress(System.identityHashCode(this));
      RegistryHolder.CONTEXTS.put(this.pointer.address(), this);
    }

    MemorySegment pointer() {
      return pointer;
    }

    private static final ThreadLocal<byte[]> SCRATCH =
        ThreadLocal.withInitial(() -> new byte[16384]);

    void write(MemorySegment data, long size) throws IOException {
      if (size <= 0) return;
      byte[] scratch = SCRATCH.get();
      MemorySegment reinterpreted = data.reinterpret(size);
      long offset = 0;
      while (offset < size) {
        int len = (int) Math.min(size - offset, scratch.length);
        MemorySegment.copy(reinterpreted, ValueLayout.JAVA_BYTE, offset, scratch, 0, len);
        out.write(scratch, 0, len);
        offset += len;
      }
    }
  }

  private static final class RegistryHolder {
    static final Map<Long, WriteContext> CONTEXTS = new ConcurrentHashMap<>();
  }

  @SuppressWarnings("unused")
  private static int writeBlockCallback(MemorySegment pThis, MemorySegment pData, long size) {
    WriteContext ctx = RegistryHolder.CONTEXTS.get(pThis.address());
    if (ctx == null) return 0;
    try {
      ctx.write(pData, size);
      return 1;
    } catch (IOException _) {
      return 0;
    }
  }

  private static IOException mapErrorCode(int code) {
    return switch (code) {
      case -1 -> new IOException("Invalid parameters for QPDF save");
      case -2 -> new IOException("Failed to open or parse source PDF");
      case -3 -> new IOException("Cannot update encrypted PDF via QPDF save. Decrypt first.");
      case -5 -> new IOException("Failed to inject metadata into PDF");
      case -6 -> new IOException("Failed to write output PDF file");
      default -> new IOException("QPDF save failed with native code: " + code);
    };
  }
}
