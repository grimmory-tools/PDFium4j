package org.grimmory.pdfium4j.internal;

import static org.grimmory.pdfium4j.internal.FfmHelper.C_INT;
import static org.grimmory.pdfium4j.internal.FfmHelper.C_POINTER;
import static org.grimmory.pdfium4j.internal.FfmHelper.C_SIZE_T;
import static org.grimmory.pdfium4j.internal.FfmHelper.LINKER;
import static org.grimmory.pdfium4j.internal.FfmHelper.LOOKUP;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/** FFM bindings for the pdfium4j C++ shim library. */
@SuppressWarnings("preview")
public final class ShimBindings {

  private ShimBindings() {}

  /** Ensures all required shim symbols are available. */
  public static void checkRequired() {
    check("pdfium4j_page_count", pdfium4jPageCount());
  }

  private static void check(String name, MethodHandle mh) {
    if (mh == null) {
      InternalLogger.error("CRITICAL: Missing required PDFium shim symbol: " + name);
      throw new RuntimeException("Missing required PDFium shim symbol: " + name);
    }
  }

  private static MethodHandle find(String name, FunctionDescriptor desc, boolean critical) {
    SymbolLookup shim = NativeLoader.getShimLookup();
    MemorySegment addr = null;
    if (shim != null) {
      addr = shim.find(name).orElse(null);
    }
    if (addr == null) {
      addr = LOOKUP.find(name).orElse(null);
    }
    if (addr == null) return null;
    return LINKER.downcallHandle(
        addr, desc, critical ? FfmHelper.CRITICAL_OPTIONS : FfmHelper.NO_OPTIONS);
  }

  private static final StableValue<MethodHandle> pdfium4jPageCountV = StableValue.of();

  public static MethodHandle pdfium4jPageCount() {
    return pdfium4jPageCountV.orElseSet(
        () -> find("pdfium4j_page_count", FunctionDescriptor.of(C_INT, C_POINTER), true));
  }

  private static final StableValue<MethodHandle> pdfium4jGetMetaUtf8V = StableValue.of();

  public static MethodHandle pdfium4jGetMetaUtf8() {
    return pdfium4jGetMetaUtf8V.orElseSet(
        () ->
            find(
                "pdfium4j_get_meta_utf8",
                FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_INT),
                false));
  }

  private static final StableValue<MethodHandle> pdfium4jSetMetaUtf8V = StableValue.of();

  public static MethodHandle pdfium4jSetMetaUtf8() {
    return pdfium4jSetMetaUtf8V.orElseSet(
        () ->
            find(
                "pdfium4j_set_meta_utf8",
                FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER),
                false));
  }

  private static final StableValue<MethodHandle> pdfium4jGetCustomXmpV = StableValue.of();

  public static MethodHandle pdfium4jGetCustomXmp() {
    return pdfium4jGetCustomXmpV.orElseSet(
        () ->
            find(
                "pdfium4j_get_custom_xmp",
                FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_INT),
                false));
  }

  private static final StableValue<MethodHandle> pdfium4jGetCustomXmpBagV = StableValue.of();

  public static MethodHandle pdfium4jGetCustomXmpBag() {
    return pdfium4jGetCustomXmpBagV.orElseSet(
        () ->
            find(
                "pdfium4j_get_custom_xmp_bag",
                FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_INT),
                false));
  }

  private static final StableValue<MethodHandle> pdfium4jGetXmpMetadataV = StableValue.of();

  public static MethodHandle pdfium4jGetXmpMetadata() {
    return pdfium4jGetXmpMetadataV.orElseSet(
        () ->
            find(
                "pdfium4j_get_xmp_metadata",
                FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT),
                false));
  }

  private static final StableValue<MethodHandle> pdfium4jPageLabelV = StableValue.of();

  public static MethodHandle pdfium4jPageLabel() {
    return pdfium4jPageLabelV.orElseSet(
        () ->
            find(
                "pdfium4j_page_label",
                FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_POINTER, C_INT),
                false));
  }

  private static final StableValue<MethodHandle> pdfium4jPageWidthV = StableValue.of();

  public static MethodHandle pdfium4jPageWidth() {
    return pdfium4jPageWidthV.orElseSet(
        () ->
            find(
                "pdfium4j_page_width",
                FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, C_POINTER, C_INT),
                true));
  }

  private static final StableValue<MethodHandle> pdfium4jPageHeightV = StableValue.of();

  public static MethodHandle pdfium4jPageHeight() {
    return pdfium4jPageHeightV.orElseSet(
        () ->
            find(
                "pdfium4j_page_height",
                FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, C_POINTER, C_INT),
                true));
  }

  private static final StableValue<MethodHandle> pdfium4jBookmarkFirstV = StableValue.of();

  public static MethodHandle pdfium4jBookmarkFirst() {
    return pdfium4jBookmarkFirstV.orElseSet(
        () -> find("pdfium4j_bookmark_first", FunctionDescriptor.of(C_POINTER, C_POINTER), true));
  }

  private static final StableValue<MethodHandle> pdfium4jBookmarkNextV = StableValue.of();

  public static MethodHandle pdfium4jBookmarkNext() {
    return pdfium4jBookmarkNextV.orElseSet(
        () ->
            find(
                "pdfium4j_bookmark_next",
                FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER),
                true));
  }

  private static final StableValue<MethodHandle> pdfium4jBookmarkFirstChildV = StableValue.of();

  public static MethodHandle pdfium4jBookmarkFirstChild() {
    return pdfium4jBookmarkFirstChildV.orElseSet(
        () ->
            find(
                "pdfium4j_bookmark_first_child",
                FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER),
                true));
  }

  private static final StableValue<MethodHandle> pdfium4jBookmarkTitleV = StableValue.of();

  public static MethodHandle pdfium4jBookmarkTitle() {
    return pdfium4jBookmarkTitleV.orElseSet(
        () ->
            find(
                "pdfium4j_bookmark_title",
                FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT),
                false));
  }

  private static final StableValue<MethodHandle> pdfium4jBookmarkPageIndexV = StableValue.of();

  public static MethodHandle pdfium4jBookmarkPageIndex() {
    return pdfium4jBookmarkPageIndexV.orElseSet(
        () ->
            find(
                "pdfium4j_bookmark_page_index",
                FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER),
                true));
  }

  private static final StableValue<MethodHandle> pdfium4jStructTreeGetV = StableValue.of();

  public static MethodHandle pdfium4jStructTreeGet() {
    return pdfium4jStructTreeGetV.orElseSet(
        () -> find("pdfium4j_struct_tree_get", FunctionDescriptor.of(C_POINTER, C_POINTER), false));
  }

  private static final StableValue<MethodHandle> pdfium4jStructTreeCloseV = StableValue.of();

  public static MethodHandle pdfium4jStructTreeClose() {
    return pdfium4jStructTreeCloseV.orElseSet(
        () -> find("pdfium4j_struct_tree_close", FunctionDescriptor.ofVoid(C_POINTER), false));
  }

  private static final StableValue<MethodHandle> pdfium4jStructTreeCountChildrenV =
      StableValue.of();

  public static MethodHandle pdfium4jStructTreeCountChildren() {
    return pdfium4jStructTreeCountChildrenV.orElseSet(
        () ->
            find(
                "pdfium4j_struct_tree_count_children",
                FunctionDescriptor.of(C_INT, C_POINTER),
                true));
  }

  private static final StableValue<MethodHandle> pdfium4jStructTreeGetChildV = StableValue.of();

  public static MethodHandle pdfium4jStructTreeGetChild() {
    return pdfium4jStructTreeGetChildV.orElseSet(
        () ->
            find(
                "pdfium4j_struct_tree_get_child",
                FunctionDescriptor.of(C_POINTER, C_POINTER, C_INT),
                true));
  }

  private static final StableValue<MethodHandle> pdfium4jStructElementCountChildrenV =
      StableValue.of();

  public static MethodHandle pdfium4jStructElementCountChildren() {
    return pdfium4jStructElementCountChildrenV.orElseSet(
        () ->
            find(
                "pdfium4j_struct_element_count_children",
                FunctionDescriptor.of(C_INT, C_POINTER),
                true));
  }

  private static final StableValue<MethodHandle> pdfium4jStructElementGetChildV = StableValue.of();

  public static MethodHandle pdfium4jStructElementGetChild() {
    return pdfium4jStructElementGetChildV.orElseSet(
        () ->
            find(
                "pdfium4j_struct_element_get_child",
                FunctionDescriptor.of(C_POINTER, C_POINTER, C_INT),
                true));
  }

  private static final StableValue<MethodHandle> pdfium4jStructElementGetMcidV = StableValue.of();

  public static MethodHandle pdfium4jStructElementGetMcid() {
    return pdfium4jStructElementGetMcidV.orElseSet(
        () ->
            find(
                "pdfium4j_struct_element_get_mcid",
                FunctionDescriptor.of(C_INT, C_POINTER, C_INT),
                true));
  }

  private static final StableValue<MethodHandle> pdfium4jStructElementGetTypeV = StableValue.of();

  public static MethodHandle pdfium4jStructElementGetType() {
    return pdfium4jStructElementGetTypeV.orElseSet(
        () ->
            find(
                "pdfium4j_struct_element_get_type",
                FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT),
                false));
  }

  private static final StableValue<MethodHandle> pdfium4jStructElementGetTitleV = StableValue.of();

  public static MethodHandle pdfium4jStructElementGetTitle() {
    return pdfium4jStructElementGetTitleV.orElseSet(
        () ->
            find(
                "pdfium4j_struct_element_get_title",
                FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT),
                false));
  }

  private static final StableValue<MethodHandle> pdfium4jStructElementGetAltTextV =
      StableValue.of();

  public static MethodHandle pdfium4jStructElementGetAltText() {
    return pdfium4jStructElementGetAltTextV.orElseSet(
        () ->
            find(
                "pdfium4j_struct_element_get_alt_text",
                FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT),
                false));
  }

  private static final StableValue<MethodHandle> pdfium4jStructElementGetActualTextV =
      StableValue.of();

  public static MethodHandle pdfium4jStructElementGetActualText() {
    return pdfium4jStructElementGetActualTextV.orElseSet(
        () ->
            find(
                "pdfium4j_struct_element_get_actual_text",
                FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT),
                false));
  }

  private static final StableValue<MethodHandle> pdfium4jStructElementGetLangV = StableValue.of();

  public static MethodHandle pdfium4jStructElementGetLang() {
    return pdfium4jStructElementGetLangV.orElseSet(
        () ->
            find(
                "pdfium4j_struct_element_get_lang",
                FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT),
                false));
  }

  private static final StableValue<MethodHandle> pdfium4jStructElementGetAttributeCountV =
      StableValue.of();

  public static MethodHandle pdfium4jStructElementGetAttributeCount() {
    return pdfium4jStructElementGetAttributeCountV.orElseSet(
        () ->
            find(
                "pdfium4j_struct_element_get_attribute_count",
                FunctionDescriptor.of(C_INT, C_POINTER),
                false));
  }

  private static final StableValue<MethodHandle> pdfium4jTextGetCharsWithBoundsV = StableValue.of();

  public static MethodHandle pdfium4jTextGetCharsWithBounds() {
    return pdfium4jTextGetCharsWithBoundsV.orElseSet(
        () ->
            find(
                "pdfium4j_text_get_chars_with_bounds",
                FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_INT, C_POINTER),
                false));
  }

  private static final StableValue<MethodHandle> pdfium4jSaveIncrementalV = StableValue.of();

  public static MethodHandle pdfium4jSaveIncremental() {
    return pdfium4jSaveIncrementalV.orElseSet(
        () ->
            find(
                "pdfium4j_save_incremental",
                FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER),
                false));
  }

  private static final StableValue<MethodHandle> pdfium4jSaveCopyV = StableValue.of();

  public static MethodHandle pdfium4jSaveCopy() {
    return pdfium4jSaveCopyV.orElseSet(
        () ->
            find("pdfium4j_save_copy", FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER), false));
  }

  private static final StableValue<MethodHandle> pdfium4jReadInfoDictV = StableValue.of();

  public static MethodHandle pdfium4jReadInfoDict() {
    return pdfium4jReadInfoDictV.orElseSet(
        () ->
            find(
                "pdfium4j_read_info_dict",
                FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER),
                false));
  }

  private static final StableValue<MethodHandle> pdfium4jReadInfoDictMemV = StableValue.of();

  public static MethodHandle pdfium4jReadInfoDictMem() {
    return pdfium4jReadInfoDictMemV.orElseSet(
        () ->
            find(
                "pdfium4j_read_info_dict_mem",
                FunctionDescriptor.of(C_INT, C_POINTER, C_SIZE_T, C_POINTER, C_POINTER),
                false));
  }

  private static final StableValue<MethodHandle> pdfium4jSaveWithMetadataV = StableValue.of();

  public static MethodHandle pdfium4jSaveWithMetadata() {
    return pdfium4jSaveWithMetadataV.orElseSet(
        () ->
            find(
                "pdfium4j_save_with_metadata_native",
                FunctionDescriptor.of(
                    C_INT, C_POINTER, C_POINTER, C_POINTER, C_INT, C_POINTER, C_INT),
                false));
  }

  private static final StableValue<MethodHandle> pdfium4jSaveWithMetadataMemV = StableValue.of();

  public static MethodHandle pdfium4jSaveWithMetadataMem() {
    return pdfium4jSaveWithMetadataMemV.orElseSet(
        () ->
            find(
                "pdfium4j_save_with_metadata_mem_native",
                FunctionDescriptor.of(
                    C_INT, C_POINTER, C_SIZE_T, C_POINTER, C_POINTER, C_POINTER, C_INT, C_POINTER,
                    C_INT),
                false));
  }

  private static final StableValue<MethodHandle> pdfium4jSaveWithMetadataMemToFileV =
      StableValue.of();

  public static MethodHandle pdfium4jSaveWithMetadataMemToFile() {
    return pdfium4jSaveWithMetadataMemToFileV.orElseSet(
        () ->
            find(
                "pdfium4j_save_with_metadata_mem_to_file_native",
                FunctionDescriptor.of(
                    C_INT, C_POINTER, C_SIZE_T, C_POINTER, C_POINTER, C_INT, C_POINTER, C_INT),
                false));
  }

  private static final StableValue<MethodHandle> pdfium4jGetXmpQpdfV = StableValue.of();

  public static MethodHandle pdfium4jGetXmpQpdf() {
    return pdfium4jGetXmpQpdfV.orElseSet(
        () ->
            find(
                "pdfium4j_get_xmp_qpdf",
                FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT),
                false));
  }

  private static final StableValue<MethodHandle> pdfium4jGetXmpQpdfMemV = StableValue.of();

  public static MethodHandle pdfium4jGetXmpQpdfMem() {
    return pdfium4jGetXmpQpdfMemV.orElseSet(
        () ->
            find(
                "pdfium4j_get_xmp_qpdf_mem",
                FunctionDescriptor.of(C_INT, C_POINTER, C_SIZE_T, C_POINTER, C_INT),
                false));
  }

  private static final StableValue<MethodHandle> pdfium4jResolveOptionalSymbolsV = StableValue.of();

  public static MethodHandle pdfium4jResolveOptionalSymbols() {
    return pdfium4jResolveOptionalSymbolsV.orElseSet(
        () -> find("pdfium4j_resolve_optional_symbols", FunctionDescriptor.ofVoid(), false));
  }

  private static volatile MemorySegment WRITE_BLOCK_CALLBACK_STUB = null;

  public static MemorySegment writeBlockCallback() {
    return WRITE_BLOCK_CALLBACK_STUB;
  }

  public static void initializeWriteBlockCallback(MethodHandle mh) {
    if (WRITE_BLOCK_CALLBACK_STUB != null) return;
    synchronized (ShimBindings.class) {
      if (WRITE_BLOCK_CALLBACK_STUB == null) {
        WRITE_BLOCK_CALLBACK_STUB =
            LINKER.upcallStub(
                mh,
                FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, ValueLayout.JAVA_LONG),
                Arena.global());
      }
    }
  }

  private static volatile MemorySegment METADATA_CALLBACK_STUB = null;

  public static MemorySegment metadataCallback() {
    return METADATA_CALLBACK_STUB;
  }

  public static void initializeMetadataCallback(MethodHandle mh) {
    if (METADATA_CALLBACK_STUB != null) return;
    synchronized (ShimBindings.class) {
      if (METADATA_CALLBACK_STUB == null) {
        METADATA_CALLBACK_STUB =
            LINKER.upcallStub(
                mh, FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER), Arena.global());
      }
    }
  }
}
