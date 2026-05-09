package org.grimmory.pdfium4j.internal;

import static org.grimmory.pdfium4j.internal.FfmHelper.C_INT;
import static org.grimmory.pdfium4j.internal.FfmHelper.C_POINTER;
import static org.grimmory.pdfium4j.internal.FfmHelper.LINKER;
import static org.grimmory.pdfium4j.internal.FfmHelper.LOOKUP;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Objects;
import java.util.Optional;

/** FFM bindings for the pdfium4j C++ shim library. */
public final class ShimBindings {

  private ShimBindings() {}

  /** Ensures all required shim symbols are available. */
  public static void checkRequired() {
    Objects.requireNonNull(pdfium4jPageCount(), "pdfium4j_page_count");
  }

  private static MethodHandle find(String name, FunctionDescriptor desc, boolean critical) {
    java.lang.foreign.MemorySegment addr = LOOKUP.find(name).orElse(null);
    if (addr == null) return null;
    return LINKER.downcallHandle(
        addr, desc, critical ? FfmHelper.CRITICAL_OPTIONS : FfmHelper.NO_OPTIONS);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jPageCountSV = StableValue.of();

  public static MethodHandle pdfium4jPageCount() {
    return pdfium4jPageCountSV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find("pdfium4j_page_count", FunctionDescriptor.of(C_INT, C_POINTER), true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jGetMetaUtf8SV = StableValue.of();

  public static MethodHandle pdfium4jGetMetaUtf8() {
    return pdfium4jGetMetaUtf8SV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_get_meta_utf8",
                        FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_INT),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jSetMetaUtf8SV = StableValue.of();

  public static MethodHandle pdfium4jSetMetaUtf8() {
    return pdfium4jSetMetaUtf8SV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_set_meta_utf8",
                        FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jGetXmpMetadataSV =
      StableValue.of();

  public static MethodHandle pdfium4jGetXmpMetadata() {
    return pdfium4jGetXmpMetadataSV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_get_xmp_metadata",
                        FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jPageLabelSV = StableValue.of();

  public static MethodHandle pdfium4jPageLabel() {
    return pdfium4jPageLabelSV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_page_label",
                        FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_POINTER, C_INT),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jPageWidthSV = StableValue.of();

  public static MethodHandle pdfium4jPageWidth() {
    return pdfium4jPageWidthSV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_page_width",
                        FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, C_POINTER, C_INT),
                        true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jPageHeightSV = StableValue.of();

  public static MethodHandle pdfium4jPageHeight() {
    return pdfium4jPageHeightSV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_page_height",
                        FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, C_POINTER, C_INT),
                        true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jBookmarkFirstSV =
      StableValue.of();

  public static MethodHandle pdfium4jBookmarkFirst() {
    return pdfium4jBookmarkFirstSV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_bookmark_first",
                        FunctionDescriptor.of(C_POINTER, C_POINTER),
                        true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jBookmarkNextSV =
      StableValue.of();

  public static MethodHandle pdfium4jBookmarkNext() {
    return pdfium4jBookmarkNextSV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_bookmark_next",
                        FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER),
                        true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jBookmarkFirstChildSV =
      StableValue.of();

  public static MethodHandle pdfium4jBookmarkFirstChild() {
    return pdfium4jBookmarkFirstChildSV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_bookmark_first_child",
                        FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER),
                        true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jBookmarkTitleSV =
      StableValue.of();

  public static MethodHandle pdfium4jBookmarkTitle() {
    return pdfium4jBookmarkTitleSV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_bookmark_title",
                        FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jBookmarkPageIndexSV =
      StableValue.of();

  public static MethodHandle pdfium4jBookmarkPageIndex() {
    return pdfium4jBookmarkPageIndexSV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_bookmark_page_index",
                        FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER),
                        true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jStructTreeGetSV =
      StableValue.of();

  public static MethodHandle pdfium4jStructTreeGet() {
    return pdfium4jStructTreeGetSV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_struct_tree_get",
                        FunctionDescriptor.of(C_POINTER, C_POINTER),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jStructTreeCloseSV =
      StableValue.of();

  public static MethodHandle pdfium4jStructTreeClose() {
    return pdfium4jStructTreeCloseSV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_struct_tree_close", FunctionDescriptor.ofVoid(C_POINTER), false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jStructTreeCountChildrenSV =
      StableValue.of();

  public static MethodHandle pdfium4jStructTreeCountChildren() {
    return pdfium4jStructTreeCountChildrenSV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_struct_tree_count_children",
                        FunctionDescriptor.of(C_INT, C_POINTER),
                        true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jStructTreeGetChildSV =
      StableValue.of();

  public static MethodHandle pdfium4jStructTreeGetChild() {
    return pdfium4jStructTreeGetChildSV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_struct_tree_get_child",
                        FunctionDescriptor.of(C_POINTER, C_POINTER, C_INT),
                        true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jStructElementCountChildrenSV =
      StableValue.of();

  public static MethodHandle pdfium4jStructElementCountChildren() {
    return pdfium4jStructElementCountChildrenSV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_struct_element_count_children",
                        FunctionDescriptor.of(C_INT, C_POINTER),
                        true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jStructElementGetChildSV =
      StableValue.of();

  public static MethodHandle pdfium4jStructElementGetChild() {
    return pdfium4jStructElementGetChildSV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_struct_element_get_child",
                        FunctionDescriptor.of(C_POINTER, C_POINTER, C_INT),
                        true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jStructElementGetMcidSV =
      StableValue.of();

  public static MethodHandle pdfium4jStructElementGetMcid() {
    return pdfium4jStructElementGetMcidSV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_struct_element_get_mcid",
                        FunctionDescriptor.of(C_INT, C_POINTER, C_INT),
                        true)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jStructElementGetTypeSV =
      StableValue.of();

  public static MethodHandle pdfium4jStructElementGetType() {
    return pdfium4jStructElementGetTypeSV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_struct_element_get_type",
                        FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jStructElementGetTitleSV =
      StableValue.of();

  public static MethodHandle pdfium4jStructElementGetTitle() {
    return pdfium4jStructElementGetTitleSV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_struct_element_get_title",
                        FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jStructElementGetAltTextSV =
      StableValue.of();

  public static MethodHandle pdfium4jStructElementGetAltText() {
    return pdfium4jStructElementGetAltTextSV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_struct_element_get_alt_text",
                        FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jStructElementGetActualTextSV =
      StableValue.of();

  public static MethodHandle pdfium4jStructElementGetActualText() {
    return pdfium4jStructElementGetActualTextSV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_struct_element_get_actual_text",
                        FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jStructElementGetLangSV =
      StableValue.of();

  public static MethodHandle pdfium4jStructElementGetLang() {
    return pdfium4jStructElementGetLangSV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_struct_element_get_lang",
                        FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>>
      pdfium4jStructElementGetAttributeCountSV = StableValue.of();

  public static MethodHandle pdfium4jStructElementGetAttributeCount() {
    return pdfium4jStructElementGetAttributeCountSV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_struct_element_get_attribute_count",
                        FunctionDescriptor.of(C_INT, C_POINTER),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jTextGetCharsWithBoundsSV =
      StableValue.of();

  public static MethodHandle pdfium4jTextGetCharsWithBounds() {
    return pdfium4jTextGetCharsWithBoundsSV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_text_get_chars_with_bounds",
                        FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_INT, C_POINTER),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jSaveIncrementalSV =
      StableValue.of();

  public static MethodHandle pdfium4jSaveIncremental() {
    return pdfium4jSaveIncrementalSV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_save_incremental",
                        FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER),
                        false)))
        .orElse(null);
  }

  private static final StableValue<Optional<MethodHandle>> pdfium4jSaveCopySV = StableValue.of();

  public static MethodHandle pdfium4jSaveCopy() {
    return pdfium4jSaveCopySV
        .orElseSet(
            () ->
                Optional.ofNullable(
                    find(
                        "pdfium4j_save_copy",
                        FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER),
                        false)))
        .orElse(null);
  }
}
