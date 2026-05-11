package org.grimmory.pdfium4j.internal;

import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Internal helper for performing native PDFium saves (e.g. FPDF_SaveAsCopy) into Java
 * OutputStreams.
 */
public final class PdfDocumentNativeSaver {

  private static final MethodHandle WRITE_BLOCK_MH;

  static {
    try {
      WRITE_BLOCK_MH =
          MethodHandles.lookup()
              .findStatic(
                  PdfDocumentNativeSaver.class,
                  "writeBlock",
                  MethodType.methodType(
                      int.class, MemorySegment.class, MemorySegment.class, long.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private PdfDocumentNativeSaver() {}

  private static final AtomicLong ID_GEN = new AtomicLong(1);
  private static final Map<Long, WriteContext> CONTEXTS = new ConcurrentHashMap<>();

  /** Saves the native document handle to the provided stream. */
  public static void save(MemorySegment docHandle, OutputStream out) throws IOException {
    long id = ID_GEN.getAndIncrement();
    try (Arena arena = Arena.ofConfined()) {
      WriteContext ctx = new WriteContext(out);
      CONTEXTS.put(id, ctx);

      // Create upcall stub for the callback
      MemorySegment callback =
          FfmHelper.LINKER.upcallStub(WRITE_BLOCK_MH, EditBindings.WRITE_BLOCK_DESC, arena);

      // Populate FPDF_FILEWRITE struct
      MemorySegment fileWrite = arena.allocate(EditBindings.FPDF_FILEWRITE_LAYOUT);
      fileWrite.set(ValueLayout.JAVA_INT, 0, 1); // version
      fileWrite.set(ValueLayout.ADDRESS, 8, callback); // WriteBlock
      fileWrite.set(ValueLayout.ADDRESS, 16, MemorySegment.ofAddress(id)); // bufferId (as pointer value)

      int rc = (int) EditBindings.fpdfSaveAsCopy().invokeExact(docHandle, fileWrite, 0);

      if (rc == 0) {
        throw new IOException("Native PDFium save failed");
      }
      out.flush();
    } catch (Throwable t) {
      if (t instanceof IOException e) throw e;
      throw new IOException("Native save error", t);
    } finally {
      CONTEXTS.remove(id);
    }
  }

  private record WriteContext(OutputStream out) {}

  @SuppressWarnings("unused")
  private static int writeBlock(MemorySegment pThis, MemorySegment pData, long size) {
    WriteContext ctx = CONTEXTS.get(pThis.address());
    if (ctx == null) return 0;
    try {
      if (size > 0) {
        ctx.out.write(pData.reinterpret(size).toArray(ValueLayout.JAVA_BYTE));
      }
      return 1;
    } catch (IOException _) {
      return 0;
    }
  }
}
