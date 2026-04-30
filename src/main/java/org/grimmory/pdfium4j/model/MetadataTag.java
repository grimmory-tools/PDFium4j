package org.grimmory.pdfium4j.model;

import edu.umd.cs.findbugs.annotations.CheckForNull;

/** Standard PDF document metadata tags from the Info dictionary. */
public enum MetadataTag {
  TITLE("Title"),
  AUTHOR("Author"),
  SUBJECT("Subject"),
  KEYWORDS("Keywords"),
  CREATOR("Creator"),
  PRODUCER("Producer"),
  CREATION_DATE("CreationDate"),
  MOD_DATE("ModDate");

  private final String pdfKey;

  MetadataTag(String pdfKey) {
    this.pdfKey = pdfKey;
  }

  /** The PDF metadata key string (e.g. "Title", "Author"). */
  public String pdfKey() {
    return pdfKey;
  }

  /**
   * Find a tag by its PDF key (case-insensitive).
   *
   * @return the matching tag, or null if not found
   */
  @CheckForNull
  public static MetadataTag fromKey(String key) {
    if (key == null) return null;
    for (MetadataTag tag : values()) {
      if (tag.pdfKey.equalsIgnoreCase(key)) return tag;
    }
    return null;
  }
}
