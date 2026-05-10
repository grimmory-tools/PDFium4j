package org.grimmory.pdfium4j.internal;

import static org.grimmory.pdfium4j.internal.FfmHelper.C_INT;
import static org.grimmory.pdfium4j.internal.FfmHelper.C_LONG;
import static org.grimmory.pdfium4j.internal.FfmHelper.C_POINTER;
import static org.grimmory.pdfium4j.internal.FfmHelper.LINKER;
import static org.grimmory.pdfium4j.internal.FfmHelper.LOOKUP;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

/** FFM bindings for PDFium digital signature functions from {@code fpdf_signature.h}. */
public final class SignatureBindings {

  private SignatureBindings() {}

  private static MethodHandle find(String name, FunctionDescriptor desc, boolean critical) {
    MemorySegment addr = LOOKUP.find(name).orElse(null);
    if (addr == null) return null;
    return LINKER.downcallHandle(
        addr, desc, critical ? FfmHelper.CRITICAL_OPTIONS : FfmHelper.NO_OPTIONS);
  }

  public static void checkRequired() {
    // Signatures are optional
  }

  private static final StableValue<Optional<MethodHandle>> FPDF_GetSignatureCount_SV =
      StableValue.of();

  public static MethodHandle fpdfGetSignatureCount() {
    return FPDF_GetSignatureCount_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find("FPDF_GetSignatureCount", FunctionDescriptor.of(C_INT, C_POINTER), true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDF_GetSignatureObject_SV =
      StableValue.of();

  public static MethodHandle fpdfGetSignatureObject() {
    return FPDF_GetSignatureObject_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDF_GetSignatureObject",
                        FunctionDescriptor.of(C_POINTER, C_POINTER, C_INT),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFSignatureObj_GetContents_SV =
      StableValue.of();

  public static MethodHandle fpdfSignatureObjGetContents() {
    return FPDFSignatureObj_GetContents_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDFSignatureObj_GetContents",
                        FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER, C_LONG),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFSignatureObj_GetByteRange_SV =
      StableValue.of();

  public static MethodHandle fpdfSignatureObjGetByteRange() {
    return FPDFSignatureObj_GetByteRange_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDFSignatureObj_GetByteRange",
                        FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER, C_LONG),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFSignatureObj_GetSubFilter_SV =
      StableValue.of();

  public static MethodHandle fpdfSignatureObjGetSubFilter() {
    return FPDFSignatureObj_GetSubFilter_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDFSignatureObj_GetSubFilter",
                        FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER, C_LONG),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFSignatureObj_GetReason_SV =
      StableValue.of();

  public static MethodHandle fpdfSignatureObjGetReason() {
    return FPDFSignatureObj_GetReason_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDFSignatureObj_GetReason",
                        FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER, C_LONG),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFSignatureObj_GetTime_SV =
      StableValue.of();

  public static MethodHandle fpdfSignatureObjGetTime() {
    return FPDFSignatureObj_GetTime_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDFSignatureObj_GetTime",
                        FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER, C_LONG),
                        false)))
        .orElse(null);
  }
}
