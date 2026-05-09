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

/** FFM bindings for PDFium document metadata and bookmark functions from {@code fpdf_doc.h}. */
public final class DocBindings {

  private DocBindings() {}

  private static MethodHandle find(String name, FunctionDescriptor desc, boolean critical) {
    MemorySegment addr = LOOKUP.find(name).orElse(null);
    if (addr == null) return null;
    return LINKER.downcallHandle(
        addr, desc, critical ? FfmHelper.CRITICAL_OPTIONS : FfmHelper.NO_OPTIONS);
  }

  public static void checkRequired() {
    try {
      Objects.requireNonNull(fpdfGetMetaText(), "FPDF_GetMetaText");
      Objects.requireNonNull(fpdfBookmarkGetFirstChild(), "FPDFBookmark_GetFirstChild");
      Objects.requireNonNull(fpdfBookmarkGetNextSibling(), "FPDFBookmark_GetNextSibling");
      Objects.requireNonNull(fpdfBookmarkGetTitle(), "FPDFBookmark_GetTitle");
      Objects.requireNonNull(fpdfBookmarkGetDest(), "FPDFBookmark_GetDest");
      Objects.requireNonNull(fpdfBookmarkGetAction(), "FPDFBookmark_GetAction");
      Objects.requireNonNull(fpdfActionGetType(), "FPDFAction_GetType");
      Objects.requireNonNull(fpdfActionGetDest(), "FPDFAction_GetDest");
      Objects.requireNonNull(fpdfGetDestPageIndex(), "FPDFDest_GetDestPageIndex");
    } catch (NullPointerException e) {
      throw new RuntimeException("Missing required PDFium doc symbol: " + e.getMessage(), e);
    }
  }

  private static volatile MethodHandle FPDF_GetMetaText_MH = null;

  public static MethodHandle fpdfGetMetaText() {
    MethodHandle mh = FPDF_GetMetaText_MH;
    if (mh == null) {
      synchronized (DocBindings.class) {
        mh = FPDF_GetMetaText_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDF_GetMetaText",
                  FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER, C_POINTER, C_LONG),
                  false);
          FPDF_GetMetaText_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDF_GetXMPMetadata_MH = null;

  public static MethodHandle fpdfGetXMPMetadata() {
    MethodHandle mh = FPDF_GetXMPMetadata_MH;
    if (mh == null) {
      synchronized (DocBindings.class) {
        mh = FPDF_GetXMPMetadata_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDF_GetXMPMetadata",
                  FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER, C_LONG),
                  false);
          FPDF_GetXMPMetadata_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDF_GetFileVersion_MH = null;

  public static MethodHandle fpdfGetFileVersion() {
    MethodHandle mh = FPDF_GetFileVersion_MH;
    if (mh == null) {
      synchronized (DocBindings.class) {
        mh = FPDF_GetFileVersion_MH;
        if (mh == null) {
          mh =
              find("FPDF_GetFileVersion", FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER), true);
          FPDF_GetFileVersion_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFPage_Delete_MH = null;

  public static MethodHandle fpdfPageDelete() {
    MethodHandle mh = FPDFPage_Delete_MH;
    if (mh == null) {
      synchronized (DocBindings.class) {
        mh = FPDFPage_Delete_MH;
        if (mh == null) {
          mh = find("FPDFPage_Delete", FunctionDescriptor.ofVoid(C_POINTER, C_INT), false);
          FPDFPage_Delete_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFBookmark_GetFirstChild_MH = null;

  public static MethodHandle fpdfBookmarkGetFirstChild() {
    MethodHandle mh = FPDFBookmark_GetFirstChild_MH;
    if (mh == null) {
      synchronized (DocBindings.class) {
        mh = FPDFBookmark_GetFirstChild_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDFBookmark_GetFirstChild",
                  FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER),
                  true);
          FPDFBookmark_GetFirstChild_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFBookmark_GetNextSibling_MH = null;

  public static MethodHandle fpdfBookmarkGetNextSibling() {
    MethodHandle mh = FPDFBookmark_GetNextSibling_MH;
    if (mh == null) {
      synchronized (DocBindings.class) {
        mh = FPDFBookmark_GetNextSibling_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDFBookmark_GetNextSibling",
                  FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER),
                  true);
          FPDFBookmark_GetNextSibling_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFBookmark_GetTitle_MH = null;

  public static MethodHandle fpdfBookmarkGetTitle() {
    MethodHandle mh = FPDFBookmark_GetTitle_MH;
    if (mh == null) {
      synchronized (DocBindings.class) {
        mh = FPDFBookmark_GetTitle_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDFBookmark_GetTitle",
                  FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER, C_LONG),
                  false);
          FPDFBookmark_GetTitle_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFBookmark_GetDest_MH = null;

  public static MethodHandle fpdfBookmarkGetDest() {
    MethodHandle mh = FPDFBookmark_GetDest_MH;
    if (mh == null) {
      synchronized (DocBindings.class) {
        mh = FPDFBookmark_GetDest_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDFBookmark_GetDest",
                  FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER),
                  true);
          FPDFBookmark_GetDest_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFBookmark_GetAction_MH = null;

  public static MethodHandle fpdfBookmarkGetAction() {
    MethodHandle mh = FPDFBookmark_GetAction_MH;
    if (mh == null) {
      synchronized (DocBindings.class) {
        mh = FPDFBookmark_GetAction_MH;
        if (mh == null) {
          mh = find("FPDFBookmark_GetAction", FunctionDescriptor.of(C_POINTER, C_POINTER), true);
          FPDFBookmark_GetAction_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFAction_GetType_MH = null;

  public static MethodHandle fpdfActionGetType() {
    MethodHandle mh = FPDFAction_GetType_MH;
    if (mh == null) {
      synchronized (DocBindings.class) {
        mh = FPDFAction_GetType_MH;
        if (mh == null) {
          mh = find("FPDFAction_GetType", FunctionDescriptor.of(C_LONG, C_POINTER), true);
          FPDFAction_GetType_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFAction_GetDest_MH = null;

  public static MethodHandle fpdfActionGetDest() {
    MethodHandle mh = FPDFAction_GetDest_MH;
    if (mh == null) {
      synchronized (DocBindings.class) {
        mh = FPDFAction_GetDest_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDFAction_GetDest",
                  FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER),
                  true);
          FPDFAction_GetDest_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFDest_GetDestPageIndex_MH = null;

  public static MethodHandle fpdfGetDestPageIndex() {
    MethodHandle mh = FPDFDest_GetDestPageIndex_MH;
    if (mh == null) {
      synchronized (DocBindings.class) {
        mh = FPDFDest_GetDestPageIndex_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDFDest_GetDestPageIndex",
                  FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER),
                  true);
          FPDFDest_GetDestPageIndex_MH = mh;
        }
      }
    }
    return mh;
  }
}
