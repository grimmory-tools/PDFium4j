package org.grimmory.pdfium4j.internal;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Internal helper for fallback metadata extraction from raw PDF files. */
public final class PdfDocumentFallbackMeta {

  private static final byte[] INFO_KEY = "/Info".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] DICT_START = "<<".getBytes(StandardCharsets.ISO_8859_1);
  private static final long FALLBACK_TAIL_SCAN_BYTES = 16384;

  private PdfDocumentFallbackMeta() {}

  public static Map<String, String> buildFallbackMeta(Path sourcePath, byte[] sourceBytes) {
    if (sourceBytes != null) {
      return parseAllInfoMetadata(MemorySegment.ofArray(sourceBytes));
    }
    if (sourcePath != null) {
      try (FileChannel fc = FileChannel.open(sourcePath, StandardOpenOption.READ);
          Arena arena = Arena.ofConfined()) {
        long fileSize = fc.size();
        if (fileSize <= 0) return Map.of();
        long tailSize = Math.min(fileSize, FALLBACK_TAIL_SCAN_BYTES);
        long tailStart = fileSize - tailSize;
        MemorySegment tail = fc.map(FileChannel.MapMode.READ_ONLY, tailStart, tailSize, arena);
        Map<String, String> result = parseAllInfoMetadata(tail);
        if (result.isEmpty() && tailStart > 0) {
          MemorySegment full = fc.map(FileChannel.MapMode.READ_ONLY, 0, fileSize, arena);
          result = parseAllInfoMetadata(full);
        }
        return result;
      } catch (IOException _) {
        return Map.of();
      }
    }
    return Map.of();
  }

  private static Map<String, String> parseAllInfoMetadata(MemorySegment pdf) {
    long size = pdf.byteSize();
    long infoPos = findLastInfoKey(pdf);
    if (infoPos < 0) return Map.of();

    long valStart = skipWs(pdf, infoPos + INFO_KEY.length, size);
    long n1End = scanDigits(pdf, valStart, size);
    if (n1End <= valStart) return Map.of();
    long n2Start = skipWs(pdf, n1End, size);
    long n2End = scanDigits(pdf, n2Start, size);
    if (n2End <= n2Start) return Map.of();

    int objNum = parsePositiveInt(pdf, valStart, n1End);
    int genNum = parsePositiveInt(pdf, n2Start, n2End);

    return extractDictAndParseInfo(pdf, objNum, genNum);
  }

  private static long findLastInfoKey(MemorySegment pdf) {
    long size = pdf.byteSize();
    long start = 0;
    long pos = size;
    while (pos > start) {
      pos = lastIndexOf(pdf, INFO_KEY, pos);
      if (pos < 0) break;
      long valStart = skipWs(pdf, pos + INFO_KEY.length, size);
      long n1End = scanDigits(pdf, valStart, size);
      if (n1End > valStart) {
        long n2Start = skipWs(pdf, n1End, size);
        long n2End = scanDigits(pdf, n2Start, size);
        if (n2End > n2Start) {
          long rPos = skipWs(pdf, n2End, size);
          if (rPos < size && pdf.get(JAVA_BYTE, rPos) == 'R') return pos;
        }
      }
      pos--;
    }
    return -1;
  }

  private static Map<String, String> extractDictAndParseInfo(
      MemorySegment pdf, int objNum, int genNum) {
    long pos = findObjectHeader(pdf, objNum, genNum);
    if (pos < 0) return Map.of();
    long dictStart = indexOf(pdf, DICT_START, pos);
    if (dictStart < 0) return Map.of();
    long dictEnd = findDictionaryEnd(pdf, dictStart);
    if (dictEnd < 0) return Map.of();

    Map<String, String> result = LinkedHashMap.newLinkedHashMap(16);
    long scanPos = dictStart + DICT_START.length;
    while (scanPos < dictEnd - 1) {
      scanPos = skipWs(pdf, scanPos, dictEnd);
      if (scanPos < dictEnd - 1) {
        if (pdf.get(JAVA_BYTE, scanPos) == '/') {
          scanPos = parseInfoDictEntry(pdf, scanPos, dictEnd, result);
        } else {
          scanPos++;
        }
      }
    }
    return Collections.unmodifiableMap(result);
  }

  private static long parseInfoDictEntry(
      MemorySegment pdf, long scanPos, long dictEnd, Map<String, String> result) {
    long keyStart = scanPos + 1;
    long keyEnd = scanPosUntilDelimiter(pdf, keyStart, dictEnd);
    if (keyEnd > keyStart) {
      String key =
          new String(
              pdf.asSlice(keyStart, keyEnd - keyStart).toArray(JAVA_BYTE),
              StandardCharsets.ISO_8859_1);
      long valPos = skipWs(pdf, keyEnd, dictEnd);
      if (valPos < dictEnd) {
        return parseAndPutValue(pdf, key, valPos, dictEnd, result);
      }
      return valPos;
    }
    return scanPos + 1;
  }

  private static long scanPosUntilDelimiter(MemorySegment pdf, long start, long limit) {
    long end = start;
    while (end < limit) {
      final boolean isDelimiter = isPdfDelimiter(pdf.get(JAVA_BYTE, end));
      if (isDelimiter) break;
      end++;
    }
    return end;
  }

  private static long parseAndPutValue(
      MemorySegment pdf, String key, long pos, long limit, Map<String, String> result) {
    byte valType = pdf.get(JAVA_BYTE, pos);
    if (valType == '(') {
      return parseLiteralString(pdf, key, pos + 1, limit, result);
    } else if (valType == '<') {
      if (pos + 1 < limit && pdf.get(JAVA_BYTE, pos + 1) == '<') {
        // Skip nested dictionary
        return findDictionaryEnd(pdf, pos);
      }
      return parseHexString(pdf, key, pos + 1, result);
    }
    return pos + 1;
  }

  private static long parseLiteralString(
      MemorySegment pdf, String key, long start, long limit, Map<String, String> result) {
    long valEnd = findClosingParen(pdf, start, limit);
    if (valEnd >= 0) {
      byte[] raw = pdf.asSlice(start, valEnd - start).toArray(JAVA_BYTE);
      result.put(key, unescape(raw));
      return valEnd + 1;
    }
    return start;
  }

  private static String unescape(byte[] raw) {
    ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length);
    for (int i = 0; i < raw.length; i++) {
      byte b = raw[i];
      if (b == '\\' && i + 1 < raw.length) {
        byte next = raw[++i];
        switch (next) {
          case 'n' -> out.write('\n');
          case 'r' -> out.write('\r');
          case 't' -> out.write('\t');
          case 'b' -> out.write('\b');
          case 'f' -> out.write('\f');
          case '(', ')', '\\' -> out.write(next);
          default -> {
            if (next >= '0' && next <= '7') {
              int octal = next - '0';
              if (i + 1 < raw.length && raw[i + 1] >= '0' && raw[i + 1] <= '7') {
                octal = octal * 8 + (raw[++i] - '0');
                if (i + 1 < raw.length && raw[i + 1] >= '0' && raw[i + 1] <= '7') {
                  octal = octal * 8 + (raw[++i] - '0');
                }
              }
              out.write(octal);
            }
          }
        }
      } else {
        out.write(b);
      }
    }
    return new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
  }

  private static long parseHexString(
      MemorySegment pdf, String key, long start, Map<String, String> result) {
    long valEnd = indexOf(pdf, new byte[] {'>'}, start);
    if (valEnd >= 0) {
      String hex =
          new String(
              pdf.asSlice(start, valEnd - start).toArray(JAVA_BYTE), StandardCharsets.ISO_8859_1);
      result.put(key, decodeHex(hex));
      return valEnd + 1;
    }
    return start;
  }

  private static long findObjectHeader(MemorySegment pdf, int objNum, int genNum) {
    byte[] marker = (objNum + " " + genNum + " obj").getBytes(StandardCharsets.ISO_8859_1);
    return lastIndexOf(pdf, marker);
  }

  private static long findDictionaryEnd(MemorySegment pdf, long start) {
    int depth = 0;
    long curr = start;
    long size = pdf.byteSize();
    while (curr < size - 1) {
      byte b = pdf.get(JAVA_BYTE, curr);
      if (b == '(') {
        curr = skipLiteralString(pdf, curr, size);
      } else if (b == '<' && pdf.get(JAVA_BYTE, curr + 1) == '<') {
        depth++;
        curr += 2;
      } else if (b == '<') {
        curr = skipHexString(pdf, curr);
      } else if (b == '>' && pdf.get(JAVA_BYTE, curr + 1) == '>') {
        depth--;
        if (depth == 0) return curr + 2;
        curr += 2;
      } else {
        curr++;
      }

      if (curr < 0) return -1;
    }
    return -1;
  }

  private static long skipLiteralString(MemorySegment pdf, long curr, long size) {
    long next = findClosingParen(pdf, curr + 1, size);
    return (next < 0) ? -1 : next + 1;
  }

  private static long skipHexString(MemorySegment pdf, long curr) {
    long next = indexOf(pdf, new byte[] {'>'}, curr + 1);
    return (next < 0) ? -1 : next + 1;
  }

  private static long findClosingParen(MemorySegment pdf, long start, long limit) {
    int depth = 1;
    long i = start;
    while (i < limit) {
      byte b = pdf.get(JAVA_BYTE, i);
      if (b == '(') {
        depth++;
      } else if (b == ')') {
        depth--;
        if (depth == 0) return i;
      } else if (b == '\\') {
        i++;
      }
      i++;
    }
    return -1;
  }

  private static boolean isPdfDelimiter(byte b) {
    return b == '(' || b == ')' || b == '<' || b == '>' || b == '[' || b == ']' || b == '{'
        || b == '}' || b == '/' || b == '%' || isWs(b);
  }

  private static boolean isWs(byte b) {
    return b == ' ' || b == '\t' || b == '\n' || b == '\r' || b == '\f' || b == 0;
  }

  private static long skipWs(MemorySegment seg, long offset, long limit) {
    while (offset < limit && isWs(seg.get(JAVA_BYTE, offset))) offset++;
    return offset;
  }

  private static long scanDigits(MemorySegment seg, long offset, long limit) {
    while (offset < limit) {
      byte b = seg.get(JAVA_BYTE, offset);
      if (b < '0' || b > '9') break;
      offset++;
    }
    return offset;
  }

  private static int parsePositiveInt(MemorySegment seg, long start, long end) {
    int res = 0;
    for (long i = start; i < end; i++) res = res * 10 + (seg.get(JAVA_BYTE, i) - '0');
    return res;
  }

  private static long lastIndexOf(MemorySegment segment, byte[] needle) {
    return lastIndexOf(segment, needle, segment.byteSize());
  }

  private static long lastIndexOf(MemorySegment segment, byte[] needle, long from) {
    long size = segment.byteSize();
    long start = Math.min(from, size - needle.length);
    for (long i = start; i >= 0; i--) {
      if (matchesAt(segment, i, needle)) {
        return i;
      }
    }
    return -1;
  }

  private static boolean matchesAt(MemorySegment segment, long pos, byte[] needle) {
    for (int j = 0; j < needle.length; j++) {
      if (segment.get(JAVA_BYTE, pos + j) != needle[j]) {
        return false;
      }
    }
    return true;
  }

  private static long indexOf(MemorySegment segment, byte[] needle, long fromIndex) {
    long size = segment.byteSize();
    for (long i = fromIndex; i <= size - needle.length; i++) {
      if (matchesAt(segment, i, needle)) {
        return i;
      }
    }
    return -1;
  }

  private static String decodeHex(String hex) {
    if (hex.length() >= 4 && hex.regionMatches(true, 0, "FEFF", 0, 4)) {
      try {
        int len = (hex.length() - 4) / 2;
        if (len <= 0) return "";
        byte[] bytes = new byte[len];
        for (int i = 0; i < bytes.length; i++) {
          bytes[i] = (byte) Integer.parseInt(hex.substring(4 + i * 2, 6 + i * 2), 16);
        }
        return new String(bytes, StandardCharsets.UTF_16BE);
      } catch (Exception _) {
        return hex;
      }
    }
    return hex;
  }
}
