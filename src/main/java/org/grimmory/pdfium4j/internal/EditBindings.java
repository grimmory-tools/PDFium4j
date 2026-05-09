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

  private static volatile MethodHandle FPDF_CreateNewDocument_MH = null;

  public static MethodHandle fpdfCreateNewDocument() {
    MethodHandle mh = FPDF_CreateNewDocument_MH;
    if (mh == null) {
      synchronized (EditBindings.class) {
        mh = FPDF_CreateNewDocument_MH;
        if (mh == null) {
          mh = find("FPDF_CreateNewDocument", FunctionDescriptor.of(C_POINTER), false);
          FPDF_CreateNewDocument_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFPage_GetRotation_MH = null;

  public static MethodHandle fpdfPageGetRotation() {
    MethodHandle mh = FPDFPage_GetRotation_MH;
    if (mh == null) {
      synchronized (EditBindings.class) {
        mh = FPDFPage_GetRotation_MH;
        if (mh == null) {
          mh = find("FPDFPage_GetRotation", FunctionDescriptor.of(C_INT, C_POINTER), true);
          FPDFPage_GetRotation_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFPage_SetRotation_MH = null;

  public static MethodHandle fpdfPageSetRotation() {
    MethodHandle mh = FPDFPage_SetRotation_MH;
    if (mh == null) {
      synchronized (EditBindings.class) {
        mh = FPDFPage_SetRotation_MH;
        if (mh == null) {
          mh = find("FPDFPage_SetRotation", FunctionDescriptor.ofVoid(C_POINTER, C_INT), false);
          FPDFPage_SetRotation_MH = mh;
        }
      }
    }
    return mh;
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

  private static volatile MethodHandle FPDF_SaveAsCopy_MH = null;

  public static MethodHandle fpdfSaveAsCopy() {
    MethodHandle mh = FPDF_SaveAsCopy_MH;
    if (mh == null) {
      synchronized (EditBindings.class) {
        mh = FPDF_SaveAsCopy_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDF_SaveAsCopy",
                  FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT),
                  false);
          FPDF_SaveAsCopy_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDF_SaveWithVersion_MH = null;

  public static MethodHandle fpdfSaveWithVersion() {
    MethodHandle mh = FPDF_SaveWithVersion_MH;
    if (mh == null) {
      synchronized (EditBindings.class) {
        mh = FPDF_SaveWithVersion_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDF_SaveWithVersion",
                  FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT, C_INT),
                  false);
          FPDF_SaveWithVersion_MH = mh;
        }
      }
    }
    return mh;
  }

  public static final int FPDF_NO_INCREMENTAL = 1 << 1;
  private static volatile MethodHandle FPDFPage_New_MH = null;

  public static MethodHandle fpdfPageNew() {
    MethodHandle mh = FPDFPage_New_MH;
    if (mh == null) {
      synchronized (EditBindings.class) {
        mh = FPDFPage_New_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDFPage_New",
                  FunctionDescriptor.of(
                      C_POINTER,
                      C_POINTER,
                      C_INT,
                      ValueLayout.JAVA_DOUBLE,
                      ValueLayout.JAVA_DOUBLE),
                  false);
          FPDFPage_New_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDF_ImportPages_MH = null;

  public static MethodHandle fpdfImportPages() {
    MethodHandle mh = FPDF_ImportPages_MH;
    if (mh == null) {
      synchronized (EditBindings.class) {
        mh = FPDF_ImportPages_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDF_ImportPages",
                  FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_INT),
                  false);
          FPDF_ImportPages_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFPage_CountObjects_MH = null;

  public static MethodHandle fpdfPageCountObjects() {
    MethodHandle mh = FPDFPage_CountObjects_MH;
    if (mh == null) {
      synchronized (EditBindings.class) {
        mh = FPDFPage_CountObjects_MH;
        if (mh == null) {
          mh = find("FPDFPage_CountObjects", FunctionDescriptor.of(C_INT, C_POINTER), true);
          FPDFPage_CountObjects_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFPage_GetObject_MH = null;

  public static MethodHandle fpdfPageGetObject() {
    MethodHandle mh = FPDFPage_GetObject_MH;
    if (mh == null) {
      synchronized (EditBindings.class) {
        mh = FPDFPage_GetObject_MH;
        if (mh == null) {
          mh = find("FPDFPage_GetObject", FunctionDescriptor.of(C_POINTER, C_POINTER, C_INT), true);
          FPDFPage_GetObject_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFPageObj_GetType_MH = null;

  public static MethodHandle fpdfPageObjGetType() {
    MethodHandle mh = FPDFPageObj_GetType_MH;
    if (mh == null) {
      synchronized (EditBindings.class) {
        mh = FPDFPageObj_GetType_MH;
        if (mh == null) {
          mh = find("FPDFPageObj_GetType", FunctionDescriptor.of(C_INT, C_POINTER), true);
          FPDFPageObj_GetType_MH = mh;
        }
      }
    }
    return mh;
  }

  public static final int FPDF_PAGEOBJ_IMAGE = 3;
  private static volatile MethodHandle FPDFImageObj_GetImageMetadata_MH = null;

  public static MethodHandle fpdfImageObjGetImageMetadata() {
    MethodHandle mh = FPDFImageObj_GetImageMetadata_MH;
    if (mh == null) {
      synchronized (EditBindings.class) {
        mh = FPDFImageObj_GetImageMetadata_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDFImageObj_GetImageMetadata",
                  FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER),
                  false);
          FPDFImageObj_GetImageMetadata_MH = mh;
        }
      }
    }
    return mh;
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
