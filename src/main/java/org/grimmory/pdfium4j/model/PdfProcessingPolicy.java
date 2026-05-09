package org.grimmory.pdfium4j.model;

/**
 * Processing policy for PDF operations.
 *
 * <p>Performance-critical paths are constrained through explicit memory budgets so FFM allocations
 * and native bitmap operations stay predictable.
 */
public record PdfProcessingPolicy(
    Mode mode,
    long maxRenderPixels,
    int maxParallelRenderThreads,
    long fileBackedThreshold,
    long maxPageCacheBytes,
    long maxTextPageCacheBytes,
    int prefetchRadius) {

  public enum Mode {
    STRICT,
    RECOVER
  }

  public static final long DEFAULT_MAX_RENDER_PIXELS = 80_000_000L;
  public static final int DEFAULT_MAX_PARALLEL_THREADS =
      Math.max(1, Runtime.getRuntime().availableProcessors());
  public static final long DEFAULT_FILE_BACKED_THRESHOLD = 64L * 1024 * 1024;
  public static final long DEFAULT_MAX_PAGE_CACHE_BYTES = 64L * 1024 * 1024;
  public static final long DEFAULT_MAX_TEXT_PAGE_CACHE_BYTES = 16L * 1024 * 1024;
  public static final int DEFAULT_PREFETCH_RADIUS = 2;

  public PdfProcessingPolicy {
    if (mode == null) {
      throw new IllegalArgumentException("mode must not be null");
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
    if (maxPageCacheBytes < 0) {
      throw new IllegalArgumentException("maxPageCacheBytes must be >= 0");
    }
    if (maxTextPageCacheBytes < 0) {
      throw new IllegalArgumentException("maxTextPageCacheBytes must be >= 0");
    }
    if (prefetchRadius < 0) {
      throw new IllegalArgumentException("prefetchRadius must be >= 0");
    }
  }

  public static PdfProcessingPolicy defaultPolicy() {
    return new PdfProcessingPolicy(
        Mode.RECOVER,
        DEFAULT_MAX_RENDER_PIXELS,
        DEFAULT_MAX_PARALLEL_THREADS,
        DEFAULT_FILE_BACKED_THRESHOLD,
        DEFAULT_MAX_PAGE_CACHE_BYTES,
        DEFAULT_MAX_TEXT_PAGE_CACHE_BYTES,
        DEFAULT_PREFETCH_RADIUS);
  }

  public PdfProcessingPolicy withMode(Mode newMode) {
    return new PdfProcessingPolicy(
        newMode,
        maxRenderPixels,
        maxParallelRenderThreads,
        fileBackedThreshold,
        maxPageCacheBytes,
        maxTextPageCacheBytes,
        prefetchRadius);
  }
}
