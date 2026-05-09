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

  public XmpMetadataWriter registerNamespace(String prefix, String uri) {
    Objects.requireNonNull(prefix, "prefix");
    Objects.requireNonNull(uri, "uri");
    if (prefix.isEmpty()) throw new IllegalArgumentException("Prefix must not be empty");
    if (Set.of("dc", "rdf", "pdfaid", "calibre", "xmp", "xap", "xml")
        .contains(prefix.toLowerCase(Locale.ROOT))) {
      throw new IllegalArgumentException("Prefix '" + prefix + "' is reserved");
    }
    validateNcName(prefix);
    customNamespaces.put(prefix, uri);
    return this;
  }

  public String write(XmpMetadata metadata) {
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

  public void write(XmpMetadata metadata, OutputStream out) {
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
      s.write("  <calibre:");
      s.write(key);
      s.write(">");
      writeEscaped(s, entry.getValue());
      s.write("</calibre:");
      s.write(key);
      s.write(">\n");
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
      String uri = customNamespaces.get(prefix);
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
        default -> s.write(cp);
      }
      i += Character.charCount(cp);
    }
  }

  private static String stripPrefix(String key) {
    int colonIdx = key.indexOf(':');
    return colonIdx >= 0 ? key.substring(colonIdx + 1) : key;
  }

  private void validate(XmpMetadata metadata) {
    metadata.calibreFields().keySet().forEach(XmpMetadataWriter::validateNcName);
    metadata.customFields().keySet().forEach(this::validateCustomField);
    metadata.customListFields().keySet().forEach(this::validateCustomField);
  }

  private void validateCustomField(String key) {
    int colonIdx = key.indexOf(':');
    if (colonIdx > 0) {
      String prefix = key.substring(0, colonIdx);
      validateNcName(prefix);
      validateNcName(key.substring(colonIdx + 1));
      if (!"xmp".equals(prefix) && !customNamespaces.containsKey(prefix)) {
        throw new IllegalArgumentException("Namespace prefix '" + prefix + "' is not registered");
      }
    } else {
      validateNcName(key);
    }
  }

  private static void validateNcName(String name) {
    if (name == null || name.isEmpty() || !isValidNcNameStart(name.charAt(0)))
      throw new IllegalArgumentException("Invalid XML name: '" + name + "'");
    final int nameLength = name.length();
    for (int i = 1; i < nameLength; i++)
      if (!isValidNcNameChar(name.charAt(i)))
        throw new IllegalArgumentException("Invalid XML name: '" + name + "'");
  }

  private static boolean isValidNcNameStart(char c) {
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_';
  }

  private static boolean isValidNcNameChar(char c) {
    return isValidNcNameStart(c) || (c >= '0' && c <= '9') || c == '-' || c == '.';
  }

  private static <T> void processField(
      String key, T value, Map<String, Map<String, T>> grouped, Map<String, T> unprefixed) {
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

  private static void groupCustomFields(
      XmpMetadata metadata,
      Map<String, Map<String, String>> simpleGrouped,
      Map<String, Map<String, List<String>>> listGrouped,
      Map<String, String> simpleUnprefixed,
      Map<String, List<String>> listUnprefixed) {
    metadata.customFields().forEach((k, v) -> processField(k, v, simpleGrouped, simpleUnprefixed));
    metadata.customListFields().forEach((k, v) -> processField(k, v, listGrouped, listUnprefixed));
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
