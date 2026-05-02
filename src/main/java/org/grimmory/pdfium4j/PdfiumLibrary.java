package org.grimmory.pdfium4j;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.atomic.AtomicInteger;
import org.grimmory.pdfium4j.exception.PdfiumException;
import org.grimmory.pdfium4j.internal.AnnotBindings;
import org.grimmory.pdfium4j.internal.BitmapBindings;
import org.grimmory.pdfium4j.internal.DocBindings;
import org.grimmory.pdfium4j.internal.EditBindings;
import org.grimmory.pdfium4j.internal.NativeLoader;
import org.grimmory.pdfium4j.internal.TextBindings;
import org.grimmory.pdfium4j.internal.ViewBindings;

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
          // Version 2 is required by modern PDFium builds
          config.set(ValueLayout.JAVA_INT, 0, 2);
          ViewBindings.FPDF_InitLibraryWithConfig.invokeExact(config);
        }
        initialized = true;
      } catch (Throwable t) {
        initError = t;
        throw new PdfiumException("Failed to initialize PDFium library", t);
      }
    }
  }

  private static final AtomicInteger openDocumentCount = new AtomicInteger(0);

  static void incrementDocumentCount() {
    synchronized (LOCK) {
      if (!initialized) {
        throw new IllegalStateException("PDFium library is not initialized");
      }
      openDocumentCount.incrementAndGet();
    }
  }

  static void decrementDocumentCount() {
    synchronized (LOCK) {
      openDocumentCount.decrementAndGet();
    }
  }

  /** Ensures the library is initialized. Internal use only. */
  static void ensureInitialized() {
    if (!initialized) {
      initialize();
    }
  }

  /**
   * Shuts down the PDFium library and releases global resources.
   *
   * @throws IllegalStateException if documents are still open
   * @throws PdfiumException if shutdown fails
   */
  public static void shutdown() {
    synchronized (LOCK) {
      if (!initialized) return;
      int open = openDocumentCount.get();
      if (open > 0) {
        throw new IllegalStateException(
            "Cannot shutdown PDFium: %d documents are still open".formatted(open));
      }
      try {
        ViewBindings.FPDF_DestroyLibrary.invokeExact();
        initialized = false;
        initError = null;
      } catch (Throwable t) {
        throw new PdfiumException("Failed to shut down PDFium library", t);
      }
    }
  }

  /**
   * Returns whether the PDFium library is currently initialized.
   *
   * @return {@code true} if initialized, {@code false} otherwise
   */
  public static boolean isInitialized() {
    return initialized;
  }

  private static final boolean LOG_SWALLOWED = false;

  private static final class SwallowLoggerHolder {
    private static final System.Logger LOGGER = System.getLogger(PdfiumLibrary.class.getName());
  }

  /**
   * Central point for swallowed exceptions.
   *
   * @param t the exception to ignore
   */
  public static void ignore(Throwable t) {
    if (!LOG_SWALLOWED) return;
    SwallowLoggerHolder.LOGGER.log(System.Logger.Level.DEBUG, "Swallowed exception", t);
  }
}
