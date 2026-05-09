package org.grimmory.pdfium4j.internal;

import static org.grimmory.pdfium4j.internal.FfmHelper.C_INT;
import static org.grimmory.pdfium4j.internal.FfmHelper.C_LONG;
import static org.grimmory.pdfium4j.internal.FfmHelper.C_POINTER;
import static org.grimmory.pdfium4j.internal.FfmHelper.C_SIZE_T;
import static org.grimmory.pdfium4j.internal.FfmHelper.LINKER;
import static org.grimmory.pdfium4j.internal.FfmHelper.LOOKUP;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

/** FFM bindings for PDFium core functions from {@code fpdfview.h}. */
public final class ViewBindings {

  private ViewBindings() {}

  private static MethodHandle find(String name, FunctionDescriptor desc, boolean critical) {
    MemorySegment addr = LOOKUP.find(name).orElse(null);
    if (addr == null) return null;
    return LINKER.downcallHandle(
        addr, desc, critical ? FfmHelper.CRITICAL_OPTIONS : FfmHelper.NO_OPTIONS);
  }

  public static void checkRequired() {
    try {
      Objects.requireNonNull(fpdfInitLibraryWithConfig(), "FPDF_InitLibraryWithConfig");
      Objects.requireNonNull(fpdfDestroyLibrary(), "FPDF_DestroyLibrary");
      Objects.requireNonNull(fpdfLoadDocument(), "FPDF_LoadDocument");
      Objects.requireNonNull(fpdfCloseDocument(), "FPDF_CloseDocument");
      Objects.requireNonNull(fpdfGetLastError(), "FPDF_GetLastError");
      Objects.requireNonNull(fpdfGetPageCount(), "FPDF_GetPageCount");
      Objects.requireNonNull(fpdfLoadPage(), "FPDF_LoadPage");
      Objects.requireNonNull(fpdfClosePage(), "FPDF_ClosePage");
      Objects.requireNonNull(fpdfRenderPageBitmap(), "FPDF_RenderPageBitmap");
      Objects.requireNonNull(fpdfGetTrailerEnds(), "FPDF_GetTrailerEnds");
    } catch (NullPointerException e) {
      throw new RuntimeException("Missing required PDFium symbol: " + e.getMessage(), e);
    }
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

  private static volatile MethodHandle FPDF_InitLibraryWithConfig_MH = null;

  public static MethodHandle fpdfInitLibraryWithConfig() {
    MethodHandle mh = FPDF_InitLibraryWithConfig_MH;
    if (mh == null) {
      synchronized (ViewBindings.class) {
        mh = FPDF_InitLibraryWithConfig_MH;
        if (mh == null) {
          mh = find("FPDF_InitLibraryWithConfig", FunctionDescriptor.ofVoid(C_POINTER), false);
          FPDF_InitLibraryWithConfig_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDF_DestroyLibrary_MH = null;

  public static MethodHandle fpdfDestroyLibrary() {
    MethodHandle mh = FPDF_DestroyLibrary_MH;
    if (mh == null) {
      synchronized (ViewBindings.class) {
        mh = FPDF_DestroyLibrary_MH;
        if (mh == null) {
          mh = find("FPDF_DestroyLibrary", FunctionDescriptor.ofVoid(), false);
          FPDF_DestroyLibrary_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDF_LoadDocument_MH = null;

  public static MethodHandle fpdfLoadDocument() {
    MethodHandle mh = FPDF_LoadDocument_MH;
    if (mh == null) {
      synchronized (ViewBindings.class) {
        mh = FPDF_LoadDocument_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDF_LoadDocument",
                  FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER),
                  false);
          FPDF_LoadDocument_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDF_LoadMemDocument_MH = null;

  public static MethodHandle fpdfLoadMemDocument() {
    MethodHandle mh = FPDF_LoadMemDocument_MH;
    if (mh == null) {
      synchronized (ViewBindings.class) {
        mh = FPDF_LoadMemDocument_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDF_LoadMemDocument",
                  FunctionDescriptor.of(C_POINTER, C_POINTER, C_INT, C_POINTER),
                  false);
          FPDF_LoadMemDocument_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDF_LoadCustomDocument_MH = null;

  public static MethodHandle fpdfLoadCustomDocument() {
    MethodHandle mh = FPDF_LoadCustomDocument_MH;
    if (mh == null) {
      synchronized (ViewBindings.class) {
        mh = FPDF_LoadCustomDocument_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDF_LoadCustomDocument",
                  FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER),
                  false);
          FPDF_LoadCustomDocument_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDF_CloseDocument_MH = null;

  public static MethodHandle fpdfCloseDocument() {
    MethodHandle mh = FPDF_CloseDocument_MH;
    if (mh == null) {
      synchronized (ViewBindings.class) {
        mh = FPDF_CloseDocument_MH;
        if (mh == null) {
          mh = find("FPDF_CloseDocument", FunctionDescriptor.ofVoid(C_POINTER), false);
          FPDF_CloseDocument_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDF_GetLastError_MH = null;

  public static MethodHandle fpdfGetLastError() {
    MethodHandle mh = FPDF_GetLastError_MH;
    if (mh == null) {
      synchronized (ViewBindings.class) {
        mh = FPDF_GetLastError_MH;
        if (mh == null) {
          mh = find("FPDF_GetLastError", FunctionDescriptor.of(C_INT), true);
          FPDF_GetLastError_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDF_DocumentHasValidCrossReferenceTable_MH = null;

  public static MethodHandle fpdfDocumentHasValidCrossReferenceTable() {
    MethodHandle mh = FPDF_DocumentHasValidCrossReferenceTable_MH;
    if (mh == null) {
      synchronized (ViewBindings.class) {
        mh = FPDF_DocumentHasValidCrossReferenceTable_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDF_DocumentHasValidCrossReferenceTable",
                  FunctionDescriptor.of(C_INT, C_POINTER),
                  true);
          FPDF_DocumentHasValidCrossReferenceTable_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDF_GetTrailerEnds_MH = null;

  public static MethodHandle fpdfGetTrailerEnds() {
    MethodHandle mh = FPDF_GetTrailerEnds_MH;
    if (mh == null) {
      synchronized (ViewBindings.class) {
        mh = FPDF_GetTrailerEnds_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDF_GetTrailerEnds",
                  FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER, C_LONG),
                  false);
          FPDF_GetTrailerEnds_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDF_GetPageCount_MH = null;

  public static MethodHandle fpdfGetPageCount() {
    MethodHandle mh = FPDF_GetPageCount_MH;
    if (mh == null) {
      synchronized (ViewBindings.class) {
        mh = FPDF_GetPageCount_MH;
        if (mh == null) {
          mh = find("FPDF_GetPageCount", FunctionDescriptor.of(C_INT, C_POINTER), true);
          FPDF_GetPageCount_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDF_LoadPage_MH = null;

  public static MethodHandle fpdfLoadPage() {
    MethodHandle mh = FPDF_LoadPage_MH;
    if (mh == null) {
      synchronized (ViewBindings.class) {
        mh = FPDF_LoadPage_MH;
        if (mh == null) {
          mh = find("FPDF_LoadPage", FunctionDescriptor.of(C_POINTER, C_POINTER, C_INT), false);
          FPDF_LoadPage_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDF_ClosePage_MH = null;

  public static MethodHandle fpdfClosePage() {
    MethodHandle mh = FPDF_ClosePage_MH;
    if (mh == null) {
      synchronized (ViewBindings.class) {
        mh = FPDF_ClosePage_MH;
        if (mh == null) {
          mh = find("FPDF_ClosePage", FunctionDescriptor.ofVoid(C_POINTER), false);
          FPDF_ClosePage_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDF_GetPageWidthF_MH = null;

  public static MethodHandle fpdfGetPageWidthF() {
    MethodHandle mh = FPDF_GetPageWidthF_MH;
    if (mh == null) {
      synchronized (ViewBindings.class) {
        mh = FPDF_GetPageWidthF_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDF_GetPageWidthF",
                  FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, C_POINTER),
                  true);
          FPDF_GetPageWidthF_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDF_GetPageHeightF_MH = null;

  public static MethodHandle fpdfGetPageHeightF() {
    MethodHandle mh = FPDF_GetPageHeightF_MH;
    if (mh == null) {
      synchronized (ViewBindings.class) {
        mh = FPDF_GetPageHeightF_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDF_GetPageHeightF",
                  FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, C_POINTER),
                  true);
          FPDF_GetPageHeightF_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDF_RenderPageBitmap_MH = null;

  public static MethodHandle fpdfRenderPageBitmap() {
    MethodHandle mh = FPDF_RenderPageBitmap_MH;
    if (mh == null) {
      synchronized (ViewBindings.class) {
        mh = FPDF_RenderPageBitmap_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDF_RenderPageBitmap",
                  FunctionDescriptor.ofVoid(
                      C_POINTER, C_POINTER, C_INT, C_INT, C_INT, C_INT, C_INT, C_INT),
                  false);
          FPDF_RenderPageBitmap_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDF_SetRendererType_MH = null;

  public static MethodHandle fpdfSetRendererType() {
    MethodHandle mh = FPDF_SetRendererType_MH;
    if (mh == null) {
      synchronized (ViewBindings.class) {
        mh = FPDF_SetRendererType_MH;
        if (mh == null) {
          mh = find("FPDF_SetRendererType", FunctionDescriptor.ofVoid(C_INT), false);
          FPDF_SetRendererType_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDF_LoadMemDocument64_MH = null;

  public static MethodHandle fpdfLoadMemDocument64() {
    MethodHandle mh = FPDF_LoadMemDocument64_MH;
    if (mh == null) {
      synchronized (ViewBindings.class) {
        mh = FPDF_LoadMemDocument64_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDF_LoadMemDocument64",
                  FunctionDescriptor.of(C_POINTER, C_POINTER, C_SIZE_T, C_POINTER),
                  false);
          FPDF_LoadMemDocument64_MH = mh;
        }
      }
    }
    return mh;
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
