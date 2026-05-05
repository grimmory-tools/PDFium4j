package org.grimmory.pdfium4j.internal;

import org.grimmory.pdfium4j.model.XmpMetadata;

/** Internal representation of an XMP update. */
public sealed interface XmpUpdate {
  record Raw(String xmp) implements XmpUpdate {}

  record Structured(XmpMetadata metadata) implements XmpUpdate {}
}
