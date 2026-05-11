package org.grimmory.pdfium4j;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.grimmory.pdfium4j.model.MetadataTag;
import org.grimmory.pdfium4j.model.PdfProcessingPolicy;
import org.grimmory.pdfium4j.model.XmpMetadata;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verifies that the library can handle realistic, complex book metadata as requested by the user,
 * including multi-value fields and custom namespaces.
 */
class CorpusRealisticMetadataTest {

  @TempDir Path tempDir;

  @BeforeAll
  static void setup() {
    PdfiumLibrary.initialize();
  }

  static Stream<Path> getGutenbergCorpus() throws IOException {
    Path corpusDir = Path.of("corpus", "gutenberg");
    if (!Files.exists(corpusDir)) {
      corpusDir = Path.of("..", "corpus", "gutenberg");
    }
    if (!Files.exists(corpusDir)) return Stream.of(Path.of("__NO_CORPUS__"));

    List<Path> files =
        Files.walk(corpusDir)
            .filter(p -> p.toString().endsWith(".pdf"))
            .filter(p -> !p.toString().contains("/quarantine/"))
            .limit(20) // Test a representative sample
            .sorted()
            .toList();
    return files.isEmpty() ? Stream.of(Path.of("__NO_CORPUS__")) : files.stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getGutenbergCorpus")
  void testRealisticMetadataRoundtrip(Path sourcePdf) throws Exception {
    Assumptions.assumeTrue(Files.exists(sourcePdf), "Corpus not available");
    Path modifiedPdf = tempDir.resolve("realistic_" + sourcePdf.getFileName());

    // Data from the user's snippet
    String title = "Season of Storms";
    List<String> creators = List.of("Andrzej Sapkowski", "Szücs Balázs", "David French");
    String description =
        "World fantasy award lifetime achievement winner, Andrzej Sapkowski, introduces Geralt of Rivia...";
    List<String> subjects =
        List.of(
            "Science Fiction & Fantasy",
            "Adventure",
            "Adulte",
            "Fantasy",
            "Aventure",
            "Fiction",
            "Science Fiction");
    String publisher = "My publisher";
    String language = "en";
    String date = "2013-01-01";

    // Custom booklore fields
    String pageCount = "329";
    String hardcoverId = "season-of-storms";
    String isbn13 = "9789634069301";
    String seriesNumber = "0.6";
    String seriesName = "The Witcher";
    List<String> tags =
        List.of(
            "Loveable Characters",
            "Not Diverse Characters",
            "Plot Driven",
            "Weak Character Development");
    List<String> moods = List.of("Adventurous");

    try (PdfDocument doc = PdfDocument.open(sourcePdf, null, PdfProcessingPolicy.defaultPolicy())) {
      // 1. Set Info Dictionary (Standard Tags)
      doc.setMetadata(MetadataTag.TITLE, title);
      doc.setMetadata(MetadataTag.AUTHOR, String.join(", ", creators));
      doc.setMetadata(MetadataTag.SUBJECT, "Fantasy");
      doc.setMetadata(MetadataTag.KEYWORDS, String.join(", ", subjects));

      // 2. Set Structured XMP
      XmpMetadata xmp =
          XmpMetadata.builder()
              .title(title)
              .creators(creators)
              .description(description)
              .subjects(subjects)
              .publisher(publisher)
              .language(language)
              .date(date)
              .customFields(
                  Map.of(
                      "booklore:pageCount", pageCount,
                      "booklore:hardcoverId", hardcoverId,
                      "booklore:isbn13", isbn13,
                      "booklore:seriesNumber", seriesNumber,
                      "booklore:seriesName", seriesName,
                      "booklore:subtitle", "A Novel of the Witcher – Now a major Netflix show",
                      "xmp:CreatorTool", "Booklore"))
              .customListFields(
                  Map.of(
                      "booklore:tags", tags,
                      "booklore:moods", moods))
              .build();

      doc.setXmpMetadata(xmp);
      doc.save(modifiedPdf);
    }

    // 3. Verify Reading Back
    try (PdfDocument doc = PdfDocument.open(modifiedPdf)) {
      // Info Dict Check
      assertEquals(title, doc.metadata(MetadataTag.TITLE).orElse(null));

      // XMP Check
      XmpMetadata readXmp = XmpMetadataParser.parseFrom(doc);
      assertEquals(title, readXmp.title().orElse(null));
      assertEquals(creators, readXmp.creators());
      assertTrue(readXmp.description().orElse("").contains("World fantasy award"));
      assertEquals(subjects, readXmp.subjects());
      assertEquals(publisher, readXmp.publisher().orElse(null));
      assertEquals(language, readXmp.language().orElse(null));
      assertEquals(date, readXmp.date().orElse(null));

      // Custom Namespaced Fields
      Map<String, String> custom = readXmp.customFields();
      assertEquals(pageCount, custom.get("booklore:pageCount"));
      assertEquals(hardcoverId, custom.get("booklore:hardcoverId"));
      assertEquals(isbn13, custom.get("booklore:isbn13"));
      assertEquals(seriesNumber, custom.get("booklore:seriesNumber"));
      assertEquals(seriesName, custom.get("booklore:seriesName"));
      assertEquals("Booklore", custom.get("xmp:CreatorTool"));

      // List Fields
      assertEquals(tags, readXmp.customListFields().get("booklore:tags"));
      assertEquals(moods, readXmp.customListFields().get("booklore:moods"));
    }
  }
}
