package org.grimmory.pdfium4j.internal;

import static java.lang.foreign.ValueLayout.*;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

/** FFM bindings for PDFium core functions from {@code fpdfview.h}. */
public final class ViewBindings {

  private static final Linker LINKER = Linker.nativeLinker();
  private static final SymbolLookup LOOKUP = SymbolLookup.loaderLookup();

  private ViewBindings() {}

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
    Objects.requireNonNull(FPDF_InitLibraryWithConfig, "FPDF_InitLibraryWithConfig");
    Objects.requireNonNull(FPDF_DestroyLibrary, "FPDF_DestroyLibrary");
    Objects.requireNonNull(FPDF_LoadDocument, "FPDF_LoadDocument");
    Objects.requireNonNull(FPDF_CloseDocument, "FPDF_CloseDocument");
    Objects.requireNonNull(FPDF_GetLastError, "FPDF_GetLastError");
    Objects.requireNonNull(FPDF_GetPageCount, "FPDF_GetPageCount");
    Objects.requireNonNull(FPDF_LoadPage, "FPDF_LoadPage");
    Objects.requireNonNull(FPDF_ClosePage, "FPDF_ClosePage");
    Objects.requireNonNull(FPDF_RenderPageBitmap, "FPDF_RenderPageBitmap");
  }

  public static final StructLayout LIBRARY_CONFIG_LAYOUT =
      MemoryLayout.structLayout(
          JAVA_INT.withName("version"),
          MemoryLayout.paddingLayout(4),
          ADDRESS.withName("m_pUserFontPaths"),
          ADDRESS.withName("m_pIsolate"),
          JAVA_INT.withName("m_v8EmbedderSlot"),
          MemoryLayout.paddingLayout(4),
          ADDRESS.withName("m_pPlatform"),
          ADDRESS.withName("m_pRendererType"));

  public static final StructLayout FPDF_FILEACCESS_LAYOUT =
      MemoryLayout.structLayout(
          JAVA_LONG.withName("m_FileLen"),
          ADDRESS.withName("m_GetBlock"),
          ADDRESS.withName("m_Param"));

  public static final FunctionDescriptor GET_BLOCK_DESC =
      FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS, JAVA_LONG);

  public static final MethodHandle FPDF_InitLibraryWithConfig =
      downcall("FPDF_InitLibraryWithConfig", FunctionDescriptor.ofVoid(ADDRESS));

  public static final MethodHandle FPDF_DestroyLibrary =
      downcall("FPDF_DestroyLibrary", FunctionDescriptor.ofVoid());

  public static final MethodHandle FPDF_LoadDocument =
      downcall("FPDF_LoadDocument", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

  public static final MethodHandle FPDF_LoadMemDocument =
      downcall("FPDF_LoadMemDocument", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, ADDRESS));

  public static final MethodHandle FPDF_LoadCustomDocument =
      downcall("FPDF_LoadCustomDocument", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

  public static final MethodHandle FPDF_CloseDocument =
      downcall("FPDF_CloseDocument", FunctionDescriptor.ofVoid(ADDRESS));

  public static final MethodHandle FPDF_GetLastError =
      downcallCritical("FPDF_GetLastError", FunctionDescriptor.of(JAVA_LONG));

  public static final MethodHandle FPDF_GetPageCount =
      downcallCritical("FPDF_GetPageCount", FunctionDescriptor.of(JAVA_INT, ADDRESS));

  public static final MethodHandle FPDF_GetPageSizeByIndex =
      downcall(
          "FPDF_GetPageSizeByIndex",
          FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));

  public static final MethodHandle FPDF_LoadPage =
      downcall("FPDF_LoadPage", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));

  public static final MethodHandle FPDF_ClosePage =
      downcall("FPDF_ClosePage", FunctionDescriptor.ofVoid(ADDRESS));

  public static final MethodHandle FPDF_GetPageWidthF =
      downcallCritical("FPDF_GetPageWidthF", FunctionDescriptor.of(JAVA_FLOAT, ADDRESS));

  public static final MethodHandle FPDF_GetPageHeightF =
      downcallCritical("FPDF_GetPageHeightF", FunctionDescriptor.of(JAVA_FLOAT, ADDRESS));

  public static final MethodHandle FPDF_RenderPageBitmap =
      downcall(
          "FPDF_RenderPageBitmap",
          FunctionDescriptor.ofVoid(
              ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));

  public static final int FPDF_ERR_SUCCESS = 0;
  public static final int FPDF_ERR_UNKNOWN = 1;
  public static final int FPDF_ERR_FILE = 2;
  public static final int FPDF_ERR_FORMAT = 3;
  public static final int FPDF_ERR_PASSWORD = 4;
  public static final int FPDF_ERR_SECURITY = 5;
  public static final int FPDF_ERR_PAGE = 6;

  public static final int FPDF_ANNOT = 0x01;
  public static final int FPDF_LCD_TEXT = 0x02;
  public static final int FPDF_NO_NATIVETEXT = 0x04;
  public static final int FPDF_GRAYSCALE = 0x08;
  public static final int FPDF_REVERSE_BYTE_ORDER = 0x10;
  public static final int FPDF_PRINTING = 0x800;
  public static final int FPDF_NO_CATCH = 0x100;
  public static final int FPDF_RENDER_LIMITEDIMAGECACHE = 0x200;
  public static final int FPDF_RENDER_FORCEHALFTONE = 0x400;
  public static final int FPDF_RENDER_NO_SMOOTHTEXT = 0x1000;
  public static final int FPDF_RENDER_NO_SMOOTHIMAGE = 0x2000;
  public static final int FPDF_RENDER_NO_SMOOTHPATH = 0x4000;
}
