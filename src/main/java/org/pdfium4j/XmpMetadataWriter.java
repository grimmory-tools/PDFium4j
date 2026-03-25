package org.pdfium4j;

import org.pdfium4j.model.XmpMetadata;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Serializes {@link XmpMetadata} to XMP XML packets suitable for embedding in PDF files.
 *
 * <p>Supports Dublin Core, PDF/A, Calibre, and user-registered custom namespaces.
 *
 * <pre>{@code
 * XmpMetadataWriter writer = new XmpMetadataWriter()
 *     .registerNamespace("booklore", "http://booklore.app/xmp-namespace");
 *
 * XmpMetadata meta = new XmpMetadata(
 *     Optional.of("My Book"), List.of("Author"), Optional.empty(), List.of("fiction"),
 *     Optional.empty(), Optional.of("en"), Optional.empty(), Optional.empty(),
 *     List.of(), Optional.empty(), Map.of(), Map.of("booklore:shelf", "favorites"));
 *
 * String xmpPacket = writer.write(meta);
 * doc.setXmpMetadata(meta, writer);
 * }</pre>
 */
public final class XmpMetadataWriter {

    private static final String NS_RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    private static final String NS_DC = "http://purl.org/dc/elements/1.1/";
    private static final String NS_PDFA_ID = "http://www.aiim.org/pdfa/ns/id/";
    private static final String NS_CALIBRE = "http://calibre-ebook.com/xmp-namespace";
    private static final String NS_XAP = "http://ns.adobe.com/xap/1.0/";

    private final Map<String, String> customNamespaces = new LinkedHashMap<>();

    /**
     * Register a custom namespace for XMP serialization.
     *
     * @param prefix the XML namespace prefix (e.g. "booklore")
     * @param uri    the namespace URI (e.g. "http://booklore.app/xmp-namespace")
     * @return this writer for chaining
     * @throws IllegalArgumentException if prefix conflicts with reserved prefixes
     */
    public XmpMetadataWriter registerNamespace(String prefix, String uri) {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(uri, "uri");
        if (prefix.isEmpty()) throw new IllegalArgumentException("Prefix must not be empty");
        if (Set.of("dc", "rdf", "pdfaid", "calibre", "xmp", "xap", "xml").contains(prefix.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Prefix '" + prefix + "' is reserved");
        }
        customNamespaces.put(prefix, uri);
        return this;
    }

    /**
     * Serialize an {@link XmpMetadata} record to a complete XMP packet string
     * with xpacket processing instructions.
     *
     * @param metadata the metadata to serialize
     * @return a complete XMP packet string ready for PDF embedding
     */
    public String write(XmpMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        StringBuilder sb = new StringBuilder();
        sb.append("<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n");
        sb.append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n");
        sb.append("<rdf:RDF xmlns:rdf=\"").append(NS_RDF).append("\">\n");

        writeDescriptions(sb, metadata);

        sb.append("</rdf:RDF>\n");
        sb.append("</x:xmpmeta>\n");
        writePadding(sb);
        sb.append("<?xpacket end=\"w\"?>");
        return sb.toString();
    }

    /**
     * Serialize metadata to raw bytes suitable for PDF XMP packet replacement.
     *
     * @param metadata the metadata to serialize
     * @return the XMP packet as UTF-8 bytes
     */
    public byte[] writeBytes(XmpMetadata metadata) {
        return write(metadata).getBytes(StandardCharsets.UTF_8);
    }

    private void writeDescriptions(StringBuilder sb, XmpMetadata metadata) {
        writeDublinCore(sb, metadata);
        writePdfAConformance(sb, metadata);
        writeCalibreFields(sb, metadata);
        writeCustomFields(sb, metadata);
    }

    private void writeDublinCore(StringBuilder sb, XmpMetadata metadata) {
        boolean hasDc = metadata.title().isPresent() || !metadata.creators().isEmpty()
                || metadata.description().isPresent() || !metadata.subjects().isEmpty()
                || metadata.publisher().isPresent() || metadata.language().isPresent()
                || metadata.date().isPresent() || metadata.rights().isPresent()
                || !metadata.identifiers().isEmpty();
        if (!hasDc) return;

        sb.append("<rdf:Description rdf:about=\"\"\n");
        sb.append("    xmlns:dc=\"").append(NS_DC).append("\">\n");

        metadata.title().ifPresent(t -> writeAlt(sb, "dc:title", t));
        if (!metadata.creators().isEmpty()) writeSeq(sb, "dc:creator", metadata.creators());
        metadata.description().ifPresent(d -> writeAlt(sb, "dc:description", d));
        if (!metadata.subjects().isEmpty()) writeBag(sb, "dc:subject", metadata.subjects());
        metadata.publisher().ifPresent(p -> writeBag(sb, "dc:publisher", List.of(p)));
        metadata.language().ifPresent(l -> writeBag(sb, "dc:language", List.of(l)));
        metadata.date().ifPresent(d -> writeSeq(sb, "dc:date", List.of(d)));
        metadata.rights().ifPresent(r -> writeAlt(sb, "dc:rights", r));
        if (!metadata.identifiers().isEmpty()) writeBag(sb, "dc:identifier", metadata.identifiers());

        sb.append("</rdf:Description>\n");
    }

    private void writePdfAConformance(StringBuilder sb, XmpMetadata metadata) {
        if (metadata.pdfaConformance().isEmpty()) return;
        String conf = metadata.pdfaConformance().get();
        if (conf.isBlank()) return;

        sb.append("<rdf:Description rdf:about=\"\"\n");
        sb.append("    xmlns:pdfaid=\"").append(NS_PDFA_ID).append("\">\n");

        if (conf.length() >= 2 && Character.isDigit(conf.charAt(0))) {
            sb.append("  <pdfaid:part>").append(conf.charAt(0)).append("</pdfaid:part>\n");
            sb.append("  <pdfaid:conformance>").append(Character.toUpperCase(conf.charAt(1))).append("</pdfaid:conformance>\n");
        } else {
            sb.append("  <pdfaid:conformance>").append(escapeXml(conf)).append("</pdfaid:conformance>\n");
        }

        sb.append("</rdf:Description>\n");
    }

    private void writeCalibreFields(StringBuilder sb, XmpMetadata metadata) {
        if (metadata.calibreFields().isEmpty()) return;

        sb.append("<rdf:Description rdf:about=\"\"\n");
        sb.append("    xmlns:calibre=\"").append(NS_CALIBRE).append("\">\n");

        for (Map.Entry<String, String> entry : metadata.calibreFields().entrySet()) {
            sb.append("  <calibre:").append(escapeXml(entry.getKey())).append(">");
            sb.append(escapeXml(entry.getValue()));
            sb.append("</calibre:").append(escapeXml(entry.getKey())).append(">\n");
        }

        sb.append("</rdf:Description>\n");
    }

    private void writeCustomFields(StringBuilder sb, XmpMetadata metadata) {
        if (metadata.customFields().isEmpty()) return;

        Map<String, Map<String, String>> grouped = new LinkedHashMap<>();
        Map<String, String> unprefixed = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : metadata.customFields().entrySet()) {
            String key = entry.getKey();
            int colonIdx = key.indexOf(':');
            if (colonIdx > 0) {
                String prefix = key.substring(0, colonIdx);
                String localName = key.substring(colonIdx + 1);
                String nsUri = customNamespaces.get(prefix);
                if (nsUri != null) {
                    grouped.computeIfAbsent(prefix, _ -> new LinkedHashMap<>())
                            .put(localName, entry.getValue());
                } else {
                    // Unknown prefix, treat as unprefixed
                    unprefixed.put(key, entry.getValue());
                }
            } else {
                unprefixed.put(key, entry.getValue());
            }
        }

        for (Map.Entry<String, Map<String, String>> nsEntry : grouped.entrySet()) {
            String prefix = nsEntry.getKey();
            String uri = customNamespaces.get(prefix);
            sb.append("<rdf:Description rdf:about=\"\"\n");
            sb.append("    xmlns:").append(prefix).append("=\"").append(escapeXml(uri)).append("\">\n");
            for (Map.Entry<String, String> field : nsEntry.getValue().entrySet()) {
                sb.append("  <").append(prefix).append(":").append(escapeXml(field.getKey())).append(">");
                sb.append(escapeXml(field.getValue()));
                sb.append("</").append(prefix).append(":").append(escapeXml(field.getKey())).append(">\n");
            }
            sb.append("</rdf:Description>\n");
        }

        if (!unprefixed.isEmpty()) {
            sb.append("<rdf:Description rdf:about=\"\"\n");
            sb.append("    xmlns:xmp=\"").append(NS_XAP).append("\">\n");
            for (Map.Entry<String, String> entry : unprefixed.entrySet()) {
                sb.append("  <xmp:").append(escapeXml(entry.getKey())).append(">");
                sb.append(escapeXml(entry.getValue()));
                sb.append("</xmp:").append(escapeXml(entry.getKey())).append(">\n");
            }
            sb.append("</rdf:Description>\n");
        }
    }

    private static void writeAlt(StringBuilder sb, String tag, String value) {
        sb.append("  <").append(tag).append(">\n");
        sb.append("    <rdf:Alt>\n");
        sb.append("      <rdf:li xml:lang=\"x-default\">").append(escapeXml(value)).append("</rdf:li>\n");
        sb.append("    </rdf:Alt>\n");
        sb.append("  </").append(tag).append(">\n");
    }

    private static void writeSeq(StringBuilder sb, String tag, List<String> values) {
        sb.append("  <").append(tag).append(">\n");
        sb.append("    <rdf:Seq>\n");
        for (String v : values) {
            sb.append("      <rdf:li>").append(escapeXml(v)).append("</rdf:li>\n");
        }
        sb.append("    </rdf:Seq>\n");
        sb.append("  </").append(tag).append(">\n");
    }

    private static void writeBag(StringBuilder sb, String tag, List<String> values) {
        sb.append("  <").append(tag).append(">\n");
        sb.append("    <rdf:Bag>\n");
        for (String v : values) {
            sb.append("      <rdf:li>").append(escapeXml(v)).append("</rdf:li>\n");
        }
        sb.append("    </rdf:Bag>\n");
        sb.append("  </").append(tag).append(">\n");
    }

    private static void writePadding(StringBuilder sb) {
        // XMP spec recommends padding to allow in-place updates
        for (int i = 0; i < 20; i++) {
            sb.append("                                                                                \n");
        }
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        StringBuilder result = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String replacement = switch (c) {
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
