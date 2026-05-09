package org.grimmory.pdfium4j.model;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a logical element in a PDF structure tree (e.g., Heading, Table, Paragraph). Used for
 * accessibility and structured data extraction from Tagged PDFs.
 */
@SuppressFBWarnings("EI_EXPOSE_REP")
public record PdfStructureElement(
    String type,
    Optional<String> title,
    Optional<String> altText,
    Optional<String> actualText,
    Optional<String> lang,
    List<PdfStructureElement> children,
    List<Integer> markedContentIds,
    int attributeCount) {
  public PdfStructureElement {
    type = Objects.requireNonNull(type, "type");
    title = Objects.requireNonNull(title, "title");
    altText = Objects.requireNonNull(altText, "altText");
    actualText = Objects.requireNonNull(actualText, "actualText");
    lang = Objects.requireNonNull(lang, "lang");
    children = Objects.requireNonNull(children, "children");
    markedContentIds = Objects.requireNonNull(markedContentIds, "markedContentIds");
  }

  /**
   * Recursively find all elements of a specific type.
   *
   * @param type the type to search for (e.g. "H1", "Table")
   * @return a list of matching elements
   */
  public List<PdfStructureElement> findAll(String type) {
    List<PdfStructureElement> result = new ArrayList<>(16);
    findAll(type, result);
    return result;
  }

  private void findAll(String type, List<PdfStructureElement> accumulator) {
    if (this.type.equalsIgnoreCase(type)) {
      accumulator.add(this);
    }
    for (PdfStructureElement child : children) {
      child.findAll(type, accumulator);
    }
  }
}
