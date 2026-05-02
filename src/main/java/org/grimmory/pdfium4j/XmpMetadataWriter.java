package org.grimmory.pdfium4j;

import java.io.IOException;
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
import org.grimmory.pdfium4j.model.XmpMetadata;
import org.grimmory.pdfium4j.model.XmpMetadata.QualifiedIdentifier;

/**
 * Serializes {@link XmpMetadata} to XMP XML packets suitable for embedding in PDF files.
 *
 * <p>Supports Dublin Core, PDF/A, Calibre, and user-registered custom namespaces.
 *
 * <pre>{@code
 * XmpMetadataWriter writer = new XmpMetadataWriter()
 *     .registerNamespace("booklore", "http://booklore.app/xmp-namespace");
 *
 * XmpMetadata meta = XmpMetadata.builder()
 *     .title("My Book")
 *     .creators(List.of("Author"))
 *     .subjects(List.of("fiction"))
 *     .language("en")
 *     .customFields(Map.of("booklore:shelf", "favorites"))
 *     .customListFields(Map.of("booklore:tags", List.of("tag1", "tag2")))
 *     .build();
 *
 * writer.write(meta, outputStream);
 * }</pre>
 */
public final class XmpMetadataWriter {

  private static final String NS_RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
  private static final String NS_DC = "http://purl.org/dc/elements/1.1/";
  private static final String NS_PDFA_ID = "http://www.aiim.org/pdfa/ns/id/";
  private static final String NS_CALIBRE = "http://calibre-ebook.com/xmp-namespace";
  private static final String NS_XAP = "http://ns.adobe.com/xap/1.0/";
  private static final String NS_XMP_IDQ = "http://ns.adobe.com/xmp/Identifier/qual/1.0/";

  private final Map<String, String> customNamespaces = LinkedHashMap.newLinkedHashMap(8);

  /**
   * Register a custom namespace for XMP serialization.
   *
   * @param prefix the XML namespace prefix (e.g. "booklore")
   * @param uri the namespace URI (e.g. "http://booklore.app/xmp-namespace")
   * @return this writer for chaining
   * @throws IllegalArgumentException if prefix conflicts with reserved prefixes
   */
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

  /**
   * Serialize an {@link XmpMetadata} record to a complete XMP packet string.
   *
   * @param metadata the metadata to serialize
   * @return a complete XMP packet string
   */
  public String write(XmpMetadata metadata) {
    StringWriter sw = new StringWriter();
    try {
      writeToWriter(metadata, sw);
    } catch (IOException e) {
      throw new RuntimeException("Unexpected I/O error writing to StringWriter", e);
    }
    return sw.toString();
  }

  private void writeToWriter(XmpMetadata metadata, Writer w) throws IOException {
    w.write("<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n");
    w.write("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n");
    w.write("<rdf:RDF xmlns:rdf=\"");
    w.write(NS_RDF);
    w.write("\">\n");

    writeDescriptions(w, metadata);

    w.write("</rdf:RDF>\n");
    w.write("</x:xmpmeta>\n");
    writePadding(w);
    w.write("<?xpacket end=\"w\"?>");
  }

  private void writeDescriptions(Writer w, XmpMetadata metadata) throws IOException {
    writeDublinCore(w, metadata);
    writePdfAConformance(w, metadata);
    writeCalibreFields(w, metadata);
    writeCustomFields(w, metadata);
    writeXmpIdentifiers(w, metadata);
  }

  private static void writeDublinCore(Writer w, XmpMetadata metadata) throws IOException {
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

    w.write("<rdf:Description rdf:about=\"\"\n");
    w.write("    xmlns:dc=\"");
    w.write(NS_DC);
    w.write("\">\n");

    Optional<String> title = metadata.title();
    if (title.isPresent()) writeAlt(w, "dc:title", title.get());

    if (!metadata.creators().isEmpty()) writeSeq(w, "dc:creator", metadata.creators());

    Optional<String> description = metadata.description();
    if (description.isPresent())
      writeAlt(w, "dc:description", description.get());

    if (!metadata.subjects().isEmpty()) writeBag(w, "dc:subject", metadata.subjects());

    Optional<String> publisher = metadata.publisher();
    if (publisher.isPresent())
      writeBag(w, "dc:publisher", List.of(publisher.get()));

    Optional<String> language = metadata.language();
    if (language.isPresent())
      writeBag(w, "dc:language", List.of(language.get()));

    Optional<String> date = metadata.date();
    if (date.isPresent()) writeSeq(w, "dc:date", List.of(date.get()));

    Optional<String> rights = metadata.rights();
    if (rights.isPresent()) writeAlt(w, "dc:rights", rights.get());

    if (!metadata.identifiers().isEmpty()) writeBag(w, "dc:identifier", metadata.identifiers());

    w.write("</rdf:Description>\n");
  }

  private static void writePdfAConformance(Writer w, XmpMetadata metadata)
      throws IOException {
    Optional<String> pdfaConformance = metadata.pdfaConformance();
    if (pdfaConformance.isEmpty()) return;
    String conf = pdfaConformance.get();
    if (conf.isBlank()) return;

    w.write("<rdf:Description rdf:about=\"\"\n");
    w.write("    xmlns:pdfaid=\"");
    w.write(NS_PDFA_ID);
    w.write("\">\n");

    if (conf.length() >= 2 && Character.isDigit(conf.charAt(0))) {
      w.write("  <pdfaid:part>");
      w.write(conf.charAt(0));
      w.write("</pdfaid:part>\n");
      w.write("  <pdfaid:conformance>");
      w.write(Character.toUpperCase(conf.charAt(1)));
      w.write("</pdfaid:conformance>\n");
    } else {
      w.write("  <pdfaid:conformance>");
      w.write(escapeXml(conf));
      w.write("</pdfaid:conformance>\n");
    }

    w.write("</rdf:Description>\n");
  }

  private static void writeCalibreFields(Writer w, XmpMetadata metadata) throws IOException {
    if (metadata.calibreFields().isEmpty()) return;

    w.write("<rdf:Description rdf:about=\"\"\n");
    w.write("    xmlns:calibre=\"");
    w.write(NS_CALIBRE);
    w.write("\">\n");

    for (Map.Entry<String, String> entry : metadata.calibreFields().entrySet()) {
      String key = entry.getKey();
      validateNcName(key);
      w.write("  <calibre:");
      w.write(key);
      w.write(">");
      w.write(escapeXml(entry.getValue()));
      w.write("</calibre:");
      w.write(key);
      w.write(">\n");
    }

    w.write("</rdf:Description>\n");
  }

  private void writeCustomFields(Writer w, XmpMetadata metadata) throws IOException {
    if (metadata.customFields().isEmpty() && metadata.customListFields().isEmpty()) return;

    Map<String, Map<String, String>> simpleGrouped = LinkedHashMap.newLinkedHashMap(8);
    Map<String, Map<String, List<String>>> listGrouped = LinkedHashMap.newLinkedHashMap(8);
    Map<String, String> simpleUnprefixed = LinkedHashMap.newLinkedHashMap(8);
    Map<String, List<String>> listUnprefixed = LinkedHashMap.newLinkedHashMap(8);

    // Group simple fields
    for (Map.Entry<String, String> entry : metadata.customFields().entrySet()) {
      processField(entry.getKey(), entry.getValue(), simpleGrouped, simpleUnprefixed);
    }

    // Group list fields
    for (Map.Entry<String, List<String>> entry : metadata.customListFields().entrySet()) {
      processField(entry.getKey(), entry.getValue(), listGrouped, listUnprefixed);
    }

    Set<String> allPrefixes = new LinkedHashSet<>(simpleGrouped.keySet());
    allPrefixes.addAll(listGrouped.keySet());

    for (String prefix : allPrefixes) {
      String uri = customNamespaces.get(prefix);
      w.write("<rdf:Description rdf:about=\"\"\n");
      w.write("    xmlns:");
      w.write(prefix);
      w.write("=\"");
      w.write(escapeXml(uri));
      w.write("\">\n");

      Map<String, String> simples = simpleGrouped.get(prefix);
      if (simples != null) {
        for (Map.Entry<String, String> field : simples.entrySet()) {
          writeSimpleField(w, prefix, field.getKey(), field.getValue());
        }
      }

      Map<String, List<String>> lists = listGrouped.get(prefix);
      if (lists != null) {
        for (Map.Entry<String, List<String>> field : lists.entrySet()) {
          writeListField(w, prefix, field.getKey(), field.getValue());
        }
      }
      w.write("</rdf:Description>\n");
    }

    if (!simpleUnprefixed.isEmpty() || !listUnprefixed.isEmpty()) {
      w.write("<rdf:Description rdf:about=\"\"\n");
      w.write("    xmlns:xmp=\"");
      w.write(NS_XAP);
      w.write("\">\n");
      for (Map.Entry<String, String> entry : simpleUnprefixed.entrySet()) {
        writeSimpleField(w, "xmp", stripPrefix(entry.getKey()), entry.getValue());
      }
      for (Map.Entry<String, List<String>> entry : listUnprefixed.entrySet()) {
        writeListField(w, "xmp", stripPrefix(entry.getKey()), entry.getValue());
      }
      w.write("</rdf:Description>\n");
    }
  }

  private <T> void processField(
      String key, T value, Map<String, Map<String, T>> grouped, Map<String, T> unprefixed) {
    int colonIdx = key.indexOf(':');
    if (colonIdx > 0) {
      String prefix = key.substring(0, colonIdx);
      String localName = key.substring(colonIdx + 1);
      validateNcName(prefix);
      validateNcName(localName);
      if ("xmp".equals(prefix)) {
        unprefixed.put(key, value);
      } else if (customNamespaces.containsKey(prefix)) {
        grouped.computeIfAbsent(prefix, _ -> LinkedHashMap.newLinkedHashMap(8)).put(localName, value);
      } else {
        throw new IllegalArgumentException("Namespace prefix '" + prefix + "' is not registered");
      }
    } else {
      validateNcName(key);
      unprefixed.put(key, value);
    }
  }

  private static String stripPrefix(String key) {
    int colonIdx = key.indexOf(':');
    return colonIdx >= 0 ? key.substring(colonIdx + 1) : key;
  }

  private static void writeSimpleField(Writer w, String prefix, String localName, String value)
      throws IOException {
    w.write("  <");
    w.write(prefix);
    w.write(":");
    w.write(localName);
    w.write(">");
    w.write(escapeXml(value));
    w.write("</");
    w.write(prefix);
    w.write(":");
    w.write(localName);
    w.write(">\n");
  }

  private static void writeListField(Writer w, String prefix, String localName, List<String> values)
      throws IOException {
    String tag = prefix + ":" + localName;
    writeBag(w, tag, values);
  }

  private static void writeXmpIdentifiers(Writer w, XmpMetadata metadata) throws IOException {
    if (metadata.xmpIdentifiers().isEmpty()) return;

    w.write("<rdf:Description rdf:about=\"\"\n");
    w.write("    xmlns:xmp=\"");
    w.write(NS_XAP);
    w.write("\"\n");
    w.write("    xmlns:xmpidq=\"");
    w.write(NS_XMP_IDQ);
    w.write("\">\n");
    w.write("  <xmp:Identifier>\n");
    w.write("    <rdf:Bag>\n");
    for (QualifiedIdentifier id : metadata.xmpIdentifiers()) {
      w.write("      <rdf:li rdf:parseType=\"Resource\">\n");
      w.write("        <xmpidq:Scheme>");
      w.write(escapeXml(id.scheme()));
      w.write("</xmpidq:Scheme>\n");
      w.write("        <rdf:value>");
      w.write(escapeXml(id.value()));
      w.write("</rdf:value>\n");
      w.write("      </rdf:li>\n");
    }
    w.write("    </rdf:Bag>\n");
    w.write("  </xmp:Identifier>\n");
    w.write("</rdf:Description>\n");
  }

  private static void writeAlt(Writer w, String tag, String value) throws IOException {
    w.write("  <");
    w.write(tag);
    w.write(">\n");
    w.write("    <rdf:Alt>\n");
    w.write("      <rdf:li xml:lang=\"x-default\">");
    w.write(escapeXml(value));
    w.write("</rdf:li>\n");
    w.write("    </rdf:Alt>\n");
    w.write("  </");
    w.write(tag);
    w.write(">\n");
  }

  private static void writeSeq(Writer w, String tag, List<String> values) throws IOException {
    w.write("  <");
    w.write(tag);
    w.write(">\n");
    w.write("    <rdf:Seq>\n");
    for (String v : values) {
      w.write("      <rdf:li>");
      w.write(escapeXml(v));
      w.write("</rdf:li>\n");
    }
    w.write("    </rdf:Seq>\n");
    w.write("  </");
    w.write(tag);
    w.write(">\n");
  }

  private static void writeBag(Writer w, String tag, List<String> values) throws IOException {
    w.write("  <");
    w.write(tag);
    w.write(">\n");
    w.write("    <rdf:Bag>\n");
    for (String v : values) {
      w.write("      <rdf:li>");
      w.write(escapeXml(v));
      w.write("</rdf:li>\n");
    }
    w.write("    </rdf:Bag>\n");
    w.write("  </");
    w.write(tag);
    w.write(">\n");
  }

  private static void writePadding(Writer w) throws IOException {
    String padding =
        "                                                                                \n";
    for (int i = 0; i < 20; i++) {
      w.write(padding);
    }
  }

  private static void validateNcName(String name) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("XML name must not be empty");
    }
    if (!isValidNcNameStart(name.charAt(0))) {
      throw new IllegalArgumentException("Invalid XML name: '" + name + "'");
    }
    for (int i = 1; i < name.length(); i++) {
      if (!isValidNcNameChar(name.charAt(i))) {
        throw new IllegalArgumentException("Invalid XML name: '" + name + "'");
      }
    }
  }

  private static boolean isValidNcNameStart(char c) {
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_';
  }

  private static boolean isValidNcNameChar(char c) {
    return isValidNcNameStart(c) || (c >= '0' && c <= '9') || c == '-' || c == '.';
  }

  private static String escapeXml(String s) {
    if (s == null) return "";
    StringBuilder result = null;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      String replacement =
          switch (c) {
            case '&' -> "&amp;";
            case '<' -> "&lt;";
            case '>' -> "&gt;";
            case '"' -> "&quot;";
            default -> null;
          };
      if (replacement != null) {
        if (result == null) {
          result = new StringBuilder(s.length() + 16);
          result.append(s, 0, i);
        }
        result.append(replacement);
      } else if (result != null) {
        result.append(c);
      }
    }
    return result != null ? result.toString() : s;
  }
}
