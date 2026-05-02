package org.grimmory.pdfium4j.model;

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
}
