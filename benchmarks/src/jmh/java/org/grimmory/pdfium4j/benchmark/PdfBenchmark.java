package org.grimmory.pdfium4j.benchmark;
 
import org.grimmory.pdfium4j.PdfDocument;
import org.grimmory.pdfium4j.PdfPage;
import org.grimmory.pdfium4j.model.MetadataTag;
import org.openjdk.jmh.annotations.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
 
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class PdfBenchmark {

    private PdfDocument document;
 
    @Setup
    public void setup() throws IOException {
        Path testPdf = Paths.get("../src/test/resources/minimal.pdf");
        document = PdfDocument.open(testPdf);
        document.indexText(); // Pre-index for search benchmarks
    }
 
    @TearDown
    public void tearDown() {
        if (document != null) {
            document.close();
        }
    }
 
    @Benchmark
    public Object testGetPageCount() {
        return document.pageCount();
    }
 
    @Benchmark
    public Object testMetadataRead() {
        return document.metadata(MetadataTag.TITLE);
    }
 
    @Benchmark
    public Object testPageLoadAndText() {
        try (PdfPage page = document.page(0)) {
            return page.extractText();
        }
    }
 
    @Benchmark
    public Object testSearchIndexed() {
        return document.search("PDF");
    }
}
