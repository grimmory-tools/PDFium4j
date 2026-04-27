package org.grimmory.pdfium4j.internal;

import static java.lang.foreign.ValueLayout.*;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

/** FFM bindings for PDFium text extraction functions from {@code fpdf_text.h}. */
public final class TextBindings {

  private static final Linker LINKER = Linker.nativeLinker();
  private static final SymbolLookup LOOKUP = SymbolLookup.loaderLookup();

  private TextBindings() {}

  private static MethodHandle downcall(String name, FunctionDescriptor desc) {
    return LOOKUP.find(name).map(addr -> LINKER.downcallHandle(addr, desc)).orElse(null);
  }

  private static MethodHandle downcallCritical(String name, FunctionDescriptor desc) {
    return LOOKUP
        .find(name)
        .map(addr -> LINKER.downcallHandle(addr, desc, Linker.Option.critical(false)))
        .orElse(null);
  }

  public static void checkRequired() {
    Objects.requireNonNull(FPDFText_LoadPage, "FPDFText_LoadPage");
    Objects.requireNonNull(FPDFText_CountChars, "FPDFText_CountChars");
  }

  /** Load a text page from a page handle. Returns FPDF_TEXTPAGE (NULL on failure). */
  public static final MethodHandle FPDFText_LoadPage =
      downcall("FPDFText_LoadPage", FunctionDescriptor.of(ADDRESS, ADDRESS));

  /** Close a text page handle. */
  public static final MethodHandle FPDFText_ClosePage =
      downcall("FPDFText_ClosePage", FunctionDescriptor.ofVoid(ADDRESS));

  /** Count characters on the text page. Returns -1 on error. */
  public static final MethodHandle FPDFText_CountChars =
      downcallCritical("FPDFText_CountChars", FunctionDescriptor.of(JAVA_INT, ADDRESS));

  /**
   * Get text in a character range (UTF-16LE output). Returns number of characters written
   * (including null terminator).
   *
   * <p>Parameters: textPage (FPDF_TEXTPAGE handle), startIndex (0-based), count (number of
   * characters to extract), result (UTF-16LE output buffer, must hold (count+1)*2 bytes).
   */
  public static final MethodHandle FPDFText_GetText =
      downcall(
          "FPDFText_GetText",
          FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS));

  /** Get unicode code point of a character by index. Returns 0 on error. */
  public static final MethodHandle FPDFText_GetUnicode =
      downcallCritical("FPDFText_GetUnicode", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

  /**
   * Get bounded text within a rectangle (UTF-16LE output). Returns number of characters written
   * (including null terminator).
   */
  public static final MethodHandle FPDFText_GetBoundedText =
      downcall(
          "FPDFText_GetBoundedText",
          FunctionDescriptor.of(
              JAVA_INT,
              ADDRESS,
              JAVA_DOUBLE,
              JAVA_DOUBLE,
              JAVA_DOUBLE,
              JAVA_DOUBLE,
              ADDRESS,
              JAVA_INT));

  /**
   * Get character bounding box by index. Writes left, right, bottom, top to the output pointers
   * (doubles). Returns 1 on success, 0 on failure.
   */
  public static final MethodHandle FPDFText_GetCharBox =
      downcall(
          "FPDFText_GetCharBox",
          FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

  /** Get font size of character at given index. Returns font size as double. */
  public static final MethodHandle FPDFText_GetFontSize =
      downcallCritical(
          "FPDFText_GetFontSize", FunctionDescriptor.of(JAVA_DOUBLE, ADDRESS, JAVA_INT));

  /** Get the origin point (baseline start) of a character by index. Returns 1 on success. */
  public static final MethodHandle FPDFText_GetCharOrigin =
      downcall(
          "FPDFText_GetCharOrigin",
          FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));

  /** Load web links from a text page. Returns FPDF_PAGELINK handle. */
  public static final MethodHandle FPDFLink_LoadWebLinks =
      downcall("FPDFLink_LoadWebLinks", FunctionDescriptor.of(ADDRESS, ADDRESS));

  /** Count detected web links. */
  public static final MethodHandle FPDFLink_CountWebLinks =
      downcallCritical("FPDFLink_CountWebLinks", FunctionDescriptor.of(JAVA_INT, ADDRESS));

  /**
   * Get URL of a web link (UTF-16LE, double-call pattern). Returns number of characters (including
   * null terminator).
   */
  public static final MethodHandle FPDFLink_GetURL =
      downcall(
          "FPDFLink_GetURL", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));

  /** Get text range for a web link. Returns 1 on success. */
  public static final MethodHandle FPDFLink_GetTextRange =
      downcall(
          "FPDFLink_GetTextRange",
          FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));

  /** Count rectangles for a web link. */
  public static final MethodHandle FPDFLink_CountRects =
      downcallCritical("FPDFLink_CountRects", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

  /**
   * Get a bounding rectangle for a web link. Writes left, top, right, bottom to output pointers
   * (doubles). Returns 1 on success.
   */
  public static final MethodHandle FPDFLink_GetRect =
      downcall(
          "FPDFLink_GetRect",
          FunctionDescriptor.of(
              JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

  /** Close web links handle. */
  public static final MethodHandle FPDFLink_CloseWebLinks =
      downcall("FPDFLink_CloseWebLinks", FunctionDescriptor.ofVoid(ADDRESS));
}
