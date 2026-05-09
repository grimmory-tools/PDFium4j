package org.grimmory.pdfium4j.internal;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.grimmory.pdfium4j.exception.PdfiumException;
import org.grimmory.pdfium4j.model.PdfSignature;
import org.grimmory.pdfium4j.util.PdfDateUtils;

/** Internal helper for PDF document signature extraction. */
public final class PdfDocumentSignatures {

  private PdfDocumentSignatures() {}

  public static List<PdfSignature> readSignatures(MemorySegment handle) {
    MethodHandle getCount = SignatureBindings.fpdfGetSignatureCount();
    if (getCount == null) {
      return Collections.emptyList();
    }
    try {
      int count = (int) getCount.invokeExact(handle);
      if (count <= 0) return Collections.emptyList();

      MethodHandle getObj = SignatureBindings.fpdfGetSignatureObject();
      if (getObj == null) return Collections.emptyList();

      List<PdfSignature> result = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        MemorySegment sig = (MemorySegment) getObj.invokeExact(handle, i);
        if (FfmHelper.isNull(sig)) continue;

        result.add(
            new PdfSignature(
                i,
                getSignatureString(sig, SignatureBindings.fpdfSignatureObjGetReason()),
                getSignatureTime(sig),
                getSignatureString(sig, SignatureBindings.fpdfSignatureObjGetSubFilter()),
                getSignatureLong(sig, SignatureBindings.fpdfSignatureObjGetContents()),
                getSignatureLong(sig, SignatureBindings.fpdfSignatureObjGetByteRange())));
      }
      return Collections.unmodifiableList(result);
    } catch (Throwable t) {
      throw new PdfiumException("Failed to read signatures", t);
    }
  }

  private static Optional<String> getSignatureString(MemorySegment sig, MethodHandle getter) {
    if (getter == null) return Optional.empty();
    try (var _ = ScratchBuffer.acquireScope()) {
      long needed = (long) getter.invokeExact(sig, MemorySegment.NULL, 0L);
      if (needed <= 2) return Optional.empty();
      MemorySegment buf = ScratchBuffer.get(needed);
      long copied = (long) getter.invokeExact(sig, buf, needed);
      return Optional.of(FfmHelper.fromWideString(buf, copied));
    } catch (Throwable _) {
      return Optional.empty();
    }
  }

  private static long getSignatureLong(MemorySegment sig, MethodHandle getter) {
    if (getter == null) return 0;
    try {
      return (long) getter.invokeExact(sig, MemorySegment.NULL, 0L);
    } catch (Throwable _) {
      return 0;
    }
  }

  private static Optional<Instant> getSignatureTime(MemorySegment sig) {
    MethodHandle getTime = SignatureBindings.fpdfSignatureObjGetTime();
    if (getTime == null) return Optional.empty();
    try (var _ = ScratchBuffer.acquireScope()) {
      try {
        long needed = (long) getTime.invokeExact(sig, MemorySegment.NULL, 0L);
        if (needed <= 0) return Optional.empty();
        MemorySegment buf = ScratchBuffer.get(needed);
        getTime.invokeExact(sig, buf, needed);
        String timeStr = FfmHelper.fromWideString(buf, needed);
        return PdfDateUtils.parse(timeStr).map(OffsetDateTime::toInstant);
      } catch (Throwable _) {
        return Optional.empty();
      }
    }
  }
}
