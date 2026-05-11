package org.grimmory.pdfium4j.internal;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.foreign.SymbolLookup;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.grimmory.pdfium4j.PdfiumLibrary;
import org.grimmory.pdfium4j.exception.NativeLoadException;

public final class NativeLoader {

  /**
   * System property: absolute filesystem path to a pdfium native library. When set, {@link
   * #ensureLoaded()} loads this path directly via {@code System.load} and skips both classpath
   * extraction and {@code System.loadLibrary("pdfium")} lookup.
   */
  public static final String PROP_LIBRARY_PATH = "pdfium4j.library.path";

  public static final String PROP_LIBRARY_PATH_ALLOW_UNSAFE = "pdfium4j.library.path.allowUnsafe";

  private static volatile boolean loaded = false;
  private static volatile Throwable loadError = null;
  private static volatile SymbolLookup shimLookup = null;

  public static SymbolLookup getShimLookup() {
    return shimLookup;
  }

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
        String msg =
            "Failed to load native library for platform "
                + detectPlatform()
                + " (JVM arch: "
                + detectArch()
                + ")";
        if (t.getMessage() != null) {
          msg += ": " + t.getMessage();
        }
        loadError = new NativeLoadException(msg, t);
        throw (NativeLoadException) loadError;
      }
    }
  }

  private static void performLoad() {
    if (tryLoadOverride()) {
      return;
    }
    try {
      tryLoadFromClasspath();
    } catch (NativeLoadException classpathMiss) {
      tryLoadSystemLibrary(classpathMiss);
    }
  }

  private static boolean tryLoadOverride() {
    String overridePath = System.getProperty(PROP_LIBRARY_PATH);
    if (overridePath == null || overridePath.isBlank()) {
      return false;
    }
    if (!Boolean.getBoolean(PROP_LIBRARY_PATH_ALLOW_UNSAFE)) {
      throw new NativeLoadException(
          "Refusing to load native override from -D"
              + PROP_LIBRARY_PATH
              + " without explicit opt-in. "
              + "Set -D"
              + PROP_LIBRARY_PATH_ALLOW_UNSAFE
              + "=true to acknowledge the security risk.");
    }
    Path validated = validateOverridePath(overridePath);
    try {
      System.load(validated.toString());
      return true;
    } catch (UnsatisfiedLinkError e) {
      throw new NativeLoadException(
          "PDFium override path set via -D"
              + PROP_LIBRARY_PATH
              + "="
              + validated
              + " but loading that file failed",
          e);
    }
  }

  private static Path validateOverridePath(String overridePath) {
    final Path raw;
    try {
      raw = Path.of(overridePath);
    } catch (InvalidPathException e) {
      throw new NativeLoadException("Invalid override path for -D" + PROP_LIBRARY_PATH, e);
    }
    if (!raw.isAbsolute()) {
      throw new NativeLoadException("Override path must be absolute: " + overridePath);
    }
    Path path = raw.normalize();
    if (!Files.exists(path)) {
      throw new NativeLoadException("Override path does not exist: " + path);
    }
    if (!Files.isRegularFile(path)) {
      throw new NativeLoadException("Override path is not a regular file: " + path);
    }
    if (!Files.isReadable(path)) {
      throw new NativeLoadException("Override path is not readable: " + path);
    }
    return path;
  }

  private static void tryLoadSystemLibrary(NativeLoadException classpathMiss) {
    try {
      System.loadLibrary("pdfium");
    } catch (UnsatisfiedLinkError e) {
      String platform = detectPlatform();
      String arch = detectArch();
      NativeLoadException ex =
          new NativeLoadException(
              "PDFium native library not found for platform "
                  + platform
                  + " (JVM arch: "
                  + arch
                  + "). "
                  + "Also tried System.loadLibrary(\"pdfium\") and failed. "
                  + "Set -D"
                  + PROP_LIBRARY_PATH
                  + "=/path/to/libpdfium.<ext> to load a system copy explicitly.",
              classpathMiss);
      ex.addSuppressed(e);
      throw ex;
    }
  }

  private static void tryLoadFromClasspath() {
    String platform = System.getProperty("pdfium4j.platform");
    if (platform == null || platform.isBlank()) {
      platform = detectPlatform();
    }
    String resourceBase = "/natives/" + platform + "/";
    String libName = nativeFilename();

    if (NativeLoader.class.getResource(resourceBase + libName) == null) {
      throw new NativeLoadException("No PDFium binary found on classpath for " + platform);
    }

    try {
      Path tmpDir = IoUtils.createTempDirectory("pdfium4j-");
      tmpDir.toFile().deleteOnExit();

      List<String> libs = readLibraryIndex(resourceBase + "native-libs.txt");
      for (String lib : libs) {
        if (!isAllowed(lib)) {
          InternalLogger.error("CRITICAL: Refusing to load untrusted native library: " + lib);
          throw new NativeLoadException(
              "Refusing to load untrusted native library '"
                  + lib
                  + "' from platform "
                  + platform
                  + ". If this is a required dependency, it must be added to the ALLOWED_LIBS set in NativeLoader.java.");
        }
        extractToDir(resourceBase + lib, tmpDir);
      }

      Path pdfiumPath = tmpDir.resolve(libName);
      if (!Files.exists(pdfiumPath)) {
        extractLib(resourceBase + libName, tmpDir, libName);
      }

      // Load main PDFium library first so dependencies (like the shim) can link against it
      loadLibraryFile(pdfiumPath);

      for (String lib : libs) {
        if (!lib.equals(libName)) {
          Path depPath = tmpDir.resolve(lib);
          if (Files.exists(depPath)) {
            var lookup = loadLibraryFile(depPath);
            if (lib.contains("shim")) {
              shimLookup = lookup;
            }
          }
        }
      }
    } catch (IOException e) {
      throw new NativeLoadException("Failed to extract native library", e);
    }
  }

  private static java.lang.foreign.SymbolLookup loadLibraryFile(Path path) {
    try {
      System.load(path.toAbsolutePath().toString());
      return java.lang.foreign.SymbolLookup.libraryLookup(path, java.lang.foreign.Arena.global());
    } catch (UnsatisfiedLinkError e) {
      throw new NativeLoadException(
          "Failed to load native library: " + path.getFileName() + ". Error: " + e.getMessage(), e);
    }
  }

  private static boolean isAllowed(String lib) {
    String lower = lib.toLowerCase(Locale.ROOT);
    return lower.contains("pdfium")
        || lower.contains("shim")
        || lower.contains("zlib")
        || lower.contains("libz")
        || lower.contains("jpeg");
  }

  private static List<String> readLibraryIndex(String resource) {
    List<String> result = new ArrayList<>(8);
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
    } catch (IOException e) {
      PdfiumLibrary.ignore(e);
    }
    return result;
  }

  private static void extractToDir(String resource, Path dir) throws IOException {
    String filename = resource.substring(resource.lastIndexOf('/') + 1);
    extractResource(resource, dir, filename);
  }

  private static void extractLib(String resource, Path dir, String filename) throws IOException {
    extractResource(resource, dir, filename);
  }

  @CheckForNull
  private static Path extractResource(String resource, Path dir, String filename)
      throws IOException {
    try (InputStream is = NativeLoader.class.getResourceAsStream(resource)) {
      if (is == null) {
        throw new NativeLoadException("Resource not found: " + resource);
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

  private static boolean isMusl() {
    return probeLibDirForMusl() || probeProcMapsForMusl();
  }

  @SuppressFBWarnings(
      value = "DMI_HARDCODED_ABSOLUTE_FILENAME",
      justification = "Musl detection requires probing standard linker locations")
  private static boolean probeLibDirForMusl() {
    try {
      Path ldMusl = Path.of("/lib");
      if (Files.exists(ldMusl)) {
        try (var files = Files.list(ldMusl)) {
          return files.anyMatch(p -> p.getFileName().toString().startsWith("ld-musl-"));
        }
      }
    } catch (IOException e) {
      PdfiumLibrary.ignore(e);
    }
    return false;
  }

  private static boolean probeProcMapsForMusl() {
    try {
      Path mapsPath = Path.of("/proc/self/maps");
      if (Files.exists(mapsPath)) {
        String maps = Files.readString(mapsPath);
        return maps.contains("musl");
      }
    } catch (IOException e) {
      PdfiumLibrary.ignore(e);
    }
    return false;
  }

  private static String detectArch() {
    String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
    if ("x86_64".equals(arch) || "amd64".equals(arch)) return "x64";
    if ("aarch64".equals(arch) || "arm64".equals(arch)) return "arm64";
    throw new NativeLoadException("Unsupported architecture: " + arch);
  }

  static String nativeFilename() {
    String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    if (os.contains("win")) return "pdfium" + ".dll";
    if (os.contains("mac")) return "lib" + "pdfium" + ".dylib";
    return "lib" + "pdfium" + ".so";
  }
}
