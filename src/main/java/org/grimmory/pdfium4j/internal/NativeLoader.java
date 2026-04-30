package org.grimmory.pdfium4j.internal;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
import org.grimmory.pdfium4j.exception.NativeLoadException;

public final class NativeLoader {

  /**
   * System property: absolute filesystem path to a pdfium native library. When set, {@link
   * #ensureLoaded()} loads this path directly via {@code System.load} and skips both classpath
   * extraction and {@code System.loadLibrary("pdfium")} lookup.
   */
  public static final String PROP_LIBRARY_PATH = "pdfium4j.library.path";

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
        performLoad();
        loaded = true;
      } catch (NativeLoadException e) {
        loadError = e;
        throw e;
      } catch (Throwable t) {
        loadError = t;
        throw new NativeLoadException("Failed to load native library", t);
      }
    }
  }

  private static void performLoad() {
    try {
      String overridePath = System.getProperty(PROP_LIBRARY_PATH);
      if (overridePath != null && !overridePath.isBlank()) {
        try {
          System.load(overridePath);
          return;
        } catch (UnsatisfiedLinkError e) {
          throw new NativeLoadException(
              "PDFium override path set via -D"
                  + PROP_LIBRARY_PATH
                  + "="
                  + overridePath
                  + " but loading that file failed",
              e);
        }
      }

      tryLoadFromClasspath();
    } catch (NativeLoadException classpathMiss) {
      try {
        System.loadLibrary("pdfium");
      } catch (UnsatisfiedLinkError e) {
        NativeLoadException ex =
            new NativeLoadException(
                "PDFium native library not found for "
                    + detectPlatform()
                    + ". Also tried System.loadLibrary(\"pdfium\") and failed. "
                    + "Set -D"
                    + PROP_LIBRARY_PATH
                    + "=/path/to/libpdfium.<ext> to load a system copy explicitly.",
                classpathMiss);
        ex.addSuppressed(e);
        throw ex;
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
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          String trimmed = line.trim();
          if (!trimmed.isEmpty() && trimmed.charAt(0) != '#') {
            result.add(trimmed);
          }
        }
      }
    } catch (IOException ignored) {
    }
    return result;
  }

  private static void extractToDir(String resource, Path dir) throws IOException {
    String filename = resource.substring(resource.lastIndexOf('/') + 1);
    extractResource(resource, dir, filename, true);
  }

  private static Path extractLib(String resource, Path dir, String filename) throws IOException {
    return extractResource(resource, dir, filename, true);
  }

  @CheckForNull
  private static Path extractResource(String resource, Path dir, String filename, boolean required)
      throws IOException {
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
      osKey = isMusl() ? "linux-musl" : "linux";
    } else {
      throw new NativeLoadException("Unsupported operating system: " + os);
    }
    return osKey + "-" + detectArch();
  }

  @SuppressFBWarnings(
      value = "DMI_HARDCODED_ABSOLUTE_FILENAME",
      justification = "Musl detection requires probing standard linker locations")
  private static boolean isMusl() {
    // Check for musl dynamic linker
    try {
      java.nio.file.Path ldMusl = java.nio.file.Path.of("/lib");
      if (java.nio.file.Files.exists(ldMusl)) {
        try (var files = java.nio.file.Files.list(ldMusl)) {
          if (files.anyMatch(p -> p.getFileName().toString().startsWith("ld-musl-"))) {
            return true;
          }
        }
      }
    } catch (Exception ignored) {
    }
    // Fallback: check /proc/self/maps for musl
    try {
      String maps = java.nio.file.Files.readString(java.nio.file.Path.of("/proc/self/maps"));
      if (maps.contains("musl")) return true;
    } catch (Exception ignored) {
    }
    return false;
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
