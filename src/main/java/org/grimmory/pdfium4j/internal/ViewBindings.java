package org.grimmory.pdfium4j.internal;

import static java.lang.foreign.ValueLayout.*;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/**
 * FFM bindings for PDFium core functions from {@code fpdfview.h}.
 *
 * <p>Covers library lifecycle, document loading, page access, and rendering.
 */
public final class ViewBindings {

  private static final Linker LINKER = Linker.nativeLinker();
  private static final SymbolLookup LOOKUP = SymbolLookup.loaderLookup();

  private ViewBindings() {}

  private static MethodHandle downcall(String name, FunctionDescriptor desc) {
    return LINKER.downcallHandle(
        LOOKUP
            .find(name)
            .orElseThrow(() -> new UnsatisfiedLinkError("PDFium symbol not found: " + name)),
        desc);
  }

  private static MethodHandle downcallCritical(String name, FunctionDescriptor desc) {
    return LINKER.downcallHandle(
        LOOKUP
            .find(name)
            .orElseThrow(() -> new UnsatisfiedLinkError("PDFium symbol not found: " + name)),
        desc,
        Linker.Option.critical(false));
  }

  /**
   * Layout of FPDF_LIBRARY_CONFIG struct.
   *
   * <pre>{@code
   * typedef struct FPDF_LIBRARY_CONFIG_ {
   *     int version;           // must be 2
   *     const char** m_pUserFontPaths;  // NULL-terminated array (can be NULL)
   *     void* m_pIsolate;      // V8 isolate (NULL)
   *     unsigned int m_v8EmbedderSlot; // V8 embedder slot (0)
   *     const char* m_pPlatform; // platform (NULL)
   *     void* m_pRendererType;   // renderer type (NULL for default)
   * } FPDF_LIBRARY_CONFIG;
   * }</pre>
   */
  public static final StructLayout LIBRARY_CONFIG_LAYOUT =
      MemoryLayout.structLayout(
          JAVA_INT.withName("version"),
          MemoryLayout.paddingLayout(4), // alignment padding
          ADDRESS.withName("m_pUserFontPaths"),
          ADDRESS.withName("m_pIsolate"),
          JAVA_INT.withName("m_v8EmbedderSlot"),
          MemoryLayout.paddingLayout(4),
          ADDRESS.withName("m_pPlatform"),
          ADDRESS.withName("m_pRendererType"));

  /** Initialize PDFium with config. */
  public static final MethodHandle FPDF_InitLibraryWithConfig =
      downcall("FPDF_InitLibraryWithConfig", FunctionDescriptor.ofVoid(ADDRESS));

  /** Destroy PDFium library. */
  public static final MethodHandle FPDF_DestroyLibrary =
      downcall("FPDF_DestroyLibrary", FunctionDescriptor.ofVoid());

  /** Load PDF from file path. Returns FPDF_DOCUMENT (NULL on failure). */
  public static final MethodHandle FPDF_LoadDocument =
      downcall("FPDF_LoadDocument", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

  /** Load PDF from memory buffer. Returns FPDF_DOCUMENT (NULL on failure). */
  public static final MethodHandle FPDF_LoadMemDocument =
      downcall("FPDF_LoadMemDocument", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, ADDRESS));

  /** Close a document. */
  public static final MethodHandle FPDF_CloseDocument =
      downcall("FPDF_CloseDocument", FunctionDescriptor.ofVoid(ADDRESS));

  /** Get last error code. */
  public static final MethodHandle FPDF_GetLastError =
      downcallCritical("FPDF_GetLastError", FunctionDescriptor.of(JAVA_LONG));

  /** Get page count. */
  public static final MethodHandle FPDF_GetPageCount =
      downcallCritical("FPDF_GetPageCount", FunctionDescriptor.of(JAVA_INT, ADDRESS));

  /**
   * Get page size by index without loading the page. Returns non-zero on success, writing
   * width/height to out params.
   */
  public static final MethodHandle FPDF_GetPageSizeByIndex =
      downcall(
          "FPDF_GetPageSizeByIndex",
          FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));

  /** Load a page. Returns FPDF_PAGE (NULL on failure). */
  public static final MethodHandle FPDF_LoadPage =
      downcall("FPDF_LoadPage", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));

  /** Close a page. */
  public static final MethodHandle FPDF_ClosePage =
      downcall("FPDF_ClosePage", FunctionDescriptor.ofVoid(ADDRESS));

  /** Get page width in points (1 pt = 1/72 inch). */
  public static final MethodHandle FPDF_GetPageWidthF =
      downcallCritical("FPDF_GetPageWidthF", FunctionDescriptor.of(JAVA_FLOAT, ADDRESS));

  /** Get page height in points. */
  public static final MethodHandle FPDF_GetPageHeightF =
      downcallCritical("FPDF_GetPageHeightF", FunctionDescriptor.of(JAVA_FLOAT, ADDRESS));

  /** Render page to bitmap. */
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
