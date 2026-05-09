package org.grimmory.pdfium4j.internal;

import static org.grimmory.pdfium4j.internal.FfmHelper.C_POINTER;
import static org.grimmory.pdfium4j.internal.FfmHelper.LINKER;
import static org.grimmory.pdfium4j.internal.FfmHelper.LOOKUP;

import java.lang.foreign.FunctionDescriptor;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

/** FFM bindings for PDFium page thumbnail functions from {@code fpdf_thumbnail.h}. */
public final class ThumbnailBindings {

  private ThumbnailBindings() {}

  private static MethodHandle findOptionalHandle(FunctionDescriptor desc) {
    return LOOKUP
        .find("FPDFPage_GetThumbnailAsBitmap")
        .map(seg -> LINKER.downcallHandle(seg, desc))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFPage_GetThumbnailAsBitmap_SV =
      StableValue.of();

  /** Get the thumbnail of a page as a bitmap. (Experimental API) */
  public static MethodHandle fpdfPageGetThumbnailAsBitmap() {
    return FPDFPage_GetThumbnailAsBitmap_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    findOptionalHandle(FunctionDescriptor.of(C_POINTER, C_POINTER))))
        .orElse(null);
  }
}
