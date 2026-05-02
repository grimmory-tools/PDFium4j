package org.grimmory.pdfium4j.util;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities for parsing and formatting PDF date strings.
 *
 * <p>PDF dates follow the format {@code D:YYYYMMDDHHmmSSOHH'mm'}, where:
 *
 * <ul>
 *   <li>{@code YYYY} is the year.
 *   <li>{@code MM} is the month (01-12).
 *   <li>{@code DD} is the day (01-31).
 *   <li>{@code HH} is the hour (00-23).
 *   <li>{@code mm} is the minute (00-59).
 *   <li>{@code SS} is the second (00-59).
 *   <li>{@code O} is the relationship of local time to UT (Universal Time): '+', '-', or 'Z'.
 *   <li>{@code HH'} is the absolute value of the offset from UT in hours.
 *   <li>{@code mm'} is the absolute value of the offset from UT in minutes.
 * </ul>
 *
 * <p>All fields after the year are optional. The default for month and day is 01, and for other
 * fields is 00. If no timezone offset is specified, UT is assumed.
 */
public final class PdfDateUtils {

  private PdfDateUtils() {}

  private static final Pattern PDF_DATE_PATTERN =
      Pattern.compile(
          "^D:(?<year>\\d{4})"
              + "(?<month>\\d{2})?"
              + "(?<day>\\d{2})?"
              + "(?<hour>\\d{2})?"
              + "(?<minute>\\d{2})?"
              + "(?<second>\\d{2})?"
              + "(?<offset>[+\\-Z])?"
              + "(?<offsetHour>\\d{2})?"
              + "'?"
              + "(?<offsetMinute>\\d{2})?"
              + "'?$");

  private static final Pattern ISO_DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

  /**
   * Parse a PDF date string into an {@link OffsetDateTime}.
   *
   * @param pdfDate the PDF date string (e.g. "D:20201231235959+05'30'" or "D:20201231235959Z")
   * @return the parsed date, or empty if the format is invalid
   */
  public static Optional<OffsetDateTime> parse(String pdfDate) {
    if (pdfDate == null || pdfDate.isBlank()) {
      return Optional.empty();
    }

    Matcher m = PDF_DATE_PATTERN.matcher(pdfDate);
    if (!m.matches()) {
      // Fallback for ISO dates (sometimes found in XMP)
      if (ISO_DATE_PATTERN.matcher(pdfDate).matches()) {
        try {
          return Optional.of(LocalDate.parse(pdfDate).atStartOfDay().atOffset(ZoneOffset.UTC));
        } catch (Exception e) {
          return Optional.empty();
        }
      }
      return Optional.empty();
    }

    try {
      int year = Integer.parseInt(m.group("year"));
      int month = m.group("month") != null ? Integer.parseInt(m.group("month")) : 1;
      int day = m.group("day") != null ? Integer.parseInt(m.group("day")) : 1;
      int hour = m.group("hour") != null ? Integer.parseInt(m.group("hour")) : 0;
      int minute = m.group("minute") != null ? Integer.parseInt(m.group("minute")) : 0;
      int second = m.group("second") != null ? Integer.parseInt(m.group("second")) : 0;

      ZoneOffset offset = ZoneOffset.UTC;
      String offsetSign = m.group("offset");
      String offsetHourRaw = m.group("offsetHour");
      String offsetMinuteRaw = m.group("offsetMinute");

      if (offsetSign == null) {
        if (offsetHourRaw != null || offsetMinuteRaw != null) {
          return Optional.empty();
        }
      } else if ("Z".equalsIgnoreCase(offsetSign)) {
        if (offsetHourRaw != null || offsetMinuteRaw != null) {
          return Optional.empty();
        }
      } else {
        if (offsetHourRaw == null) {
          return Optional.empty();
        }
        int offsetHour = Integer.parseInt(offsetHourRaw);
        int offsetMinute = offsetMinuteRaw != null ? Integer.parseInt(offsetMinuteRaw) : 0;
        int totalOffsetMinutes = offsetHour * 60 + offsetMinute;
        if ("-".equals(offsetSign)) {
          totalOffsetMinutes = -totalOffsetMinutes;
        }
        offset = ZoneOffset.ofTotalSeconds(totalOffsetMinutes * 60);
      }

      return Optional.of(OffsetDateTime.of(year, month, day, hour, minute, second, 0, offset));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  /**
   * Format an {@link OffsetDateTime} into a PDF date string.
   *
   * @param dateTime the date and time to format
   * @return the PDF date string (e.g. "D:20201231235959Z")
   */
  public static String format(OffsetDateTime dateTime) {
    StringBuilder sb = new StringBuilder("D:");
    sb.append(String.format("%04d", dateTime.getYear()));
    sb.append(String.format("%02d", dateTime.getMonthValue()));
    sb.append(String.format("%02d", dateTime.getDayOfMonth()));
    sb.append(String.format("%02d", dateTime.getHour()));
    sb.append(String.format("%02d", dateTime.getMinute()));
    sb.append(String.format("%02d", dateTime.getSecond()));

    ZoneOffset offset = dateTime.getOffset();
    if (offset.getTotalSeconds() % 60 != 0) {
      throw new IllegalArgumentException("PDF dates only support minute-precision offsets");
    }
    if (offset.getTotalSeconds() == 0) {
      sb.append("Z");
    } else {
      int totalSeconds = offset.getTotalSeconds();
      int absSeconds = Math.abs(totalSeconds);
      int hours = absSeconds / 3600;
      int minutes = (absSeconds % 3600) / 60;
      sb.append(totalSeconds >= 0 ? "+" : "-");
      sb.append(String.format("%02d", hours));
      sb.append("'");
      sb.append(String.format("%02d", minutes));
      sb.append("'");
    }
    return sb.toString();
  }
}
