package org.grimmory.pdfium4j;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.grimmory.pdfium4j.exception.PdfCorruptException;
import org.grimmory.pdfium4j.exception.PdfPasswordException;
import org.grimmory.pdfium4j.exception.PdfUnsupportedSecurityException;
import org.grimmory.pdfium4j.exception.PdfiumException;
import org.grimmory.pdfium4j.internal.ViewBindings;
import org.junit.jupiter.api.Test;

class PdfDocumentErrorMappingTest {

  @Test
  void mapOpenError_securityMapsToUnsupportedSecurityException() {
    PdfiumException exception = PdfDocument.mapOpenError("Failed to open: /tmp/test.pdf", ViewBindings.FPDF_ERR_SECURITY);

    assertInstanceOf(PdfUnsupportedSecurityException.class, exception);
    assertTrue(exception.getMessage().contains("unsupported security handler"));
  }

  @Test
  void mapOpenError_passwordAndFormatMappingsRemainUnchanged() {
    PdfiumException password = PdfDocument.mapOpenError("ctx", ViewBindings.FPDF_ERR_PASSWORD);
    PdfiumException format = PdfDocument.mapOpenError("ctx", ViewBindings.FPDF_ERR_FORMAT);

    assertInstanceOf(PdfPasswordException.class, password);
    assertInstanceOf(PdfCorruptException.class, format);
  }
}
