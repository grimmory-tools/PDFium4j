package org.grimmory.pdfium4j.internal;

import static org.grimmory.pdfium4j.internal.FfmHelper.C_INT;
import static org.grimmory.pdfium4j.internal.FfmHelper.C_LONG;
import static org.grimmory.pdfium4j.internal.FfmHelper.C_POINTER;
import static org.grimmory.pdfium4j.internal.FfmHelper.LINKER;
import static org.grimmory.pdfium4j.internal.FfmHelper.LOOKUP;

import java.lang.foreign.FunctionDescriptor;
import java.lang.invoke.MethodHandle;
import java.util.Objects;
import java.util.Optional;

/** FFM bindings for PDFium bitmap functions from {@code fpdfview.h}. */
public final class BitmapBindings {

  private BitmapBindings() {}

  private static MethodHandle find(String name, FunctionDescriptor desc, boolean critical) {
    java.lang.foreign.MemorySegment addr = LOOKUP.find(name).orElse(null);
    if (addr == null) return null;
    return LINKER.downcallHandle(
        addr, desc, critical ? FfmHelper.CRITICAL_OPTIONS : FfmHelper.NO_OPTIONS);
  }

  public static void checkRequired() {
    Objects.requireNonNull(fpdfBitmapCreate(), "FPDFBitmap_Create");
    Objects.requireNonNull(fpdfBitmapDestroy(), "FPDFBitmap_Destroy");
    Objects.requireNonNull(fpdfBitmapGetBuffer(), "FPDFBitmap_GetBuffer");
    Objects.requireNonNull(fpdfBitmapGetWidth(), "FPDFBitmap_GetWidth");
    Objects.requireNonNull(fpdfBitmapGetHeight(), "FPDFBitmap_GetHeight");
    Objects.requireNonNull(fpdfBitmapGetStride(), "FPDFBitmap_GetStride");
  }

  private static final StableValue<Optional<MethodHandle>> FPDFBitmap_Create_SV = StableValue.of();

  public static MethodHandle fpdfBitmapCreate() {
    return FPDFBitmap_Create_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDFBitmap_Create",
                        FunctionDescriptor.of(C_POINTER, C_INT, C_INT, C_INT),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFBitmap_CreateEx_SV =
      StableValue.of();

  public static MethodHandle fpdfBitmapCreateEx() {
    return FPDFBitmap_CreateEx_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDFBitmap_CreateEx",
                        FunctionDescriptor.of(C_POINTER, C_INT, C_INT, C_INT, C_POINTER, C_INT),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFBitmap_FillRect_SV =
      StableValue.of();

  public static MethodHandle fpdfBitmapFillRect() {
    return FPDFBitmap_FillRect_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDFBitmap_FillRect",
                        FunctionDescriptor.ofVoid(C_POINTER, C_INT, C_INT, C_INT, C_INT, C_LONG),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFBitmap_GetBuffer_SV =
      StableValue.of();

  public static MethodHandle fpdfBitmapGetBuffer() {
    return FPDFBitmap_GetBuffer_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDFBitmap_GetBuffer",
                        FunctionDescriptor.of(C_POINTER, C_POINTER),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFBitmap_GetWidth_SV =
      StableValue.of();

  public static MethodHandle fpdfBitmapGetWidth() {
    return FPDFBitmap_GetWidth_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find("FPDFBitmap_GetWidth", FunctionDescriptor.of(C_INT, C_POINTER), true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFBitmap_GetHeight_SV =
      StableValue.of();

  public static MethodHandle fpdfBitmapGetHeight() {
    return FPDFBitmap_GetHeight_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find("FPDFBitmap_GetHeight", FunctionDescriptor.of(C_INT, C_POINTER), true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFBitmap_GetStride_SV =
      StableValue.of();

  public static MethodHandle fpdfBitmapGetStride() {
    return FPDFBitmap_GetStride_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find("FPDFBitmap_GetStride", FunctionDescriptor.of(C_INT, C_POINTER), true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFBitmap_Destroy_SV = StableValue.of();

  public static MethodHandle fpdfBitmapDestroy() {
    return FPDFBitmap_Destroy_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find("FPDFBitmap_Destroy", FunctionDescriptor.ofVoid(C_POINTER), false)))
        .orElse(null);
  }
}
