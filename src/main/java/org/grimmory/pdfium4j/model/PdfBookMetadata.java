package org.grimmory.pdfium4j.model;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * PDF implementation of {@link BookMetadata}. Wraps PDF metadata from Info dictionary and XMP
 * packet to provide unified access to standard metadata fields.
 *
 * @param title the book's primary title
 * @param authors list of all authors/creators
 * @param series the series name (from XMP Calibre namespace)
 * @param seriesNumber the volume number within the series
 * @param isbn the ISBN identifier
 * @param language the language code
 * @param publishedDate the publication date
 * @param subjects list of subjects/genres
 * @param description the book description/abstract
 * @param publisher the publisher name
 * @param rawMetadata the underlying XmpMetadata object
 * @param customFields additional custom metadata fields
 */
public record PdfBookMetadata(
    Optional<String> title,
    Optional<String> subtitle,
    List<String> authors,
    Optional<String> series,
    Optional<Float> seriesNumber,
    Optional<String> isbn,
    Optional<String> language,
    Optional<LocalDate> publishedDate,
    List<String> subjects,
    Optional<String> description,
    Optional<String> publisher,
    XmpMetadata rawMetadata,
    Map<String, String> customFields)
    implements BookMetadata {

  private static final Pattern UNIFORM_DIGIT_PATTERN = Pattern.compile("^(\\d)\\1{9,12}$");
  private static final Pattern NON_ISBN_CHARS_PATTERN = Pattern.compile("[^0-9Xx]");

  public PdfBookMetadata {
    authors = Collections.unmodifiableList(authors);
    subjects = Collections.unmodifiableList(subjects);
    customFields = Collections.unmodifiableMap(customFields);
  }

  @CheckForNull
  private static String cleanIsbn(String id) {
    if (id == null) return null;
    String cleaned = NON_ISBN_CHARS_PATTERN.matcher(id).replaceAll("").toUpperCase(Locale.ROOT);

    // Reject uniform sequences like 0000000000 or 1111111111111 (fake ISBNs)
    if (UNIFORM_DIGIT_PATTERN.matcher(cleaned).matches()) return null;

    if (cleaned.length() == 10 && isValidIsbn10(cleaned)) return cleaned;
    if (cleaned.length() == 13 && isValidIsbn13(cleaned)) return cleaned;
    return null;
  }

  private static boolean isValidIsbn10(String isbn) {
    int sum = 0;
    for (int i = 0; i < 10; i++) {
      char c = isbn.charAt(i);
      int digit = (c == 'X') ? 10 : (c - '0');
      if (digit < 0 || (digit > 9 && i < 9)) return false;
      sum += digit * (10 - i);
    }
    return sum % 11 == 0;
  }

  private static boolean isValidIsbn13(String isbn) {
    int sum = 0;
    for (int i = 0; i < 13; i++) {
      int digit = isbn.charAt(i) - '0';
      if (digit < 0 || digit > 9) return false;
      sum += digit * ((i % 2 == 0) ? 1 : 3);
    }
    return sum % 10 == 0;
  }
}
