package org.grimmory.pdfium4j.model;

import java.util.Optional;

/** Represents a file attachment embedded in a PDF document. */
public record PdfAttachment(
    int index,
    String name,
    long size,
    Optional<String> description,
    Optional<String> creationDate,
    Optional<String> modificationDate) {}
