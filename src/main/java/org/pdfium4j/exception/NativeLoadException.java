package org.pdfium4j.exception;

public class NativeLoadException extends PdfiumException {

    public NativeLoadException(String message) {
        super(message);
    }

    public NativeLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
