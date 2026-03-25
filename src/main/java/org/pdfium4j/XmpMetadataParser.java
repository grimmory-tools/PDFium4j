package org.pdfium4j;

import org.pdfium4j.model.XmpMetadata;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.*;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Parses XMP metadata XML into structured {@link XmpMetadata}.
 * Handles Dublin Core, PDF/A, and Calibre namespaces.
 * Ported from Grimmory's PdfMetadataExtractor patterns with secure XML parsing.
 */
public final class XmpMetadataParser {

    private static final int MAX_XMP_BYTES = 10 * 1024 * 1024; // 10 MB

    // XMP/RDF namespace URIs
    private static final String NS_DC = "http://purl.org/dc/elements/1.1/";
    private static final String NS_PDFA_ID = "http://www.aiim.org/pdfa/ns/id/";
    private static final String NS_CALIBRE = "http://calibre-ebook.com/xmp-namespace";
    private static final String NS_XMP = "http://ns.adobe.com/xap/1.0/";
    private static final String NS_RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";

    private XmpMetadataParser() {}

    /**
     * Parse XMP metadata from raw XML bytes.
     *
     * @param xmpBytes the raw XMP XML bytes (as returned by PdfDocument.xmpMetadata())
     * @return parsed metadata, or empty metadata if parsing fails
     */
    public static XmpMetadata parse(byte[] xmpBytes) {
        if (xmpBytes == null || xmpBytes.length == 0) {
            return XmpMetadata.empty();
        }
        if (xmpBytes.length > MAX_XMP_BYTES) {
            return XmpMetadata.empty();
        }
        // Let the XML parser detect encoding from byte stream (BOM / XML declaration)
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new ByteArrayInputStream(xmpBytes)));
            return extractFromDocument(doc);
        } catch (Exception _) {
            // Corrupt producers may embed null bytes, stray BOMs, or garbage before the declaration
            return parse(new String(xmpBytes, StandardCharsets.UTF_8));
        }
    }

    /**
     * Parse XMP metadata from an XML string.
     *
     * @param xmpXml the XMP XML string
     * @return parsed metadata, or empty metadata if parsing fails
     */
    public static XmpMetadata parse(String xmpXml) {
        if (xmpXml == null || xmpXml.isBlank()) {
            return XmpMetadata.empty();
        }

        String xml = sanitizeXmpString(xmpXml);

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // Secure parsing: disable external entities (XXE protection)
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            return extractFromDocument(doc);
        } catch (Exception e) {
            return XmpMetadata.empty();
        }
    }

    /**
     * Sanitizes XMP XML strings to handle known vendor corruption patterns.
     * Handles: BOM prefixes, Nitro PDF UTF-16 BOM in text elements,
     * null bytes, and XML declaration encoding mismatches.
     */
    private static String sanitizeXmpString(String xmp) {
        String s = xmp;
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') {
            s = s.substring(1);
        }

        // Some corrupt producers embed null bytes
        if (s.indexOf('\0') >= 0) {
            s = s.replace("\0", "");
        }

        // Some tools embed garbage before the XML declaration
        int xmlDeclStart = s.indexOf("<?xml");
        if (xmlDeclStart > 0) {
            s = s.substring(xmlDeclStart);
        }

        // Nitro PDF embeds stray BOMs inside element text
        String replace = s.replace("\uFEFF", "");

        return replace;
    }

    /**
     * Parse XMP metadata directly from an open PdfDocument.
     * Convenience method that combines xmpMetadata() + parse().
     *
     * @param document the open PDF document
     * @return parsed metadata
     */
    public static XmpMetadata parseFrom(PdfDocument document) {
        return parse(document.xmpMetadata());
    }

    private static XmpMetadata extractFromDocument(Document doc) {
        // Dublin Core fields
        Optional<String> title = getFirstDcText(doc, "title");
        List<String> creators = getDcList(doc, "creator");
        Optional<String> description = getFirstDcText(doc, "description");
        List<String> subjects = getDcList(doc, "subject");
        Optional<String> publisher = getFirstDcText(doc, "publisher");
        Optional<String> language = getFirstDcText(doc, "language");
        Optional<String> date = getFirstDcText(doc, "date");
        Optional<String> rights = getFirstDcText(doc, "rights");
        List<String> identifiers = getDcList(doc, "identifier");

        // PDF/A conformance
        Optional<String> pdfaConformance = extractPdfAConformance(doc);

        // Calibre fields
        Map<String, String> calibreFields = extractCalibreFields(doc);

        // Custom fields from xmp: namespace
        Map<String, String> customFields = extractXmpFields(doc);

        return new XmpMetadata(title, creators, description, subjects,
                publisher, language, date, rights, identifiers,
                pdfaConformance, calibreFields, customFields);
    }

    /**
     * Get the first text value from a Dublin Core element.
     * Handles both simple text and RDF Alt/Bag/Seq containers.
     */
    private static Optional<String> getFirstDcText(Document doc, String localName) {
        NodeList nodes = doc.getElementsByTagNameNS(NS_DC, localName);
        if (nodes.getLength() == 0) return Optional.empty();

        Element elem = (Element) nodes.item(0);
        String text = getTextFromRdfContainer(elem);
        if (text == null || text.isBlank()) {
            text = elem.getTextContent();
        }
        return (text != null && !text.isBlank()) ? Optional.of(text.trim()) : Optional.empty();
    }

    /**
     * Get all values from a Dublin Core element (handles RDF Bag/Seq).
     */
    private static List<String> getDcList(Document doc, String localName) {
        NodeList nodes = doc.getElementsByTagNameNS(NS_DC, localName);
        if (nodes.getLength() == 0) return List.of();

        List<String> values = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element elem = (Element) nodes.item(i);
            List<String> rdfValues = getListFromRdfContainer(elem);
            if (!rdfValues.isEmpty()) {
                values.addAll(rdfValues);
            } else {
                String text = elem.getTextContent();
                if (text != null && !text.isBlank()) {
                    values.add(text.trim());
                }
            }
        }
        return values;
    }

    /**
     * Extract text from RDF Alt container (used for localized values like title).
     */
    private static String getTextFromRdfContainer(Element parent) {
        NodeList lis = parent.getElementsByTagNameNS(NS_RDF, "li");
        if (lis.getLength() == 0) {
            return null;
        }
        for (int i = 0; i < lis.getLength(); i++) {
            Element li = (Element) lis.item(i);
            String lang = li.getAttributeNS("http://www.w3.org/XML/1998/namespace", "lang");
            if ("x-default".equals(lang)) {
                return li.getTextContent();
            }
        }
        return lis.item(0).getTextContent();
    }

    /**
     * Extract all list values from RDF Bag/Seq container.
     */
    private static List<String> getListFromRdfContainer(Element parent) {
        List<String> values = new ArrayList<>();
        NodeList lis = parent.getElementsByTagNameNS(NS_RDF, "li");
        for (int i = 0; i < lis.getLength(); i++) {
            String text = lis.item(i).getTextContent();
            if (text != null && !text.isBlank()) {
                values.add(text.trim());
            }
        }
        return values;
    }

    /**
     * Extract PDF/A conformance level from pdfaid:part and pdfaid:conformance.
     */
    private static Optional<String> extractPdfAConformance(Document doc) {
        String part = getElementText(doc, NS_PDFA_ID, "part");
        String conformance = getElementText(doc, NS_PDFA_ID, "conformance");
        if (part != null && !part.isBlank()) {
            String level = part + (conformance != null ? conformance.toLowerCase(Locale.ROOT) : "");
            return Optional.of(level);
        }
        return Optional.empty();
    }

    /**
     * Extract Calibre-specific metadata fields.
     */
    private static Map<String, String> extractCalibreFields(Document doc) {
        Map<String, String> fields = new LinkedHashMap<>();
        NodeList descNodes = doc.getElementsByTagNameNS(NS_RDF, "Description");
        for (int i = 0; i < descNodes.getLength(); i++) {
            Element desc = (Element) descNodes.item(i);
            NamedNodeMap attrs = desc.getAttributes();
            for (int j = 0; j < attrs.getLength(); j++) {
                Node attr = attrs.item(j);
                if (NS_CALIBRE.equals(attr.getNamespaceURI())) {
                    fields.put(attr.getLocalName(), attr.getNodeValue());
                }
            }
            NodeList children = desc.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child.getNodeType() == Node.ELEMENT_NODE
                        && NS_CALIBRE.equals(child.getNamespaceURI())) {
                    Element childElem = (Element) child;
                    List<String> listValues = getListFromRdfContainer(childElem);
                    if (!listValues.isEmpty()) {
                        fields.put(child.getLocalName(), String.join(",", listValues));
                    } else {
                        String text = child.getTextContent();
                        if (text != null && !text.isBlank()) {
                            fields.put(child.getLocalName(), text.trim());
                        }
                    }
                }
            }
        }
        return fields;
    }

    /**
     * Extract standard XMP fields.
     */
    private static Map<String, String> extractXmpFields(Document doc) {
        Map<String, String> fields = new LinkedHashMap<>();
        String[] xmpFields = {"CreateDate", "ModifyDate", "CreatorTool", "MetadataDate"};
        for (String field : xmpFields) {
            String value = getElementText(doc, NS_XMP, field);
            if (value != null && !value.isBlank()) {
                fields.put(field, value.trim());
            }
        }
        return fields;
    }

    private static String getElementText(Document doc, String namespaceURI, String localName) {
        NodeList nodes = doc.getElementsByTagNameNS(namespaceURI, localName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }
}
