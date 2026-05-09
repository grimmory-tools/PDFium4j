package org.grimmory.pdfium4j;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.grimmory.pdfium4j.exception.PdfiumException;
import org.grimmory.pdfium4j.internal.FfmHelper;
import org.grimmory.pdfium4j.internal.ScratchBuffer;
import org.grimmory.pdfium4j.internal.ShimBindings;
import org.grimmory.pdfium4j.model.PdfStructureElement;

/** Internal helper for reading the PDF structure tree (tagged PDF) via the shim. */
final class StructureTreeReader {

  private StructureTreeReader() {}

  static List<PdfStructureElement> read(MemorySegment pageHandle) {
    MemorySegment treeHandle;
    try {
      treeHandle = (MemorySegment) ShimBindings.pdfium4jStructTreeGet().invokeExact(pageHandle);
    } catch (Throwable t) {
      throw new PdfiumException("Failed to acquire structure tree", t);
    }

    if (FfmHelper.isNull(treeHandle)) {
      return List.of();
    }

    try {
      int count = (int) ShimBindings.pdfium4jStructTreeCountChildren().invokeExact(treeHandle);
      if (count <= 0) {
        return List.of();
      }

      List<PdfStructureElement> elements = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        MemorySegment elementHandle =
            (MemorySegment) ShimBindings.pdfium4jStructTreeGetChild().invokeExact(treeHandle, i);
        if (!FfmHelper.isNull(elementHandle)) {
          elements.add(readElement(elementHandle));
        }
      }

      return List.copyOf(elements);
    } catch (PdfiumException e) {
      throw e;
    } catch (Throwable t) {
      throw new PdfiumException("Failed to read structure tree", t);
    } finally {
      try {
        ShimBindings.pdfium4jStructTreeClose().invokeExact(treeHandle);
      } catch (Throwable ignored) {
        PdfiumLibrary.ignore(ignored);
      }
    }
  }

  private static PdfStructureElement readElement(MemorySegment elementHandle) throws Throwable {
    String type =
        readUtf8(elementHandle, ShimBindings.pdfium4jStructElementGetType()).orElse("Unknown");
    Optional<String> title = readUtf8(elementHandle, ShimBindings.pdfium4jStructElementGetTitle());
    Optional<String> altText =
        readUtf8(elementHandle, ShimBindings.pdfium4jStructElementGetAltText());
    Optional<String> actualText =
        readUtf8(elementHandle, ShimBindings.pdfium4jStructElementGetActualText());
    Optional<String> lang = readUtf8(elementHandle, ShimBindings.pdfium4jStructElementGetLang());

    int attributeCount =
        (int) ShimBindings.pdfium4jStructElementGetAttributeCount().invokeExact(elementHandle);
    int childCount =
        (int) ShimBindings.pdfium4jStructElementCountChildren().invokeExact(elementHandle);

    List<PdfStructureElement> children = new ArrayList<>(childCount);
    List<Integer> mcids = new ArrayList<>(childCount);

    for (int i = 0; i < childCount; i++) {
      MemorySegment childHandle =
          (MemorySegment)
              ShimBindings.pdfium4jStructElementGetChild().invokeExact(elementHandle, i);
      if (!FfmHelper.isNull(childHandle)) {
        children.add(readElement(childHandle));
      } else {
        int mcid = (int) ShimBindings.pdfium4jStructElementGetMcid().invokeExact(elementHandle, i);
        if (mcid != -1) {
          mcids.add(mcid);
        }
      }
    }

    return new PdfStructureElement(
        type, title, altText, actualText, lang, children, mcids, attributeCount);
  }

  private static Optional<String> readUtf8(MemorySegment elementHandle, MethodHandle getter)
      throws Throwable {
    try (var _ = ScratchBuffer.acquireScope()) {
      int needed = (int) getter.invokeExact(elementHandle, MemorySegment.NULL, 0);
      if (needed <= 1) return Optional.empty();

      MemorySegment buf = ScratchBuffer.get(needed);
      getter.invokeExact(elementHandle, buf, needed);
      return Optional.of(FfmHelper.fromUtf8String(buf, needed));
    }
  }
}
