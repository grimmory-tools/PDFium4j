package org.grimmory.pdfium4j.internal;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

/** FFM bindings for PDFium bitmap functions from {@code fpdfview.h}. */
public final class BitmapBindings {

  private static final Linker LINKER = Linker.nativeLinker();
  private static final SymbolLookup LOOKUP = SymbolLookup.loaderLookup();

  private BitmapBindings() {}

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
    Objects.requireNonNull(FPDFBitmap_Create, "FPDFBitmap_Create");
    Objects.requireNonNull(FPDFBitmap_Destroy, "FPDFBitmap_Destroy");
    Objects.requireNonNull(FPDFBitmap_GetBuffer, "FPDFBitmap_GetBuffer");
    Objects.requireNonNull(FPDFBitmap_GetWidth, "FPDFBitmap_GetWidth");
    Objects.requireNonNull(FPDFBitmap_GetHeight, "FPDFBitmap_GetHeight");
    Objects.requireNonNull(FPDFBitmap_GetStride, "FPDFBitmap_GetStride");
  }

  /**
   * Create a new bitmap. Parameters: width (pixels), height (pixels), alpha (0 = no alpha/BGRx,
   * non-zero = with alpha/BGRA). Returns FPDF_BITMAP handle (NULL on failure).
   */
  public static final MethodHandle FPDFBitmap_Create =
      downcall("FPDFBitmap_Create", FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));

  /** Fill a rectangle in the bitmap. color is 0xAARRGGBB. */
  public static final MethodHandle FPDFBitmap_FillRect =
      downcall(
          "FPDFBitmap_FillRect",
          FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG));

  /** Get pointer to first scanline of pixel data. */
  public static final MethodHandle FPDFBitmap_GetBuffer =
      downcallCritical("FPDFBitmap_GetBuffer", FunctionDescriptor.of(ADDRESS, ADDRESS));

  /** Get bitmap width. */
  public static final MethodHandle FPDFBitmap_GetWidth =
      downcallCritical("FPDFBitmap_GetWidth", FunctionDescriptor.of(JAVA_INT, ADDRESS));

  /** Get bitmap height. */
  public static final MethodHandle FPDFBitmap_GetHeight =
      downcallCritical("FPDFBitmap_GetHeight", FunctionDescriptor.of(JAVA_INT, ADDRESS));

  /** Get number of bytes per scanline. */
  public static final MethodHandle FPDFBitmap_GetStride =
      downcallCritical("FPDFBitmap_GetStride", FunctionDescriptor.of(JAVA_INT, ADDRESS));

  /** Destroy a bitmap and free its buffer (unless externally allocated). */
  public static final MethodHandle FPDFBitmap_Destroy =
      downcallCritical("FPDFBitmap_Destroy", FunctionDescriptor.ofVoid(ADDRESS));
}
