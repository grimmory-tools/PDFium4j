package org.grimmory.pdfium4j.internal;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

/** FFM bindings for PDFium document metadata and bookmark functions from {@code fpdf_doc.h}. */
public final class DocBindings {

  private static final Linker LINKER = Linker.nativeLinker();
  private static final SymbolLookup LOOKUP = SymbolLookup.loaderLookup();

  private DocBindings() {}

  private static MethodHandle downcall(String name, FunctionDescriptor desc) {
    return LOOKUP.find(name).map(addr -> LINKER.downcallHandle(addr, desc)).orElse(null);
  }

  private static MethodHandle downcallCritical(String name, FunctionDescriptor desc) {
    return LOOKUP
        .find(name)
        .map(addr -> LINKER.downcallHandle(addr, desc, Linker.Option.critical(false)))
        .orElse(null);
  }

  public static void checkRequired() {
    // Metadata is often provided by FPDF_GetMetaText, but XMP is optional as we have a byte-level
    // fallback
    Objects.requireNonNull(FPDF_GetMetaText, "FPDF_GetMetaText");
  }

  public static final MethodHandle FPDF_GetMetaText =
      downcall(
          "FPDF_GetMetaText",
          FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG));

  public static final MethodHandle FPDF_GetXMPMetadata =
      downcall(
          "FPDF_GetXMPMetadata", FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, JAVA_LONG));

  public static final MethodHandle FPDF_GetFileVersion =
      downcallCritical("FPDF_GetFileVersion", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

  public static final MethodHandle FPDF_GetPageLabel =
      downcall(
          "FPDF_GetPageLabel",
          FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, ADDRESS, JAVA_LONG));

  public static final MethodHandle FPDFPage_Delete =
      downcall("FPDFPage_Delete", FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

  public static final MethodHandle FPDFBookmark_GetFirstChild =
      downcallCritical(
          "FPDFBookmark_GetFirstChild", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

  public static final MethodHandle FPDFBookmark_GetNextSibling =
      downcallCritical(
          "FPDFBookmark_GetNextSibling", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

  public static final MethodHandle FPDFBookmark_GetTitle =
      downcall(
          "FPDFBookmark_GetTitle", FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, JAVA_LONG));

  public static final MethodHandle FPDFBookmark_GetDest =
      downcallCritical("FPDFBookmark_GetDest", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

  public static final MethodHandle FPDFBookmark_GetAction =
      downcallCritical("FPDFBookmark_GetAction", FunctionDescriptor.of(ADDRESS, ADDRESS));

  public static final long PDFACTION_GOTO = 1;

  public static final MethodHandle FPDFAction_GetType =
      downcallCritical("FPDFAction_GetType", FunctionDescriptor.of(JAVA_LONG, ADDRESS));

  public static final MethodHandle FPDFAction_GetDest =
      downcallCritical("FPDFAction_GetDest", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

  public static final MethodHandle FPDFDest_GetDestPageIndex =
      downcallCritical(
          "FPDFDest_GetDestPageIndex", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
}
