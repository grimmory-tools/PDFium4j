package org.grimmory.pdfium4j.model;

import java.util.Optional;

/**
 * Represents a PDF annotation on a page.
 *
 * @param type the annotation subtype
 * @param rect the annotation bounding rectangle in page coordinates (left, bottom, right, top)
 * @param contents the Contents entry (text content of the annotation), if any
 * @param author the author (T entry), if any
 * @param subject the subject line, if any
 */
public record PdfAnnotation(
    AnnotationType type,
    Rect rect,
    Optional<String> contents,
    Optional<String> author,
    Optional<String> subject) {
  /** A rectangle in PDF page coordinates (origin = bottom-left). */
  public record Rect(float left, float bottom, float right, float top) {
    public Rect {
      left = sanitize(left);
      bottom = sanitize(bottom);
      right = sanitize(right);
      top = sanitize(top);
    }

    private static float sanitize(float value) {
      return Float.isFinite(value) ? value : 0f;
    }
  }
}
