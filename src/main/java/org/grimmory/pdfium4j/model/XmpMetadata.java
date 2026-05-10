package org.grimmory.pdfium4j.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Structured representation of XMP metadata extracted from a PDF. Contains Dublin Core fields,
 * PDF/A conformance info, and Calibre-specific metadata.
 *
 * @param title dc:title
 * @param creators dc:creator (typically authors)
 * @param description dc:description
 * @param subjects dc:subject (keywords/tags)
 * @param publisher dc:publisher
 * @param language dc:language
 * @param date dc:date
 * @param rights dc:rights
 * @param identifiers dc:identifier (ISBNs, URIs, etc.)
 * @param pdfaConformance PDF/A conformance level (e.g., "1b", "2a", "3u"), or empty
 * @param calibreFields Calibre custom metadata fields (calibre: namespace)
 * @param customFields other custom namespace fields (simple text values)
 * @param customListFields other custom namespace fields (list/bag values)
 * @param xmpIdentifiers qualified identifiers from xmp:Identifier
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
    Map<String, String> customFields,
    Map<String, List<String>> customListFields,
    List<QualifiedIdentifier> xmpIdentifiers) {

  /** A qualified identifier with a scheme and a value. */
  public record QualifiedIdentifier(String scheme, String value) {
    public QualifiedIdentifier {
      Objects.requireNonNull(scheme, "scheme");
      Objects.requireNonNull(value, "value");
    }
  }

  private static final Pattern WHITESPACE_HYPHEN = Pattern.compile("[\\s-]");
  private static final Pattern ISBN_FORMAT =
      Pattern.compile("(?i)^(urn:isbn:|isbn[: ]?)?[0-9X-]{10,17}$");
  private static final Pattern PATTERN = Pattern.compile("(?i)^(urn:isbn:|isbn[: ]?)");

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
    Map<String, List<String>> listCopy = LinkedHashMap.newLinkedHashMap(16);
    Objects.requireNonNull(customListFields, "customListFields")
        .forEach(
            (k, v) ->
                listCopy.put(
                    Objects.requireNonNull(k, "customListFields key"),
                    List.copyOf(Objects.requireNonNull(v, "customListFields value"))));
    customListFields = Collections.unmodifiableMap(listCopy);
    xmpIdentifiers = List.copyOf(Objects.requireNonNull(xmpIdentifiers, "xmpIdentifiers"));
  }

  /** Get all ISBN identifiers found in dc:identifier fields. */
  public List<String> isbns() {
    return identifiers.stream()
        .filter(id -> ISBN_FORMAT.matcher(id).matches())
        .map(id -> WHITESPACE_HYPHEN.matcher(PATTERN.matcher(id).replaceAll("")).replaceAll(""))
        .toList();
  }

  /** Get Calibre series name, if present. */
  public Optional<String> calibreSeries() {
    return Optional.ofNullable(calibreFields.get("series")).filter(s -> !s.isBlank());
  }

  /** Get Calibre series index, if present and parseable. */
  public Optional<Double> calibreSeriesIndex() {
    String val = calibreFields.get("series_index");
    if (val == null || val.isBlank()) return Optional.empty();
    try {
      return Optional.of(Double.parseDouble(val));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  /** Get Calibre rating (0-10 scale), if present and parseable. */
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
   * Get Calibre tags as a list. Calibre stores tags as comma-separated values or as individual
   * rdf:Bag entries.
   */
  public List<String> calibreTags() {
    String val = calibreFields.get("tags");
    if (val == null || val.isBlank()) return List.of();
    return Arrays.stream(val.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  /**
   * Finds a simple field by its local name, searching across all custom namespaces. Returns the
   * first match found.
   */
  public Optional<String> findField(String localName) {
    String suffix = ":" + localName;
    for (Map.Entry<String, String> entry : customFields.entrySet()) {
      if (entry.getKey().endsWith(suffix) || entry.getKey().equals(localName)) {
        return Optional.of(entry.getValue());
      }
    }
    // Also check calibre fields
    for (Map.Entry<String, String> entry : calibreFields.entrySet()) {
      if (entry.getKey().equals(localName)) {
        return Optional.of(entry.getValue());
      }
    }
    return Optional.empty();
  }

  /**
   * Finds a list field by its local name, searching across all custom namespaces. Returns the first
   * match found.
   */
  public List<String> findListField(String localName) {
    String suffix = ":" + localName;
    for (Map.Entry<String, List<String>> entry : customListFields.entrySet()) {
      if (entry.getKey().endsWith(suffix) || entry.getKey().equals(localName)) {
        return entry.getValue();
      }
    }
    return Collections.emptyList();
  }

  /**
   * Get an XMP identifier value by its scheme (case-insensitive).
   *
   * <p>Note: This performs a linear scan of the identifiers list.
   *
   * @param scheme the scheme (e.g. "ISBN", "AMAZON", "GOOGLE")
   * @return the value, if found
   */
  public Optional<String> xmpIdentifier(String scheme) {
    return xmpIdentifiers.stream()
        .filter(id -> id.scheme().equalsIgnoreCase(scheme))
        .map(QualifiedIdentifier::value)
        .findFirst();
  }

  /** Creates a new {@link Builder} initialized with the values of this metadata. */
  public Builder toBuilder() {
    return new Builder()
        .title(title.orElse(null))
        .creators(creators)
        .description(description.orElse(null))
        .subjects(subjects)
        .publisher(publisher.orElse(null))
        .language(language.orElse(null))
        .date(date.orElse(null))
        .rights(rights.orElse(null))
        .identifiers(identifiers)
        .pdfaConformance(pdfaConformance.orElse(null))
        .calibreFields(calibreFields)
        .customFields(customFields)
        .customListFields(customListFields)
        .xmpIdentifiers(xmpIdentifiers);
  }

  /** Create an empty XmpMetadata. */
  public static XmpMetadata empty() {
    return builder().build();
  }

  /** Create a new {@link Builder} for {@link XmpMetadata}. */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for {@link XmpMetadata} to insulate callers from record component additions. */
  public static final class Builder {
    private String title;
    private final List<String> creators = new ArrayList<>(8);
    private String description;
    private final List<String> subjects = new ArrayList<>(8);
    private String publisher;
    private String language;
    private String date;
    private String rights;
    private final List<String> identifiers = new ArrayList<>(8);
    private String pdfaConformance;
    private final Map<String, String> calibreFields = new LinkedHashMap<>(8);
    private final Map<String, String> customFields = new LinkedHashMap<>(8);
    private final Map<String, List<String>> customListFields = new LinkedHashMap<>(8);
    private final List<QualifiedIdentifier> xmpIdentifiers = new ArrayList<>(8);

    public Builder title(String val) {
      this.title = val;
      return this;
    }

    public Builder creators(List<String> val) {
      this.creators.clear();
      if (val != null) this.creators.addAll(val);
      return this;
    }

    public Builder addCreator(String val) {
      if (val != null) this.creators.add(val);
      return this;
    }

    public Builder description(String val) {
      this.description = val;
      return this;
    }

    public Builder subjects(List<String> val) {
      this.subjects.clear();
      if (val != null) this.subjects.addAll(val);
      return this;
    }

    public Builder addSubject(String val) {
      if (val != null) this.subjects.add(val);
      return this;
    }

    public Builder publisher(String val) {
      this.publisher = val;
      return this;
    }

    public Builder language(String val) {
      this.language = val;
      return this;
    }

    public Builder date(String val) {
      this.date = val;
      return this;
    }

    public Builder rights(String val) {
      this.rights = val;
      return this;
    }

    public Builder identifiers(List<String> val) {
      this.identifiers.clear();
      if (val != null) this.identifiers.addAll(val);
      return this;
    }

    public Builder addIdentifier(String val) {
      if (val != null) this.identifiers.add(val);
      return this;
    }

    public Builder pdfaConformance(String val) {
      this.pdfaConformance = val;
      return this;
    }

    public Builder calibreFields(Map<String, String> val) {
      this.calibreFields.clear();
      if (val != null) this.calibreFields.putAll(val);
      return this;
    }

    public Builder putCalibreField(String k, String v) {
      if (k != null && v != null) this.calibreFields.put(k, v);
      return this;
    }

    public Builder customFields(Map<String, String> val) {
      this.customFields.clear();
      if (val != null) this.customFields.putAll(val);
      return this;
    }

    public Builder putCustomField(String k, String v) {
      if (k != null && v != null) this.customFields.put(k, v);
      return this;
    }

    public Builder customListFields(Map<String, List<String>> val) {
      this.customListFields.clear();
      if (val != null) {
        val.forEach((k, v) -> this.customListFields.put(k, new ArrayList<>(v)));
      }
      return this;
    }

    public Builder putCustomListField(String k, List<String> v) {
      if (k != null && v != null) this.customListFields.put(k, new ArrayList<>(v));
      return this;
    }

    public Builder xmpIdentifiers(List<QualifiedIdentifier> val) {
      this.xmpIdentifiers.clear();
      if (val != null) this.xmpIdentifiers.addAll(val);
      return this;
    }

    public Builder addXmpIdentifier(QualifiedIdentifier val) {
      if (val != null) this.xmpIdentifiers.add(val);
      return this;
    }

    public XmpMetadata build() {
      return new XmpMetadata(
          Optional.ofNullable(title),
          List.copyOf(creators),
          Optional.ofNullable(description),
          List.copyOf(subjects),
          Optional.ofNullable(publisher),
          Optional.ofNullable(language),
          Optional.ofNullable(date),
          Optional.ofNullable(rights),
          List.copyOf(identifiers),
          Optional.ofNullable(pdfaConformance),
          Map.copyOf(calibreFields),
          Map.copyOf(customFields),
          customListFields.entrySet().stream()
              .collect(
                  Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue()))),
          List.copyOf(xmpIdentifiers));
    }
  }
}
