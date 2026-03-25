package org.pdfium4j;

import org.pdfium4j.exception.PdfiumException;
import org.pdfium4j.internal.EditBindings;

import java.io.ByteArrayOutputStream;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static java.lang.foreign.ValueLayout.*;

final class PdfSaver {

    private static final ThreadLocal<ByteArrayOutputStream> WRITE_BUFFER = new ThreadLocal<>();

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
        // Work with ISO-8859-1 to preserve binary bytes as 1:1 char mapping
        String text = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);

        int trailerIdx = text.lastIndexOf("trailer");
        if (trailerIdx < 0 || text.indexOf("/Info", trailerIdx) >= 0) {
            return pdf;
        }

        java.util.regex.Pattern infoObjPattern = java.util.regex.Pattern.compile(
                "(\\d+)\\s+0\\s+obj\\s*<<.*?/(?:Title|Author|Subject|Creator|Producer|Keywords)\\s*\\(");
        java.util.regex.Matcher matcher = infoObjPattern.matcher(text);
        if (!matcher.find()) {
            return pdf;
        }
        String objNum = matcher.group(1);

        // Inject /Info reference into trailer so PDF readers find updated metadata
        int insertPos = text.indexOf(">>", trailerIdx);
        if (insertPos < 0) {
            return pdf;
        }
        String patched = text.substring(0, insertPos) + "/Info " + objNum + " 0 R" + text.substring(insertPos);
        return patched.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    private static int writeBlockCallback(MemorySegment pThis, MemorySegment pData, long size) {
        ByteArrayOutputStream baos = WRITE_BUFFER.get();
        if (baos == null || size <= 0) return 0;
        byte[] data = pData.reinterpret(size).toArray(JAVA_BYTE);
        baos.write(data, 0, data.length);
        return 1;
    }
}
