package org.pdfium4j.internal;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.*;

/**
 * FFM bindings for PDFium page editing and document saving functions
 * from {@code fpdf_edit.h} and {@code fpdf_save.h}.
 */
public final class EditBindings {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP = SymbolLookup.loaderLookup();

    private EditBindings() {}

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

    /**
     * Get page rotation.
     * Returns 0 (0°), 1 (90°), 2 (180°), or 3 (270°).
     */
    public static final MethodHandle FPDFPage_GetRotation = downcallCritical("FPDFPage_GetRotation",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /**
     * Set page rotation.
     * Parameters: page (FPDF_PAGE handle), rotate: 0 (0), 1 (90), 2 (180), or 3 (270).
     */
    public static final MethodHandle FPDFPage_SetRotation = downcall("FPDFPage_SetRotation",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

    /** Commit page object changes (must call after modifications). Returns 1 on success. */
    public static final MethodHandle FPDFPage_GenerateContent = downcall("FPDFPage_GenerateContent",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /**
     * FPDF_FILEWRITE struct layout:
     * <pre>{@code
     * typedef struct FPDF_FILEWRITE_ {
     *     int version;  // must be 1
     *     int (*WriteBlock)(FPDF_FILEWRITE* pThis, const void* pData, unsigned long size);
     * } FPDF_FILEWRITE;
     * }</pre>
     */
    public static final StructLayout FPDF_FILEWRITE_LAYOUT = MemoryLayout.structLayout(
            JAVA_INT.withName("version"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("WriteBlock")
    );

    /** WriteBlock callback signature: int (*)(FPDF_FILEWRITE*, const void*, unsigned long) */
    public static final FunctionDescriptor WRITE_BLOCK_DESC = FunctionDescriptor.of(
            JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG);

    /**
     * Save the document to an FPDF_FILEWRITE sink.
     * Flags: 0 = full save, 1 = incremental.
     * Returns 1 on success.
     */
    public static final MethodHandle FPDF_SaveAsCopy = downcall("FPDF_SaveAsCopy",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

    /**
     * Save the document with a specific PDF version number.
     * Returns 1 on success.
     */
    public static final MethodHandle FPDF_SaveWithVersion = downcall("FPDF_SaveWithVersion",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));

    /**
     * Set a document metadata tag value.
     * Parameters: doc (FPDF_DOCUMENT handle), tag (FPDF_BYTESTRING, e.g. "Title"),
     * value (FPDF_WIDESTRING UTF-16LE null-terminated). Returns 1 on success.
     */
    public static final MethodHandle EPDF_SetMetaText = downcall("EPDF_SetMetaText",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

    /**
     * Create a new blank page at the given index.
     * Returns FPDF_PAGE handle (NULL on failure).
     */
    public static final MethodHandle FPDFPage_New = downcall("FPDFPage_New",
            FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_DOUBLE, JAVA_DOUBLE));

    /** Delete a page at the given index. */
    public static final MethodHandle FPDFPage_Delete = downcall("FPDFPage_Delete",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));

    /**
     * Import pages from another document.
     * pageRange is a comma-separated page range string like "1,3,5-7" (1-based) or NULL for all.
     * insertIndex is 0-based position in the destination document.
     * Returns 1 on success.
     */
    public static final MethodHandle FPDF_ImportPages = downcall("FPDF_ImportPages",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT));
}
