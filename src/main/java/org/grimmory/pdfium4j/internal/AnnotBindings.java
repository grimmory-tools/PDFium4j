package org.grimmory.pdfium4j.internal;

import static org.grimmory.pdfium4j.internal.FfmHelper.C_INT;
import static org.grimmory.pdfium4j.internal.FfmHelper.C_LONG;
import static org.grimmory.pdfium4j.internal.FfmHelper.C_POINTER;
import static org.grimmory.pdfium4j.internal.FfmHelper.LINKER;
import static org.grimmory.pdfium4j.internal.FfmHelper.LOOKUP;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

/** FFM bindings for PDFium annotation functions from {@code fpdf_annot.h}. */
public final class AnnotBindings {

  private AnnotBindings() {}

  private static MethodHandle find(String name, FunctionDescriptor desc, boolean critical) {
    MemorySegment addr = LOOKUP.find(name).orElse(null);
    if (addr == null) return null;
    return LINKER.downcallHandle(
        addr, desc, critical ? FfmHelper.CRITICAL_OPTIONS : FfmHelper.NO_OPTIONS);
  }

  public static void checkRequired() {
    try {
      Objects.requireNonNull(fpdfPageGetAnnotCount(), "FPDFPage_GetAnnotCount");
      Objects.requireNonNull(fpdfPageGetAnnot(), "FPDFPage_GetAnnot");
      Objects.requireNonNull(fpdfPageCloseAnnot(), "FPDFPage_CloseAnnot");
      Objects.requireNonNull(fpdfAnnotGetSubtype(), "FPDFAnnot_GetSubtype");
      Objects.requireNonNull(fpdfAnnotGetStringValue(), "FPDFAnnot_GetStringValue");
      Objects.requireNonNull(fpdfAnnotGetRect(), "FPDFAnnot_GetRect");
    } catch (NullPointerException e) {
      throw new RuntimeException("Missing required PDFium annot symbol: " + e.getMessage(), e);
    }
  }

  /** FS_RECTF struct layout: left, top, right, bottom (all floats). */
  public static final StructLayout FS_RECTF_LAYOUT =
      MemoryLayout.structLayout(
          ValueLayout.JAVA_FLOAT.withName("left"),
          ValueLayout.JAVA_FLOAT.withName("top"),
          ValueLayout.JAVA_FLOAT.withName("right"),
          ValueLayout.JAVA_FLOAT.withName("bottom"));

  private static final StableValue<MethodHandle> FPDFPage_GetAnnotCount_V = StableValue.of();

  public static MethodHandle fpdfPageGetAnnotCount() {
    return FPDFPage_GetAnnotCount_V.orElseSet(
        () -> find("FPDFPage_GetAnnotCount", FunctionDescriptor.of(C_INT, C_POINTER), true));
  }

  private static final StableValue<MethodHandle> FPDFPage_GetAnnot_V = StableValue.of();

  public static MethodHandle fpdfPageGetAnnot() {
    return FPDFPage_GetAnnot_V.orElseSet(
        () -> find("FPDFPage_GetAnnot", FunctionDescriptor.of(C_POINTER, C_POINTER, C_INT), true));
  }

  private static final StableValue<MethodHandle> FPDFPage_CloseAnnot_V = StableValue.of();

  public static MethodHandle fpdfPageCloseAnnot() {
    return FPDFPage_CloseAnnot_V.orElseSet(
        () -> find("FPDFPage_CloseAnnot", FunctionDescriptor.ofVoid(C_POINTER), true));
  }

  private static final StableValue<MethodHandle> FPDFAnnot_GetSubtype_V = StableValue.of();

  public static MethodHandle fpdfAnnotGetSubtype() {
    return FPDFAnnot_GetSubtype_V.orElseSet(
        () -> find("FPDFAnnot_GetSubtype", FunctionDescriptor.of(C_INT, C_POINTER), true));
  }

  private static final StableValue<MethodHandle> FPDFAnnot_GetStringValue_V = StableValue.of();

  public static MethodHandle fpdfAnnotGetStringValue() {
    return FPDFAnnot_GetStringValue_V.orElseSet(
        () ->
            find(
                "FPDFAnnot_GetStringValue",
                FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER, C_POINTER, C_LONG),
                false));
  }

  private static final StableValue<MethodHandle> FPDFAnnot_GetRect_V = StableValue.of();

  public static MethodHandle fpdfAnnotGetRect() {
    return FPDFAnnot_GetRect_V.orElseSet(
        () -> find("FPDFAnnot_GetRect", FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER), true));
  }
}
