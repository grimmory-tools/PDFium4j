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

  private static volatile MethodHandle FPDFBitmap_Create_MH = null;

  public static MethodHandle fpdfBitmapCreate() {
    MethodHandle mh = FPDFBitmap_Create_MH;
    if (mh == null) {
      synchronized (BitmapBindings.class) {
        mh = FPDFBitmap_Create_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDFBitmap_Create",
                  FunctionDescriptor.of(C_POINTER, C_INT, C_INT, C_INT),
                  false);
          FPDFBitmap_Create_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFBitmap_CreateEx_MH = null;

  public static MethodHandle fpdfBitmapCreateEx() {
    MethodHandle mh = FPDFBitmap_CreateEx_MH;
    if (mh == null) {
      synchronized (BitmapBindings.class) {
        mh = FPDFBitmap_CreateEx_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDFBitmap_CreateEx",
                  FunctionDescriptor.of(C_POINTER, C_INT, C_INT, C_INT, C_POINTER, C_INT),
                  false);
          FPDFBitmap_CreateEx_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFBitmap_FillRect_MH = null;

  public static MethodHandle fpdfBitmapFillRect() {
    MethodHandle mh = FPDFBitmap_FillRect_MH;
    if (mh == null) {
      synchronized (BitmapBindings.class) {
        mh = FPDFBitmap_FillRect_MH;
        if (mh == null) {
          mh =
              find(
                  "FPDFBitmap_FillRect",
                  FunctionDescriptor.ofVoid(C_POINTER, C_INT, C_INT, C_INT, C_INT, C_LONG),
                  false);
          FPDFBitmap_FillRect_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFBitmap_GetBuffer_MH = null;

  public static MethodHandle fpdfBitmapGetBuffer() {
    MethodHandle mh = FPDFBitmap_GetBuffer_MH;
    if (mh == null) {
      synchronized (BitmapBindings.class) {
        mh = FPDFBitmap_GetBuffer_MH;
        if (mh == null) {
          mh = find("FPDFBitmap_GetBuffer", FunctionDescriptor.of(C_POINTER, C_POINTER), false);
          FPDFBitmap_GetBuffer_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFBitmap_GetWidth_MH = null;

  public static MethodHandle fpdfBitmapGetWidth() {
    MethodHandle mh = FPDFBitmap_GetWidth_MH;
    if (mh == null) {
      synchronized (BitmapBindings.class) {
        mh = FPDFBitmap_GetWidth_MH;
        if (mh == null) {
          mh = find("FPDFBitmap_GetWidth", FunctionDescriptor.of(C_INT, C_POINTER), true);
          FPDFBitmap_GetWidth_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFBitmap_GetHeight_MH = null;

  public static MethodHandle fpdfBitmapGetHeight() {
    MethodHandle mh = FPDFBitmap_GetHeight_MH;
    if (mh == null) {
      synchronized (BitmapBindings.class) {
        mh = FPDFBitmap_GetHeight_MH;
        if (mh == null) {
          mh = find("FPDFBitmap_GetHeight", FunctionDescriptor.of(C_INT, C_POINTER), true);
          FPDFBitmap_GetHeight_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFBitmap_GetStride_MH = null;

  public static MethodHandle fpdfBitmapGetStride() {
    MethodHandle mh = FPDFBitmap_GetStride_MH;
    if (mh == null) {
      synchronized (BitmapBindings.class) {
        mh = FPDFBitmap_GetStride_MH;
        if (mh == null) {
          mh = find("FPDFBitmap_GetStride", FunctionDescriptor.of(C_INT, C_POINTER), true);
          FPDFBitmap_GetStride_MH = mh;
        }
      }
    }
    return mh;
  }

  private static volatile MethodHandle FPDFBitmap_Destroy_MH = null;

  public static MethodHandle fpdfBitmapDestroy() {
    MethodHandle mh = FPDFBitmap_Destroy_MH;
    if (mh == null) {
      synchronized (BitmapBindings.class) {
        mh = FPDFBitmap_Destroy_MH;
        if (mh == null) {
          mh = find("FPDFBitmap_Destroy", FunctionDescriptor.ofVoid(C_POINTER), false);
          FPDFBitmap_Destroy_MH = mh;
        }
      }
    }
    return mh;
  }
}
