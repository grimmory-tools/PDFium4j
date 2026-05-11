package org.grimmory.pdfium4j.internal;

import static java.lang.foreign.ValueLayout.*;
import static org.grimmory.pdfium4j.internal.FfmHelper.C_INT;
import static org.grimmory.pdfium4j.internal.FfmHelper.C_POINTER;
import static org.grimmory.pdfium4j.internal.FfmHelper.C_SIZE_T;
import static org.grimmory.pdfium4j.internal.FfmHelper.LINKER;
import static org.grimmory.pdfium4j.internal.FfmHelper.LOOKUP;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/** FFM bindings for the pdfium4j C++ shim library. */
public final class ShimBindings {

  private ShimBindings() {}

  /** Ensures all required shim symbols are available. */
  public static void checkRequired() {
    check("pdfium4j_page_count", pdfium4jPageCount());
    check("pdfium4j_get_meta_utf8", pdfium4jGetMetaUtf8());
    check("pdfium4j_set_meta_utf8", pdfium4jSetMetaUtf8());
    check("pdfium4j_get_custom_xmp", pdfium4jGetCustomXmp());
    check("pdfium4j_get_custom_xmp_bag", pdfium4jGetCustomXmpBag());
    check("pdfium4j_get_xmp_metadata", pdfium4jGetXmpMetadata());
    check("pdfium4j_page_label", pdfium4jPageLabel());
    check("pdfium4j_page_width", pdfium4jPageWidth());
    check("pdfium4j_page_height", pdfium4jPageHeight());
    check("pdfium4j_bookmark_first", pdfium4jBookmarkFirst());
    check("pdfium4j_bookmark_next", pdfium4jBookmarkNext());
    check("pdfium4j_bookmark_first_child", pdfium4jBookmarkFirstChild());
    check("pdfium4j_bookmark_title", pdfium4jBookmarkTitle());
    check("pdfium4j_bookmark_page_index", pdfium4jBookmarkPageIndex());
    check("pdfium4j_struct_tree_get", pdfium4jStructTreeGet());
    check("pdfium4j_struct_tree_close", pdfium4jStructTreeClose());
    check("pdfium4j_struct_tree_count_children", pdfium4jStructTreeCountChildren());
    check("pdfium4j_struct_tree_get_child", pdfium4jStructTreeGetChild());
    check("pdfium4j_struct_element_count_children", pdfium4jStructElementCountChildren());
    check("pdfium4j_struct_element_get_child", pdfium4jStructElementGetChild());
    check("pdfium4j_struct_element_get_mcid", pdfium4jStructElementGetMcid());
    check("pdfium4j_struct_element_get_type", pdfium4jStructElementGetType());
    check("pdfium4j_struct_element_get_title", pdfium4jStructElementGetTitle());
    check("pdfium4j_struct_element_get_alt_text", pdfium4jStructElementGetAltText());
    check("pdfium4j_struct_element_get_actual_text", pdfium4jStructElementGetActualText());
    check("pdfium4j_struct_element_get_lang", pdfium4jStructElementGetLang());
    check("pdfium4j_struct_element_get_attribute_count", pdfium4jStructElementGetAttributeCount());
    check("pdfium4j_text_get_chars_with_bounds", pdfium4jTextGetCharsWithBounds());
    check("pdfium4j_save_incremental", pdfium4jSaveIncremental());
    check("pdfium4j_save_copy", pdfium4jSaveCopy());
    check("pdfium4j_read_info_dict", pdfium4jReadInfoDict());
    check("pdfium4j_read_info_dict_mem", pdfium4jReadInfoDictMem());
    check("pdfium4j_save_with_metadata_native", pdfium4jSaveWithMetadata());
    check("pdfium4j_save_with_metadata_mem_native", pdfium4jSaveWithMetadataMem());
  }

  private static void check(String name, MethodHandle mh) {
    if (mh == null) {
      InternalLogger.error("CRITICAL: Missing required PDFium shim symbol: " + name);
      throw new RuntimeException("Missing required PDFium shim symbol: " + name);
    }
  }

  private static MethodHandle find(String name, FunctionDescriptor desc, boolean critical) {
    java.lang.foreign.SymbolLookup shim = NativeLoader.getShimLookup();
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

  private static volatile MethodHandle pdfium4jPageCountMH = null;

  public static MethodHandle pdfium4jPageCount() {
    MethodHandle mh = pdfium4jPageCountMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jPageCountMH;
        if (mh == null) {
          mh = find("pdfium4j_page_count", FunctionDescriptor.of(C_INT, C_POINTER), true);
          pdfium4jPageCountMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jGetMetaUtf8MH = null;

  public static MethodHandle pdfium4jGetMetaUtf8() {
    MethodHandle mh = pdfium4jGetMetaUtf8MH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jGetMetaUtf8MH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_get_meta_utf8",
                  FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_INT),
                  false);
          pdfium4jGetMetaUtf8MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jSetMetaUtf8MH = null;

  public static MethodHandle pdfium4jSetMetaUtf8() {
    MethodHandle mh = pdfium4jSetMetaUtf8MH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jSetMetaUtf8MH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_set_meta_utf8",
                  FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER),
                  false);
          pdfium4jSetMetaUtf8MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jGetCustomXmpMH = null;

  public static MethodHandle pdfium4jGetCustomXmp() {
    MethodHandle mh = pdfium4jGetCustomXmpMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jGetCustomXmpMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_get_custom_xmp",
                  FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_INT),
                  false);
          pdfium4jGetCustomXmpMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jGetCustomXmpBagMH = null;

  public static MethodHandle pdfium4jGetCustomXmpBag() {
    MethodHandle mh = pdfium4jGetCustomXmpBagMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jGetCustomXmpBagMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_get_custom_xmp_bag",
                  FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_INT),
                  false);
          pdfium4jGetCustomXmpBagMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jGetXmpMetadataMH = null;

  public static MethodHandle pdfium4jGetXmpMetadata() {
    MethodHandle mh = pdfium4jGetXmpMetadataMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jGetXmpMetadataMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_get_xmp_metadata",
                  FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT),
                  false);
          pdfium4jGetXmpMetadataMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jPageLabelMH = null;

  public static MethodHandle pdfium4jPageLabel() {
    MethodHandle mh = pdfium4jPageLabelMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jPageLabelMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_page_label",
                  FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_POINTER, C_INT),
                  false);
          pdfium4jPageLabelMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jPageWidthMH = null;

  public static MethodHandle pdfium4jPageWidth() {
    MethodHandle mh = pdfium4jPageWidthMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jPageWidthMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_page_width",
                  FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, C_POINTER, C_INT),
                  true);
          pdfium4jPageWidthMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jPageHeightMH = null;

  public static MethodHandle pdfium4jPageHeight() {
    MethodHandle mh = pdfium4jPageHeightMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jPageHeightMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_page_height",
                  FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, C_POINTER, C_INT),
                  true);
          pdfium4jPageHeightMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jBookmarkFirstMH = null;

  public static MethodHandle pdfium4jBookmarkFirst() {
    MethodHandle mh = pdfium4jBookmarkFirstMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jBookmarkFirstMH;
        if (mh == null) {
          mh = find("pdfium4j_bookmark_first", FunctionDescriptor.of(C_POINTER, C_POINTER), true);
          pdfium4jBookmarkFirstMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jBookmarkNextMH = null;

  public static MethodHandle pdfium4jBookmarkNext() {
    MethodHandle mh = pdfium4jBookmarkNextMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jBookmarkNextMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_bookmark_next",
                  FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER),
                  true);
          pdfium4jBookmarkNextMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jBookmarkFirstChildMH = null;

  public static MethodHandle pdfium4jBookmarkFirstChild() {
    MethodHandle mh = pdfium4jBookmarkFirstChildMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jBookmarkFirstChildMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_bookmark_first_child",
                  FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER),
                  true);
          pdfium4jBookmarkFirstChildMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jBookmarkTitleMH = null;

  public static MethodHandle pdfium4jBookmarkTitle() {
    MethodHandle mh = pdfium4jBookmarkTitleMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jBookmarkTitleMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_bookmark_title",
                  FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT),
                  false);
          pdfium4jBookmarkTitleMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jBookmarkPageIndexMH = null;

  public static MethodHandle pdfium4jBookmarkPageIndex() {
    MethodHandle mh = pdfium4jBookmarkPageIndexMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jBookmarkPageIndexMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_bookmark_page_index",
                  FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER),
                  true);
          pdfium4jBookmarkPageIndexMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jStructTreeGetMH = null;

  public static MethodHandle pdfium4jStructTreeGet() {
    MethodHandle mh = pdfium4jStructTreeGetMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jStructTreeGetMH;
        if (mh == null) {
          mh = find("pdfium4j_struct_tree_get", FunctionDescriptor.of(C_POINTER, C_POINTER), false);
          pdfium4jStructTreeGetMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jStructTreeCloseMH = null;

  public static MethodHandle pdfium4jStructTreeClose() {
    MethodHandle mh = pdfium4jStructTreeCloseMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jStructTreeCloseMH;
        if (mh == null) {
          mh = find("pdfium4j_struct_tree_close", FunctionDescriptor.ofVoid(C_POINTER), false);
          pdfium4jStructTreeCloseMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jStructTreeCountChildrenMH = null;

  public static MethodHandle pdfium4jStructTreeCountChildren() {
    MethodHandle mh = pdfium4jStructTreeCountChildrenMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jStructTreeCountChildrenMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_struct_tree_count_children",
                  FunctionDescriptor.of(C_INT, C_POINTER),
                  true);
          pdfium4jStructTreeCountChildrenMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jStructTreeGetChildMH = null;

  public static MethodHandle pdfium4jStructTreeGetChild() {
    MethodHandle mh = pdfium4jStructTreeGetChildMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jStructTreeGetChildMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_struct_tree_get_child",
                  FunctionDescriptor.of(C_POINTER, C_POINTER, C_INT),
                  true);
          pdfium4jStructTreeGetChildMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jStructElementCountChildrenMH = null;

  public static MethodHandle pdfium4jStructElementCountChildren() {
    MethodHandle mh = pdfium4jStructElementCountChildrenMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jStructElementCountChildrenMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_struct_element_count_children",
                  FunctionDescriptor.of(C_INT, C_POINTER),
                  true);
          pdfium4jStructElementCountChildrenMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jStructElementGetChildMH = null;

  public static MethodHandle pdfium4jStructElementGetChild() {
    MethodHandle mh = pdfium4jStructElementGetChildMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jStructElementGetChildMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_struct_element_get_child",
                  FunctionDescriptor.of(C_POINTER, C_POINTER, C_INT),
                  true);
          pdfium4jStructElementGetChildMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jStructElementGetMcidMH = null;

  public static MethodHandle pdfium4jStructElementGetMcid() {
    MethodHandle mh = pdfium4jStructElementGetMcidMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jStructElementGetMcidMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_struct_element_get_mcid",
                  FunctionDescriptor.of(C_INT, C_POINTER, C_INT),
                  true);
          pdfium4jStructElementGetMcidMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jStructElementGetTypeMH = null;

  public static MethodHandle pdfium4jStructElementGetType() {
    MethodHandle mh = pdfium4jStructElementGetTypeMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jStructElementGetTypeMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_struct_element_get_type",
                  FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT),
                  false);
          pdfium4jStructElementGetTypeMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jStructElementGetTitleMH = null;

  public static MethodHandle pdfium4jStructElementGetTitle() {
    MethodHandle mh = pdfium4jStructElementGetTitleMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jStructElementGetTitleMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_struct_element_get_title",
                  FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT),
                  false);
          pdfium4jStructElementGetTitleMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jStructElementGetAltTextMH = null;

  public static MethodHandle pdfium4jStructElementGetAltText() {
    MethodHandle mh = pdfium4jStructElementGetAltTextMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jStructElementGetAltTextMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_struct_element_get_alt_text",
                  FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT),
                  false);
          pdfium4jStructElementGetAltTextMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jStructElementGetActualTextMH = null;

  public static MethodHandle pdfium4jStructElementGetActualText() {
    MethodHandle mh = pdfium4jStructElementGetActualTextMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jStructElementGetActualTextMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_struct_element_get_actual_text",
                  FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT),
                  false);
          pdfium4jStructElementGetActualTextMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jStructElementGetLangMH = null;

  public static MethodHandle pdfium4jStructElementGetLang() {
    MethodHandle mh = pdfium4jStructElementGetLangMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jStructElementGetLangMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_struct_element_get_lang",
                  FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT),
                  false);
          pdfium4jStructElementGetLangMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jStructElementGetAttributeCountMH = null;

  public static MethodHandle pdfium4jStructElementGetAttributeCount() {
    MethodHandle mh = pdfium4jStructElementGetAttributeCountMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jStructElementGetAttributeCountMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_struct_element_get_attribute_count",
                  FunctionDescriptor.of(C_INT, C_POINTER),
                  false);
          pdfium4jStructElementGetAttributeCountMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jTextGetCharsWithBoundsMH = null;

  public static MethodHandle pdfium4jTextGetCharsWithBounds() {
    MethodHandle mh = pdfium4jTextGetCharsWithBoundsMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jTextGetCharsWithBoundsMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_text_get_chars_with_bounds",
                  FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_INT, C_POINTER),
                  false);
          pdfium4jTextGetCharsWithBoundsMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jSaveIncrementalMH = null;

  public static MethodHandle pdfium4jSaveIncremental() {
    MethodHandle mh = pdfium4jSaveIncrementalMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jSaveIncrementalMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_save_incremental",
                  FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER),
                  false);
          pdfium4jSaveIncrementalMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jSaveCopyMH = null;

  public static MethodHandle pdfium4jSaveCopy() {
    MethodHandle mh = pdfium4jSaveCopyMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jSaveCopyMH;
        if (mh == null) {
          mh =
              find("pdfium4j_save_copy", FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER), false);
          pdfium4jSaveCopyMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jReadInfoDictMH = null;

  public static MethodHandle pdfium4jReadInfoDict() {
    MethodHandle mh = pdfium4jReadInfoDictMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jReadInfoDictMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_read_info_dict",
                  FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER),
                  false);
          pdfium4jReadInfoDictMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jReadInfoDictMemMH = null;

  public static MethodHandle pdfium4jReadInfoDictMem() {
    MethodHandle mh = pdfium4jReadInfoDictMemMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jReadInfoDictMemMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_read_info_dict_mem",
                  FunctionDescriptor.of(C_INT, C_POINTER, C_SIZE_T, C_POINTER, C_POINTER),
                  false);
          pdfium4jReadInfoDictMemMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jSaveWithMetadataMH = null;

  public static MethodHandle pdfium4jSaveWithMetadata() {
    MethodHandle mh = pdfium4jSaveWithMetadataMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jSaveWithMetadataMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_save_with_metadata_native",
                  FunctionDescriptor.of(
                      C_INT, C_POINTER, C_POINTER, C_POINTER, C_INT, C_POINTER, C_INT),
                  false);
          pdfium4jSaveWithMetadataMH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle pdfium4jSaveWithMetadataMemMH = null;

  public static MethodHandle pdfium4jSaveWithMetadataMem() {
    MethodHandle mh = pdfium4jSaveWithMetadataMemMH;
    if (mh == null) {
      synchronized (ShimBindings.class) {
        mh = pdfium4jSaveWithMetadataMemMH;
        if (mh == null) {
          mh =
              find(
                  "pdfium4j_save_with_metadata_mem_native",
                  FunctionDescriptor.of(
                      C_INT, C_POINTER, C_SIZE_T, C_POINTER, C_POINTER, C_POINTER, C_INT, C_POINTER,
                      C_INT),
                  false);
          pdfium4jSaveWithMetadataMemMH = mh;
        }
      }
    }
    return mh;
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
