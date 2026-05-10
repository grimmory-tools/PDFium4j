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

/** FFM bindings for PDFium document attachment functions from {@code fpdf_attachment.h}. */
public final class AttachmentBindings {

  private AttachmentBindings() {}

  private static MethodHandle find(String name, FunctionDescriptor desc, boolean critical) {
    MemorySegment addr = LOOKUP.find(name).orElse(null);
    if (addr == null) return null;
    return LINKER.downcallHandle(
        addr, desc, critical ? FfmHelper.CRITICAL_OPTIONS : FfmHelper.NO_OPTIONS);
  }

  public static void checkRequired() {
    // Attachments are optional
  }

  private static final StableValue<Optional<MethodHandle>> FPDFDoc_GetAttachmentCount_SV =
      StableValue.of();

  public static MethodHandle fpdfDocGetAttachmentCount() {
    return FPDFDoc_GetAttachmentCount_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDFDoc_GetAttachmentCount",
                        FunctionDescriptor.of(C_INT, C_POINTER),
                        true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFDoc_GetAttachment_SV =
      StableValue.of();

  public static MethodHandle fpdfDocGetAttachment() {
    return FPDFDoc_GetAttachment_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDFDoc_GetAttachment",
                        FunctionDescriptor.of(C_POINTER, C_POINTER, C_INT),
                        true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFAttachment_GetName_SV =
      StableValue.of();

  public static MethodHandle fpdfAttachmentGetName() {
    return FPDFAttachment_GetName_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDFAttachment_GetName",
                        FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER, C_LONG),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFAttachment_GetStringValue_SV =
      StableValue.of();

  public static MethodHandle fpdfAttachmentGetStringValue() {
    return FPDFAttachment_GetStringValue_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDFAttachment_GetStringValue",
                        FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER, C_POINTER, C_LONG),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFAttachment_GetFile_SV =
      StableValue.of();

  public static MethodHandle fpdfAttachmentGetFile() {
    return FPDFAttachment_GetFile_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDFAttachment_GetFile",
                        FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_LONG, C_POINTER),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFDoc_AddAttachment_SV =
      StableValue.of();

  public static MethodHandle fpdfDocAddAttachment() {
    return FPDFDoc_AddAttachment_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDFDoc_AddAttachment",
                        FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFAttachment_SetFile_SV =
      StableValue.of();

  public static MethodHandle fpdfAttachmentSetFile() {
    return FPDFAttachment_SetFile_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDFAttachment_SetFile",
                        FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_LONG),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> FPDFAttachment_SetStringValue_SV =
      StableValue.of();

  public static MethodHandle fpdfAttachmentSetStringValue() {
    return FPDFAttachment_SetStringValue_SV.orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "FPDFAttachment_SetStringValue",
                        FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER),
                        false)))
        .orElse(null);
  }
}
