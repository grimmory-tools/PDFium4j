package org.grimmory.pdfium4j.internal;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

/** Internal bridge for logging. DO NOT USE outside of this library. */
public final class InternalLogger {
  private static final Logger LOGGER = System.getLogger("PDFium4j-Internal");

  private InternalLogger() {}

  public static void warn(String msg) {
    LOGGER.log(Level.WARNING, msg);
  }

  public static void error(String msg) {
    LOGGER.log(Level.ERROR, msg);
  }
}
