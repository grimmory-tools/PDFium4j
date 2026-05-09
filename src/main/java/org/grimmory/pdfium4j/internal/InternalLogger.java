package org.grimmory.pdfium4j.internal;

/** Internal bridge for logging. DO NOT USE outside of this library. */
public final class InternalLogger {
  private static final System.Logger LOGGER = System.getLogger("PDFium4j-Internal");

  private InternalLogger() {}

  public static void warn(String msg) {
    LOGGER.log(System.Logger.Level.WARNING, msg);
  }
}
