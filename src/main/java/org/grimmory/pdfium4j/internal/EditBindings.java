package org.grimmory.pdfium4j.internal;

import static java.lang.foreign.ValueLayout.*;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * FFM bindings for PDFium page editing and document saving functions from {@code fpdf_edit.h} and
 * {@code fpdf_save.h}.
 */
public final class EditBindings {

  private static final Linker LINKER = Linker.nativeLinker();
  private static final SymbolLookup LOOKUP = SymbolLookup.loaderLookup();

  private EditBindings() {}

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

  /** Get page rotation. Returns 0 (0°), 1 (90°), 2 (180°), or 3 (270°). */
  public static final MethodHandle FPDFPage_GetRotation =
      downcallCritical("FPDFPage_GetRotation", FunctionDescriptor.of(JAVA_INT, ADDRESS));

  /**
   * Set page rotation. Parameters: page (FPDF_PAGE handle), rotate: 0 (0), 1 (90), 2 (180), or 3
   * (270).
   */
  public static final MethodHandle FPDFPage_SetRotation =
      downcall("FPDFPage_SetRotation", FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

  /** Commit page object changes (must call after modifications). Returns 1 on success. */
  public static final MethodHandle FPDFPage_GenerateContent =
      downcall("FPDFPage_GenerateContent", FunctionDescriptor.of(JAVA_INT, ADDRESS));

  /**
   * FPDF_FILEWRITE struct layout:
   *
   * <pre>{@code
   * typedef struct FPDF_FILEWRITE_ {
   *     int version;  // must be 1
   *     int (*WriteBlock)(FPDF_FILEWRITE* pThis, const void* pData, unsigned long size);
   * } FPDF_FILEWRITE;
   * }</pre>
   */
  public static final StructLayout FPDF_FILEWRITE_LAYOUT =
      MemoryLayout.structLayout(
          JAVA_INT.withName("version"),
          MemoryLayout.paddingLayout(4),
          ADDRESS.withName("WriteBlock"));

  /** WriteBlock callback signature: int (*)(FPDF_FILEWRITE*, const void*, unsigned long) */
  public static final FunctionDescriptor WRITE_BLOCK_DESC =
      FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG);

  /** Create a new empty document. Returns FPDF_DOCUMENT handle (NULL on failure). */
  public static final MethodHandle FPDF_CreateNewDocument =
      downcall("FPDF_CreateNewDocument", FunctionDescriptor.of(ADDRESS));

  /**
   * Save the document to an FPDF_FILEWRITE sink. Flags: 0 = full save, 1 = incremental. Returns 1
   * on success.
   */
  public static final MethodHandle FPDF_SaveAsCopy =
      downcall("FPDF_SaveAsCopy", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

  /** Save the document with a specific PDF version number. Returns 1 on success. */
  public static final MethodHandle FPDF_SaveWithVersion =
      downcall(
          "FPDF_SaveWithVersion",
          FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));

  /** Create a new blank page at the given index. Returns FPDF_PAGE handle (NULL on failure). */
  public static final MethodHandle FPDFPage_New =
      downcall(
          "FPDFPage_New",
          FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_DOUBLE, JAVA_DOUBLE));

  /** Delete a page at the given index. */
  public static final MethodHandle FPDFPage_Delete =
      downcall("FPDFPage_Delete", FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

  /**
   * Import pages from another document. pageRange is a comma-separated page range string like
   * "1,3,5-7" (1-based) or NULL for all. insertIndex is 0-based position in the destination
   * document. Returns 1 on success.
   */
  public static final MethodHandle FPDF_ImportPages =
      downcall(
          "FPDF_ImportPages", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT));

  // -- Page object API (fpdf_edit.h) --

  /** Get the number of page objects on a page. */
  public static final MethodHandle FPDFPage_CountObjects =
      downcallCritical("FPDFPage_CountObjects", FunctionDescriptor.of(JAVA_INT, ADDRESS));

  /** Get a page object by index. Returns FPDF_PAGEOBJECT handle (do NOT free individually). */
  public static final MethodHandle FPDFPage_GetObject =
      downcallCritical("FPDFPage_GetObject", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));

  /** Get the type of a page object. Returns FPDF_PAGEOBJ_* constant. */
  public static final MethodHandle FPDFPageObj_GetType =
      downcallCritical("FPDFPageObj_GetType", FunctionDescriptor.of(JAVA_INT, ADDRESS));

  /** FPDF_PAGEOBJ_IMAGE constant (image XObject). */
  public static final int FPDF_PAGEOBJ_IMAGE = 3;

  /**
   * Render an image object's content to a bitmap (respecting transforms). Returns FPDF_BITMAP
   * handle (caller must destroy). Parameters: document, page, imageObject.
   */
  public static final MethodHandle FPDFImageObj_GetRenderedBitmap =
      downcall(
          "FPDFImageObj_GetRenderedBitmap",
          FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));

  /**
   * Get the image metadata for an image object. Writes into an FPDF_IMAGEOBJ_METADATA struct.
   * Returns 1 on success.
   *
   * <p>FPDF_IMAGEOBJ_METADATA layout: { unsigned int width; unsigned int height; float
   * horizontal_dpi; float vertical_dpi; unsigned int bits_per_pixel; int colorspace; int
   * marked_content_id; }
   */
  public static final MethodHandle FPDFImageObj_GetImageMetadata =
      downcall(
          "FPDFImageObj_GetImageMetadata",
          FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

  /**
   * FPDF_IMAGEOBJ_METADATA struct layout. Fields: width (uint), height (uint), horizontal_dpi
   * (float), vertical_dpi (float), bits_per_pixel (uint), colorspace (int), marked_content_id
   * (int).
   */
  public static final StructLayout IMAGE_METADATA_LAYOUT =
      MemoryLayout.structLayout(
          JAVA_INT.withName("width"),
          JAVA_INT.withName("height"),
          JAVA_FLOAT.withName("horizontal_dpi"),
          JAVA_FLOAT.withName("vertical_dpi"),
          JAVA_INT.withName("bits_per_pixel"),
          JAVA_INT.withName("colorspace"),
          JAVA_INT.withName("marked_content_id"));
}
