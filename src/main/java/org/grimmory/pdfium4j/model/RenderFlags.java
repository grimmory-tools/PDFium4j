package org.grimmory.pdfium4j.model;

import org.grimmory.pdfium4j.internal.ViewBindings;

/**
 * Rendering flags for PDF page rasterization.
 *
 * <p>Use the builder to combine flags:
 *
 * <pre>{@code
 * RenderFlags flags = RenderFlags.builder()
 *     .annotations(true)
 *     .lcdText(true)
 *     .antiAlias(true)
 *     .build();
 * }</pre>
 */
public record RenderFlags(int value) {

  /** Default flags: annotations rendered, anti-aliasing on, RGBA byte order. */
  public static final RenderFlags DEFAULT = builder().build();

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private boolean annotations = true;
    private static final boolean PRINTING = false;
    private boolean antiAlias = true;

    private Builder() {}

    public Builder annotations(boolean v) {
      this.annotations = v;
      return this;
    }

    public Builder antiAlias(boolean v) {
      this.antiAlias = v;
      return this;
    }

    public RenderFlags build() {
      int flags = ViewBindings.FPDF_REVERSE_BYTE_ORDER; // always RGBA for Java
      if (annotations) flags |= ViewBindings.FPDF_ANNOT;

      if (PRINTING) flags |= ViewBindings.FPDF_PRINTING;
      if (!antiAlias) {
        flags |= ViewBindings.FPDF_RENDER_NO_SMOOTHTEXT;
        flags |= ViewBindings.FPDF_RENDER_NO_SMOOTHIMAGE;
        flags |= ViewBindings.FPDF_RENDER_NO_SMOOTHPATH;
      }
      return new RenderFlags(flags);
    }
  }
}
