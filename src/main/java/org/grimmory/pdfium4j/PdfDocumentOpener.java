package org.grimmory.pdfium4j;

import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.grimmory.pdfium4j.exception.PdfCorruptException;
import org.grimmory.pdfium4j.exception.PdfiumException;
import org.grimmory.pdfium4j.internal.DocBindings;
import org.grimmory.pdfium4j.internal.EditBindings;
import org.grimmory.pdfium4j.internal.FfmHelper;
import org.grimmory.pdfium4j.internal.IoUtils;
import org.grimmory.pdfium4j.internal.ViewBindings;
import org.grimmory.pdfium4j.model.PdfErrorCode;
import org.grimmory.pdfium4j.model.PdfProcessingPolicy;

/**
 * Internal helper for opening and repairing PDF documents. Extracts logic from PdfDocument to
 * reduce its complexity.
 */
final class PdfDocumentOpener {

  private static final Logger LOGGER = Logger.getLogger(PdfDocumentOpener.class.getName());

  private PdfDocumentOpener() {}

  static PdfDocument create(PdfProcessingPolicy policy) {
    PdfiumLibrary.ensureInitialized();
    try {
      MemorySegment h = (MemorySegment) EditBindings.fpdfCreateNewDocument().invokeExact();
      if (FfmHelper.isNull(h)) {
        throw new PdfiumException(
            "Failed to create new document", PdfErrorCode.UNKNOWN, "create", null);
      }
      return new PdfDocument(
          h, null, policy, new PdfDocument.SourceInfo(null, null, null, 0, Thread.currentThread()));
    } catch (PdfiumException e) {
      throw e;
    } catch (Throwable t) {
      throw new PdfiumException("Failed to create new document", t);
    }
  }

  static PdfDocument open(Path path, String password, PdfProcessingPolicy policy) {
    PdfiumLibrary.ensureInitialized();
    try {
      return openFromNativePath(path, password, policy, null);
    } catch (PdfCorruptException e) {
      if (policy.mode() == PdfProcessingPolicy.Mode.RECOVER) {
        return openWithRepair(path, password, policy);
      }
      throw e;
    }
  }

  static PdfDocument open(InputStream in, String password, PdfProcessingPolicy policy) {
    if (in == null) throw new IllegalArgumentException("InputStream must not be null");
    PdfiumLibrary.ensureInitialized();
    Path temp = null;
    try {
      temp = IoUtils.createTempFile("pdfium4j-stream-", ".pdf");
      try (InputStream input = in) {
        Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
      }
      return openStreamWithPossibleRepair(temp, password, policy);
    } catch (IOException e) {
      cleanupTempFile(temp);
      throw new PdfiumException("Failed to open document: failed to buffer input stream", e);
    }
  }

  private static PdfDocument openStreamWithPossibleRepair(
      Path temp, String password, PdfProcessingPolicy policy) {
    Path cleanupPath = temp;
    try {
      // Always hand temp ownership to the resulting document so it is removed on close().
      return openFromNativePath(temp, password, policy, cleanupPath);
    } catch (PdfCorruptException e) {
      if (policy.mode() == PdfProcessingPolicy.Mode.RECOVER) {
        try {
          return openWithRepair(cleanupPath, password, policy);
        } finally {
          cleanupTempFile(cleanupPath);
        }
      }
      cleanupTempFile(cleanupPath);
      throw e;
    } catch (Exception t) {
      cleanupTempFile(cleanupPath);
      throw t;
    }
  }

  static PdfDocument open(byte[] data, String password, PdfProcessingPolicy policy) {
    if (data == null || data.length == 0)
      throw new IllegalArgumentException("data is null or empty");
    PdfiumLibrary.ensureInitialized();
    Arena arena = Arena.ofShared();
    try {
      MemorySegment memSeg = arena.allocate(data.length);
      memSeg.copyFrom(MemorySegment.ofArray(data));

      PdfDocument pdfDoc = open(memSeg, password, policy, arena, data);

      if (policy.mode() == PdfProcessingPolicy.Mode.RECOVER
          && !pdfDoc.hasValidCrossReferenceTable()) {
        pdfDoc.close();
        return repairAndReopenFromBytes(data, password, policy);
      }
      return pdfDoc;
    } catch (PdfCorruptException e) {
      arena.close();
      if (policy.mode() == PdfProcessingPolicy.Mode.RECOVER) {
        return repairAndReopenFromBytes(data, password, policy);
      }
      throw e;
    } catch (PdfiumException e) {
      arena.close();
      throw e;
    } catch (Exception t) {
      arena.close();
      throw new PdfiumException(
          "Failed to open document: unexpected corruption or processing error", t);
    }
  }

  private static PdfDocument repairAndReopenFromBytes(
      byte[] data, String password, PdfProcessingPolicy policy) {
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      PdfSaver.SaveParams params =
          new PdfSaver.SaveParams(
              MemorySegment.NULL,
              Map.of(),
              Map.of(),
              _ -> null,
              false,
              null,
              null,
              null,
              data,
              MemorySegment.NULL,
              bos);
      PdfSaver.save(params);
      byte[] repaired = bos.toByteArray();
      return open(repaired, password, policy.withMode(PdfProcessingPolicy.Mode.STRICT));
    } catch (IOException | IllegalArgumentException e) {
      throw new PdfCorruptException(
          "Failed to open document: corruption detected and repair failed",
          PdfErrorCode.FORMAT,
          "open",
          null,
          e);
    }
  }

  static PdfDocument open(
      MemorySegment segment,
      String password,
      PdfProcessingPolicy policy,
      Arena arena,
      byte[] sourceBytes) {
    try {
      MemorySegment pwdSeg = password == null ? MemorySegment.NULL : arena.allocateFrom(password);
      MemorySegment docHandle =
          (MemorySegment)
              ViewBindings.fpdfLoadMemDocument64().invokeExact(segment, segment.byteSize(), pwdSeg);

      if (FfmHelper.isNull(docHandle)) {
        int err = (int) ViewBindings.fpdfGetLastError().invokeExact();
        throw PdfDocument.mapOpenError("Failed to open document: corruption detected", err);
      }

      return new PdfDocument(
          docHandle,
          arena,
          policy,
          new PdfDocument.SourceInfo(
              null, null, sourceBytes, readFileVersion(docHandle), Thread.currentThread()));
    } catch (PdfiumException e) {
      throw e;
    } catch (Throwable t) {
      throw new PdfiumException(
          "Failed to open document: unexpected corruption or processing error", t);
    }
  }

  private static PdfDocument openFromNativePath(
      Path path, String password, PdfProcessingPolicy policy, Path tempFile) {
    Arena docArena = Arena.ofShared();
    try {
      MemorySegment pathSeg = docArena.allocateFrom(path.toString());
      MemorySegment pwdSeg =
          (password != null) ? docArena.allocateFrom(password) : MemorySegment.NULL;
      MemorySegment doc =
          (MemorySegment) ViewBindings.fpdfLoadDocument().invokeExact(pathSeg, pwdSeg);

      if (FfmHelper.isNull(doc)) {
        int err = (int) ViewBindings.fpdfGetLastError().invokeExact();
        throw PdfDocument.mapOpenError("Failed to open document: corruption detected", err);
      }

      PdfDocument pdfDoc =
          new PdfDocument(
              doc,
              docArena,
              policy,
              new PdfDocument.SourceInfo(
                  path, tempFile, null, readFileVersion(doc), Thread.currentThread()));

      pdfDoc.sourceSegmentArena = Arena.ofShared();
      pdfDoc.sourceSegment = mapSourceSegment(path, pdfDoc.sourceSegmentArena);
      pdfDoc.state.updateSourceSegmentArena(pdfDoc.sourceSegmentArena);

      if (policy.mode() == PdfProcessingPolicy.Mode.RECOVER
          && !pdfDoc.hasValidCrossReferenceTable()) {
        pdfDoc.close();
        return openWithRepair(path, password, policy);
      }
      return pdfDoc;
    } catch (PdfiumException e) {
      docArena.close();
      throw e;
    } catch (Throwable t) {
      docArena.close();
      throw new PdfiumException("Failed to open document: corruption detected for " + path, t);
    }
  }

  private static PdfDocument openWithRepair(
      Path path, String password, PdfProcessingPolicy resolvedPolicy) {
    if (LOGGER.isLoggable(Level.WARNING)) {
      LOGGER.log(
          Level.WARNING,
          "Document corruption detected for {0}. Attempting automatic repair...",
          path);
    }
    Path temp;
    try {
      temp = IoUtils.createTempFile("pdfium4j-autorepair-", ".pdf");
      try (OutputStream out = Files.newOutputStream(temp)) {
        PdfSaver.SaveParams params =
            new PdfSaver.SaveParams(
                MemorySegment.NULL,
                Map.of(),
                Map.of(),
                _ -> null,
                false,
                null,
                path,
                temp,
                null,
                MemorySegment.NULL,
                out);
        PdfSaver.save(params);
      }
      return openFromNativePath(
          temp, password, resolvedPolicy.withMode(PdfProcessingPolicy.Mode.STRICT), temp);
    } catch (Exception e) {
      if (LOGGER.isLoggable(Level.SEVERE)) {
        LOGGER.log(Level.SEVERE, "Automatic repair failed for {0}", path);
      }
      throw new PdfCorruptException(
          "Failed to open document: corruption detected and repair failed for " + path,
          PdfErrorCode.FORMAT,
          "open",
          path.toString(),
          e);
    }
  }

  private static int readFileVersion(MemorySegment doc) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment version = arena.allocate(JAVA_INT.byteSize(), JAVA_INT.byteAlignment());
      int ok = (int) DocBindings.fpdfGetFileVersion().invokeExact(doc, version);
      return ok != 0 ? version.get(JAVA_INT, 0) : 0;
    } catch (Throwable _) {
      return 0;
    }
  }

  private static MemorySegment mapSourceSegment(Path path, Arena arena) {
    try (FileChannel fc = FileChannel.open(path, StandardOpenOption.READ)) {
      return fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size(), arena);
    } catch (IOException e) {
      PdfiumLibrary.ignore(e);
      return null;
    }
  }

  private static void cleanupTempFile(Path temp) {
    if (temp != null) {
      try {
        Files.deleteIfExists(temp);
      } catch (IOException e) {
        PdfiumLibrary.ignore(e);
      }
    }
  }
}
