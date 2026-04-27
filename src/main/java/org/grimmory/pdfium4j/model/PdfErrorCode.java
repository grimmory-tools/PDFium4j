package org.grimmory.pdfium4j.model;

/** PDFium error codes returned by FPDF_GetLastError(). */
public enum PdfErrorCode {
  SUCCESS(0, "No error"),
  UNKNOWN(1, "Unknown error"),
  FILE(2, "file not found or cannot be opened"),
  FORMAT(3, "invalid or corrupt PDF format"),
  PASSWORD(4, "password required or incorrect"),
  SECURITY(5, "unsupported security handler"),
  PAGE(6, "page not found or invalid");

  private final int code;
  private final String description;

  PdfErrorCode(int code, String description) {
    this.code = code;
    this.description = description;
  }

  public int code() {
    return code;
  }

  public String description() {
    return description;
  }

  public static PdfErrorCode fromCode(int code) {
    for (PdfErrorCode e : values()) {
      if (e.code == code) return e;
    }
    return UNKNOWN;
  }
}
