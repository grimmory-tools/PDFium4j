package org.grimmory.pdfium4j.internal;

import static java.lang.foreign.ValueLayout.*;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

/** FFM bindings for PDFium annotation functions from {@code fpdf_annot.h}. */
public final class AnnotBindings {

  private static final Linker LINKER = Linker.nativeLinker();
  private static final SymbolLookup LOOKUP = SymbolLookup.loaderLookup();

  private AnnotBindings() {}

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
    // Annotation support is technically optional but core to grimmory
    Objects.requireNonNull(FPDFPage_GetAnnotCount, "FPDFPage_GetAnnotCount");
  }

  /** FS_RECTF struct layout: left, bottom, right, top (all floats). */
  public static final StructLayout FS_RECTF_LAYOUT =
      MemoryLayout.structLayout(
          JAVA_FLOAT.withName("left"),
          JAVA_FLOAT.withName("bottom"),
          JAVA_FLOAT.withName("right"),
          JAVA_FLOAT.withName("top"));

  /** Get the number of annotations on a page. */
  public static final MethodHandle FPDFPage_GetAnnotCount =
      downcallCritical("FPDFPage_GetAnnotCount", FunctionDescriptor.of(JAVA_INT, ADDRESS));

  /** Get annotation by index. Returns FPDF_ANNOTATION (must be closed). */
  public static final MethodHandle FPDFPage_GetAnnot =
      downcall("FPDFPage_GetAnnot", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));

  /** Close an annotation handle. */
  public static final MethodHandle FPDFPage_CloseAnnot =
      downcall("FPDFPage_CloseAnnot", FunctionDescriptor.ofVoid(ADDRESS));

  /** Get annotation subtype. Returns FPDF_ANNOTATION_SUBTYPE (int). */
  public static final MethodHandle FPDFAnnot_GetSubtype =
      downcallCritical("FPDFAnnot_GetSubtype", FunctionDescriptor.of(JAVA_INT, ADDRESS));

  /**
   * Get annotation string value by key (UTF-16LE, double-call pattern). Common keys: "Contents",
   * "T" (author), "Subj" (subject), "M" (modification date).
   */
  public static final MethodHandle FPDFAnnot_GetStringValue =
      downcall(
          "FPDFAnnot_GetStringValue",
          FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG));

  /** Get annotation bounding rectangle. Writes into FS_RECTF. Returns 1 on success. */
  public static final MethodHandle FPDFAnnot_GetRect =
      downcall("FPDFAnnot_GetRect", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
}
