package org.pdfium4j.model;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Structured representation of XMP metadata extracted from a PDF.
 * Contains Dublin Core fields, PDF/A conformance info, and Calibre-specific metadata.
 *
 * @param title       dc:title
 * @param creators    dc:creator (typically authors)
 * @param description dc:description
 * @param subjects    dc:subject (keywords/tags)
 * @param publisher   dc:publisher
 * @param language    dc:language
 * @param date        dc:date
 * @param rights      dc:rights
 * @param identifiers dc:identifier (ISBNs, URIs, etc.)
 * @param pdfaConformance PDF/A conformance level (e.g., "1b", "2a", "3u"), or empty
 * @param calibreFields Calibre custom metadata fields (calibre: namespace)
 * @param customFields other custom namespace fields
 */
public record XmpMetadata(
        Optional<String> title,
        List<String> creators,
        Optional<String> description,
        List<String> subjects,
        Optional<String> publisher,
        Optional<String> language,
        Optional<String> date,
        Optional<String> rights,
        List<String> identifiers,
        Optional<String> pdfaConformance,
        Map<String, String> calibreFields,
        Map<String, String> customFields
) {
    private static final Pattern WHITESPACE_HYPHEN = Pattern.compile("[\\s-]");
    private static final Pattern ISBN_FORMAT = Pattern.compile("(?i)^(urn:isbn:|isbn[: ]?)?[0-9X-]{10,17}$");

    public XmpMetadata {
        Objects.requireNonNull(title, "title");
        creators = List.copyOf(Objects.requireNonNull(creators, "creators"));
        Objects.requireNonNull(description, "description");
        subjects = List.copyOf(Objects.requireNonNull(subjects, "subjects"));
        Objects.requireNonNull(publisher, "publisher");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(rights, "rights");
        identifiers = List.copyOf(Objects.requireNonNull(identifiers, "identifiers"));
        Objects.requireNonNull(pdfaConformance, "pdfaConformance");
        calibreFields = Map.copyOf(Objects.requireNonNull(calibreFields, "calibreFields"));
        customFields = Map.copyOf(Objects.requireNonNull(customFields, "customFields"));
    }

    /**
     * Whether this PDF declares PDF/A conformance.
     */
    public boolean isPdfA() {
        return pdfaConformance.isPresent() && !pdfaConformance.get().isBlank();
    }

    /**
     * Get the first creator (author), if any.
     */
    public Optional<String> firstCreator() {
        return creators.isEmpty() ? Optional.empty() : Optional.of(creators.getFirst());
    }

    /**
     * Get all ISBN identifiers found in dc:identifier fields.
     */
    public List<String> isbns() {
        return identifiers.stream()
                .filter(id -> ISBN_FORMAT.matcher(id).matches())
                .map(id -> WHITESPACE_HYPHEN.matcher(id.replaceAll("(?i)^(urn:isbn:|isbn[: ]?)", "")).replaceAll(""))
                .toList();
    }

    /**
     * Get Calibre series name, if present.
     */
    public Optional<String> calibreSeries() {
        return Optional.ofNullable(calibreFields.get("series"))
                .filter(s -> !s.isBlank());
    }

    /**
     * Get Calibre series index as a double, if present and parseable.
     */
    public OptionalDouble calibreSeriesIndex() {
        String val = calibreFields.get("series_index");
        if (val == null || val.isBlank()) return OptionalDouble.empty();
        try {
            return OptionalDouble.of(Double.parseDouble(val));
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }

    /**
     * Get Calibre rating (0-10 scale), if present and parseable.
     */
    public OptionalInt calibreRating() {
        String val = calibreFields.get("rating");
        if (val == null || val.isBlank()) return OptionalInt.empty();
        try {
            return OptionalInt.of(Integer.parseInt(val));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    /**
     * Get Calibre tags as a list. Calibre stores tags as comma-separated values
     * or as individual rdf:Bag entries.
     */
    public List<String> calibreTags() {
        String val = calibreFields.get("tags");
        if (val == null || val.isBlank()) return List.of();
        return Arrays.stream(val.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Create an empty XmpMetadata.
     */
    public static XmpMetadata empty() {
        return new XmpMetadata(
                Optional.empty(), List.of(), Optional.empty(), List.of(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(), Optional.empty(), Map.of(), Map.of());
    }
}
