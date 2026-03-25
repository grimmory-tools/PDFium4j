package org.pdfium4j.model;

import org.pdfium4j.XmpMetadataParser;
import org.pdfium4j.PdfDocument;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PDF implementation of {@link BookMetadata}.
 * Wraps PDF metadata from Info dictionary and XMP packet to provide
 * unified access to standard metadata fields.
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
        Map<String, String> customFields
    ) implements BookMetadata {

    private static final Pattern REPEATED_DIGIT_ISBN = Pattern.compile("^(\\d)\\1{9,12}$");
    private static final Pattern ISBN_NON_DIGIT = Pattern.compile("[^0-9Xx]");
    private static final Pattern KEYWORD_SEPARATOR = Pattern.compile("[,;]");

    public PdfBookMetadata {
        authors = List.copyOf(authors);
        subjects = List.copyOf(subjects);
        customFields = Map.copyOf(customFields);
    }

    @Override
    public BookFormat format() {
        return BookFormat.PDF;
    }

    /**
     * Create BookMetadata from a PDF document.
     * Combines Info dictionary metadata with XMP metadata.
     *
     * @param document the PDF document
     * @return unified metadata
     */
    public static PdfBookMetadata from(PdfDocument document) {
        if (document == null) {
            return empty();
        }

        XmpMetadata xmp = XmpMetadataParser.parseFrom(document);

        Optional<String> title = xmp.title()
                .or(() -> document.metadata(MetadataTag.TITLE));

        List<String> authors = extractAuthors(xmp, document);

        Optional<String> series = Optional.ofNullable(xmp.calibreFields().get("series"))
                .filter(s -> !s.isBlank());

        Optional<Float> seriesNumber = Optional.ofNullable(xmp.calibreFields().get("series_index"))
                .flatMap(s -> {
                    try {
                        return Optional.of(Float.parseFloat(s));
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                });

        Optional<String> isbn = extractIsbn(xmp);

        Optional<String> language = xmp.language()
                .or(() -> Optional.ofNullable(xmp.customFields().get("Language")))
                .map(PdfBookMetadata::normalizeLanguage);

        Optional<LocalDate> publishedDate = extractPublishedDate(xmp, document);

        List<String> subjects = new ArrayList<>(xmp.subjects());
        Optional<String> keywordsOpt = document.metadata(MetadataTag.KEYWORDS);
        if (keywordsOpt.isPresent()) {
            for (String part : KEYWORD_SEPARATOR.split(keywordsOpt.get())) {
                String trimmed = part.trim();
                if (!trimmed.isBlank() && !subjects.contains(trimmed)) {
                    subjects.add(trimmed);
                }
            }
        }
        subjects = List.copyOf(subjects);

        Optional<String> description = xmp.description();
        Optional<String> publisher = xmp.publisher();
        Map<String, String> customFields = extractCustomFields(xmp, document);

        return new PdfBookMetadata(
                title, authors, series, seriesNumber, isbn, language,
                publishedDate, subjects, description, publisher, xmp, customFields);
    }

    /**
     * Create an empty PdfBookMetadata.
     */
    public static PdfBookMetadata empty() {
        return new PdfBookMetadata(
                Optional.empty(), List.of(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), List.of(),
                Optional.empty(), Optional.empty(), XmpMetadata.empty(), Map.of());
    }

    private static List<String> extractAuthors(XmpMetadata xmp, PdfDocument document) {

        List<String> authors = new ArrayList<>(xmp.creators());

        if (authors.isEmpty()) {
            document.metadata(MetadataTag.AUTHOR).ifPresent(author -> {
                String[] parts = KEYWORD_SEPARATOR.split(author);
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (!trimmed.isBlank() && !authors.contains(trimmed)) {
                        authors.add(trimmed);
                    }
                }
            });
        }

        return List.copyOf(authors);
    }

    private static Optional<String> extractIsbn(XmpMetadata xmp) {
        List<String> isbns = xmp.isbns();
        if (!isbns.isEmpty()) {
            return Optional.of(isbns.getFirst());
        }

        for (String id : xmp.identifiers()) {
            if (id.toLowerCase(Locale.ROOT).contains("isbn")) {
                String cleaned = cleanIsbn(id);
                if (cleaned != null) {
                    return Optional.of(cleaned);
                }
            }
        }

        return Optional.empty();
    }

    private static final Pattern PDF_DATE_PREFIX = 
            Pattern.compile("^D:(\\d{4})(\\d{2})?(\\d{2})?(\\d{2})?(\\d{2})?(\\d{2})?");

    private static final Pattern FOUR_DIGIT_YEAR = Pattern.compile("\\b(\\d{4})\\b");

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ISO_LOCAL_DATE,                         // 2024-01-15
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),                // 2024/01/15
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),                // 2024.01.15
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),                // 15/01/2024
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),                // 15-01-2024
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),                // 01/15/2024
            DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US),   // Jan 15, 2024
            DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.US),  // January 15, 2024
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US),    // 15 Jan 2024
            DateTimeFormatter.ofPattern("yyyy")                       // 2024 (year only)
    };

    private static Optional<LocalDate> extractPublishedDate(XmpMetadata xmp, PdfDocument document) {
        List<String> candidates = new ArrayList<>();
        xmp.date().ifPresent(candidates::add);
        String createDate = xmp.customFields().get("CreateDate");
        if (createDate != null) candidates.add(createDate);

        // Fall back to Info dictionary dates
        document.metadata(MetadataTag.CREATION_DATE).ifPresent(candidates::add);
        document.metadata(MetadataTag.MOD_DATE).ifPresent(candidates::add);

        for (String raw : candidates) {
            Optional<LocalDate> parsed = parseDate(raw.strip());
            if (parsed.isPresent()) return parsed;
        }
        return Optional.empty();
    }

    private static Optional<LocalDate> parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return Optional.empty();

        // Try PDF date format: D:YYYYMMDDHHmmSS+TZ
        Matcher pdfMatcher = PDF_DATE_PREFIX.matcher(dateStr);
        if (pdfMatcher.find()) {
            int year = Integer.parseInt(pdfMatcher.group(1));
            int month = pdfMatcher.group(2) != null ? Integer.parseInt(pdfMatcher.group(2)) : 1;
            int day = pdfMatcher.group(3) != null ? Integer.parseInt(pdfMatcher.group(3)) : 1;
            if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                try {
                    return Optional.of(LocalDate.of(year, month, day));
                } catch (DateTimeException ignored) {}
            }
        }

        String dateOnly = dateStr.contains("T") ? dateStr.substring(0, dateStr.indexOf('T')) : dateStr;

        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                return Optional.of(LocalDate.parse(dateOnly, fmt));
            } catch (DateTimeParseException ignored) {}
        }

        // Last resort: extract 4-digit year
        Matcher yearMatcher = FOUR_DIGIT_YEAR.matcher(dateStr);
        if (yearMatcher.find()) {
            int year = Integer.parseInt(yearMatcher.group(1));
            if (year >= 1000 && year <= 9999) {
                return Optional.of(LocalDate.of(year, 1, 1));
            }
        }

        return Optional.empty();
    }

    private static Map<String, String> extractCustomFields(XmpMetadata xmp, PdfDocument document) {
        Map<String, String> custom = new LinkedHashMap<>();

        custom.putAll(xmp.customFields());

        for (Map.Entry<String, String> entry : xmp.calibreFields().entrySet()) {
            if (!"series".equalsIgnoreCase(entry.getKey()) &&
                !"series_index".equalsIgnoreCase(entry.getKey())) {
                custom.put("calibre:" + entry.getKey(), entry.getValue());
            }
        }

        xmp.pdfaConformance().ifPresent(conf ->
            custom.put("pdfaConformance", conf));

        try {
            Map<String, String> infoDict = document.metadata();
            for (Map.Entry<String, String> entry : infoDict.entrySet()) {
                String key = entry.getKey();
                if ("Title".equalsIgnoreCase(key) || "Author".equalsIgnoreCase(key) ||
                    "Subject".equalsIgnoreCase(key) || "Keywords".equalsIgnoreCase(key) ||
                    "Creator".equalsIgnoreCase(key) || "Producer".equalsIgnoreCase(key) ||
                    "CreationDate".equalsIgnoreCase(key) || "ModDate".equalsIgnoreCase(key)) {
                    continue;
                }
                custom.put("info:" + key, entry.getValue());
            }
        } catch (Exception e) {
            // Ignore metadata access errors
        }

        return Map.copyOf(custom);
    }

    private static String cleanIsbn(String id) {
        if (id == null) return null;
        String cleaned = ISBN_NON_DIGIT.matcher(id).replaceAll("").toUpperCase();

        // Reject uniform sequences like 0000000000 or 1111111111111 (fake ISBNs)
        if (REPEATED_DIGIT_ISBN.matcher(cleaned).matches()) return null;

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
            sum += digit * (i + 1);
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

    private static String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) return null;
        String lower = language.trim().toLowerCase(Locale.ROOT);
        if (lower.length() == 2) return lower;
        int dashPos = lower.indexOf('-');
        if (dashPos > 0) return lower.substring(0, dashPos);
        
        return lower;
    }

    /**
     * Get the PDF/A conformance level, if declared.
     */
    public Optional<String> pdfaConformance() {
        return rawMetadata.pdfaConformance();
    }

    /**
     * Whether this PDF is image-only (scanned) with no extractable text.
     * This is a hint based on XMP metadata, not a definitive check.
     */
    public boolean isLikelyImageOnly() {
        // If no title, creators, or subjects, might be image-only
        return title.isEmpty() && authors.isEmpty() && subjects.isEmpty();
    }
}
