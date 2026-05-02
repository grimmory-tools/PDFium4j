package org.grimmory.pdfium4j;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.grimmory.pdfium4j.model.XmpMetadata;
import org.junit.jupiter.api.Test;

class XmpMetadataParserTest {

  private static final String BASIC_XMP =
      "<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>"
          + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">"
          + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">"
          + "<rdf:Description rdf:about=\"\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">"
          + "<dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">My Book Title</rdf:li></rdf:Alt></dc:title>"
          + "<dc:creator><rdf:Seq><rdf:li>Author A</rdf:li><rdf:li>Author B</rdf:li></rdf:Seq></dc:creator>"
          + "<dc:description><rdf:Alt><rdf:li xml:lang=\"x-default\">A great description</rdf:li></rdf:Alt></dc:description>"
          + "<dc:subject><rdf:Bag><rdf:li>Fantasy</rdf:li><rdf:li>Adventure</rdf:li></rdf:Bag></dc:subject>"
          + "<dc:publisher><rdf:Bag><rdf:li>My Publisher</rdf:li></rdf:Bag></dc:publisher>"
          + "<dc:language><rdf:Bag><rdf:li>en</rdf:li></rdf:Bag></dc:language>"
          + "<dc:date><rdf:Seq><rdf:li>2024-06-15</rdf:li></rdf:Seq></dc:date>"
          + "<dc:rights><rdf:Alt><rdf:li xml:lang=\"x-default\">Copyright 2024</rdf:li></rdf:Alt></dc:rights>"
          + "<dc:identifier><rdf:Bag><rdf:li>urn:isbn:978-1234567890</rdf:li></rdf:Bag></dc:identifier>"
          + "</rdf:Description>"
          + "<rdf:Description rdf:about=\"\" xmlns:calibre=\"http://calibre-ebook.com/xmp-namespace\" xmlns:calibreSI=\"http://calibre-ebook.com/xmp-namespace/seriesIndex\">"
          + "<calibre:series>Epic Series</calibre:series>"
          + "<calibre:series_index><calibreSI:series_index>3.5</calibreSI:series_index></calibre:series_index>"
          + "<calibre:rating>8</calibre:rating>"
          + "<calibre:tags><rdf:Bag><rdf:li>Tag 1</rdf:li><rdf:li>Tag 2</rdf:li></rdf:Bag></calibre:tags>"
          + "</rdf:Description>"
          + "</rdf:RDF></x:xmpmeta><?xpacket end=\"w\"?>";

  private static final String BOOKLORE_XMP =
      "<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>"
          + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">"
          + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">"
          + "<rdf:Description rdf:about=\"\" xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\">"
          + "<xmp:CreatorTool>Booklore</xmp:CreatorTool>"
          + "</rdf:Description>"
          + "<rdf:Description rdf:about=\"\" xmlns:booklore=\"http://booklore.org/metadata/1.0/\">"
          + "<booklore:subtitle>An Epic Subtitle</booklore:subtitle>"
          + "<booklore:tags><rdf:Bag><rdf:li>tag1</rdf:li><rdf:li>tag2</rdf:li></rdf:Bag></booklore:tags>"
          + "</rdf:Description>"
          + "<rdf:Description rdf:about=\"\" xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\" xmlns:xmpidq=\"http://ns.adobe.com/xmp/Identifier/qual/1.0/\">"
          + "<xmp:Identifier><rdf:Bag>"
          + "<rdf:li rdf:parseType=\"Resource\"><xmpidq:Scheme>ISBN</xmpidq:Scheme><rdf:value>9781234567890</rdf:value></rdf:li>"
          + "<rdf:li rdf:parseType=\"Resource\"><xmpidq:Scheme>AMAZON</xmpidq:Scheme><rdf:value>B00XXXXXX</rdf:value></rdf:li>"
          + "</rdf:Bag></xmp:Identifier>"
          + "</rdf:Description>"
          + "</rdf:RDF></x:xmpmeta><?xpacket end=\"w\"?>";

  @Test
  void parsesBasicDublinCore() {
    XmpMetadata meta = XmpMetadataParser.parse(BASIC_XMP);
    assertEquals("My Book Title", meta.title().orElse(""));
    assertEquals(List.of("Author A", "Author B"), meta.creators());
    assertEquals("A great description", meta.description().orElse(""));
    assertEquals(List.of("Fantasy", "Adventure"), meta.subjects());
    assertEquals("My Publisher", meta.publisher().orElse(""));
    assertEquals("en", meta.language().orElse(""));
    assertEquals("2024-06-15", meta.date().orElse(""));
    assertEquals("Copyright 2024", meta.rights().orElse(""));
    assertEquals(List.of("urn:isbn:978-1234567890"), meta.identifiers());
    assertEquals(List.of("9781234567890"), meta.isbns());
  }

  @Test
  void parsesCalibreFields() {
    XmpMetadata meta = XmpMetadataParser.parse(BASIC_XMP);
    assertEquals("Epic Series", meta.calibreSeries().orElse(""));
    assertEquals(3.5, meta.calibreSeriesIndex().orElse(0), 0.01);
    assertEquals(8, meta.calibreRating().orElse(0));
    assertEquals(List.of("Tag 1", "Tag 2"), meta.calibreTags());
  }

  @Test
  void parsesBookloreXmpFields() {
    XmpMetadata meta = XmpMetadataParser.parse(BOOKLORE_XMP);
    assertTrue(meta.customFields().containsKey("xmp:CreatorTool"));
    assertEquals("Booklore", meta.customFields().get("xmp:CreatorTool"));
    assertEquals("An Epic Subtitle", meta.customFields().get("booklore:subtitle"));
    assertEquals(List.of("tag1", "tag2"), meta.customListFields().get("booklore:tags"));
  }

  @Test
  void parsesXmpIdentifiers() {
    XmpMetadata meta = XmpMetadataParser.parse(BOOKLORE_XMP);
    assertEquals(2, meta.xmpIdentifiers().size());
    assertEquals("9781234567890", meta.xmpIdentifier("ISBN").orElse(""));
    assertEquals("B00XXXXXX", meta.xmpIdentifier("AMAZON").orElse(""));
  }

  @Test
  void writerParserRoundTrip() {
    XmpMetadata original =
        XmpMetadata.builder()
            .title("My Book Title")
            .creators(List.of("Author A", "Author B"))
            .description("A great description")
            .subjects(List.of("Fantasy", "Adventure", "Magic"))
            .publisher("My Publisher")
            .language("en")
            .date("2024-06-15")
            .rights("Copyright 2024")
            .identifiers(List.of("urn:isbn:978-1234567890"))
            .calibreFields(Map.of("series", "Epic Series", "series_index", "3"))
            .build();

    XmpMetadataWriter writer = new XmpMetadataWriter();
    String xmpPacket = writer.write(original);

    XmpMetadata parsed = XmpMetadataParser.parse(xmpPacket);

    assertEquals(original.title(), parsed.title());
    assertEquals(original.creators(), parsed.creators());
    assertEquals(original.description(), parsed.description());
    assertEquals(original.subjects(), parsed.subjects());
    assertEquals(original.publisher(), parsed.publisher());
    assertEquals(original.language(), parsed.language());
    assertEquals(original.date(), parsed.date());
    assertEquals(original.rights(), parsed.rights());
    assertEquals(original.calibreSeries().orElse(""), parsed.calibreSeries().orElse(""));
    assertEquals(original.calibreSeriesIndex().orElse(0), parsed.calibreSeriesIndex().orElse(0));
  }

  @Test
  void handlesEmptyInput() {
    XmpMetadata meta = XmpMetadataParser.parse((String) null);
    assertFalse(meta.title().isPresent());
    assertTrue(meta.creators().isEmpty());

    meta = XmpMetadataParser.parse("");
    assertFalse(meta.title().isPresent());

    meta = XmpMetadataParser.parse("   ");
    assertFalse(meta.title().isPresent());
  }

  @Test
  void handlesMalformedXml() {
    XmpMetadata meta = XmpMetadataParser.parse("<x:xmpmeta><rdf:RDF><bad xml");
    assertFalse(meta.title().isPresent());
  }

  @Test
  void writerParserRoundTripWithBookloreNamespace() {
    Map<String, String> customFields = new LinkedHashMap<>();
    customFields.put("booklore:subtitle", "An Epic Subtitle");
    customFields.put("booklore:isbn13", "9781234567890");
    customFields.put("booklore:isbn10", "1234567890");
    customFields.put("booklore:goodreadsId", "12345");
    customFields.put("booklore:goodreadsRating", "4.5");

    XmpMetadata original =
        XmpMetadata.builder()
            .title("Test Title")
            .creators(List.of("Test Author"))
            .customFields(customFields)
            .build();

    XmpMetadataWriter writer =
        new XmpMetadataWriter().registerNamespace("booklore", "http://booklore.org/metadata/1.0/");
    String xmpPacket = writer.write(original);

    // Verify the raw XML contains booklore elements
    assertTrue(
        xmpPacket.contains("booklore:subtitle"), "XMP should contain booklore:subtitle element");
    assertTrue(xmpPacket.contains("An Epic Subtitle"));
    assertTrue(xmpPacket.contains("booklore:isbn13"));
    assertTrue(xmpPacket.contains("9781234567890"));
    assertTrue(xmpPacket.contains("booklore:goodreadsId"));
    assertTrue(xmpPacket.contains("12345"));

    // Parse it back - Dublin Core and booklore fields should survive
    XmpMetadata parsed = XmpMetadataParser.parse(xmpPacket);
    assertEquals(original.title(), parsed.title());
    assertEquals(original.creators(), parsed.creators());
    assertEquals("An Epic Subtitle", parsed.customFields().get("booklore:subtitle"));
    assertEquals("9781234567890", parsed.customFields().get("booklore:isbn13"));
    assertEquals("12345", parsed.customFields().get("booklore:goodreadsId"));
  }

  @Test
  void parsesLocalizedAltValues() {
    String xmp =
        "<x:xmpmeta xmlns:x='adobe:ns:meta/'><rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#' xmlns:dc='http://purl.org/dc/elements/1.1/'>"
            + "<rdf:Description rdf:about=''>"
            + "<dc:title><rdf:Alt>"
            + "<rdf:li xml:lang='fr'>Le Titre</rdf:li>"
            + "<rdf:li xml:lang='x-default'>The Title</rdf:li>"
            + "</rdf:Alt></dc:title>"
            + "</rdf:Description></rdf:RDF></x:xmpmeta>";
    XmpMetadata meta = XmpMetadataParser.parse(xmp);
    assertEquals("The Title", meta.title().orElse(""));
  }

  @Test
  void parsesNestedCalibreSeriesIndex() {
    String xmp =
        "<x:xmpmeta xmlns:x='adobe:ns:meta/'><rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#' xmlns:calibre='http://calibre-ebook.com/xmp-namespace' xmlns:calibreSI='http://calibre-ebook.com/xmp-namespace/seriesIndex'>"
            + "<rdf:Description rdf:about=''>"
            + "<calibre:series_index><calibreSI:series_index>12.5</calibreSI:series_index></calibre:series_index>"
            + "</rdf:Description></rdf:RDF></x:xmpmeta>";
    XmpMetadata meta = XmpMetadataParser.parse(xmp);
    assertEquals(12.5, meta.calibreSeriesIndex().orElse(0), 0.01);
  }
}
