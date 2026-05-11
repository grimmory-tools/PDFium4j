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

/**
 * FFM bindings for PDFium page editing and document saving functions from {@code fpdf_edit.h} and
 * {@code fpdf_save.h}.
 */
public final class EditBindings {

  private EditBindings() {}

  private static MethodHandle find(String name, FunctionDescriptor desc, boolean critical) {
    MemorySegment addr = LOOKUP.find(name).orElse(null);
    if (addr == null) return null;
    return LINKER.downcallHandle(
        addr, desc, critical ? FfmHelper.CRITICAL_OPTIONS : FfmHelper.NO_OPTIONS);
  }

  public static void checkRequired() {
    try {
      Objects.requireNonNull(fpdfSaveAsCopy(), "FPDF_SaveAsCopy");
      Objects.requireNonNull(fpdfCreateNewDocument(), "FPDF_CreateNewDocument");
    } catch (NullPointerException e) {
      throw new RuntimeException("Missing required PDFium edit symbol: " + e.getMessage(), e);
    }
  }

  private static final StableValue<MethodHandle> FPDF_CreateNewDocument_V = StableValue.of();

  public static MethodHandle fpdfCreateNewDocument() {
    return FPDF_CreateNewDocument_V.orElseSet(
        () -> find("FPDF_CreateNewDocument", FunctionDescriptor.of(C_POINTER), false));
  }

  private static final StableValue<MethodHandle> FPDFPage_GetRotation_V = StableValue.of();

  public static MethodHandle fpdfPageGetRotation() {
    return FPDFPage_GetRotation_V.orElseSet(
        () -> find("FPDFPage_GetRotation", FunctionDescriptor.of(C_INT, C_POINTER), true));
  }

  private static final StableValue<MethodHandle> FPDFPage_SetRotation_V = StableValue.of();

  public static MethodHandle fpdfPageSetRotation() {
    return FPDFPage_SetRotation_V.orElseSet(
        () -> find("FPDFPage_SetRotation", FunctionDescriptor.ofVoid(C_POINTER, C_INT), false));
  }

  /** FPDF_FILEWRITE struct layout. */
  public static final StructLayout FPDF_FILEWRITE_LAYOUT =
      MemoryLayout.structLayout(
          ValueLayout.JAVA_INT.withName("version"),
          MemoryLayout.paddingLayout(4),
          C_POINTER.withName("WriteBlock"),
          C_POINTER.withName("bufferId"));

  /** WriteBlock callback signature. */
  public static final FunctionDescriptor WRITE_BLOCK_DESC =
      FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_LONG);

  private static final StableValue<MethodHandle> FPDF_SaveAsCopy_V = StableValue.of();

  public static MethodHandle fpdfSaveAsCopy() {
    return FPDF_SaveAsCopy_V.orElseSet(
        () ->
            find(
                "FPDF_SaveAsCopy",
                FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT),
                false));
  }

  private static final StableValue<MethodHandle> FPDF_SaveWithVersion_V = StableValue.of();

  public static MethodHandle fpdfSaveWithVersion() {
    return FPDF_SaveWithVersion_V.orElseSet(
        () ->
            find(
                "FPDF_SaveWithVersion",
                FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT, C_INT),
                false));
  }

  public static final int FPDF_NO_INCREMENTAL = 1 << 1;

  private static final StableValue<MethodHandle> FPDFPage_New_V = StableValue.of();

  public static MethodHandle fpdfPageNew() {
    return FPDFPage_New_V.orElseSet(
        () ->
            find(
                "FPDFPage_New",
                FunctionDescriptor.of(
                    C_POINTER,
                    C_POINTER,
                    C_INT,
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.JAVA_DOUBLE),
                false));
  }

  private static final StableValue<MethodHandle> FPDF_ImportPages_V = StableValue.of();

  public static MethodHandle fpdfImportPages() {
    return FPDF_ImportPages_V.orElseSet(
        () ->
            find(
                "FPDF_ImportPages",
                FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_INT),
                false));
  }

  private static final StableValue<MethodHandle> FPDFPage_CountObjects_V = StableValue.of();

  public static MethodHandle fpdfPageCountObjects() {
    return FPDFPage_CountObjects_V.orElseSet(
        () -> find("FPDFPage_CountObjects", FunctionDescriptor.of(C_INT, C_POINTER), true));
  }

  private static final StableValue<MethodHandle> FPDFPage_GetObject_V = StableValue.of();

  public static MethodHandle fpdfPageGetObject() {
    return FPDFPage_GetObject_V.orElseSet(
        () -> find("FPDFPage_GetObject", FunctionDescriptor.of(C_POINTER, C_POINTER, C_INT), true));
  }

  private static final StableValue<MethodHandle> FPDFPageObj_GetType_V = StableValue.of();

  public static MethodHandle fpdfPageObjGetType() {
    return FPDFPageObj_GetType_V.orElseSet(
        () -> find("FPDFPageObj_GetType", FunctionDescriptor.of(C_INT, C_POINTER), true));
  }

  public static final int FPDF_PAGEOBJ_IMAGE = 3;

  private static final StableValue<MethodHandle> FPDFImageObj_GetImageMetadata_V = StableValue.of();

  public static MethodHandle fpdfImageObjGetImageMetadata() {
    return FPDFImageObj_GetImageMetadata_V.orElseSet(
        () ->
            find(
                "FPDFImageObj_GetImageMetadata",
                FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER),
                false));
  }

  public static final StructLayout IMAGE_METADATA_LAYOUT =
      MemoryLayout.structLayout(
          C_INT.withName("width"),
          C_INT.withName("height"),
          ValueLayout.JAVA_FLOAT.withName("horizontal_dpi"),
          ValueLayout.JAVA_FLOAT.withName("vertical_dpi"),
          C_INT.withName("bits_per_pixel"),
          C_INT.withName("colorspace"),
          C_INT.withName("marked_content_id"));
}
