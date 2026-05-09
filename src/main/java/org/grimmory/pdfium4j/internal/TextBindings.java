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

  private static volatile MethodHandle FPDFText_LoadPage_MH = null;

  public static MethodHandle fpdfTextLoadPage() {
    MethodHandle mh = FPDFText_LoadPage_MH;
    if (mh == null) {
      synchronized (TextBindings.class) {
        mh = FPDFText_LoadPage_MH;
        if (mh == null) {
          mh = find("FPDFText_LoadPage", FunctionDescriptor.of(C_POINTER, C_POINTER), false);
          FPDFText_LoadPage_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFText_ClosePage_MH = null;

  public static MethodHandle fpdfTextClosePage() {
    MethodHandle mh = FPDFText_ClosePage_MH;
    if (mh == null) {
      synchronized (TextBindings.class) {
        mh = FPDFText_ClosePage_MH;
        if (mh == null) {
          mh = find("FPDFText_ClosePage", FunctionDescriptor.ofVoid(C_POINTER), false);
          FPDFText_ClosePage_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFText_CountChars_MH = null;

  public static MethodHandle fpdfTextCountChars() {
    MethodHandle mh = FPDFText_CountChars_MH;
    if (mh == null) {
      synchronized (TextBindings.class) {
        mh = FPDFText_CountChars_MH;
        if (mh == null) {
          mh = find("FPDFText_CountChars", FunctionDescriptor.of(C_INT, C_POINTER), true);
          FPDFText_CountChars_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFText_GetText_MH = null;

  public static MethodHandle fpdfTextGetText() {
    MethodHandle mh = FPDFText_GetText_MH;
    if (mh == null) {
      synchronized (TextBindings.class) {
        mh = FPDFText_GetText_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDFText_GetText",
                  FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_INT, C_POINTER),
                  false);
          FPDFText_GetText_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFLink_LoadWebLinks_MH = null;

  public static MethodHandle fpdfLinkLoadWebLinks() {
    MethodHandle mh = FPDFLink_LoadWebLinks_MH;
    if (mh == null) {
      synchronized (TextBindings.class) {
        mh = FPDFLink_LoadWebLinks_MH;
        if (mh == null) {
          mh = find("FPDFLink_LoadWebLinks", FunctionDescriptor.of(C_POINTER, C_POINTER), false);
          FPDFLink_LoadWebLinks_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFLink_CountWebLinks_MH = null;

  public static MethodHandle fpdfLinkCountWebLinks() {
    MethodHandle mh = FPDFLink_CountWebLinks_MH;
    if (mh == null) {
      synchronized (TextBindings.class) {
        mh = FPDFLink_CountWebLinks_MH;
        if (mh == null) {
          mh = find("FPDFLink_CountWebLinks", FunctionDescriptor.of(C_INT, C_POINTER), true);
          FPDFLink_CountWebLinks_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFLink_GetURL_MH = null;

  public static MethodHandle fpdfLinkGetURL() {
    MethodHandle mh = FPDFLink_GetURL_MH;
    if (mh == null) {
      synchronized (TextBindings.class) {
        mh = FPDFLink_GetURL_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDFLink_GetURL",
                  FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_POINTER, C_INT),
                  false);
          FPDFLink_GetURL_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFLink_CountRects_MH = null;

  public static MethodHandle fpdfLinkCountRects() {
    MethodHandle mh = FPDFLink_CountRects_MH;
    if (mh == null) {
      synchronized (TextBindings.class) {
        mh = FPDFLink_CountRects_MH;
        if (mh == null) {
          mh = find("FPDFLink_CountRects", FunctionDescriptor.of(C_INT, C_POINTER, C_INT), true);
          FPDFLink_CountRects_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFLink_GetRect_MH = null;

  public static MethodHandle fpdfLinkGetRect() {
    MethodHandle mh = FPDFLink_GetRect_MH;
    if (mh == null) {
      synchronized (TextBindings.class) {
        mh = FPDFLink_GetRect_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDFLink_GetRect",
                  FunctionDescriptor.of(
                      C_INT, C_POINTER, C_INT, C_INT, C_POINTER, C_POINTER, C_POINTER, C_POINTER),
                  false);
          FPDFLink_GetRect_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFLink_CloseWebLinks_MH = null;

  public static MethodHandle fpdfLinkCloseWebLinks() {
    MethodHandle mh = FPDFLink_CloseWebLinks_MH;
    if (mh == null) {
      synchronized (TextBindings.class) {
        mh = FPDFLink_CloseWebLinks_MH;
        if (mh == null) {
          mh = find("FPDFLink_CloseWebLinks", FunctionDescriptor.ofVoid(C_POINTER), false);
          FPDFLink_CloseWebLinks_MH = mh;
        }
      }
    }
    return mh;
  }
}
