package org.grimmory.pdfium4j;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.grimmory.pdfium4j.exception.PdfiumException;
import org.grimmory.pdfium4j.internal.*;

/**
 * Global initialization and configuration for the PDFium native library.
 *
 * <p>PDFium must be initialized before performing any document operations. This is handled
 * automatically by the high-level API, but can be triggered explicitly.
 */
public final class PdfiumLibrary {

  private static final Object LOCK = new Object();
  private static volatile boolean initialized = false;
  private static volatile Throwable initError = null;

  private PdfiumLibrary() {}

  /**
   * Initializes the PDFium library if not already initialized. This method is thread-safe and can
   * be called multiple times.
   *
   * @throws PdfiumException if initialization fails (e.g. binaries not found)
   */
  public static void initialize() {
    if (initialized) return;

    synchronized (LOCK) {
      if (initialized) return;
      if (initError != null) {
        throw new PdfiumException("PDFium previously failed to initialize", initError);
      }

      try {
        NativeLoader.ensureLoaded();

        // Check for required symbols AFTER loading the library
        ViewBindings.checkRequired();
        DocBindings.checkRequired();
        EditBindings.checkRequired();
        BitmapBindings.checkRequired();
        TextBindings.checkRequired();
        AnnotBindings.checkRequired();

        try (Arena arena = Arena.ofConfined()) {
          MemorySegment config = arena.allocate(ViewBindings.LIBRARY_CONFIG_LAYOUT);
          // Use version 1 for maximum compatibility with older builds
          config.set(ValueLayout.JAVA_INT, 0, 1);
          ViewBindings.FPDF_InitLibraryWithConfig.invokeExact(config);
        }
        initialized = true;
      } catch (Throwable t) {
        initError = t;
        throw new PdfiumException("Failed to initialize PDFium library", t);
      }
    }
  }

  /**
   * Shuts down the PDFium library and releases all global native resources. Subsequent operations
   * will fail until re-initialized.
   */
  public static void shutdown() {
    synchronized (LOCK) {
      if (!initialized) return;
      try {
        ViewBindings.FPDF_DestroyLibrary.invokeExact();
      } catch (Throwable ignored) {
      } finally {
        initialized = false;
        initError = null;
      }
    }
  }

  /** Ensures the library is initialized. Internal use only. */
  static void ensureInitialized() {
    if (!initialized) {
      initialize();
    }
  }

  /** Whether the library is currently initialized. */
  public static boolean isInitialized() {
    return initialized;
  }
}
