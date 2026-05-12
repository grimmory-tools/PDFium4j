package org.grimmory.pdfium4j.internal;

import static org.grimmory.pdfium4j.internal.FfmHelper.C_INT;
import static org.grimmory.pdfium4j.internal.FfmHelper.C_LONG;
import static org.grimmory.pdfium4j.internal.FfmHelper.C_POINTER;
import static org.grimmory.pdfium4j.internal.FfmHelper.LINKER;
import static org.grimmory.pdfium4j.internal.FfmHelper.LOOKUP;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

/** FFM bindings for PDFium bitmap functions from {@code fpdfview.h}. */
public final class BitmapBindings {

  private BitmapBindings() {}

  private static MethodHandle find(String name, FunctionDescriptor desc, boolean critical) {
    MemorySegment addr = LOOKUP.find(name).orElse(null);
    if (addr == null) return null;
    return LINKER.downcallHandle(
        addr, desc, critical ? FfmHelper.CRITICAL_OPTIONS : FfmHelper.NO_OPTIONS);
  }

  public static void checkRequired() {
    try {
      Objects.requireNonNull(fpdfBitmapCreate(), "FPDFBitmap_Create");
      Objects.requireNonNull(fpdfBitmapDestroy(), "FPDFBitmap_Destroy");
      Objects.requireNonNull(fpdfBitmapGetBuffer(), "FPDFBitmap_GetBuffer");
      Objects.requireNonNull(fpdfBitmapGetWidth(), "FPDFBitmap_GetWidth");
      Objects.requireNonNull(fpdfBitmapGetHeight(), "FPDFBitmap_GetHeight");
      Objects.requireNonNull(fpdfBitmapGetStride(), "FPDFBitmap_GetStride");
    } catch (NullPointerException e) {
      throw new RuntimeException("Missing required PDFium bitmap symbol: " + e.getMessage(), e);
    }
  }

  private static final StableValue<MethodHandle> FPDFBitmap_Create_V = StableValue.of();

  public static MethodHandle fpdfBitmapCreate() {
    return FPDFBitmap_Create_V.orElseSet(
        () ->
            find(
                "FPDFBitmap_Create", FunctionDescriptor.of(C_POINTER, C_INT, C_INT, C_INT), false));
  }

  private static final StableValue<MethodHandle> FPDFBitmap_CreateEx_V = StableValue.of();

  public static MethodHandle fpdfBitmapCreateEx() {
    return FPDFBitmap_CreateEx_V.orElseSet(
        () ->
            find(
                "FPDFBitmap_CreateEx",
                FunctionDescriptor.of(C_POINTER, C_INT, C_INT, C_INT, C_POINTER, C_INT),
                false));
  }

  private static final StableValue<MethodHandle> FPDFBitmap_FillRect_V = StableValue.of();

  public static MethodHandle fpdfBitmapFillRect() {
    return FPDFBitmap_FillRect_V.orElseSet(
        () ->
            find(
                "FPDFBitmap_FillRect",
                FunctionDescriptor.ofVoid(C_POINTER, C_INT, C_INT, C_INT, C_INT, C_LONG),
                false));
  }

  private static final StableValue<MethodHandle> FPDFBitmap_GetBuffer_V = StableValue.of();

  public static MethodHandle fpdfBitmapGetBuffer() {
    return FPDFBitmap_GetBuffer_V.orElseSet(
        () -> find("FPDFBitmap_GetBuffer", FunctionDescriptor.of(C_POINTER, C_POINTER), false));
  }

  private static final StableValue<MethodHandle> FPDFBitmap_GetWidth_V = StableValue.of();

  public static MethodHandle fpdfBitmapGetWidth() {
    return FPDFBitmap_GetWidth_V.orElseSet(
        () -> find("FPDFBitmap_GetWidth", FunctionDescriptor.of(C_INT, C_POINTER), true));
  }

  private static final StableValue<MethodHandle> FPDFBitmap_GetHeight_V = StableValue.of();

  public static MethodHandle fpdfBitmapGetHeight() {
    return FPDFBitmap_GetHeight_V.orElseSet(
        () -> find("FPDFBitmap_GetHeight", FunctionDescriptor.of(C_INT, C_POINTER), true));
  }

  private static final StableValue<MethodHandle> FPDFBitmap_GetStride_V = StableValue.of();

  public static MethodHandle fpdfBitmapGetStride() {
    return FPDFBitmap_GetStride_V.orElseSet(
        () -> find("FPDFBitmap_GetStride", FunctionDescriptor.of(C_INT, C_POINTER), true));
  }

  private static final StableValue<MethodHandle> FPDFBitmap_Destroy_V = StableValue.of();

  public static MethodHandle fpdfBitmapDestroy() {
    return FPDFBitmap_Destroy_V.orElseSet(
        () -> find("FPDFBitmap_Destroy", FunctionDescriptor.ofVoid(C_POINTER), false));
  }
}
