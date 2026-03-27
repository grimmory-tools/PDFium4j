package org.grimmory.pdfium4j;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.grimmory.pdfium4j.exception.PdfiumException;
import org.grimmory.pdfium4j.internal.NativeLoader;
import org.grimmory.pdfium4j.internal.ViewBindings;

/**
 * Manages the global PDFium library lifecycle.
 *
 * <p>PDFium requires a single call to {@code FPDF_InitLibraryWithConfig} before any document
 * operations, and {@code FPDF_DestroyLibrary} at shutdown. This class handles both automatically.
 *
 * <p>Thread safety: initialization is synchronized. After init, independent {@link PdfDocument}
 * instances on separate threads are safe. A single document/page handle must not be accessed
 * concurrently.
 */
public final class PdfiumLibrary {

  private static volatile boolean initialized = false;

  private PdfiumLibrary() {}

  /**
   * Ensure PDFium is initialized. Safe to call multiple times. Called automatically by {@link
   * PdfDocument#open} methods.
   */
  public static synchronized void initialize() {
    if (initialized) return;

    NativeLoader.ensureLoaded();

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment config = arena.allocate(ViewBindings.LIBRARY_CONFIG_LAYOUT);
      config.set(ValueLayout.JAVA_INT, 0, 2);

      ViewBindings.FPDF_InitLibraryWithConfig.invokeExact(config);
    } catch (PdfiumException e) {
      throw e;
    } catch (Throwable t) {
      throw new PdfiumException("Failed to initialize PDFium", t);
    }

    initialized = true;

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    ViewBindings.FPDF_DestroyLibrary.invokeExact();
                  } catch (Throwable ignored) {
                  }
                },
                "pdfium4j-shutdown"));
  }

  static void ensureInitialized() {
    if (!initialized) {
      initialize();
    }
  }
}
