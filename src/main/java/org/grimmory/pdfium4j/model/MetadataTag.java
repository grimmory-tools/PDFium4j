package org.grimmory.pdfium4j.model;

import java.nio.charset.StandardCharsets;

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
  private final byte[] pdfKeyBytes;

  MetadataTag(String pdfKey) {
    this.pdfKey = pdfKey;
    this.pdfKeyBytes = pdfKey.getBytes(StandardCharsets.ISO_8859_1);
  }

  /** The PDF metadata key string (e.g. "Title", "Author"). */
  public String pdfKey() {
    return pdfKey;
  }

  /**
   * The pre-encoded PDF metadata key bytes.
   *
   * @return the internal byte array (MUST NOT BE MODIFIED)
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings("EI_EXPOSE_REP")
  public byte[] pdfKeyBytes() {
    return pdfKeyBytes;
  }
}
