package org.grimmory.pdfium4j.model;

/**
 * Processing policy for PDF operations.
 *
 * <p>Performance-critical paths are constrained through explicit memory budgets so FFM allocations
 * and native bitmap operations stay predictable.
 */
public record PdfProcessingPolicy(
    Mode mode,
    long maxDocumentBytes,
    long maxRenderPixels,
    int maxParallelRenderThreads,
    long fileBackedThreshold) {

  public enum Mode {
    STRICT,
    RECOVER
  }

  public static final long DEFAULT_MAX_DOCUMENT_BYTES = 512L * 1024 * 1024;
  public static final long DEFAULT_MAX_RENDER_PIXELS = 80_000_000L;
  public static final int DEFAULT_MAX_PARALLEL_THREADS =
      Math.max(1, Runtime.getRuntime().availableProcessors());
  public static final long DEFAULT_FILE_BACKED_THRESHOLD = 64L * 1024 * 1024;

  public PdfProcessingPolicy {
    if (mode == null) {
      throw new IllegalArgumentException("mode must not be null");
    }
    if (maxDocumentBytes <= 0) {
      throw new IllegalArgumentException("maxDocumentBytes must be > 0");
    }
    if (maxRenderPixels <= 0) {
      throw new IllegalArgumentException("maxRenderPixels must be > 0");
    }
    if (maxParallelRenderThreads <= 0) {
      throw new IllegalArgumentException("maxParallelRenderThreads must be > 0");
    }
    if (fileBackedThreshold <= 0) {
      throw new IllegalArgumentException("fileBackedThreshold must be > 0");
    }
  }

  public static PdfProcessingPolicy defaultPolicy() {
    return new PdfProcessingPolicy(
        Mode.RECOVER,
        DEFAULT_MAX_DOCUMENT_BYTES,
        DEFAULT_MAX_RENDER_PIXELS,
        DEFAULT_MAX_PARALLEL_THREADS,
        DEFAULT_FILE_BACKED_THRESHOLD);
  }

  public static PdfProcessingPolicy strictPolicy() {
    return new PdfProcessingPolicy(
        Mode.STRICT,
        DEFAULT_MAX_DOCUMENT_BYTES,
        DEFAULT_MAX_RENDER_PIXELS,
        DEFAULT_MAX_PARALLEL_THREADS,
        DEFAULT_FILE_BACKED_THRESHOLD);
  }

  public PdfProcessingPolicy withMode(Mode value) {
    return new PdfProcessingPolicy(
        value, maxDocumentBytes, maxRenderPixels, maxParallelRenderThreads, fileBackedThreshold);
  }

  public PdfProcessingPolicy withMaxDocumentBytes(long value) {
    return new PdfProcessingPolicy(
        mode, value, maxRenderPixels, maxParallelRenderThreads, fileBackedThreshold);
  }

  public PdfProcessingPolicy withMaxRenderPixels(long value) {
    return new PdfProcessingPolicy(
        mode, maxDocumentBytes, value, maxParallelRenderThreads, fileBackedThreshold);
  }

  public PdfProcessingPolicy withMaxParallelRenderThreads(int value) {
    return new PdfProcessingPolicy(
        mode, maxDocumentBytes, maxRenderPixels, value, fileBackedThreshold);
  }

  public PdfProcessingPolicy withFileBackedThreshold(long value) {
    return new PdfProcessingPolicy(
        mode, maxDocumentBytes, maxRenderPixels, maxParallelRenderThreads, value);
  }
}
