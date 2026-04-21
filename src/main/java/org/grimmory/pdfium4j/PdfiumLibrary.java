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
 * <h2>Concurrency model</h2>
 *
 * <p>Initialization is driven by the <b>Initialization-on-demand holder idiom</b> (JLS §12.4.2):
 * the JVM class-loader lock guarantees {@code FPDF_InitLibraryWithConfig} is invoked exactly once
 * per JVM, under the class-loader initialization lock, before any thread can observe a successful
 * state. No explicit synchronized block, volatile read, or double-checked locking is needed on the
 * hot path.
 *
 * <p>This makes {@link #initialize()} and {@link #isAvailable()} safe to call from any thread at
 * any time, including concurrent {@code @BeforeAll} hooks in a parallel JUnit test run.
 *
 * <p>After initialization, independent {@link PdfDocument} instances on separate threads are safe.
 * A single document or page handle must not be accessed concurrently.
 *
 * <h2>System properties</h2>
 *
 * <ul>
 *   <li><b>{@value #PROP_LIBRARY_PATH}</b>, absolute filesystem path to a {@code libpdfium} binary.
 *       When set, this path is loaded directly via {@code System.load} and classpath extraction is
 *       skipped. Useful when pdfium is installed system-wide (e.g. an Alpine Docker image).
 *   <li><b>{@value #PROP_AUTOINIT}</b>, when {@code "false"}, {@link PdfDocument} operations will
 *       not auto-trigger initialization. Callers must invoke {@link #initialize()} explicitly.
 *       Default: {@code "true"}.
 * </ul>
 */
public final class PdfiumLibrary {

  /** See class javadoc for usage. */
  public static final String PROP_LIBRARY_PATH = "pdfium4j.library.path";

  /** See class javadoc for usage. */
  public static final String PROP_AUTOINIT = "pdfium4j.autoinit";

  private PdfiumLibrary() {}

  /**
   * Ensure PDFium is initialized. Safe to call multiple times. Called automatically by {@link
   * PdfDocument#open} methods (unless {@link #PROP_AUTOINIT} is set to {@code "false"}).
   *
   * @throws PdfiumException if the native library cannot be loaded or initialized
   */
  public static void initialize() {
    Holder.STATE.require();
  }

  /**
   * Non-throwing availability probe.
   *
   * <p>Returns {@code true} if the native library has been successfully loaded and {@code
   * FPDF_InitLibraryWithConfig} has been invoked. Returns {@code false} if initialization has
   * failed, the cause is suppressed, never re-thrown from this method. Safe to call from any
   * thread.
   *
   * <p>This is the preferred way to gate optional functionality:
   *
   * <pre>{@code
   * if (PdfiumLibrary.isAvailable()) {
   *   try (PdfDocument doc = PdfDocument.open(path)) { ... }
   * }
   * }</pre>
   *
   * @return {@code true} iff PDFium is fully initialized in this JVM
   */
  public static boolean isAvailable() {
    return Holder.STATE.loaded;
  }

  /**
   * Returns the Throwable that caused initialization to fail, or {@code null} if initialization
   * succeeded or has not yet been attempted for reasons other than failure. Useful for
   * diagnostics/logging without forcing a throw.
   */
  public static Throwable loadError() {
    return Holder.STATE.loadError;
  }

  static void ensureInitialized() {
    if (!autoInitEnabled()) {
      // Honour the explicit opt-out; require the caller has already run initialize().
      if (!Holder.STATE.loaded) {
        throw new PdfiumException(
            "PDFium auto-init is disabled ("
                + PROP_AUTOINIT
                + "=false); call PdfiumLibrary.initialize() explicitly");
      }
      return;
    }
    Holder.STATE.require();
  }

  private static boolean autoInitEnabled() {
    String v = System.getProperty(PROP_AUTOINIT);
    return v == null || !"false".equalsIgnoreCase(v.trim());
  }

  private static final class Holder {
    static final State STATE = new State();
  }

  private static final class State {
    final boolean loaded;
    final Throwable loadError;

    State() {
      Throwable err = null;
      boolean ok = false;
      try {
        NativeLoader.ensureLoaded();
        try (Arena arena = Arena.ofConfined()) {
          MemorySegment config = arena.allocate(ViewBindings.LIBRARY_CONFIG_LAYOUT);
          config.set(ValueLayout.JAVA_INT, 0, 2);
          ViewBindings.FPDF_InitLibraryWithConfig.invokeExact(config);
        }
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
        ok = true;
      } catch (Throwable t) {
        err = t;
      }
      this.loaded = ok;
      this.loadError = err;
    }

    void require() {
      if (loaded) return;
      if (loadError instanceof PdfiumException pe) {
        throw pe;
      }
      throw new PdfiumException("Failed to initialize PDFium", loadError);
    }
  }
}
