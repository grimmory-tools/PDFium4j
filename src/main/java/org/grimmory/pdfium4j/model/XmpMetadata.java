package org.grimmory.pdfium4j.model;

import java.util.*;
import java.util.regex.Pattern;

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
    Map<String, List<String>> listCopy = new LinkedHashMap<>();
    Objects.requireNonNull(customListFields, "customListFields")
        .forEach((k, v) -> listCopy.put(k, List.copyOf(v)));
    customListFields = Collections.unmodifiableMap(listCopy);
    xmpIdentifiers = List.copyOf(Objects.requireNonNull(xmpIdentifiers, "xmpIdentifiers"));
  }

  /** Whether this PDF declares PDF/A conformance. */
  public boolean isPdfA() {
    return pdfaConformance.isPresent() && !pdfaConformance.get().isBlank();
  }

  /** Get the first creator (author), if any. */
  public Optional<String> firstCreator() {
    return creators.isEmpty() ? Optional.empty() : Optional.of(creators.getFirst());
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

  /** Get Calibre series index as a double, if present and parseable. */
  public OptionalDouble calibreSeriesIndex() {
    String val = calibreFields.get("series_index");
    if (val == null || val.isBlank()) return OptionalDouble.empty();
    try {
      return OptionalDouble.of(Double.parseDouble(val));
    } catch (NumberFormatException e) {
      return OptionalDouble.empty();
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
    private Optional<String> title = Optional.empty();
    private List<String> creators = new ArrayList<>();
    private Optional<String> description = Optional.empty();
    private List<String> subjects = new ArrayList<>();
    private Optional<String> publisher = Optional.empty();
    private Optional<String> language = Optional.empty();
    private Optional<String> date = Optional.empty();
    private Optional<String> rights = Optional.empty();
    private List<String> identifiers = new ArrayList<>();
    private Optional<String> pdfaConformance = Optional.empty();
    private Map<String, String> calibreFields = new LinkedHashMap<>();
    private Map<String, String> customFields = new LinkedHashMap<>();
    private Map<String, List<String>> customListFields = new LinkedHashMap<>();
    private List<QualifiedIdentifier> xmpIdentifiers = new ArrayList<>();

    public Builder title(String val) {
      this.title = Optional.ofNullable(val);
      return this;
    }

    public Builder creators(List<String> val) {
      this.creators = new ArrayList<>(val);
      return this;
    }

    public Builder description(String val) {
      this.description = Optional.ofNullable(val);
      return this;
    }

    public Builder subjects(List<String> val) {
      this.subjects = new ArrayList<>(val);
      return this;
    }

    public Builder publisher(String val) {
      this.publisher = Optional.ofNullable(val);
      return this;
    }

    public Builder language(String val) {
      this.language = Optional.ofNullable(val);
      return this;
    }

    public Builder date(String val) {
      this.date = Optional.ofNullable(val);
      return this;
    }

    public Builder rights(String val) {
      this.rights = Optional.ofNullable(val);
      return this;
    }

    public Builder identifiers(List<String> val) {
      this.identifiers = new ArrayList<>(val);
      return this;
    }

    public Builder pdfaConformance(String val) {
      this.pdfaConformance = Optional.ofNullable(val);
      return this;
    }

    public Builder calibreFields(Map<String, String> val) {
      this.calibreFields = new LinkedHashMap<>(val);
      return this;
    }

    public Builder customFields(Map<String, String> val) {
      this.customFields = new LinkedHashMap<>(val);
      return this;
    }

    public Builder customListFields(Map<String, List<String>> val) {
      this.customListFields = new LinkedHashMap<>();
      val.forEach((k, v) -> this.customListFields.put(k, new ArrayList<>(v)));
      return this;
    }

    public Builder xmpIdentifiers(List<QualifiedIdentifier> val) {
      this.xmpIdentifiers = new ArrayList<>(val);
      return this;
    }

    public XmpMetadata build() {
      return new XmpMetadata(
          title,
          creators,
          description,
          subjects,
          publisher,
          language,
          date,
          rights,
          identifiers,
          pdfaConformance,
          calibreFields,
          customFields,
          customListFields,
          xmpIdentifiers);
    }
  }
}
