package org.pdfium4j;

import org.pdfium4j.exception.PdfiumException;
import org.pdfium4j.internal.EditBindings;

import java.io.ByteArrayOutputStream;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;

import static java.lang.foreign.ValueLayout.*;

final class PdfSaver {

    private static final ThreadLocal<ByteArrayOutputStream> WRITE_BUFFER = new ThreadLocal<>();

    private static final byte[] TRAILER_KW = "trailer".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] DICT_CLOSE = ">>".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] INFO_KEY = "/Info".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] OBJ_MARKER = " 0 obj".getBytes(StandardCharsets.US_ASCII);
    private static final byte[][] META_KEYS = {
            "/Title".getBytes(StandardCharsets.US_ASCII),
            "/Author".getBytes(StandardCharsets.US_ASCII),
            "/Subject".getBytes(StandardCharsets.US_ASCII),
            "/Creator".getBytes(StandardCharsets.US_ASCII),
            "/Producer".getBytes(StandardCharsets.US_ASCII),
            "/Keywords".getBytes(StandardCharsets.US_ASCII),
    };

    private PdfSaver() {}

    static byte[] saveToBytes(MemorySegment docHandle, boolean metadataDirty) {
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
            if (metadataDirty) {
                result = patchInfoTrailer(result);
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

    private static byte[] patchInfoTrailer(byte[] pdf) {
        int trailerIdx = lastIndexOf(pdf, TRAILER_KW, pdf.length);
        if (trailerIdx < 0) return pdf;

        int trailerEnd = indexOf(pdf, DICT_CLOSE, trailerIdx);
        if (trailerEnd < 0) return pdf;

        // Already linked
        int existingInfo = indexOf(pdf, INFO_KEY, trailerIdx);
        if (existingInfo >= 0 && existingInfo < trailerEnd) return pdf;

        // Locate the Info dict object by finding any metadata key
        int metaKeyPos = -1;
        for (byte[] key : META_KEYS) {
            metaKeyPos = indexOf(pdf, key, 0);
            if (metaKeyPos >= 0) break;
        }
        if (metaKeyPos < 0) return pdf;

        int objIdx = lastIndexOf(pdf, OBJ_MARKER, metaKeyPos);
        if (objIdx < 0) return pdf;

        // Walk backwards past digits to extract the object number
        int numEnd = objIdx;
        int numStart = numEnd;
        while (numStart > 0 && pdf[numStart - 1] >= '0' && pdf[numStart - 1] <= '9') {
            numStart--;
        }
        if (numStart == numEnd) return pdf;

        byte[] objNum = new byte[numEnd - numStart];
        System.arraycopy(pdf, numStart, objNum, 0, objNum.length);

        // Build " /Info N 0 R" reference
        byte[] prefix = " /Info ".getBytes(StandardCharsets.US_ASCII);
        byte[] suffix = " 0 R".getBytes(StandardCharsets.US_ASCII);
        byte[] infoRef = new byte[prefix.length + objNum.length + suffix.length];
        System.arraycopy(prefix, 0, infoRef, 0, prefix.length);
        System.arraycopy(objNum, 0, infoRef, prefix.length, objNum.length);
        System.arraycopy(suffix, 0, infoRef, prefix.length + objNum.length, suffix.length);

        // Splice into the trailer before ">>"
        byte[] result = new byte[pdf.length + infoRef.length];
        System.arraycopy(pdf, 0, result, 0, trailerEnd);
        System.arraycopy(infoRef, 0, result, trailerEnd, infoRef.length);
        System.arraycopy(pdf, trailerEnd, result, trailerEnd + infoRef.length, pdf.length - trailerEnd);
        return result;
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

    private static int lastIndexOf(byte[] haystack, byte[] needle, int beforeIndex) {
        for (int i = Math.min(beforeIndex, haystack.length) - needle.length; i >= 0; i--) {
            boolean match = true;
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) { match = false; break; }
            }
            if (match) return i;
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
