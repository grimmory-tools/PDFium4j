package org.grimmory.pdfium4j.internal;

import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import org.grimmory.pdfium4j.PdfiumLibrary;
import org.grimmory.pdfium4j.exception.PdfPasswordException;
import org.grimmory.pdfium4j.exception.PdfiumException;
import org.grimmory.pdfium4j.model.PdfErrorCode;

/**
 * Internal helper for probing PDF files without triggering Java allocations. Used for
 * high-performance corpus scanning and validation.
 */
public final class NoAllocationPathProbe implements AutoCloseable {
  private final Arena arena;
  private final String pathLabel;
  private final MemorySegment pathSeg;
  private final MemorySegment passwordSeg;

  public NoAllocationPathProbe(Path path, String password) {
    this.arena = Arena.ofShared();
    this.pathLabel = path.toString();
    this.pathSeg = arena.allocateFrom(pathLabel);
    this.passwordSeg = password != null ? arena.allocateFrom(password) : MemorySegment.NULL;
  }

  public void inspect(int[] output, MemorySegment trailerBuffer) {
    if (output == null || output.length < 3) {
      throw new IllegalArgumentException("output must have length >= 3");
    }
    PdfiumLibrary.ensureInitialized();
    MemorySegment doc = MemorySegment.NULL;
    try {
      doc = (MemorySegment) ViewBindings.fpdfLoadDocument().invokeExact(pathSeg, passwordSeg);
      if (FfmHelper.isNull(doc)) {
        int err = (int) ViewBindings.fpdfGetLastError().invokeExact();
        throw mapOpenError("Failed to probe document: " + pathLabel, err);
      }
      output[0] = (int) ViewBindings.fpdfGetPageCount().invokeExact(doc);
      output[1] =
          ViewBindings.fpdfDocumentHasValidCrossReferenceTable() == null
              ? 1
              : (int) ViewBindings.fpdfDocumentHasValidCrossReferenceTable().invokeExact(doc);
      output[2] = readTrailerEndsInto(doc, trailerBuffer);
    } catch (PdfiumException e) {
      throw e;
    } catch (Error e) {
      throw e;
    } catch (Throwable t) {
      throw new PdfiumException("Failed to inspect document without allocations", t);
    } finally {
      if (!FfmHelper.isNull(doc)) {
        try {
          ViewBindings.fpdfCloseDocument().invokeExact(doc);
        } catch (Throwable closeError) {
          PdfiumLibrary.ignore(closeError);
        }
      }
    }
  }

  @Override
  public void close() {
    arena.close();
  }

  private static int readTrailerEndsInto(MemorySegment doc, MemorySegment trailerBuffer)
      throws Throwable {
    if (ViewBindings.fpdfGetTrailerEnds() == null
        || trailerBuffer == null
        || FfmHelper.isNull(trailerBuffer)) {
      return 0;
    }
    long capacity = trailerBuffer.byteSize() / JAVA_INT.byteSize();
    if (capacity <= 0) {
      return 0;
    }
    long written =
        (long) ViewBindings.fpdfGetTrailerEnds().invokeExact(doc, trailerBuffer, capacity);
    if (written <= 0) {
      return 0;
    }
    if (written > capacity) {
      throw new IllegalArgumentException("trailerBuffer is too small for reported trailer count");
    }
    return Math.toIntExact(written);
  }

  private static PdfiumException mapOpenError(String ctx, int code) {
    PdfErrorCode ec = PdfErrorCode.fromCode(code);
    if (ec == PdfErrorCode.PASSWORD) {
      return new PdfPasswordException(ctx, ec, "open", null);
    }
    return new PdfiumException(ctx, ec, "open", null);
  }
}
