package org.grimmory.pdfium4j;

public class NativeSmokeTest {
    static void main() {
        try {
            System.out.println("Starting PDFium4j Smoke Test...");
            System.out.println("OS: " + System.getProperty("os.name"));
            System.out.println("Arch: " + System.getProperty("os.arch"));
            
            // This will trigger native loading
            int version = Pdfium4j.getLibraryVersion();
            System.out.println("Successfully loaded PDFium4j!");
            System.out.println("PDFium Version: " + version);
            
            System.out.println("--- SMOKE TEST PASSED ---");
        } catch (Throwable e) {
            System.err.println("--- SMOKE TEST FAILED ---");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
