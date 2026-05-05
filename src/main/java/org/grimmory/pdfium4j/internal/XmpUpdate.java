package org.grimmory.pdfium4j.internal;

import java.util.Objects;
import org.grimmory.pdfium4j.model.XmpMetadata;

/** Internal representation of an XMP update. */
public sealed interface XmpUpdate {
  record Raw(String xmp) implements XmpUpdate {
    public Raw {
      Objects.requireNonNull(xmp, "xmp");
    }
  }

  record Structured(XmpMetadata metadata) implements XmpUpdate {
    public Structured {
      Objects.requireNonNull(metadata, "metadata");
    }
  }
}
