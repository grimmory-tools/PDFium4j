package org.grimmory.pdfium4j.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Format-neutral metadata contract for documents handled by PDFium4j. */
public interface BookMetadata {

  enum BookFormat {
    PDF
  }

  Optional<String> title();

  Optional<String> subtitle();

  List<String> authors();

  Optional<String> series();

  Optional<Float> seriesNumber();

  Optional<String> isbn();

  Optional<String> language();

  Optional<LocalDate> publishedDate();

  List<String> subjects();

  Optional<String> description();

  Optional<String> publisher();

  Map<String, String> customFields();
}
