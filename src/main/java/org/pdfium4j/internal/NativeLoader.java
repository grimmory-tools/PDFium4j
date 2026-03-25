package org.pdfium4j.internal;

import org.pdfium4j.exception.NativeLoadException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NativeLoader {

    private static volatile boolean loaded = false;
    private static volatile Throwable loadError = null;

    private NativeLoader() {}

    public static void ensureLoaded() {
        if (loaded) return;
        if (loadError != null) {
            throw new NativeLoadException("Native library failed to load previously", loadError);
        }
        synchronized (NativeLoader.class) {
            if (loaded) return;
            if (loadError != null) {
                throw new NativeLoadException("Native library failed to load previously", loadError);
            }
            try {
                tryLoadFromClasspath();
                loaded = true;
            } catch (NativeLoadException classpathMiss) {
                try {
                    System.loadLibrary("pdfium");
                    loaded = true;
                } catch (UnsatisfiedLinkError e) {
                    NativeLoadException ex = new NativeLoadException(
                            "PDFium native library not found for " + detectPlatform()
                                    + ". Also tried System.loadLibrary(\"pdfium\") and failed.",
                            classpathMiss);
                    ex.addSuppressed(e);
                    loadError = ex;
                    throw ex;
                }
            } catch (Throwable t) {
                loadError = t;
                throw (t instanceof NativeLoadException nle) ? nle
                        : new NativeLoadException("Failed to load native library", t);
            }
        }
    }

    private static void tryLoadFromClasspath() {
        String platform = detectPlatform();
        String resourceBase = "/natives/" + platform + "/";
        String libName = nativeFilename("pdfium");

        if (NativeLoader.class.getResource(resourceBase + libName) == null) {
            throw new NativeLoadException("No PDFium binary found on classpath for " + platform);
        }

        try {
            Path tmpDir = Files.createTempDirectory("pdfium4j-");
            tmpDir.toFile().deleteOnExit();

            List<String> libs = readLibraryIndex(resourceBase + "native-libs.txt");
            for (String lib : libs) {
                extractToDir(resourceBase + lib, tmpDir);
            }

            Path pdfiumPath = tmpDir.resolve(libName);
            if (!Files.exists(pdfiumPath)) {
                extractLib(resourceBase + libName, tmpDir, libName);
            }

            // Dependencies must be loaded before libpdfium
            for (String lib : libs) {
                if (!lib.equals(libName)) {
                    Path depPath = tmpDir.resolve(lib);
                    if (Files.exists(depPath)) {
                        System.load(depPath.toAbsolutePath().toString());
                    }
                }
            }

            System.load(pdfiumPath.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new NativeLoadException("Failed to extract native library", e);
        }
    }

    private static List<String> readLibraryIndex(String resource) {
        List<String> result = new ArrayList<>();
        try (InputStream is = NativeLoader.class.getResourceAsStream(resource)) {
            if (is == null) return result;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && trimmed.charAt(0) != '#') {
                        result.add(trimmed);
                    }
                }
            }
        } catch (IOException ignored) {}
        return result;
    }

    private static void extractToDir(String resource, Path dir) throws IOException {
        String filename = resource.substring(resource.lastIndexOf('/') + 1);
        extractResource(resource, dir, filename, true);
    }

    private static Path extractLib(String resource, Path dir, String filename) throws IOException {
        return extractResource(resource, dir, filename, true);
    }

    private static Path extractResource(String resource, Path dir, String filename,
            boolean required) throws IOException {
        try (InputStream is = NativeLoader.class.getResourceAsStream(resource)) {
            if (is == null) {
                if (required) {
                    throw new NativeLoadException("Resource not found: " + resource);
                }
                return null;
            }
            Path target = dir.resolve(filename);
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            target.toFile().deleteOnExit();
            return target;
        }
    }

    static String detectPlatform() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String osKey;
        if (os.contains("win")) {
            osKey = "windows";
        } else if (os.contains("mac")) {
            osKey = "darwin";
        } else if (os.contains("nux")) {
            osKey = "linux";
        } else {
            throw new NativeLoadException("Unsupported operating system: " + os);
        }
        return osKey + "-" + detectArch();
    }

    private static String detectArch() {
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        if ("x86_64".equals(arch) || "amd64".equals(arch)) return "x64";
        if ("aarch64".equals(arch) || "arm64".equals(arch)) return "arm64";
        throw new NativeLoadException("Unsupported architecture: " + arch);
    }

    static String nativeFilename(String lib) {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return lib + ".dll";
        if (os.contains("mac")) return "lib" + lib + ".dylib";
        return "lib" + lib + ".so";
    }
}
