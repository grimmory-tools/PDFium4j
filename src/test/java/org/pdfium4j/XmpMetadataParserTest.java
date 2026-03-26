package org.pdfium4j;

import org.pdfium4j.model.XmpMetadata;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class XmpMetadataParserTest {

    private static final String FULL_XMP = """
            <?xpacket begin="\uFEFF" id="W5M0MpCehiHzreSzNTczkc9d"?>
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description rdf:about=""
                    xmlns:dc="http://purl.org/dc/elements/1.1/"
                    xmlns:pdfaid="http://www.aiim.org/pdfa/ns/id/"
                    xmlns:xmp="http://ns.adobe.com/xap/1.0/">
                  <dc:title>
                    <rdf:Alt><rdf:li xml:lang="x-default">Test Book</rdf:li></rdf:Alt>
                  </dc:title>
                  <dc:creator>
                    <rdf:Seq>
                      <rdf:li>Author One</rdf:li>
                      <rdf:li>Author Two</rdf:li>
                    </rdf:Seq>
                  </dc:creator>
                  <dc:description>
                    <rdf:Alt><rdf:li xml:lang="x-default">A test description</rdf:li></rdf:Alt>
                  </dc:description>
                  <dc:subject>
                    <rdf:Bag>
                      <rdf:li>fiction</rdf:li>
                      <rdf:li>fantasy</rdf:li>
                    </rdf:Bag>
                  </dc:subject>
                  <dc:publisher>
                    <rdf:Alt><rdf:li xml:lang="x-default">Test Publisher</rdf:li></rdf:Alt>
                  </dc:publisher>
                  <dc:language>
                    <rdf:Bag><rdf:li>en</rdf:li></rdf:Bag>
                  </dc:language>
                  <dc:date>
                    <rdf:Seq><rdf:li>2024-03-15</rdf:li></rdf:Seq>
                  </dc:date>
                  <dc:rights>
                    <rdf:Alt><rdf:li xml:lang="x-default">Copyright 2024</rdf:li></rdf:Alt>
                  </dc:rights>
                  <dc:identifier>urn:isbn:978-0-306-40615-7</dc:identifier>
                  <pdfaid:part>1</pdfaid:part>
                  <pdfaid:conformance>B</pdfaid:conformance>
                  <xmp:CreatorTool>TestTool</xmp:CreatorTool>
                </rdf:Description>
              </rdf:RDF>
            </x:xmpmeta>
            """;

    @Test
    void parsesTitle() {
        XmpMetadata meta = XmpMetadataParser.parse(FULL_XMP);
        assertTrue(meta.title().isPresent());
        assertEquals("Test Book", meta.title().get());
    }

    @Test
    void parsesMultipleCreators() {
        XmpMetadata meta = XmpMetadataParser.parse(FULL_XMP);
        assertEquals(2, meta.creators().size());
        assertEquals("Author One", meta.creators().get(0));
        assertEquals("Author Two", meta.creators().get(1));
    }

    @Test
    void parsesDescription() {
        XmpMetadata meta = XmpMetadataParser.parse(FULL_XMP);
        assertTrue(meta.description().isPresent());
        assertEquals("A test description", meta.description().get());
    }

    @Test
    void parsesSubjects() {
        XmpMetadata meta = XmpMetadataParser.parse(FULL_XMP);
        assertEquals(2, meta.subjects().size());
        assertTrue(meta.subjects().contains("fiction"));
        assertTrue(meta.subjects().contains("fantasy"));
    }

    @Test
    void parsesPublisher() {
        XmpMetadata meta = XmpMetadataParser.parse(FULL_XMP);
        assertTrue(meta.publisher().isPresent());
        assertEquals("Test Publisher", meta.publisher().get());
    }

    @Test
    void parsesLanguage() {
        XmpMetadata meta = XmpMetadataParser.parse(FULL_XMP);
        assertTrue(meta.language().isPresent());
        assertEquals("en", meta.language().get());
    }

    @Test
    void parsesDate() {
        XmpMetadata meta = XmpMetadataParser.parse(FULL_XMP);
        assertTrue(meta.date().isPresent());
        assertEquals("2024-03-15", meta.date().get());
    }

    @Test
    void parsesRights() {
        XmpMetadata meta = XmpMetadataParser.parse(FULL_XMP);
        assertTrue(meta.rights().isPresent());
        assertEquals("Copyright 2024", meta.rights().get());
    }

    @Test
    void parsesIdentifiers() {
        XmpMetadata meta = XmpMetadataParser.parse(FULL_XMP);
        assertFalse(meta.identifiers().isEmpty());
        assertTrue(meta.identifiers().getFirst().contains("978-0-306-40615-7"));
    }

    @Test
    void parsesPdfAConformance() {
        XmpMetadata meta = XmpMetadataParser.parse(FULL_XMP);
        assertTrue(meta.isPdfA());
        assertTrue(meta.pdfaConformance().isPresent());
        assertEquals("1b", meta.pdfaConformance().get());
    }

    @Test
    void parsesXmpCustomFields() {
        XmpMetadata meta = XmpMetadataParser.parse(FULL_XMP);
        assertTrue(meta.customFields().containsKey("CreatorTool"));
        assertEquals("TestTool", meta.customFields().get("CreatorTool"));
    }

    @Test
    void firstCreatorReturnsFirst() {
        XmpMetadata meta = XmpMetadataParser.parse(FULL_XMP);
        assertTrue(meta.firstCreator().isPresent());
        assertEquals("Author One", meta.firstCreator().get());
    }

    @Test
    void isbnExtraction() {
        XmpMetadata meta = XmpMetadataParser.parse(FULL_XMP);
        assertFalse(meta.isbns().isEmpty());
    }

    @Test
    void nullBytesReturnsEmpty() {
        XmpMetadata meta = XmpMetadataParser.parse((byte[]) null);
        assertTrue(meta.title().isEmpty());
        assertTrue(meta.creators().isEmpty());
        assertFalse(meta.isPdfA());
    }

    @Test
    void emptyStringReturnsEmpty() {
        XmpMetadata meta = XmpMetadataParser.parse("");
        assertTrue(meta.title().isEmpty());
    }

    @Test
    void malformedXmlReturnsEmpty() {
        XmpMetadata meta = XmpMetadataParser.parse("<broken>xml<");
        assertTrue(meta.title().isEmpty());
    }

    @Test
    void byteArrayOverloadWorks() {
        XmpMetadata meta = XmpMetadataParser.parse(FULL_XMP.getBytes(StandardCharsets.UTF_8));
        assertTrue(meta.title().isPresent());
        assertEquals("Test Book", meta.title().get());
    }

    @Test
    void parsesCalibreFields() {
        String xmp = """
                <x:xmpmeta xmlns:x="adobe:ns:meta/">
                  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description rdf:about=""
                        xmlns:calibre="http://calibre-ebook.com/xmp-namespace"
                        calibre:series="Test Series"
                        calibre:series_index="3">
                    </rdf:Description>
                  </rdf:RDF>
                </x:xmpmeta>
                """;
        XmpMetadata meta = XmpMetadataParser.parse(xmp);
        assertEquals("Test Series", meta.calibreFields().get("series"));
        assertEquals("3", meta.calibreFields().get("series_index"));
    }

    @Test
    void calibreSeriesConvenience() {
        String xmp = """
                <x:xmpmeta xmlns:x="adobe:ns:meta/">
                  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description rdf:about=""
                        xmlns:calibre="http://calibre-ebook.com/xmp-namespace"
                        calibre:series="Discworld"
                        calibre:series_index="5.5"
                        calibre:rating="8">
                    </rdf:Description>
                  </rdf:RDF>
                </x:xmpmeta>
                """;
        XmpMetadata meta = XmpMetadataParser.parse(xmp);
        assertEquals("Discworld", meta.calibreSeries().orElse(""));
        assertEquals(5.5, meta.calibreSeriesIndex().orElse(0));
        assertEquals(8, meta.calibreRating().orElse(0));
    }

    @Test
    void calibreTagsFromBag() {
        String xmp = """
                <x:xmpmeta xmlns:x="adobe:ns:meta/">
                  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description rdf:about=""
                        xmlns:calibre="http://calibre-ebook.com/xmp-namespace">
                      <calibre:tags>
                        <rdf:Bag>
                          <rdf:li>fantasy</rdf:li>
                          <rdf:li>humor</rdf:li>
                          <rdf:li>british</rdf:li>
                        </rdf:Bag>
                      </calibre:tags>
                    </rdf:Description>
                  </rdf:RDF>
                </x:xmpmeta>
                """;
        XmpMetadata meta = XmpMetadataParser.parse(xmp);
        assertEquals(3, meta.calibreTags().size());
        assertTrue(meta.calibreTags().contains("fantasy"));
        assertTrue(meta.calibreTags().contains("humor"));
        assertTrue(meta.calibreTags().contains("british"));
    }

    @Test
    void calibreConvenienceMethodsReturnEmptyWhenAbsent() {
        XmpMetadata empty = XmpMetadata.empty();
        assertTrue(empty.calibreSeries().isEmpty());
        assertTrue(empty.calibreSeriesIndex().isEmpty());
        assertTrue(empty.calibreRating().isEmpty());
        assertTrue(empty.calibreTags().isEmpty());
    }

    @Test
    void xDefaultPreferredInRdfAlt() {
        String xmp = """
                <x:xmpmeta xmlns:x="adobe:ns:meta/">
                  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description rdf:about=""
                        xmlns:dc="http://purl.org/dc/elements/1.1/">
                      <dc:title>
                        <rdf:Alt>
                          <rdf:li xml:lang="de">Deutscher Titel</rdf:li>
                          <rdf:li xml:lang="x-default">Default Title</rdf:li>
                          <rdf:li xml:lang="fr">Titre Français</rdf:li>
                        </rdf:Alt>
                      </dc:title>
                    </rdf:Description>
                  </rdf:RDF>
                </x:xmpmeta>
                """;
        XmpMetadata meta = XmpMetadataParser.parse(xmp);
        assertEquals("Default Title", meta.title().orElse(""));
    }

    @Test
    void emptyMetadataHelperMethod() {
        XmpMetadata empty = XmpMetadata.empty();
        assertTrue(empty.title().isEmpty());
        assertTrue(empty.creators().isEmpty());
        assertTrue(empty.subjects().isEmpty());
        assertTrue(empty.identifiers().isEmpty());
        assertFalse(empty.isPdfA());
        assertTrue(empty.calibreFields().isEmpty());
        assertTrue(empty.customFields().isEmpty());
    }

    // --- Booklore namespace XMP tests (real-world metadata structure) ---

    private static final String BOOKLORE_XMP = """
            <?xpacket begin="\uFEFF" id="W5M0MpCehiHzreSzNTczkc9d"?>
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description rdf:about=""
                    xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <dc:title>
                    <rdf:Alt><rdf:li xml:lang="x-default">Dead Simple Python</rdf:li></rdf:Alt>
                  </dc:title>
                  <dc:creator>
                    <rdf:Seq><rdf:li>Jason C. McDonald</rdf:li></rdf:Seq>
                  </dc:creator>
                  <dc:description>
                    <rdf:Alt><rdf:li xml:lang="x-default">&lt;p&gt;A comprehensive guide to Python.&lt;/p&gt;</rdf:li></rdf:Alt>
                  </dc:description>
                  <dc:subject>
                    <rdf:Bag>
                      <rdf:li>Coding</rdf:li>
                      <rdf:li>Computer Science</rdf:li>
                      <rdf:li>Nonfiction</rdf:li>
                      <rdf:li>Programming</rdf:li>
                      <rdf:li>Reference</rdf:li>
                      <rdf:li>Software</rdf:li>
                      <rdf:li>Technology</rdf:li>
                    </rdf:Bag>
                  </dc:subject>
                  <dc:publisher>
                    <rdf:Bag><rdf:li>No Starch Press</rdf:li></rdf:Bag>
                  </dc:publisher>
                  <dc:language>
                    <rdf:Bag><rdf:li>English</rdf:li></rdf:Bag>
                  </dc:language>
                  <dc:date>
                    <rdf:Seq><rdf:li>2023-01-01</rdf:li></rdf:Seq>
                  </dc:date>
                </rdf:Description>
                <rdf:Description rdf:about=""
                    xmlns:xmp="http://ns.adobe.com/xap/1.0/">
                  <xmp:CreatorTool>Booklore</xmp:CreatorTool>
                  <xmp:MetadataDate>2025-06-01T12:00:00Z</xmp:MetadataDate>
                </rdf:Description>
                <rdf:Description rdf:about=""
                    xmlns:booklore="http://booklore.org/metadata/1.0/">
                  <booklore:subtitle>Idiomatic Python for the Impatient Programmer</booklore:subtitle>
                  <booklore:isbn13>9781718500921</booklore:isbn13>
                  <booklore:isbn10>1718500920</booklore:isbn10>
                  <booklore:goodreadsId>52555538</booklore:goodreadsId>
                  <booklore:goodreadsRating>4.4</booklore:goodreadsRating>
                  <booklore:pageCount>713</booklore:pageCount>
                </rdf:Description>
              </rdf:RDF>
            </x:xmpmeta>
            <?xpacket end="w"?>
            """;

    @Test
    void parsesBookloreDublinCore() {
        XmpMetadata meta = XmpMetadataParser.parse(BOOKLORE_XMP);
        assertEquals("Dead Simple Python", meta.title().orElse(""));
        assertEquals(List.of("Jason C. McDonald"), meta.creators());
        assertTrue(meta.description().orElse("").contains("comprehensive guide"));
        assertEquals(7, meta.subjects().size());
        assertTrue(meta.subjects().contains("Programming"));
        assertEquals("No Starch Press", meta.publisher().orElse(""));
        assertEquals("English", meta.language().orElse(""));
        assertEquals("2023-01-01", meta.date().orElse(""));
    }

    @Test
    void parsesBookloreXmpFields() {
        XmpMetadata meta = XmpMetadataParser.parse(BOOKLORE_XMP);
        assertTrue(meta.customFields().containsKey("CreatorTool"));
        assertEquals("Booklore", meta.customFields().get("CreatorTool"));
    }

    @Test
    void writerParserRoundTrip() {
        XmpMetadata original = new XmpMetadata(
                Optional.of("My Book Title"),
                List.of("Author A", "Author B"),
                Optional.of("A great description"),
                List.of("Fantasy", "Adventure", "Magic"),
                Optional.of("My Publisher"),
                Optional.of("en"),
                Optional.of("2024-06-15"),
                Optional.of("Copyright 2024"),
                List.of("urn:isbn:978-1234567890"),
                Optional.empty(),
                Map.of("series", "Epic Series", "series_index", "3"),
                Map.of()
        );

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
    void writerParserRoundTripWithBookloreNamespace() {
        Map<String, String> customFields = new LinkedHashMap<>();
        customFields.put("booklore:subtitle", "An Epic Subtitle");
        customFields.put("booklore:isbn13", "9781234567890");
        customFields.put("booklore:isbn10", "1234567890");
        customFields.put("booklore:goodreadsId", "12345");
        customFields.put("booklore:goodreadsRating", "4.5");

        XmpMetadata original = new XmpMetadata(
                Optional.of("Test Title"),
                List.of("Test Author"),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Map.of(),
                customFields
        );

        XmpMetadataWriter writer = new XmpMetadataWriter()
                .registerNamespace("booklore", "http://booklore.org/metadata/1.0/");
        String xmpPacket = writer.write(original);

        // Verify the raw XML contains booklore elements
        assertTrue(xmpPacket.contains("booklore:subtitle"), "XMP should contain booklore:subtitle element");
        assertTrue(xmpPacket.contains("An Epic Subtitle"));
        assertTrue(xmpPacket.contains("booklore:isbn13"));
        assertTrue(xmpPacket.contains("9781234567890"));
        assertTrue(xmpPacket.contains("booklore:goodreadsId"));
        assertTrue(xmpPacket.contains("12345"));

        // Parse it back — Dublin Core should survive
        XmpMetadata parsed = XmpMetadataParser.parse(xmpPacket);
        assertEquals("Test Title", parsed.title().orElse(""));
        assertEquals(List.of("Test Author"), parsed.creators());
    }
}
