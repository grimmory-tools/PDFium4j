package org.grimmory.pdfium4j.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Represents a digital signature in a PDF document. */
public record PdfSignature(
    int index,
    Optional<String> reason,
    Optional<Instant> time,
    Optional<String> subFilter,
    long contentsSize,
    long byteRangeSize) {
  public PdfSignature {
    reason = Objects.requireNonNull(reason, "reason");
    time = Objects.requireNonNull(time, "time");
    subFilter = Objects.requireNonNull(subFilter, "subFilter");
  }
}
