package org.grimmory.pdfium4j.model;

/** PDF annotation subtypes as defined in the PDF specification. */
public enum AnnotationType {
  UNKNOWN(0),
  TEXT(1),
  LINK(2),
  FREE_TEXT(3),
  LINE(4),
  SQUARE(5),
  CIRCLE(6),
  POLYGON(7),
  POLYLINE(8),
  HIGHLIGHT(9),
  UNDERLINE(10),
  SQUIGGLY(11),
  STRIKEOUT(12),
  STAMP(13),
  CARET(14),
  INK(15),
  POPUP(16),
  FILE_ATTACHMENT(17),
  SOUND(18),
  MOVIE(19),
  WIDGET(20),
  SCREEN(21),
  PRINTER_MARK(22),
  TRAP_NET(23),
  WATERMARK(24),
  THREE_D(25),
  RICH_MEDIA(26),
  XFA_WIDGET(27),
  REDACT(28);

  private final int pdfiumCode;

  AnnotationType(int pdfiumCode) {
    this.pdfiumCode = pdfiumCode;
  }

  public int pdfiumCode() {
    return pdfiumCode;
  }

  public static AnnotationType fromCode(int code) {
    for (AnnotationType type : values()) {
      if (type.pdfiumCode == code) return type;
    }
    return UNKNOWN;
  }

  /** Whether this annotation type represents a text markup (highlight, underline, etc.) */
  public boolean isTextMarkup() {
    return this == HIGHLIGHT || this == UNDERLINE || this == SQUIGGLY || this == STRIKEOUT;
  }
}
