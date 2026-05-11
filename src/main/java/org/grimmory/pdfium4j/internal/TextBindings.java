package org.grimmory.pdfium4j.internal;

import static org.grimmory.pdfium4j.internal.FfmHelper.C_INT;
import static org.grimmory.pdfium4j.internal.FfmHelper.C_POINTER;
import static org.grimmory.pdfium4j.internal.FfmHelper.LINKER;
import static org.grimmory.pdfium4j.internal.FfmHelper.LOOKUP;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

/** FFM bindings for PDFium text extraction functions from {@code fpdf_text.h}. */
public final class TextBindings {

  private TextBindings() {}

  private static MethodHandle find(String name, FunctionDescriptor desc, boolean critical) {
    MemorySegment addr = LOOKUP.find(name).orElse(null);
    if (addr == null) return null;
    return LINKER.downcallHandle(
        addr, desc, critical ? FfmHelper.CRITICAL_OPTIONS : FfmHelper.NO_OPTIONS);
  }

  public static void checkRequired() {
    try {
      Objects.requireNonNull(fpdfTextLoadPage(), "FPDFText_LoadPage");
      Objects.requireNonNull(fpdfTextClosePage(), "FPDFText_ClosePage");
      Objects.requireNonNull(fpdfTextCountChars(), "FPDFText_CountChars");
      Objects.requireNonNull(fpdfTextGetText(), "FPDFText_GetText");
      Objects.requireNonNull(fpdfLinkLoadWebLinks(), "FPDFLink_LoadWebLinks");
      Objects.requireNonNull(fpdfLinkCountWebLinks(), "FPDFLink_CountWebLinks");
      Objects.requireNonNull(fpdfLinkGetURL(), "FPDFLink_GetURL");
      Objects.requireNonNull(fpdfLinkCountRects(), "FPDFLink_CountRects");
      Objects.requireNonNull(fpdfLinkGetRect(), "FPDFLink_GetRect");
      Objects.requireNonNull(fpdfLinkCloseWebLinks(), "FPDFLink_CloseWebLinks");
    } catch (NullPointerException e) {
      throw new RuntimeException("Missing required PDFium text symbol: " + e.getMessage(), e);
    }
  }

  private static final StableValue<MethodHandle> FPDFText_LoadPage_V = StableValue.of();

  public static MethodHandle fpdfTextLoadPage() {
    return FPDFText_LoadPage_V.orElseSet(
        () -> find("FPDFText_LoadPage", FunctionDescriptor.of(C_POINTER, C_POINTER), false));
  }

  private static final StableValue<MethodHandle> FPDFText_ClosePage_V = StableValue.of();

  public static MethodHandle fpdfTextClosePage() {
    return FPDFText_ClosePage_V.orElseSet(
        () -> find("FPDFText_ClosePage", FunctionDescriptor.ofVoid(C_POINTER), false));
  }

  private static final StableValue<MethodHandle> FPDFText_CountChars_V = StableValue.of();

  public static MethodHandle fpdfTextCountChars() {
    return FPDFText_CountChars_V.orElseSet(
        () -> find("FPDFText_CountChars", FunctionDescriptor.of(C_INT, C_POINTER), true));
  }

  private static final StableValue<MethodHandle> FPDFText_GetText_V = StableValue.of();

  public static MethodHandle fpdfTextGetText() {
    return FPDFText_GetText_V.orElseSet(
        () ->
            find(
                "FPDFText_GetText",
                FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_INT, C_POINTER),
                false));
  }

  private static final StableValue<MethodHandle> FPDFLink_LoadWebLinks_V = StableValue.of();

  public static MethodHandle fpdfLinkLoadWebLinks() {
    return FPDFLink_LoadWebLinks_V.orElseSet(
        () -> find("FPDFLink_LoadWebLinks", FunctionDescriptor.of(C_POINTER, C_POINTER), false));
  }

  private static final StableValue<MethodHandle> FPDFLink_CountWebLinks_V = StableValue.of();

  public static MethodHandle fpdfLinkCountWebLinks() {
    return FPDFLink_CountWebLinks_V.orElseSet(
        () -> find("FPDFLink_CountWebLinks", FunctionDescriptor.of(C_INT, C_POINTER), true));
  }

  private static final StableValue<MethodHandle> FPDFLink_GetURL_V = StableValue.of();

  public static MethodHandle fpdfLinkGetURL() {
    return FPDFLink_GetURL_V.orElseSet(
        () ->
            find(
                "FPDFLink_GetURL",
                FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_POINTER, C_INT),
                false));
  }

  private static final StableValue<MethodHandle> FPDFLink_CountRects_V = StableValue.of();

  public static MethodHandle fpdfLinkCountRects() {
    return FPDFLink_CountRects_V.orElseSet(
        () -> find("FPDFLink_CountRects", FunctionDescriptor.of(C_INT, C_POINTER, C_INT), true));
  }

  private static final StableValue<MethodHandle> FPDFLink_GetRect_V = StableValue.of();

  public static MethodHandle fpdfLinkGetRect() {
    return FPDFLink_GetRect_V.orElseSet(
        () ->
            find(
                "FPDFLink_GetRect",
                FunctionDescriptor.of(
                    C_INT, C_POINTER, C_INT, C_INT, C_POINTER, C_POINTER, C_POINTER, C_POINTER),
                false));
  }

  private static final StableValue<MethodHandle> FPDFLink_CloseWebLinks_V = StableValue.of();

  public static MethodHandle fpdfLinkCloseWebLinks() {
    return FPDFLink_CloseWebLinks_V.orElseSet(
        () -> find("FPDFLink_CloseWebLinks", FunctionDescriptor.ofVoid(C_POINTER), false));
  }
}
