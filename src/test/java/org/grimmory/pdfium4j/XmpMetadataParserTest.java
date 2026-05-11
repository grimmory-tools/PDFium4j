package org.grimmory.pdfium4j;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.grimmory.pdfium4j.model.XmpMetadata;
import org.junit.jupiter.api.Test;

class XmpMetadataParserTest {

  private static final String BASIC_XMP =
      """
      <?xpacket begin="\uFEFF" id="W5M0MpCehiHzreSzNTczkc9d"?>\
      <x:xmpmeta xmlns:x="adobe:ns:meta/">\
      <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">\
      <rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/">\
      <dc:title><rdf:Alt><rdf:li xml:lang="x-default">My Book Title</rdf:li></rdf:Alt></dc:title>\
      <dc:creator><rdf:Seq><rdf:li>Author A</rdf:li><rdf:li>Author B</rdf:li></rdf:Seq></dc:creator>\
      <dc:description><rdf:Alt><rdf:li xml:lang="x-default">A great description</rdf:li></rdf:Alt></dc:description>\
      <dc:subject><rdf:Bag><rdf:li>Fantasy</rdf:li><rdf:li>Adventure</rdf:li></rdf:Bag></dc:subject>\
      <dc:publisher><rdf:Bag><rdf:li>My Publisher</rdf:li></rdf:Bag></dc:publisher>\
      <dc:language><rdf:Bag><rdf:li>en</rdf:li></rdf:Bag></dc:language>\
      <dc:date><rdf:Seq><rdf:li>2024-06-15</rdf:li></rdf:Seq></dc:date>\
      <dc:rights><rdf:Alt><rdf:li xml:lang="x-default">Copyright 2024</rdf:li></rdf:Alt></dc:rights>\
      <dc:identifier><rdf:Bag><rdf:li>urn:isbn:978-1234567890</rdf:li></rdf:Bag></dc:identifier>\
      </rdf:Description>\
      <rdf:Description rdf:about="" xmlns:calibre="http://calibre-ebook.com/xmp-namespace" xmlns:calibreSI="http://calibre-ebook.com/xmp-namespace/seriesIndex">\
      <calibre:series>Epic Series</calibre:series>\
      <calibre:series_index><calibreSI:series_index>3.5</calibreSI:series_index></calibre:series_index>\
      <calibre:rating>8</calibre:rating>\
      <calibre:tags><rdf:Bag><rdf:li>Tag 1</rdf:li><rdf:li>Tag 2</rdf:li></rdf:Bag></calibre:tags>\
      </rdf:Description>\
      </rdf:RDF></x:xmpmeta><?xpacket end="w"?>\
      """;

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
    assertEquals(3.5, meta.calibreSeriesIndex().orElse(0.0), 0.01);
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
    assertEquals(
        original.calibreSeriesIndex().orElse(0.0), parsed.calibreSeriesIndex().orElse(0.0));
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

    XmpMetadataWriter writer = new XmpMetadataWriter();
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
    assertEquals(12.5, meta.calibreSeriesIndex().orElse(0.0), 0.01);
  }

  private static final String BOOKLORE_FULL_XMP =
      """
      <?xpacket begin="\uFEFF" id="W5M0MpCehiHzreSzNTczkc9d"?>
      <x:xmpmeta xmlns:x="adobe:ns:meta/">
      <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
      <rdf:Description rdf:about=""
          xmlns:dc="http://purl.org/dc/elements/1.1/">
        <dc:title><rdf:Alt><rdf:li xml:lang="x-default">\u00e1\u00e9\u0151\u00fa\u00e9\u0151\u00e1\u00fa\u00e9\u0151\u00e9\u0151\u00fa\
      rrvsevrsevser</rdf:li></rdf:Alt></dc:title>
        <dc:creator><rdf:Seq>\
      <rdf:li>Andrzej Sapkowski</rdf:li>\
      <rdf:li>David French</rdf:li>\
      </rdf:Seq></dc:creator>
        <dc:description><rdf:Alt><rdf:li xml:lang="x-default">\
      World fantasy award lifetime achievement winner, Andrzej Sapkowski, introduces Geralt of Rivia.\
      </rdf:li></rdf:Alt></dc:description>
        <dc:subject><rdf:Bag>\
      <rdf:li>Science Fiction &amp; Fantasy</rdf:li>\
      <rdf:li>Adventure</rdf:li>\
      <rdf:li>Science fiction</rdf:li>\
      <rdf:li>Adulte</rdf:li>\
      <rdf:li>Biography</rdf:li>\
      <rdf:li>Fantasy</rdf:li>\
      <rdf:li>Assassins</rdf:li>\
      <rdf:li>Aventure</rdf:li>\
      <rdf:li>Fiction</rdf:li>\
      <rdf:li>Humor</rdf:li>\
      </rdf:Bag></dc:subject>
        <dc:publisher><rdf:Bag><rdf:li>Orbit</rdf:li></rdf:Bag></dc:publisher>
        <dc:language><rdf:Bag><rdf:li>en</rdf:li></rdf:Bag></dc:language>
        <dc:date><rdf:Seq><rdf:li>2013-11-06</rdf:li></rdf:Seq></dc:date>
      </rdf:Description>
      <rdf:Description rdf:about=""
          xmlns:booklore="http://booklore.org/metadata/1.0/">
        <booklore:googleId>GAg_swEACAAJ</booklore:googleId>
        <booklore:pageCount>329</booklore:pageCount>
        <booklore:hardcoverRating>3.9</booklore:hardcoverRating>
        <booklore:subtitle>\u0151\u00e1\u00fc\u0151\u00fc\u00e1\u00fc\u0151\u00e1\
      \u00fc\u0151\u00e1\u00fc\u0151\u00e1\u00fc\u00e1</booklore:subtitle>
        <booklore:goodreadsRating>4.0</booklore:goodreadsRating>
        <booklore:seriesTotal>5</booklore:seriesTotal>
        <booklore:isbn13>9780316441636</booklore:isbn13>
        <booklore:isbn10>0678452202</booklore:isbn10>
        <booklore:seriesNumber>0.6</booklore:seriesNumber>
        <booklore:hardcoverBookId>461967</booklore:hardcoverBookId>
        <booklore:seriesName>The Witcher</booklore:seriesName>
        <booklore:goodreadsId>36099978</booklore:goodreadsId>
        <booklore:hardcoverId>season-of-storms</booklore:hardcoverId>
      </rdf:Description>
      <rdf:Description rdf:about=""
          xmlns:xmp="http://ns.adobe.com/xap/1.0/">
        <xmp:MetadataDate>2026-05-12T11:22:50.667354886Z</xmp:MetadataDate>
        <xmp:ModifyDate>2026-05-12T11:22:50.667354886Z</xmp:ModifyDate>
        <xmp:CreateDate>2013-11-06</xmp:CreateDate>
        <xmp:CreatorTool>Booklore</xmp:CreatorTool>
      </rdf:Description>
      <rdf:Description rdf:about=""
          xmlns:booklore="http://booklore.org/metadata/1.0/">
        <booklore:tags><rdf:Bag>
          <rdf:li>Loveable Characters</rdf:li>
          <rdf:li>Not Diverse Characters</rdf:li>
          <rdf:li>Plot Driven</rdf:li>
          <rdf:li>Weak Character Development</rdf:li>
        </rdf:Bag></booklore:tags>
      </rdf:Description>
      </rdf:RDF>
      </x:xmpmeta>
      <?xpacket end="w"?>""";

  @Test
  void parsesBookloreSampleDublinCore() {
    XmpMetadata meta = XmpMetadataParser.parse(BOOKLORE_FULL_XMP);

    // Unicode title round-trip
    assertTrue(meta.title().isPresent(), "title should be present");
    assertTrue(meta.title().get().contains("rrvsevrsevser"), "title should contain ASCII suffix");
    assertTrue(meta.title().get().startsWith("\u00e1"), "title should start with unicode char");

    // Multiple creators
    assertEquals(List.of("Andrzej Sapkowski", "David French"), meta.creators());

    // Publisher wrapped in rdf:Bag
    assertEquals("Orbit", meta.publisher().orElse(""));

    // Language
    assertEquals("en", meta.language().orElse(""));

    // Date in rdf:Seq
    assertEquals("2013-11-06", meta.date().orElse(""));

    // 10 subjects
    assertEquals(10, meta.subjects().size());
    assertTrue(meta.subjects().contains("Science Fiction & Fantasy"));
    assertTrue(meta.subjects().contains("Fantasy"));
    assertTrue(meta.subjects().contains("Humor"));
  }

  @Test
  void parsesBookloreSampleCustomFields() {
    XmpMetadata meta = XmpMetadataParser.parse(BOOKLORE_FULL_XMP);

    // Simple booklore fields
    assertEquals("GAg_swEACAAJ", meta.customFields().get("booklore:googleId"));
    assertEquals("329", meta.customFields().get("booklore:pageCount"));
    assertEquals("3.9", meta.customFields().get("booklore:hardcoverRating"));
    assertEquals("4.0", meta.customFields().get("booklore:goodreadsRating"));
    assertEquals("5", meta.customFields().get("booklore:seriesTotal"));
    assertEquals("9780316441636", meta.customFields().get("booklore:isbn13"));
    assertEquals("0678452202", meta.customFields().get("booklore:isbn10"));
    assertEquals("0.6", meta.customFields().get("booklore:seriesNumber"));
    assertEquals("461967", meta.customFields().get("booklore:hardcoverBookId"));
    assertEquals("The Witcher", meta.customFields().get("booklore:seriesName"));
    assertEquals("36099978", meta.customFields().get("booklore:goodreadsId"));
    assertEquals("season-of-storms", meta.customFields().get("booklore:hardcoverId"));

    // Unicode subtitle
    String subtitle = meta.customFields().get("booklore:subtitle");
    assertNotNull(subtitle, "booklore:subtitle should be present");
    assertTrue(subtitle.startsWith("\u0151"), "subtitle should start with unicode char");

    // xmp namespace fields
    assertEquals("Booklore", meta.customFields().get("xmp:CreatorTool"));
    assertEquals("2013-11-06", meta.customFields().get("xmp:CreateDate"));
    assertNotNull(meta.customFields().get("xmp:MetadataDate"));
    assertNotNull(meta.customFields().get("xmp:ModifyDate"));
  }

  @Test
  void parsesBookloreSampleTagsBag() {
    XmpMetadata meta = XmpMetadataParser.parse(BOOKLORE_FULL_XMP);

    // booklore:tags in a second booklore rdf:Description block (multi-block same NS)
    List<String> tags = meta.customListFields().get("booklore:tags");
    assertNotNull(tags, "booklore:tags list field should be present");
    assertEquals(4, tags.size());
    assertTrue(tags.contains("Loveable Characters"));
    assertTrue(tags.contains("Not Diverse Characters"));
    assertTrue(tags.contains("Plot Driven"));
    assertTrue(tags.contains("Weak Character Development"));
  }

  @Test
  void parsesBookloreSampleWriteRoundTrip() {
    XmpMetadata parsed = XmpMetadataParser.parse(BOOKLORE_FULL_XMP);

    // Write and re-parse
    XmpMetadataWriter writer = new XmpMetadataWriter();
    String rewritten = writer.write(parsed);
    XmpMetadata reparsed = XmpMetadataParser.parse(rewritten);

    // DC fields survive round-trip
    assertEquals(parsed.title(), reparsed.title());
    assertEquals(parsed.creators(), reparsed.creators());
    assertEquals(parsed.publisher(), reparsed.publisher());
    assertEquals(parsed.language(), reparsed.language());
    assertEquals(parsed.date(), reparsed.date());
    assertEquals(parsed.subjects(), reparsed.subjects());

    // booklore simple fields survive
    assertEquals(
        parsed.customFields().get("booklore:isbn13"),
        reparsed.customFields().get("booklore:isbn13"));
    assertEquals(
        parsed.customFields().get("booklore:seriesName"),
        reparsed.customFields().get("booklore:seriesName"));
    assertEquals(
        parsed.customFields().get("booklore:subtitle"),
        reparsed.customFields().get("booklore:subtitle"));
    assertEquals(
        parsed.customFields().get("xmp:CreatorTool"),
        reparsed.customFields().get("xmp:CreatorTool"));

    // booklore:tags list survives
    assertEquals(
        parsed.customListFields().get("booklore:tags"),
        reparsed.customListFields().get("booklore:tags"));
  }
}
