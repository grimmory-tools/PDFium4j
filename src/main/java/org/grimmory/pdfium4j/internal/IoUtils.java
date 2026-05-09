package org.grimmory.pdfium4j.internal;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
    FileAttribute<?>[] attrs =
        IS_POSIX ? new FileAttribute<?>[] {OWNER_ONLY_FILE} : Generators.emptyFileAttributes();
    Path path = Files.createTempFile(prefix, suffix, attrs);
    if (!IS_POSIX) {
      enforceOwnerOnlyPermissions(path, false);
    }
    return path;
  }

  /** Securely creates a temporary directory with owner-only permissions. */
  public static Path createTempDirectory(String prefix) throws IOException {
    FileAttribute<?>[] attrs =
        IS_POSIX ? new FileAttribute<?>[] {OWNER_ONLY_DIR} : Generators.emptyFileAttributes();
    Path path = Files.createTempDirectory(prefix, attrs);
    if (!IS_POSIX) {
      enforceOwnerOnlyPermissions(path, true);
    }
    return path;
  }

  @SuppressFBWarnings(
      value = "RV_RETURN_VALUE_IGNORED_BAD_PRACTICE",
      justification = "Best-effort on non-POSIX")
  private static void enforceOwnerOnlyPermissions(Path path, boolean executable) {
    File file = path.toFile();
    // On non-POSIX systems (Windows), these calls are best-effort.
    // We don't fail the build/load if they return false, as default temp dirs
    // are usually already restricted to the current user.
    file.setReadable(false, false);
    file.setReadable(true, true);
    file.setWritable(false, false);
    file.setWritable(true, true);
    file.setExecutable(false, false);
    if (executable) {
      file.setExecutable(true, true);
    }
  }
}
