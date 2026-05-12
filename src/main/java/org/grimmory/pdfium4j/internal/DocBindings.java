package org.grimmory.pdfium4j.internal;

import static org.grimmory.pdfium4j.internal.FfmHelper.C_INT;
import static org.grimmory.pdfium4j.internal.FfmHelper.C_LONG;
import static org.grimmory.pdfium4j.internal.FfmHelper.C_POINTER;
import static org.grimmory.pdfium4j.internal.FfmHelper.LINKER;
import static org.grimmory.pdfium4j.internal.FfmHelper.LOOKUP;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

/** FFM bindings for PDFium document metadata and bookmark functions from {@code fpdf_doc.h}. */
public final class DocBindings {

  private DocBindings() {}

  private static MethodHandle find(String name, FunctionDescriptor desc, boolean critical) {
    MemorySegment addr = LOOKUP.find(name).orElse(null);
    if (addr == null) return null;
    return LINKER.downcallHandle(
        addr, desc, critical ? FfmHelper.CRITICAL_OPTIONS : FfmHelper.NO_OPTIONS);
  }

  public static void checkRequired() {
    try {
      Objects.requireNonNull(fpdfGetMetaText(), "FPDF_GetMetaText");
      Objects.requireNonNull(fpdfBookmarkGetFirstChild(), "FPDFBookmark_GetFirstChild");
      Objects.requireNonNull(fpdfBookmarkGetNextSibling(), "FPDFBookmark_GetNextSibling");
      Objects.requireNonNull(fpdfBookmarkGetTitle(), "FPDFBookmark_GetTitle");
      Objects.requireNonNull(fpdfBookmarkGetDest(), "FPDFBookmark_GetDest");
      Objects.requireNonNull(fpdfBookmarkGetAction(), "FPDFBookmark_GetAction");
      Objects.requireNonNull(fpdfActionGetType(), "FPDFAction_GetType");
      Objects.requireNonNull(fpdfActionGetDest(), "FPDFAction_GetDest");
      Objects.requireNonNull(fpdfGetDestPageIndex(), "FPDFDest_GetDestPageIndex");
    } catch (NullPointerException e) {
      throw new RuntimeException("Missing required PDFium doc symbol: " + e.getMessage(), e);
    }
  }

  private static final StableValue<MethodHandle> FPDF_GetMetaText_V = StableValue.of();

  public static MethodHandle fpdfGetMetaText() {
    return FPDF_GetMetaText_V.orElseSet(
        () ->
            find(
                "FPDF_GetMetaText",
                FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER, C_POINTER, C_LONG),
                false));
  }

  private static final StableValue<MethodHandle> FPDF_GetXMPMetadata_V = StableValue.of();

  public static MethodHandle fpdfGetXMPMetadata() {
    return FPDF_GetXMPMetadata_V.orElseSet(
        () ->
            find(
                "FPDF_GetXMPMetadata",
                FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER, C_LONG),
                false));
  }

  private static final StableValue<MethodHandle> FPDF_GetFileVersion_V = StableValue.of();

  public static MethodHandle fpdfGetFileVersion() {
    return FPDF_GetFileVersion_V.orElseSet(
        () ->
            find("FPDF_GetFileVersion", FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER), true));
  }

  private static final StableValue<MethodHandle> FPDFPage_Delete_V = StableValue.of();

  public static MethodHandle fpdfPageDelete() {
    return FPDFPage_Delete_V.orElseSet(
        () -> find("FPDFPage_Delete", FunctionDescriptor.ofVoid(C_POINTER, C_INT), false));
  }

  private static final StableValue<MethodHandle> FPDFBookmark_GetFirstChild_V = StableValue.of();

  public static MethodHandle fpdfBookmarkGetFirstChild() {
    return FPDFBookmark_GetFirstChild_V.orElseSet(
        () ->
            find(
                "FPDFBookmark_GetFirstChild",
                FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER),
                true));
  }

  private static final StableValue<MethodHandle> FPDFBookmark_GetNextSibling_V = StableValue.of();

  public static MethodHandle fpdfBookmarkGetNextSibling() {
    return FPDFBookmark_GetNextSibling_V.orElseSet(
        () ->
            find(
                "FPDFBookmark_GetNextSibling",
                FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER),
                true));
  }

  private static final StableValue<MethodHandle> FPDFBookmark_GetTitle_V = StableValue.of();

  public static MethodHandle fpdfBookmarkGetTitle() {
    return FPDFBookmark_GetTitle_V.orElseSet(
        () ->
            find(
                "FPDFBookmark_GetTitle",
                FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER, C_LONG),
                false));
  }

  private static final StableValue<MethodHandle> FPDFBookmark_GetDest_V = StableValue.of();

  public static MethodHandle fpdfBookmarkGetDest() {
    return FPDFBookmark_GetDest_V.orElseSet(
        () ->
            find(
                "FPDFBookmark_GetDest",
                FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER),
                true));
  }

  private static final StableValue<MethodHandle> FPDFBookmark_GetAction_V = StableValue.of();

  public static MethodHandle fpdfBookmarkGetAction() {
    return FPDFBookmark_GetAction_V.orElseSet(
        () -> find("FPDFBookmark_GetAction", FunctionDescriptor.of(C_POINTER, C_POINTER), true));
  }

  private static final StableValue<MethodHandle> FPDFAction_GetType_V = StableValue.of();

  public static MethodHandle fpdfActionGetType() {
    return FPDFAction_GetType_V.orElseSet(
        () -> find("FPDFAction_GetType", FunctionDescriptor.of(C_LONG, C_POINTER), true));
  }

  private static final StableValue<MethodHandle> FPDFAction_GetDest_V = StableValue.of();

  public static MethodHandle fpdfActionGetDest() {
    return FPDFAction_GetDest_V.orElseSet(
        () ->
            find(
                "FPDFAction_GetDest",
                FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER),
                true));
  }

  private static final StableValue<MethodHandle> FPDFDest_GetDestPageIndex_V = StableValue.of();

  public static MethodHandle fpdfGetDestPageIndex() {
    return FPDFDest_GetDestPageIndex_V.orElseSet(
        () ->
            find(
                "FPDFDest_GetDestPageIndex",
                FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER),
                true));
  }
}
