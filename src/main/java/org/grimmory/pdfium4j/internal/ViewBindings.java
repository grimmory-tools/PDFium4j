package org.grimmory.pdfium4j.internal;

import static org.grimmory.pdfium4j.internal.FfmHelper.C_INT;
import static org.grimmory.pdfium4j.internal.FfmHelper.C_LONG;
import static org.grimmory.pdfium4j.internal.FfmHelper.C_POINTER;
import static org.grimmory.pdfium4j.internal.FfmHelper.C_SIZE_T;
import static org.grimmory.pdfium4j.internal.FfmHelper.LINKER;
import static org.grimmory.pdfium4j.internal.FfmHelper.LOOKUP;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Objects;
import java.util.Optional;

/** FFM bindings for PDFium core functions from {@code fpdfview.h}. */
public final class ViewBindings {

  private ViewBindings() {}

  private static MethodHandle find(String name, FunctionDescriptor desc, boolean critical) {
    java.lang.foreign.MemorySegment addr = LOOKUP.find(name).orElse(null);
    if (addr == null) return null;
    return LINKER.downcallHandle(
        addr, desc, critical ? FfmHelper.CRITICAL_OPTIONS : FfmHelper.NO_OPTIONS);
  }

  public static void checkRequired() {
    Objects.requireNonNull(fpdfInitLibraryWithConfig(), "FPDF_InitLibraryWithConfig");
    Objects.requireNonNull(fpdfDestroyLibrary(), "FPDF_DestroyLibrary");
    Objects.requireNonNull(fpdfLoadDocument(), "FPDF_LoadDocument");
    Objects.requireNonNull(fpdfCloseDocument(), "FPDF_CloseDocument");
    Objects.requireNonNull(fpdfGetLastError(), "FPDF_GetLastError");
    Objects.requireNonNull(fpdfGetPageCount(), "FPDF_GetPageCount");
    Objects.requireNonNull(fpdfLoadPage(), "FPDF_LoadPage");
    Objects.requireNonNull(fpdfClosePage(), "FPDF_ClosePage");
    Objects.requireNonNull(fpdfRenderPageBitmap(), "FPDF_RenderPageBitmap");
  }

  public static final StructLayout LIBRARY_CONFIG_LAYOUT =
      MemoryLayout.structLayout(
          ValueLayout.JAVA_INT.withName("version"),
          MemoryLayout.paddingLayout(4),
          C_POINTER.withName("m_pUserFontPaths"),
          C_POINTER.withName("m_pIsolate"),
          ValueLayout.JAVA_INT.withName("m_v8EmbedderSlot"),
          MemoryLayout.paddingLayout(4),
          C_POINTER.withName("m_pPlatform"),
          C_POINTER.withName("m_pRendererType"));
  public static final StructLayout FPDF_FILEACCESS_LAYOUT =
      C_LONG.byteSize() == 8
          ? MemoryLayout.structLayout(
              C_LONG.withName("m_FileLen"),
              C_POINTER.withName("m_GetBlock"),
              C_POINTER.withName("m_Param"))
          : MemoryLayout.structLayout(
              C_LONG.withName("m_FileLen"),
              MemoryLayout.paddingLayout(4),
              C_POINTER.withName("m_GetBlock"),
              C_POINTER.withName("m_Param"));
  public static final FunctionDescriptor GET_BLOCK_DESC =
      FunctionDescriptor.of(C_INT, C_POINTER, C_LONG, C_POINTER, C_LONG);
  private static final StableValue<Optional<MethodHandle>> FPDF_InitLibraryWithConfig_SV =
      StableValue.of();

  public static MethodHandle fpdfInitLibraryWithConfig() {
    return FPDF_InitLibraryWithConfig_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDF_InitLibraryWithConfig", FunctionDescriptor.ofVoid(C_POINTER), false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDF_DestroyLibrary_SV =
      StableValue.of();

  public static MethodHandle fpdfDestroyLibrary() {
    return FPDF_DestroyLibrary_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find("FPDF_DestroyLibrary", FunctionDescriptor.ofVoid(), false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDF_LoadDocument_SV = StableValue.of();

  public static MethodHandle fpdfLoadDocument() {
    return FPDF_LoadDocument_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDF_LoadDocument",
                        FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDF_LoadMemDocument_SV =
      StableValue.of();

  public static MethodHandle fpdfLoadMemDocument() {
    return FPDF_LoadMemDocument_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDF_LoadMemDocument",
                        FunctionDescriptor.of(C_POINTER, C_POINTER, C_INT, C_POINTER),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDF_LoadCustomDocument_SV =
      StableValue.of();

  public static MethodHandle fpdfLoadCustomDocument() {
    return FPDF_LoadCustomDocument_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDF_LoadCustomDocument",
                        FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDF_CloseDocument_SV = StableValue.of();

  public static MethodHandle fpdfCloseDocument() {
    return FPDF_CloseDocument_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find("FPDF_CloseDocument", FunctionDescriptor.ofVoid(C_POINTER), false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDF_GetLastError_SV = StableValue.of();

  public static MethodHandle fpdfGetLastError() {
    return FPDF_GetLastError_SV.orElseSet(
            () ->
                Optional.ofNullable(find("FPDF_GetLastError", FunctionDescriptor.of(C_INT), true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>>
      FPDF_DocumentHasValidCrossReferenceTable_SV = StableValue.of();

  public static MethodHandle fpdfDocumentHasValidCrossReferenceTable() {
    return FPDF_DocumentHasValidCrossReferenceTable_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDF_DocumentHasValidCrossReferenceTable",
                        FunctionDescriptor.of(C_INT, C_POINTER),
                        true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDF_GetTrailerEnds_SV =
      StableValue.of();

  public static MethodHandle fpdfGetTrailerEnds() {
    return FPDF_GetTrailerEnds_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDF_GetTrailerEnds",
                        FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER, C_SIZE_T),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDF_GetPageCount_SV = StableValue.of();

  public static MethodHandle fpdfGetPageCount() {
    return FPDF_GetPageCount_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find("FPDF_GetPageCount", FunctionDescriptor.of(C_INT, C_POINTER), true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDF_LoadPage_SV = StableValue.of();

  public static MethodHandle fpdfLoadPage() {
    return FPDF_LoadPage_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDF_LoadPage",
                        FunctionDescriptor.of(C_POINTER, C_POINTER, C_INT),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDF_ClosePage_SV = StableValue.of();

  public static MethodHandle fpdfClosePage() {
    return FPDF_ClosePage_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find("FPDF_ClosePage", FunctionDescriptor.ofVoid(C_POINTER), false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDF_GetPageWidthF_SV = StableValue.of();

  public static MethodHandle fpdfGetPageWidthF() {
    return FPDF_GetPageWidthF_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDF_GetPageWidthF",
                        FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, C_POINTER),
                        true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDF_GetPageHeightF_SV =
      StableValue.of();

  public static MethodHandle fpdfGetPageHeightF() {
    return FPDF_GetPageHeightF_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDF_GetPageHeightF",
                        FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, C_POINTER),
                        true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDF_RenderPageBitmap_SV =
      StableValue.of();

  public static MethodHandle fpdfRenderPageBitmap() {
    return FPDF_RenderPageBitmap_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDF_RenderPageBitmap",
                        FunctionDescriptor.ofVoid(
                            C_POINTER, C_POINTER, C_INT, C_INT, C_INT, C_INT, C_INT, C_INT),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDF_SetRendererType_SV =
      StableValue.of();

  public static MethodHandle fpdfSetRendererType() {
    return FPDF_SetRendererType_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find("FPDF_SetRendererType", FunctionDescriptor.ofVoid(C_INT), false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDF_LoadMemDocument64_SV =
      StableValue.of();

  public static MethodHandle fpdfLoadMemDocument64() {
    return FPDF_LoadMemDocument64_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDF_LoadMemDocument64",
                        FunctionDescriptor.of(C_POINTER, C_POINTER, C_SIZE_T, C_POINTER),
                        false)))
        .orElse(null);
  }

  public static final int FPDF_RENDERER_TYPE_SKIA = 1;
  public static final int FPDF_ERR_FORMAT = 3;
  public static final int FPDF_ERR_PASSWORD = 4;
  public static final int FPDF_ERR_SECURITY = 5;
  public static final int FPDF_ANNOT = 0x01;
  public static final int FPDF_LCD_TEXT = 0x02;
  public static final int FPDF_GRAYSCALE = 0x08;
  public static final int FPDF_REVERSE_BYTE_ORDER = 0x10;
  public static final int FPDF_PRINTING = 0x800;
  public static final int FPDF_RENDER_NO_SMOOTHTEXT = 0x1000;
  public static final int FPDF_RENDER_NO_SMOOTHIMAGE = 0x2000;
  public static final int FPDF_RENDER_NO_SMOOTHPATH = 0x4000;
}
