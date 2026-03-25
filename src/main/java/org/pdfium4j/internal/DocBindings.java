package org.pdfium4j.internal;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.*;

/**
 * FFM bindings for PDFium document metadata and bookmark functions from {@code fpdf_doc.h}.
 */
public final class DocBindings {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP = SymbolLookup.loaderLookup();

    private DocBindings() {}

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
     * Get document metadata value by tag name.
     * Double-call pattern: first call with NULL buffer returns required size in bytes.
     * Tags: "Title", "Author", "Subject", "Keywords", "Creator", "Producer",
     *        "CreationDate", "ModDate".
     * Returns UTF-16LE encoded string (byte count including null terminator).
     */
    public static final MethodHandle FPDF_GetMetaText = downcall(
            "FPDF_GetMetaText",
            FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG));

    /** Get document permissions bitmask. */
    public static final MethodHandle FPDF_GetDocPermissions = downcallCritical(
            "FPDF_GetDocPermissions",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /** Get security handler revision (0 = not encrypted). */
    public static final MethodHandle FPDF_GetSecurityHandlerRevision = downcallCritical(
            "FPDF_GetSecurityHandlerRevision",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /** Get PDF file version (e.g. 14 for 1.4). Returns 1 on success. */
    public static final MethodHandle FPDF_GetFileVersion = downcall(
            "FPDF_GetFileVersion",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    /**
     * Get the page label for a given page index (UTF-16LE, double-call pattern).
     * Page labels are logical labels like "i", "ii", "1", "2", "A-1".
     * Returns byte count including null terminator, or 0 if no label.
     */
    public static final MethodHandle FPDF_GetPageLabel = downcall(
            "FPDF_GetPageLabel",
            FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, ADDRESS, JAVA_LONG));

    /**
     * Get first child bookmark. Pass NULL parent for root-level bookmarks.
     * Returns FPDF_BOOKMARK (NULL if none).
     */
    public static final MethodHandle FPDFBookmark_GetFirstChild = downcallCritical(
            "FPDFBookmark_GetFirstChild",
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

    /** Get next sibling bookmark. Returns NULL if no more siblings. */
    public static final MethodHandle FPDFBookmark_GetNextSibling = downcallCritical(
            "FPDFBookmark_GetNextSibling",
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

    /**
     * Get bookmark title (UTF-16LE, double-call pattern).
     * Returns byte count including null terminator.
     */
    public static final MethodHandle FPDFBookmark_GetTitle = downcall(
            "FPDFBookmark_GetTitle",
            FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, JAVA_LONG));

    /** Get bookmark destination. Returns FPDF_DEST (NULL if external). */
    public static final MethodHandle FPDFBookmark_GetDest = downcallCritical(
            "FPDFBookmark_GetDest",
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

    /** Get bookmark action. Returns FPDF_ACTION (NULL if none). */
    public static final MethodHandle FPDFBookmark_GetAction = downcallCritical(
            "FPDFBookmark_GetAction",
            FunctionDescriptor.of(ADDRESS, ADDRESS));

    /** Get count of children (negative = closed, positive = open, 0 = no children). */
    public static final MethodHandle FPDFBookmark_GetCount = downcallCritical(
            "FPDFBookmark_GetCount",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /** Get action type. Returns PDFACTION_* constant. */
    public static final MethodHandle FPDFAction_GetType = downcallCritical(
            "FPDFAction_GetType",
            FunctionDescriptor.of(JAVA_LONG, ADDRESS));

    /** Get destination from an action. Returns FPDF_DEST (NULL if not a goto). */
    public static final MethodHandle FPDFAction_GetDest = downcallCritical(
            "FPDFAction_GetDest",
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

    /** Get URI path from action (byte string, double-call). */
    public static final MethodHandle FPDFAction_GetURIPath = downcall(
            "FPDFAction_GetURIPath",
            FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG));

    /** Get file path from action (byte string, double-call). */
    public static final MethodHandle FPDFAction_GetFilePath = downcall(
            "FPDFAction_GetFilePath",
            FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, JAVA_LONG));

    /** Get 0-based page index from destination. Returns -1 on error. */
    public static final MethodHandle FPDFDest_GetDestPageIndex = downcallCritical(
            "FPDFDest_GetDestPageIndex",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

    public static final long PDFACTION_UNSUPPORTED  = 0;
    public static final long PDFACTION_GOTO         = 1;
    public static final long PDFACTION_REMOTEGOTO   = 2;
    public static final long PDFACTION_URI          = 3;
    public static final long PDFACTION_LAUNCH       = 4;
    public static final long PDFACTION_EMBEDDEDGOTO = 5;
}
