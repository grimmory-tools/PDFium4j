package org.grimmory.pdfium4j.internal;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/** Internal I/O utilities for secure file handling. */
public final class IoUtils {

  private static final boolean IS_POSIX =
      FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

  private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY_FILE =
      IS_POSIX
          ? PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))
          : null;

  private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY_DIR =
      IS_POSIX
          ? PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
          : null;

  private IoUtils() {}

  /** Securely creates a temporary file with owner-only permissions. */
  public static Path createTempFile(String prefix, String suffix) throws IOException {
    if (IS_POSIX) {
      return Files.createTempFile(prefix, suffix, OWNER_ONLY_FILE);
    } else {
      Path path = Files.createTempFile(prefix, suffix);
      enforceOwnerOnlyPermissions(path, false);
      return path;
    }
  }

  /** Securely creates a temporary directory with owner-only permissions. */
  public static Path createTempDirectory(String prefix) throws IOException {
    if (IS_POSIX) {
      return Files.createTempDirectory(prefix, OWNER_ONLY_DIR);
    } else {
      Path path = Files.createTempDirectory(prefix);
      enforceOwnerOnlyPermissions(path, true);
      return path;
    }
  }

  private static void enforceOwnerOnlyPermissions(Path path, boolean executable)
      throws IOException {
    File file = path.toFile();
    boolean ok = true;
    ok &= file.setReadable(false, false);
    ok &= file.setReadable(true, true);
    ok &= file.setWritable(false, false);
    ok &= file.setWritable(true, true);
    ok &= file.setExecutable(false, false);
    if (executable) {
      ok &= file.setExecutable(true, true);
    }
    if (!ok) {
      throw new IOException("Failed to apply owner-only permissions to temp path: " + path);
    }
  }
}
