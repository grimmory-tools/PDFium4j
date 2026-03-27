package org.grimmory.pdfium4j.model;

/**
 * Result of probing a PDF file for basic validity and characteristics. This is a lightweight check
 * that does not fully parse the document.
 *
 * @param status the probe status
 * @param pageCount page count if available (-1 if not determinable)
 * @param encrypted whether the document requires a password
 * @param errorCode the PDFium error code if status is not OK
 * @param errorMessage human-readable error description
 */
public record PdfProbeResult(
    Status status, int pageCount, boolean encrypted, PdfErrorCode errorCode, String errorMessage) {
  public enum Status {
    /** Document is valid and can be opened. */
    OK,
    /** Document requires a password. */
    PASSWORD_REQUIRED,
    /** Document is corrupt or malformed. */
    CORRUPT,
    /** Document uses unsupported features. */
    UNSUPPORTED,
    /** File cannot be read. */
    UNREADABLE,
    /** Unknown error occurred. */
    ERROR
  }

  public boolean isValid() {
    return status == Status.OK;
  }

  public boolean needsPassword() {
    return status == Status.PASSWORD_REQUIRED || encrypted;
  }

  public static PdfProbeResult ok(int pageCount, boolean encrypted) {
    return new PdfProbeResult(Status.OK, pageCount, encrypted, PdfErrorCode.SUCCESS, null);
  }

  public static PdfProbeResult error(Status status, PdfErrorCode errorCode, String message) {
    return new PdfProbeResult(status, -1, false, errorCode, message);
  }
}
