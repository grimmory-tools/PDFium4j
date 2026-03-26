package org.pdfium4j;

import org.pdfium4j.exception.PdfiumException;
import org.pdfium4j.internal.EditBindings;
import org.pdfium4j.model.MetadataTag;

import java.io.ByteArrayOutputStream;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.foreign.ValueLayout.*;

/**
 * Handles saving PDF documents. Uses PDFium's native FPDF_SaveAsCopy for the base save,
 * then applies pure-Java incremental updates for Info dictionary and XMP metadata.
 * <p>
 * Incremental updates (PDF spec §7.5.6) append new objects, a new xref section,
 * and a new trailer at the end of the file — the original bytes are never modified.
 * This is the standard mechanism used by all PDF editors.
 */
final class PdfSaver {

    private static final ThreadLocal<ByteArrayOutputStream> WRITE_BUFFER = new ThreadLocal<>();

    private PdfSaver() {}

    /**
     * Apply an incremental update to existing PDF bytes without native serialization.
     * This is the fast path for metadata-only changes — it reads the original file
     * and appends new Info/XMP objects + xref + trailer at the end.
     */
    static byte[] applyIncrementalUpdate(byte[] originalPdf, Map<MetadataTag, String> pendingMetadata, String pendingXmp) {
        boolean hasInfoUpdate = pendingMetadata != null && !pendingMetadata.isEmpty();
        boolean hasXmpUpdate = pendingXmp != null && !pendingXmp.isEmpty();
        if (!hasInfoUpdate && !hasXmpUpdate) {
            return originalPdf;
        }
        return appendIncrementalUpdate(originalPdf, pendingMetadata, pendingXmp);
    }

    static byte[] saveToBytes(MemorySegment docHandle, Map<MetadataTag, String> pendingMetadata, String pendingXmp) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        WRITE_BUFFER.set(baos);
        try (Arena arena = Arena.ofConfined()) {
            MethodHandle writeBlockMH = MethodHandles.lookup().findStatic(
                    PdfSaver.class, "writeBlockCallback",
                    MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class, long.class));

            MemorySegment writeBlockStub = Linker.nativeLinker().upcallStub(
                    writeBlockMH, EditBindings.WRITE_BLOCK_DESC, arena);

            MemorySegment fileWrite = arena.allocate(EditBindings.FPDF_FILEWRITE_LAYOUT);
            fileWrite.set(JAVA_INT, 0, 1);
            fileWrite.set(ADDRESS, 8, writeBlockStub);

            int ok = (int) EditBindings.FPDF_SaveAsCopy.invokeExact(docHandle, fileWrite, 0);
            if (ok == 0) {
                throw new PdfiumException("FPDF_SaveAsCopy failed");
            }

            byte[] result = baos.toByteArray();

            // Apply incremental updates for metadata
            boolean hasInfoUpdate = pendingMetadata != null && !pendingMetadata.isEmpty();
            boolean hasXmpUpdate = pendingXmp != null && !pendingXmp.isEmpty();
            if (hasInfoUpdate || hasXmpUpdate) {
                result = appendIncrementalUpdate(result, pendingMetadata, pendingXmp);
            }
            return result;
        } catch (PdfiumException e) {
            throw e;
        } catch (Throwable t) {
            throw new PdfiumException("Failed to save document", t);
        } finally {
            WRITE_BUFFER.remove();
        }
    }

    /**
     * Append a PDF incremental update containing new Info dictionary and/or XMP metadata objects.
     * This appends after the existing %%EOF — the original file bytes are untouched.
     */
    private static byte[] appendIncrementalUpdate(byte[] pdf, Map<MetadataTag, String> metadata, String xmp) {
        String text = new String(pdf, StandardCharsets.ISO_8859_1);

        // Find previous startxref value (points to the current xref/xref-stream)
        int prevXrefOffset = findLastStartxrefValue(text);

        // Parse existing trailer to get /Size and /Root
        TrailerInfo trailer = parseTrailer(pdf, text);

        int maxObjNum = findMaxObjectNumber(text);
        int nextObj = maxObjNum + 1;

        // Track new objects: objNum -> (offset relative to start of appended bytes, content)
        Map<Integer, String> newObjects = new LinkedHashMap<>();
        int infoObjNum = 0;
        int xmpObjNum = 0;

        if (metadata != null && !metadata.isEmpty()) {
            infoObjNum = nextObj++;
            StringBuilder infoObj = new StringBuilder();
            infoObj.append(infoObjNum).append(" 0 obj\n<<\n");
            for (Map.Entry<MetadataTag, String> entry : metadata.entrySet()) {
                String key = entry.getKey().pdfKey();
                String value = entry.getValue();
                infoObj.append("/").append(key).append(" ");
                infoObj.append(encodePdfString(value)).append("\n");
            }
            infoObj.append("/ModDate ").append(encodePdfString(formatPdfDate())).append("\n");
            infoObj.append(">>\nendobj\n");
            newObjects.put(infoObjNum, infoObj.toString());
        }

        if (xmp != null && !xmp.isEmpty()) {
            xmpObjNum = nextObj++;
            byte[] xmpBytes = xmp.getBytes(StandardCharsets.UTF_8);
            StringBuilder xmpObj = new StringBuilder();
            xmpObj.append(xmpObjNum).append(" 0 obj\n");
            xmpObj.append("<< /Type /Metadata /Subtype /XML /Length ").append(xmpBytes.length).append(" >>\n");
            xmpObj.append("stream\n");
            xmpObj.append(xmp);
            xmpObj.append("\nendstream\nendobj\n");
            newObjects.put(xmpObjNum, xmpObj.toString());
        }

        // Build the incremental update
        int baseOffset = pdf.length; // offset where appended bytes start
        StringBuilder update = new StringBuilder();
        update.append('\n'); // separator after %%EOF

        // Write new objects and record their offsets
        Map<Integer, Integer> objOffsets = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : newObjects.entrySet()) {
            objOffsets.put(entry.getKey(), baseOffset + update.length());
            update.append(entry.getValue());
        }

        // Write xref table for the new objects
        int xrefOffset = baseOffset + update.length();
        update.append("xref\n");
        // Write one subsection per object (or contiguous ranges)
        for (Map.Entry<Integer, Integer> entry : objOffsets.entrySet()) {
            update.append(entry.getKey()).append(" 1\n");
            update.append(String.format("%010d 00000 n \r\n", entry.getValue()));
        }

        // Write new trailer
        update.append("trailer\n");
        update.append("<< /Size ").append(nextObj);
        update.append(" /Root ").append(trailer.rootRef);
        if (infoObjNum > 0) {
            update.append(" /Info ").append(infoObjNum).append(" 0 R");
        } else if (trailer.infoRef != null) {
            update.append(" /Info ").append(trailer.infoRef);
        }
        if (xmpObjNum > 0) {
            // Catalog /Metadata needs updating too — but for incremental update
            // the trailer doesn't carry /Metadata; it goes in the Catalog.
            // For simplicity, we write a new Catalog object with /Metadata ref.
            // Actually: /Info is in trailer, /Metadata is in Catalog. For XMP we need
            // to update the Catalog. Let's handle this below.
        }
        update.append(" /Prev ").append(prevXrefOffset);
        update.append(" >>\n");
        update.append("startxref\n");
        update.append(xrefOffset).append("\n");
        update.append("%%EOF\n");

        byte[] updateBytes = update.toString().getBytes(StandardCharsets.ISO_8859_1);
        byte[] result = new byte[pdf.length + updateBytes.length];
        System.arraycopy(pdf, 0, result, 0, pdf.length);
        System.arraycopy(updateBytes, 0, result, pdf.length, updateBytes.length);

        // If XMP object was added, we need to also update the Catalog to reference it
        if (xmpObjNum > 0) {
            result = appendCatalogUpdate(result, trailer, xmpObjNum, nextObj, xrefOffset);
        }

        return result;
    }

    /**
     * Append another incremental update that rewrites the Catalog object with a /Metadata reference.
     */
    private static byte[] appendCatalogUpdate(byte[] pdf, TrailerInfo trailer, int metadataObjNum, int sizeBase, int prevXrefOffset) {
        String text = new String(pdf, StandardCharsets.ISO_8859_1);

        // Find the Catalog object and its contents
        int catalogObjNum = extractObjNum(trailer.rootRef);
        String catalogDict = findObjectDict(text, catalogObjNum);
        if (catalogDict == null) return pdf;

        // Create a new Catalog object (same obj number, incremental update replaces it)
        StringBuilder newCatalog = new StringBuilder();
        newCatalog.append(catalogObjNum).append(" 0 obj\n");

        // Remove existing /Metadata if present, add our new one
        String dict = catalogDict;
        dict = dict.replaceFirst("/Metadata\\s+\\d+\\s+\\d+\\s+R", "");
        // Insert /Metadata ref before the closing >>
        int closeIdx = dict.lastIndexOf(">>");
        if (closeIdx >= 0) {
            dict = dict.substring(0, closeIdx) + "/Metadata " + metadataObjNum + " 0 R " + dict.substring(closeIdx);
        }
        newCatalog.append(dict).append("\n");
        newCatalog.append("endobj\n");

        int baseOffset = pdf.length;
        StringBuilder update = new StringBuilder();
        update.append('\n');

        int catalogOffset = baseOffset + update.length();
        update.append(newCatalog);

        // Find actual previous xref from the current file end
        int actualPrev = findLastStartxrefValue(new String(pdf, StandardCharsets.ISO_8859_1));

        int xrefOffset = baseOffset + update.length();
        update.append("xref\n");
        update.append(catalogObjNum).append(" 1\n");
        update.append(String.format("%010d 00000 n \r\n", catalogOffset));

        update.append("trailer\n");
        update.append("<< /Size ").append(sizeBase);
        update.append(" /Root ").append(trailer.rootRef);
        // Carry forward /Info from previous trailer
        String prevTrailerInfo = findTrailerEntry(new String(pdf, StandardCharsets.ISO_8859_1), "Info");
        if (prevTrailerInfo != null) {
            update.append(" /Info ").append(prevTrailerInfo);
        }
        update.append(" /Prev ").append(actualPrev);
        update.append(" >>\n");
        update.append("startxref\n");
        update.append(xrefOffset).append("\n");
        update.append("%%EOF\n");

        byte[] updateBytes = update.toString().getBytes(StandardCharsets.ISO_8859_1);
        byte[] result = new byte[pdf.length + updateBytes.length];
        System.arraycopy(pdf, 0, result, 0, pdf.length);
        System.arraycopy(updateBytes, 0, result, pdf.length, updateBytes.length);
        return result;
    }

    /**
     * Write or replace the XMP metadata packet in the PDF by in-place replacement
     * (without incremental update — used when XMP packet already exists with padding).
     */
    static byte[] replaceXmpPacketInPlace(byte[] pdf, String xmpPacket) {
        byte[] xmpBytes = xmpPacket.getBytes(StandardCharsets.UTF_8);
        byte[] beginMarker = "<?xpacket begin=".getBytes(StandardCharsets.US_ASCII);
        byte[] endMarker = "<?xpacket end=".getBytes(StandardCharsets.US_ASCII);

        int beginPos = indexOf(pdf, beginMarker, 0);
        if (beginPos < 0) return null; // no existing packet

        int endPos = indexOf(pdf, endMarker, beginPos);
        if (endPos < 0) return null;

        int endTagClose = indexOf(pdf, "?>".getBytes(StandardCharsets.US_ASCII), endPos);
        if (endTagClose < 0) return null;

        int packetEnd = endTagClose + 2;
        int oldLen = packetEnd - beginPos;

        byte[] paddedXmp = padXmpToLength(xmpBytes, oldLen);
        if (paddedXmp.length != oldLen) {
            return null; // can't fit in-place, caller should use incremental update
        }

        byte[] result = new byte[pdf.length];
        System.arraycopy(pdf, 0, result, 0, pdf.length);
        System.arraycopy(paddedXmp, 0, result, beginPos, paddedXmp.length);
        return result;
    }

    // --- Trailer parsing ---

    private record TrailerInfo(String rootRef, String infoRef) {}

    private static TrailerInfo parseTrailer(byte[] pdf, String text) {
        // Try traditional trailer first
        String rootRef = findTrailerEntry(text, "Root");
        String infoRef = findTrailerEntry(text, "Info");

        if (rootRef != null) {
            return new TrailerInfo(rootRef, infoRef);
        }

        // For cross-reference streams, find the last xref stream object
        // which contains /Root in its dictionary
        Pattern rootPattern = Pattern.compile("/Root\\s+(\\d+\\s+\\d+\\s+R)");
        Matcher m = rootPattern.matcher(text);
        String lastRoot = null;
        String lastInfo = null;
        while (m.find()) {
            lastRoot = m.group(1);
        }
        Pattern infoPattern = Pattern.compile("/Info\\s+(\\d+\\s+\\d+\\s+R)");
        Matcher m2 = infoPattern.matcher(text);
        while (m2.find()) {
            lastInfo = m2.group(1);
        }
        return new TrailerInfo(
                lastRoot != null ? lastRoot : "1 0 R",
                lastInfo
        );
    }

    private static String findTrailerEntry(String text, String key) {
        // Search backwards from end for the latest trailer
        int searchFrom = text.length();
        while (true) {
            int trailerIdx = text.lastIndexOf("trailer", searchFrom);
            if (trailerIdx < 0) break;

            int dictStart = text.indexOf("<<", trailerIdx);
            if (dictStart < 0) break;

            int dictEnd = text.indexOf(">>", dictStart);
            if (dictEnd < 0) break;

            String dict = text.substring(dictStart, dictEnd + 2);
            Pattern p = Pattern.compile("/" + key + "\\s+(\\d+\\s+\\d+\\s+R)");
            Matcher m = p.matcher(dict);
            if (m.find()) {
                return m.group(1);
            }
            searchFrom = trailerIdx - 1;
            if (searchFrom < 0) break;
        }
        return null;
    }

    private static int findLastStartxrefValue(String text) {
        int idx = text.lastIndexOf("startxref");
        if (idx < 0) return 0;
        String after = text.substring(idx + "startxref".length()).trim();
        try {
            return Integer.parseInt(after.lines().findFirst().orElse("0").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int extractObjNum(String ref) {
        // "N 0 R" -> N
        Matcher m = Pattern.compile("(\\d+)").matcher(ref);
        if (m.find()) return Integer.parseInt(m.group(1));
        return 1;
    }

    private static String findObjectDict(String text, int objNum) {
        // Find "N 0 obj" then extract the dictionary << ... >>
        String marker = objNum + " 0 obj";
        int idx = text.lastIndexOf(marker);
        if (idx < 0) return null;

        int dictStart = text.indexOf("<<", idx);
        if (dictStart < 0) return null;

        // Find matching >> (handle nested <<>>)
        int depth = 0;
        int pos = dictStart;
        while (pos < text.length() - 1) {
            if (text.charAt(pos) == '<' && text.charAt(pos + 1) == '<') {
                depth++;
                pos += 2;
            } else if (text.charAt(pos) == '>' && text.charAt(pos + 1) == '>') {
                depth--;
                if (depth == 0) {
                    return text.substring(dictStart, pos + 2);
                }
                pos += 2;
            } else {
                pos++;
            }
        }
        return null;
    }

    // --- Helpers ---

    private static int findMaxObjectNumber(String pdfText) {
        Pattern objPattern = Pattern.compile("(\\d+)\\s+0\\s+obj\\b");
        Matcher matcher = objPattern.matcher(pdfText);
        int max = 0;
        while (matcher.find()) {
            int num = Integer.parseInt(matcher.group(1));
            if (num > max) max = num;
        }
        return max;
    }

    private static byte[] padXmpToLength(byte[] xmp, int targetLen) {
        if (xmp.length >= targetLen) {
            return xmp;
        }
        byte[] endPI = "<?xpacket end=".getBytes(StandardCharsets.US_ASCII);
        int endIdx = indexOf(xmp, endPI, 0);
        if (endIdx < 0) return xmp;

        int paddingNeeded = targetLen - xmp.length;
        byte[] padded = new byte[targetLen];
        System.arraycopy(xmp, 0, padded, 0, endIdx);
        for (int i = 0; i < paddingNeeded; i++) {
            padded[endIdx + i] = (i % 80 == 79) ? (byte) '\n' : (byte) ' ';
        }
        System.arraycopy(xmp, endIdx, padded, endIdx + paddingNeeded, xmp.length - endIdx);
        return padded;
    }

    /**
     * Encode a Java string as a PDF string literal using UTF-16BE with BOM for non-ASCII,
     * or PDFDocEncoding (Latin-1) for ASCII-only strings.
     */
    static String encodePdfString(String value) {
        if (value == null || value.isEmpty()) return "()";

        boolean needsUnicode = false;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 127) {
                needsUnicode = true;
                break;
            }
        }

        if (!needsUnicode) {
            StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '(' -> sb.append("\\(");
                    case ')' -> sb.append("\\)");
                    case '\\' -> sb.append("\\\\");
                    default -> sb.append(c);
                }
            }
            sb.append(')');
            return sb.toString();
        }

        byte[] utf16 = value.getBytes(java.nio.charset.StandardCharsets.UTF_16BE);
        StringBuilder sb = new StringBuilder("<FEFF");
        for (byte b : utf16) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        sb.append('>');
        return sb.toString();
    }

    private static String formatPdfDate() {
        ZonedDateTime now = ZonedDateTime.now();
        return "D:" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private static int indexOf(byte[] haystack, byte[] needle, int fromIndex) {
        outer:
        for (int i = fromIndex; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static int writeBlockCallback(MemorySegment pThis, MemorySegment pData, long size) {
        ByteArrayOutputStream baos = WRITE_BUFFER.get();
        if (baos == null || size <= 0) return 0;
        byte[] data = pData.reinterpret(size).toArray(JAVA_BYTE);
        baos.write(data, 0, data.length);
        return 1;
    }
}
