package org.grimmory.pdfium4j.model;

/**
 * Options for controlling PDF save behavior.
 *
 * @param skipValidation when {@code true}, skip the re-parse validation step after appending an
 *     incremental update. This eliminates a full PDF re-open (~30-40% of save time) but trades away
 *     the guarantee that corrupt output is never produced. Safe for metadata-only changes where the
 *     incremental update logic is well-tested.
 */
public record SaveOptions(boolean skipValidation) {

  /** Default options: full validation enabled. */
  public static final SaveOptions DEFAULT = new SaveOptions(false);

  /** Skip validation — use only when the save is metadata-only. */
  public static final SaveOptions SKIP_VALIDATION = new SaveOptions(true);
}
