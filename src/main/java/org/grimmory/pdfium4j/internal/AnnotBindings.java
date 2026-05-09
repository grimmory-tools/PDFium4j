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

  private static volatile MethodHandle FPDFPage_GetAnnotCount_MH = null;

  public static MethodHandle fpdfPageGetAnnotCount() {
    MethodHandle mh = FPDFPage_GetAnnotCount_MH;
    if (mh == null) {
      synchronized (AnnotBindings.class) {
        mh = FPDFPage_GetAnnotCount_MH;
        if (mh == null) {
          mh = find("FPDFPage_GetAnnotCount", FunctionDescriptor.of(C_INT, C_POINTER), true);
          FPDFPage_GetAnnotCount_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFPage_GetAnnot_MH = null;

  public static MethodHandle fpdfPageGetAnnot() {
    MethodHandle mh = FPDFPage_GetAnnot_MH;
    if (mh == null) {
      synchronized (AnnotBindings.class) {
        mh = FPDFPage_GetAnnot_MH;
        if (mh == null) {
          mh = find("FPDFPage_GetAnnot", FunctionDescriptor.of(C_POINTER, C_POINTER, C_INT), true);
          FPDFPage_GetAnnot_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFPage_CloseAnnot_MH = null;

  public static MethodHandle fpdfPageCloseAnnot() {
    MethodHandle mh = FPDFPage_CloseAnnot_MH;
    if (mh == null) {
      synchronized (AnnotBindings.class) {
        mh = FPDFPage_CloseAnnot_MH;
        if (mh == null) {
          mh = find("FPDFPage_CloseAnnot", FunctionDescriptor.ofVoid(C_POINTER), true);
          FPDFPage_CloseAnnot_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFAnnot_GetSubtype_MH = null;

  public static MethodHandle fpdfAnnotGetSubtype() {
    MethodHandle mh = FPDFAnnot_GetSubtype_MH;
    if (mh == null) {
      synchronized (AnnotBindings.class) {
        mh = FPDFAnnot_GetSubtype_MH;
        if (mh == null) {
          mh = find("FPDFAnnot_GetSubtype", FunctionDescriptor.of(C_INT, C_POINTER), true);
          FPDFAnnot_GetSubtype_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFAnnot_GetStringValue_MH = null;

  public static MethodHandle fpdfAnnotGetStringValue() {
    MethodHandle mh = FPDFAnnot_GetStringValue_MH;
    if (mh == null) {
      synchronized (AnnotBindings.class) {
        mh = FPDFAnnot_GetStringValue_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDFAnnot_GetStringValue",
                  FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER, C_POINTER, C_LONG),
                  false);
          FPDFAnnot_GetStringValue_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFAnnot_GetRect_MH = null;

  public static MethodHandle fpdfAnnotGetRect() {
    MethodHandle mh = FPDFAnnot_GetRect_MH;
    if (mh == null) {
      synchronized (AnnotBindings.class) {
        mh = FPDFAnnot_GetRect_MH;
        if (mh == null) {
          mh = find("FPDFAnnot_GetRect", FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER), true);
          FPDFAnnot_GetRect_MH = mh;
        }
      }
    }
    return mh;
  }
}
