package org.grimmory.pdfium4j;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.grimmory.pdfium4j.model.XmpMetadata;
import org.grimmory.pdfium4j.model.XmpMetadata.QualifiedIdentifier;
import org.junit.jupiter.api.Test;

class XmpMetadataRoundtripTest {

  @Test
  void testCustomListRoundtrip() {
    XmpMetadataWriter writer =
        new XmpMetadataWriter().registerNamespace("booklore", "http://booklore.org/metadata/1.0/");

    Map<String, List<String>> listFields = new LinkedHashMap<>();
    listFields.put("booklore:tags", List.of("tag1", "tag2", "tag3"));
    listFields.put("booklore:moods", List.of("mood1"));

    XmpMetadata meta =
        XmpMetadata.builder()
            .title("Roundtrip Title")
            .creators(List.of("Author One", "Author Two"))
            .description("Description")
            .subjects(List.of("Subject 1", "Subject 2"))
            .publisher("Publisher")
            .language("en")
            .date("2024-04-24")
            .pdfaConformance("2a")
            .calibreFields(Map.of("series", "Test Series", "series_index", "1.5"))
            .customFields(Map.of("booklore:subtitle", "Test Subtitle"))
            .customListFields(listFields)
            .xmpIdentifiers(
                List.of(
                    new QualifiedIdentifier("ISBN", "1234567890"),
                    new QualifiedIdentifier("AMAZON", "B00XXXXXX")))
            .build();

    String xmp = writer.write(meta);
    XmpMetadata parsed = XmpMetadataParser.parse(xmp);

    assertEquals(meta.title(), parsed.title());
    assertEquals(meta.creators(), parsed.creators());
    assertEquals(meta.description(), parsed.description());
    assertEquals(meta.subjects(), parsed.subjects());
    assertEquals(meta.publisher(), parsed.publisher());
    assertEquals(meta.language(), parsed.language());
    assertEquals(meta.date(), parsed.date());
    assertEquals(meta.pdfaConformance(), parsed.pdfaConformance());
    assertEquals(meta.calibreFields().get("series"), parsed.calibreFields().get("series"));
    assertEquals(
        meta.calibreFields().get("series_index"), parsed.calibreFields().get("series_index"));
    assertEquals(
        meta.customFields().get("booklore:subtitle"),
        parsed.customFields().get("booklore:subtitle"));

    // Check list fields
    assertEquals(
        meta.customListFields().get("booklore:tags"),
        parsed.customListFields().get("booklore:tags"));
    assertEquals(
        meta.customListFields().get("booklore:moods"),
        parsed.customListFields().get("booklore:moods"));

    // Check XMP identifiers
    assertEquals(meta.xmpIdentifiers().size(), parsed.xmpIdentifiers().size());
    assertTrue(parsed.xmpIdentifier("ISBN").isPresent());
    assertEquals("1234567890", parsed.xmpIdentifier("ISBN").get());
    assertTrue(parsed.xmpIdentifier("AMAZON").isPresent());
    assertEquals("B00XXXXXX", parsed.xmpIdentifier("AMAZON").get());
  }

  @Test
  void testUnregisteredPrefixFallsBackToXmpNamespace() {
    XmpMetadataWriter writer = new XmpMetadataWriter();
    // booklore is not registered
    Map<String, String> customFields = Map.of("booklore:subtitle", "Test Subtitle");

    XmpMetadata metaWithCustom = XmpMetadata.builder().customFields(customFields).build();

    String xmp = writer.write(metaWithCustom);
    XmpMetadata parsed = XmpMetadataParser.parse(xmp);

    // Unregistered prefix should be stripped and stored as xmp:subtitle
    assertTrue(parsed.customFields().containsKey("xmp:subtitle"));
    assertEquals("Test Subtitle", parsed.customFields().get("xmp:subtitle"));
  }
}
