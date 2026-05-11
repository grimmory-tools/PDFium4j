package org.grimmory.pdfium4j;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.grimmory.pdfium4j.exception.PdfiumException;
import org.grimmory.pdfium4j.model.XmpMetadata;
import org.grimmory.pdfium4j.model.XmpMetadata.QualifiedIdentifier;

/** Serializes {@link XmpMetadata} to XMP XML packets suitable for embedding in PDF files. */
public final class XmpMetadataWriter {

  private static final String NS_RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
  private static final String NS_DC = "http://purl.org/dc/elements/1.1/";
  private static final String NS_PDFA_ID = "http://www.aiim.org/pdfa/ns/id/";
  private static final String NS_CALIBRE = "http://calibre-ebook.com/xmp-namespace";
  private static final String NS_XAP = "http://ns.adobe.com/xap/1.0/";
  private static final String NS_XMP_IDQ = "http://ns.adobe.com/xmp/Identifier/qual/1.0/";
  private static final String NS_XMP_MM = "http://ns.adobe.com/xap/1.0/mm/";
  private static final String NS_PDF = "http://ns.adobe.com/pdf/1.3/";
  private static final String NS_PHOTOSHOP = "http://ns.adobe.com/photoshop/1.0/";
  private static final String NS_TIFF = "http://ns.adobe.com/tiff/1.0/";
  private static final String NS_EXIF = "http://ns.adobe.com/exif/1.0/";
  private static final String NS_ST_REF = "http://ns.adobe.com/xap/1.0/sType/ResourceRef#";
  private static final String NS_ST_EVT = "http://ns.adobe.com/xap/1.0/sType/ResourceEvent#";
  private static final String NS_BOOKLORE = "http://booklore.org/xmp/1.0/";
  private static final String NS_GRIMMORY = "http://grimmory.org/xmp/1.0/";
  private static final String NS_XAP_G = "http://ns.adobe.com/xap/1.0/g/";

  private static final Map<String, String> BUILTIN_NS_PREFIXES =
      Map.ofEntries(
          Map.entry("calibre", NS_CALIBRE),
          Map.entry("xmp", NS_XAP),
          Map.entry("xap", NS_XAP),
          Map.entry("xapG", NS_XAP_G),
          Map.entry("xmpMM", NS_XMP_MM),
          Map.entry("xapMM", NS_XMP_MM),
          Map.entry("dc", NS_DC),
          Map.entry("pdf", NS_PDF),
          Map.entry("photoshop", NS_PHOTOSHOP),
          Map.entry("tiff", NS_TIFF),
          Map.entry("exif", NS_EXIF),
          Map.entry("stRef", NS_ST_REF),
          Map.entry("stEvt", NS_ST_EVT),
          Map.entry("booklore", NS_BOOKLORE),
          Map.entry("grimmory", NS_GRIMMORY),
          Map.entry("pdfx", "http://ns.adobe.com/pdfx/1.3/"),
          Map.entry("prism", "http://prismstandard.org/namespaces/basic/2.0/"),
          Map.entry("pdfaid", "http://www.aiim.org/pdfa/ns/id/"));

  private static final String RDF_DESCRIPTION_START = "<rdf:Description rdf:about=\"\"\n";
  private static final String RDF_DESCRIPTION_END = "</rdf:Description>\n";
  private static final String XMLNS_ATTR = "    xmlns:";
  private static final String RDF_LI_START = "      <rdf:li>";
  private static final String RDF_LI_END = "</rdf:li>\n";
  private static final String RDF_BAG_START = "    <rdf:Bag>\n";
  private static final String RDF_BAG_END = "    </rdf:Bag>\n";
  private static final String RDF_SEQ_START = "    <rdf:Seq>\n";
  private static final String RDF_SEQ_END = "    </rdf:Seq>\n";
  private static final String ATTRIBUTE_END = "\">\n";

  private final Map<String, String> customNamespaces = LinkedHashMap.newLinkedHashMap(8);

  public synchronized XmpMetadataWriter registerNamespace(String prefix, String uri) {
    Objects.requireNonNull(prefix, "prefix");
    Objects.requireNonNull(uri, "uri");
    if (prefix.isEmpty()) throw new IllegalArgumentException("Prefix must not be empty");
    if (BUILTIN_NS_PREFIXES.containsKey(prefix.toLowerCase(Locale.ROOT))
        || "rdf".equals(prefix)
        || "xml".equals(prefix)) {
      throw new IllegalArgumentException("Prefix '" + prefix + "' is reserved");
    }
    if (!isValidNcName(prefix)) throw new IllegalArgumentException("Invalid prefix: " + prefix);
    customNamespaces.put(prefix, uri);
    return this;
  }

  public synchronized String write(XmpMetadata metadata) {
    Objects.requireNonNull(metadata, "metadata");
    validate(metadata);
    StringWriter sw = new StringWriter();
    try {
      writeToSink(metadata, new WriterSink(sw));
    } catch (IOException e) {
      throw new PdfiumException("Unexpected I/O error writing to StringWriter", e);
    }
    return sw.toString();
  }

  public synchronized void write(XmpMetadata metadata, OutputStream out) {
    Objects.requireNonNull(metadata, "metadata");
    Objects.requireNonNull(out, "out");
    validate(metadata);
    try {
      writeToSink(metadata, new OutputStreamSink(out));
      out.flush();
    } catch (IOException e) {
      throw new PdfiumException("I/O error writing XMP to stream", e);
    }
  }

  private interface Sink {
    void write(String s) throws IOException;

    void write(int cp) throws IOException;
  }

  private static final class WriterSink implements Sink {
    private final Writer writer;

    WriterSink(Writer writer) {
      this.writer = writer;
    }

    @Override
    public void write(String s) throws IOException {
      writer.write(s);
    }

    @Override
    public void write(int cp) throws IOException {
      if (Character.isSupplementaryCodePoint(cp)) {
        writer.write(Character.highSurrogate(cp));
        writer.write(Character.lowSurrogate(cp));
      } else {
        writer.write(cp);
      }
    }
  }

  private static final class OutputStreamSink implements Sink {
    private final OutputStream out;

    OutputStreamSink(OutputStream out) {
      this.out = out;
    }

    @Override
    public void write(String s) throws IOException {
      if (s == null) return;
      int len = s.length();
      int i = 0;
      while (i < len) {
        char c = s.charAt(i);
        if (Character.isHighSurrogate(c) && i + 1 < len) {
          char next = s.charAt(i + 1);
          if (Character.isLowSurrogate(next)) {
            write(Character.toCodePoint(c, next));
            i += 2;
            continue;
          }
        }
        write(c);
        i++;
      }
    }

    @Override
    public void write(int cp) throws IOException {
      if (cp <= 127) {
        out.write(cp);
      } else if (cp <= 0x7FF) {
        out.write(0xC0 | (cp >> 6));
        out.write(0x80 | (cp & 0x3F));
      } else if (cp <= 0xFFFF) {
        out.write(0xE0 | (cp >> 12));
        out.write(0x80 | ((cp >> 6) & 0x3F));
        out.write(0x80 | (cp & 0x3F));
      } else {
        out.write(0xF0 | (cp >> 18));
        out.write(0x80 | ((cp >> 12) & 0x3F));
        out.write(0x80 | ((cp >> 6) & 0x3F));
        out.write(0x80 | (cp & 0x3F));
      }
    }
  }

  private void writeToSink(XmpMetadata metadata, Sink s) throws IOException {
    s.write("<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n");
    s.write("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n");
    s.write("<rdf:RDF xmlns:rdf=\"");
    s.write(NS_RDF);
    s.write(ATTRIBUTE_END);

    writeDescriptions(s, metadata);

    s.write("</rdf:RDF>\n");
    s.write("</x:xmpmeta>\n");
    writePadding(s);
    s.write("<?xpacket end=\"w\"?>");
  }

  private void writeDescriptions(Sink s, XmpMetadata metadata) throws IOException {
    writeDublinCore(s, metadata);
    writePdfAConformance(s, metadata);
    writeCalibreFields(s, metadata);
    writeCustomFields(s, metadata);
    writeXmpIdentifiers(s, metadata);
  }

  private static void writeDublinCore(Sink s, XmpMetadata metadata) throws IOException {
    boolean hasDc =
        metadata.title().isPresent()
            || !metadata.creators().isEmpty()
            || metadata.description().isPresent()
            || !metadata.subjects().isEmpty()
            || metadata.publisher().isPresent()
            || metadata.language().isPresent()
            || metadata.date().isPresent()
            || metadata.rights().isPresent()
            || !metadata.identifiers().isEmpty();
    if (!hasDc) return;

    s.write(RDF_DESCRIPTION_START);
    s.write(XMLNS_ATTR);
    s.write("dc=\"");
    s.write(NS_DC);
    s.write(ATTRIBUTE_END);

    metadata.title().ifPresent(v -> wrapError(() -> writeAlt(s, "dc:title", v)));
    if (!metadata.creators().isEmpty()) writeSeq(s, "dc:creator", metadata.creators());
    metadata.description().ifPresent(v -> wrapError(() -> writeAlt(s, "dc:description", v)));
    if (!metadata.subjects().isEmpty()) writeBag(s, "dc:subject", metadata.subjects());
    metadata.publisher().ifPresent(v -> wrapError(() -> writeBag(s, "dc:publisher", List.of(v))));
    metadata.language().ifPresent(v -> wrapError(() -> writeBag(s, "dc:language", List.of(v))));
    metadata.date().ifPresent(v -> wrapError(() -> writeSeq(s, "dc:date", List.of(v))));
    metadata.rights().ifPresent(v -> wrapError(() -> writeAlt(s, "dc:rights", v)));
    if (!metadata.identifiers().isEmpty()) writeBag(s, "dc:identifier", metadata.identifiers());

    s.write(RDF_DESCRIPTION_END);
  }

  private static void writePdfAConformance(Sink s, XmpMetadata metadata) throws IOException {
    Optional<String> pdfaConformance = metadata.pdfaConformance();
    if (pdfaConformance.isEmpty()) return;
    String conf = pdfaConformance.get();
    if (conf.isBlank()) return;

    s.write(RDF_DESCRIPTION_START);
    s.write(XMLNS_ATTR);
    s.write("pdfaid=\"");
    s.write(NS_PDFA_ID);
    s.write(ATTRIBUTE_END);

    if (conf.length() >= 2 && Character.isDigit(conf.charAt(0))) {
      s.write("  <pdfaid:part>");
      s.write(conf.charAt(0));
      s.write("</pdfaid:part>\n");
      s.write("  <pdfaid:conformance>");
      s.write(Character.toUpperCase(conf.charAt(1)));
      s.write("</pdfaid:conformance>\n");
    } else {
      s.write("  <pdfaid:conformance>");
      writeEscaped(s, conf);
      s.write("</pdfaid:conformance>\n");
    }

    s.write(RDF_DESCRIPTION_END);
  }

  private static void writeCalibreFields(Sink s, XmpMetadata metadata) throws IOException {
    if (metadata.calibreFields().isEmpty()) return;

    s.write(RDF_DESCRIPTION_START);
    s.write(XMLNS_ATTR);
    s.write("calibre=\"");
    s.write(NS_CALIBRE);
    s.write(ATTRIBUTE_END);

    for (Map.Entry<String, String> entry : metadata.calibreFields().entrySet()) {
      String key = entry.getKey();
      if (isValidNcName(key)) {
        s.write("  <calibre:");
        s.write(key);
        s.write(">");
        writeEscaped(s, entry.getValue());
        s.write("</calibre:");
        s.write(key);
        s.write(">\n");
      }
    }

    s.write(RDF_DESCRIPTION_END);
  }

  private void writeCustomFields(Sink s, XmpMetadata metadata) throws IOException {
    if (metadata.customFields().isEmpty() && metadata.customListFields().isEmpty()) return;

    Map<String, Map<String, String>> simpleGrouped = LinkedHashMap.newLinkedHashMap(8);
    Map<String, Map<String, List<String>>> listGrouped = LinkedHashMap.newLinkedHashMap(8);
    Map<String, String> simpleUnprefixed = LinkedHashMap.newLinkedHashMap(8);
    Map<String, List<String>> listUnprefixed = LinkedHashMap.newLinkedHashMap(8);

    groupCustomFields(metadata, simpleGrouped, listGrouped, simpleUnprefixed, listUnprefixed);

    Set<String> allPrefixes = new LinkedHashSet<>(simpleGrouped.keySet());
    allPrefixes.addAll(listGrouped.keySet());

    for (String prefix : allPrefixes) {
      String uri =
          customNamespaces.getOrDefault(prefix, BUILTIN_NS_PREFIXES.getOrDefault(prefix, ""));
      s.write(RDF_DESCRIPTION_START);
      s.write(XMLNS_ATTR);
      s.write(prefix);
      s.write("=\"");
      writeEscaped(s, uri);
      s.write(ATTRIBUTE_END);

      Map<String, String> simples = simpleGrouped.get(prefix);
      if (simples != null) {
        for (Map.Entry<String, String> field : simples.entrySet()) {
          writeSimpleField(s, prefix, field.getKey(), field.getValue());
        }
      }

      Map<String, List<String>> lists = listGrouped.get(prefix);
      if (lists != null) {
        for (Map.Entry<String, List<String>> field : lists.entrySet()) {
          writeListField(s, prefix, field.getKey(), field.getValue());
        }
      }
      s.write(RDF_DESCRIPTION_END);
    }
    writeUnprefixedCustomDescription(s, simpleUnprefixed, listUnprefixed);
  }

  private static void writeUnprefixedCustomDescription(
      Sink s, Map<String, String> simpleUnprefixed, Map<String, List<String>> listUnprefixed)
      throws IOException {
    if (simpleUnprefixed.isEmpty() && listUnprefixed.isEmpty()) return;

    s.write(RDF_DESCRIPTION_START);
    s.write(XMLNS_ATTR);
    s.write("xmp=\"");
    s.write(NS_XAP);
    s.write(ATTRIBUTE_END);
    for (Map.Entry<String, String> entry : simpleUnprefixed.entrySet()) {
      writeSimpleField(s, "xmp", stripPrefix(entry.getKey()), entry.getValue());
    }
    for (Map.Entry<String, List<String>> entry : listUnprefixed.entrySet()) {
      writeListField(s, "xmp", stripPrefix(entry.getKey()), entry.getValue());
    }
    s.write(RDF_DESCRIPTION_END);
  }

  private static void writeSimpleField(Sink s, String prefix, String localName, String value)
      throws IOException {
    s.write("  <");
    s.write(prefix);
    s.write(":");
    s.write(localName);
    s.write(">");
    writeEscaped(s, value);
    s.write("</");
    s.write(prefix);
    s.write(":");
    s.write(localName);
    s.write(">\n");
  }

  private static void writeListField(Sink s, String prefix, String localName, List<String> values)
      throws IOException {
    writeBag(s, prefix + ":" + localName, values);
  }

  private static void writeXmpIdentifiers(Sink s, XmpMetadata metadata) throws IOException {
    if (metadata.xmpIdentifiers().isEmpty()) return;

    s.write(RDF_DESCRIPTION_START);
    s.write(XMLNS_ATTR);
    s.write("xmp=\"");
    s.write(NS_XAP);
    s.write("\"\n");
    s.write(XMLNS_ATTR);
    s.write("xmpidq=\"");
    s.write(NS_XMP_IDQ);
    s.write(ATTRIBUTE_END);
    s.write("  <xmp:Identifier>\n");
    s.write(RDF_BAG_START);
    for (QualifiedIdentifier id : metadata.xmpIdentifiers()) {
      s.write("      <rdf:li rdf:parseType=\"Resource\">\n");
      s.write("        <xmpidq:Scheme>");
      writeEscaped(s, id.scheme());
      s.write("</xmpidq:Scheme>\n");
      s.write("        <rdf:value>");
      writeEscaped(s, id.value());
      s.write("</rdf:value>\n");
      s.write("      </rdf:li>\n");
    }
    s.write(RDF_BAG_END);
    s.write("  </xmp:Identifier>\n");
    s.write(RDF_DESCRIPTION_END);
  }

  private static void writeAlt(Sink s, String tag, String value) throws IOException {
    s.write("  <");
    s.write(tag);
    s.write(">\n");
    s.write("    <rdf:Alt>\n");
    s.write("      <rdf:li xml:lang=\"x-default\">");
    writeEscaped(s, value);
    s.write(RDF_LI_END);
    s.write("    </rdf:Alt>\n");
    s.write("  </");
    s.write(tag);
    s.write(">\n");
  }

  private static void writeSeq(Sink s, String tag, List<String> values) throws IOException {
    s.write("  <");
    s.write(tag);
    s.write(">\n");
    s.write(RDF_SEQ_START);
    for (String v : values) {
      s.write(RDF_LI_START);
      writeEscaped(s, v);
      s.write(RDF_LI_END);
    }
    s.write(RDF_SEQ_END);
    s.write("  </");
    s.write(tag);
    s.write(">\n");
  }

  private static void writeBag(Sink s, String tag, List<String> values) throws IOException {
    s.write("  <");
    s.write(tag);
    s.write(">\n");
    s.write(RDF_BAG_START);
    for (String v : values) {
      s.write(RDF_LI_START);
      writeEscaped(s, v);
      s.write(RDF_LI_END);
    }
    s.write(RDF_BAG_END);
    s.write("  </");
    s.write(tag);
    s.write(">\n");
  }

  private static void writePadding(Sink s) throws IOException {
    String padding =
        "                                                                                \n";
    for (int i = 0; i < 20; i++) s.write(padding);
  }

  private static void writeEscaped(Sink s, String text) throws IOException {
    if (text == null) return;
    final int textLength = text.length();
    int i = 0;
    while (i < textLength) {
      int cp = text.codePointAt(i);
      switch (cp) {
        case '&' -> s.write("&amp;");
        case '<' -> s.write("&lt;");
        case '>' -> s.write("&gt;");
        case '"' -> s.write("&quot;");
        default -> {
          if (isValidXml10CodePoint(cp)) {
            s.write(cp);
          }
        }
      }
      i += Character.charCount(cp);
    }
  }

  private static boolean isValidXml10CodePoint(int cp) {
    return cp == 0x9
        || cp == 0xA
        || cp == 0xD
        || (cp >= 0x20 && cp <= 0xD7FF)
        || (cp >= 0xE000 && cp <= 0xFFFD)
        || (cp >= 0x10000 && cp <= 0x10FFFF);
  }

  private static String stripPrefix(String key) {
    int colonIdx = key.indexOf(':');
    return colonIdx >= 0 ? key.substring(colonIdx + 1) : key;
  }

  private void validate(XmpMetadata metadata) {
    // We no longer throw during validation of custom fields; instead we skip invalid ones during
    // write.
    metadata
        .calibreFields()
        .keySet()
        .forEach(
            k -> {
              if (!isValidNcName(k))
                throw new IllegalArgumentException("Invalid Calibre field name: " + k);
            });
  }

  private boolean isValidCustomField(String key) {
    int colonIdx = key.indexOf(':');
    if (colonIdx > 0) {
      String prefix = key.substring(0, colonIdx);
      String localName = key.substring(colonIdx + 1);
      if (!isValidNcName(prefix) || !isValidNcName(localName)) return false;

      String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
      if (!BUILTIN_NS_PREFIXES.containsKey(lowerPrefix) && !customNamespaces.containsKey(prefix)) {
        customNamespaces.put(prefix, "http://pdfium4j.org/ns/auto-generated/" + prefix);
      }
      return true;
    } else {
      return isValidNcName(key);
    }
  }

  private static boolean isValidNcName(String name) {
    if (name == null || name.isEmpty() || !isValidNcNameStart(name.charAt(0))) return false;
    final int nameLength = name.length();
    for (int i = 1; i < nameLength; i++) {
      if (!isValidNcNameChar(name.charAt(i))) return false;
    }
    return true;
  }

  private static boolean isValidNcNameStart(char c) {
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_';
  }

  private static boolean isValidNcNameChar(char c) {
    return isValidNcNameStart(c) || (c >= '0' && c <= '9') || c == '-' || c == '.';
  }

  private void groupCustomFields(
      XmpMetadata metadata,
      Map<String, Map<String, String>> simpleGrouped,
      Map<String, Map<String, List<String>>> listGrouped,
      Map<String, String> simpleUnprefixed,
      Map<String, List<String>> listUnprefixed) {
    metadata.customFields().forEach((k, v) -> processField(k, v, simpleGrouped, simpleUnprefixed));
    metadata.customListFields().forEach((k, v) -> processField(k, v, listGrouped, listUnprefixed));
  }

  private <T> void processField(
      String key, T value, Map<String, Map<String, T>> grouped, Map<String, T> unprefixed) {
    if (!isValidCustomField(key)) return;

    int colonIdx = key.indexOf(':');
    if (colonIdx > 0) {
      String prefix = key.substring(0, colonIdx);
      if ("xmp".equals(prefix)) unprefixed.put(key, value);
      else
        grouped
            .computeIfAbsent(prefix, _ -> LinkedHashMap.newLinkedHashMap(8))
            .put(key.substring(colonIdx + 1), value);
    } else unprefixed.put(key, value);
  }

  private interface ThrowingRunnable {
    void run() throws IOException;
  }

  private static void wrapError(ThrowingRunnable r) {
    try {
      r.run();
    } catch (IOException e) {
      throw new PdfiumException("XMP serialization error", e);
    }
  }
}
