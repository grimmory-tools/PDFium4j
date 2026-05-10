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
import java.util.Optional;

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
    Objects.requireNonNull(fpdfGetMetaText(), "FPDF_GetMetaText");
    Objects.requireNonNull(fpdfBookmarkGetFirstChild(), "FPDFBookmark_GetFirstChild");
    Objects.requireNonNull(fpdfBookmarkGetNextSibling(), "FPDFBookmark_GetNextSibling");
    Objects.requireNonNull(fpdfBookmarkGetTitle(), "FPDFBookmark_GetTitle");
    Objects.requireNonNull(fpdfBookmarkGetDest(), "FPDFBookmark_GetDest");
    Objects.requireNonNull(fpdfBookmarkGetAction(), "FPDFBookmark_GetAction");
    Objects.requireNonNull(fpdfActionGetType(), "FPDFAction_GetType");
    Objects.requireNonNull(fpdfActionGetDest(), "FPDFAction_GetDest");
    Objects.requireNonNull(fpdfDestGetDestPageIndex(), "FPDFDest_GetDestPageIndex");
  }

  private static final StableValue<Optional<MethodHandle>> FPDF_GetMetaText_SV = StableValue.of();

  public static MethodHandle fpdfGetMetaText() {
    return FPDF_GetMetaText_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDF_GetMetaText",
                        FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER, C_POINTER, C_LONG),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDF_GetXMPMetadata_SV =
      StableValue.of();

  public static MethodHandle fpdfGetXMPMetadata() {
    return FPDF_GetXMPMetadata_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDF_GetXMPMetadata",
                        FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER, C_LONG),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDF_GetFileVersion_SV =
      StableValue.of();

  public static MethodHandle fpdfGetFileVersion() {
    return FPDF_GetFileVersion_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDF_GetFileVersion",
                        FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER),
                        true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFPage_Delete_SV = StableValue.of();

  public static MethodHandle fpdfPageDelete() {
    return FPDFPage_Delete_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find("FPDFPage_Delete", FunctionDescriptor.ofVoid(C_POINTER, C_INT), false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFBookmark_GetFirstChild_SV =
      StableValue.of();

  public static MethodHandle fpdfBookmarkGetFirstChild() {
    return FPDFBookmark_GetFirstChild_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDFBookmark_GetFirstChild",
                        FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER),
                        true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFBookmark_GetNextSibling_SV =
      StableValue.of();

  public static MethodHandle fpdfBookmarkGetNextSibling() {
    return FPDFBookmark_GetNextSibling_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDFBookmark_GetNextSibling",
                        FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER),
                        true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFBookmark_GetTitle_SV =
      StableValue.of();

  public static MethodHandle fpdfBookmarkGetTitle() {
    return FPDFBookmark_GetTitle_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDFBookmark_GetTitle",
                        FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER, C_LONG),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFBookmark_GetDest_SV =
      StableValue.of();

  public static MethodHandle fpdfBookmarkGetDest() {
    return FPDFBookmark_GetDest_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDFBookmark_GetDest",
                        FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER),
                        true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFBookmark_GetAction_SV =
      StableValue.of();

  public static MethodHandle fpdfBookmarkGetAction() {
    return FPDFBookmark_GetAction_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDFBookmark_GetAction",
                        FunctionDescriptor.of(C_POINTER, C_POINTER),
                        true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFAction_GetType_SV = StableValue.of();

  public static MethodHandle fpdfActionGetType() {
    return FPDFAction_GetType_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find("FPDFAction_GetType", FunctionDescriptor.of(C_LONG, C_POINTER), true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFAction_GetDest_SV = StableValue.of();

  public static MethodHandle fpdfActionGetDest() {
    return FPDFAction_GetDest_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDFAction_GetDest",
                        FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER),
                        true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFDest_GetDestPageIndex_SV =
      StableValue.of();

  public static MethodHandle fpdfDestGetDestPageIndex() {
    return FPDFDest_GetDestPageIndex_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDFDest_GetDestPageIndex",
                        FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER),
                        true)))
        .orElse(null);
  }
}
