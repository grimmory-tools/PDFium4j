package org.grimmory.pdfium4j;

public class NativeSmokeTest {
  public static void main(String[] args) {
    try {
      System.out.println("Starting PDFium4j Smoke Test...");
      System.out.println("OS: " + System.getProperty("os.name"));
      System.out.println("Arch: " + System.getProperty("os.arch"));

      // This will trigger native loading and library initialization
      PdfiumLibrary.initialize();
      System.out.println("Successfully loaded and initialized PDFium4j!");

      System.out.println("--- SMOKE TEST PASSED ---");
    } catch (Throwable e) {
      System.err.println("--- SMOKE TEST FAILED ---");
      e.printStackTrace();
      System.exit(1);
    }
  }
}
