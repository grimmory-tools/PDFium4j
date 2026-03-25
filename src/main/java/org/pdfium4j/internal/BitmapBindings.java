package org.pdfium4j.internal;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.*;

/**
 * FFM bindings for PDFium bitmap functions from {@code fpdfview.h}.
 */
public final class BitmapBindings {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP = SymbolLookup.loaderLookup();

    private BitmapBindings() {}

    private static MethodHandle downcall(String name, FunctionDescriptor desc) {
        return LINKER.downcallHandle(
                LOOKUP.find(name).orElseThrow(() ->
                        new UnsatisfiedLinkError("PDFium symbol not found: " + name)),
                desc);
    }

    private static MethodHandle downcallCritical(String name, FunctionDescriptor desc) {
        return LINKER.downcallHandle(
                LOOKUP.find(name).orElseThrow(() ->
                        new UnsatisfiedLinkError("PDFium symbol not found: " + name)),
                desc, Linker.Option.critical(false));
    }

    // Bitmap format constants
    public static final int FPDFBitmap_Unknown = 0;
    public static final int FPDFBitmap_Gray    = 1;
    public static final int FPDFBitmap_BGR     = 2;
    public static final int FPDFBitmap_BGRx    = 3;
    public static final int FPDFBitmap_BGRA    = 4;

    /**
     * Create a new bitmap.
     * Parameters: width (pixels), height (pixels), alpha (0 = no alpha/BGRx,
     * non-zero = with alpha/BGRA). Returns FPDF_BITMAP handle (NULL on failure).
     */
    public static final MethodHandle FPDFBitmap_Create = downcall(
            "FPDFBitmap_Create",
            FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));

    /**
     * Create a bitmap using an externally allocated buffer.
     * Parameters: width, height, format (FPDFBitmap_* constant),
     * firstScan (pointer to first scanline byte), stride (bytes per scanline).
     * Returns FPDF_BITMAP handle.
     */
    public static final MethodHandle FPDFBitmap_CreateEx = downcall(
            "FPDFBitmap_CreateEx",
            FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT));

    /** Fill a rectangle in the bitmap. color is 0xAARRGGBB. */
    public static final MethodHandle FPDFBitmap_FillRect = downcall(
            "FPDFBitmap_FillRect",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG));

    /** Get pointer to first scanline of pixel data. */
    public static final MethodHandle FPDFBitmap_GetBuffer = downcallCritical(
            "FPDFBitmap_GetBuffer",
            FunctionDescriptor.of(ADDRESS, ADDRESS));

    /** Get bitmap width. */
    public static final MethodHandle FPDFBitmap_GetWidth = downcallCritical(
            "FPDFBitmap_GetWidth",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /** Get bitmap height. */
    public static final MethodHandle FPDFBitmap_GetHeight = downcallCritical(
            "FPDFBitmap_GetHeight",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /** Get number of bytes per scanline. */
    public static final MethodHandle FPDFBitmap_GetStride = downcallCritical(
            "FPDFBitmap_GetStride",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /** Destroy a bitmap and free its buffer (unless externally allocated). */
    public static final MethodHandle FPDFBitmap_Destroy = downcall(
            "FPDFBitmap_Destroy",
            FunctionDescriptor.ofVoid(ADDRESS));
}
