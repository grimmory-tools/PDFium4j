package org.grimmory.pdfium4j.util;

import static org.junit.jupiter.api.Assertions.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PdfDateUtilsTest {

  @Test
  void testParseFullDateWithOffset() {
    String pdfDate = "D:20201231235959+05'30'";
    Optional<OffsetDateTime> parsed = PdfDateUtils.parse(pdfDate);
    assertTrue(parsed.isPresent());
    assertEquals(2020, parsed.get().getYear());
    assertEquals(12, parsed.get().getMonthValue());
    assertEquals(31, parsed.get().getDayOfMonth());
    assertEquals(23, parsed.get().getHour());
    assertEquals(59, parsed.get().getMinute());
    assertEquals(59, parsed.get().getSecond());
    assertEquals(ZoneOffset.ofHoursMinutes(5, 30), parsed.get().getOffset());
  }

  @Test
  void testParseFullDateWithNegativeOffset() {
    String pdfDate = "D:20201231235959-08'00'";
    Optional<OffsetDateTime> parsed = PdfDateUtils.parse(pdfDate);
    assertTrue(parsed.isPresent());
    assertEquals(ZoneOffset.ofHours(-8), parsed.get().getOffset());
  }

  @Test
  void testParseUtcWithZ() {
    String pdfDate = "D:20201231235959Z";
    Optional<OffsetDateTime> parsed = PdfDateUtils.parse(pdfDate);
    assertTrue(parsed.isPresent());
    assertEquals(ZoneOffset.UTC, parsed.get().getOffset());
  }

  @Test
  void testParsePartialDate() {
    String pdfDate = "D:202012";
    Optional<OffsetDateTime> parsed = PdfDateUtils.parse(pdfDate);
    assertTrue(parsed.isPresent());
    assertEquals(2020, parsed.get().getYear());
    assertEquals(12, parsed.get().getMonthValue());
    assertEquals(1, parsed.get().getDayOfMonth());
    assertEquals(0, parsed.get().getHour());
    assertEquals(ZoneOffset.UTC, parsed.get().getOffset());
  }

  @Test
  void testParseIsoDateFallback() {
    String isoDate = "2021-02-17";
    Optional<OffsetDateTime> parsed = PdfDateUtils.parse(isoDate);
    assertTrue(parsed.isPresent());
    assertEquals(2021, parsed.get().getYear());
    assertEquals(2, parsed.get().getMonthValue());
    assertEquals(17, parsed.get().getDayOfMonth());
  }

  @Test
  void testParseEdgeCases() {
    assertFalse(PdfDateUtils.parse(null).isPresent());
    assertFalse(PdfDateUtils.parse("").isPresent());
    assertFalse(PdfDateUtils.parse("garbage").isPresent());
    assertFalse(PdfDateUtils.parse("D:202").isPresent()); // too short
  }

  @Test
  void testFormat() {
    OffsetDateTime dt =
        OffsetDateTime.of(2020, 12, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
    String formatted = PdfDateUtils.format(dt);
    assertEquals("D:20201231235959+05'30'", formatted);
  }

  @Test
  void testFormatUtc() {
    OffsetDateTime dt = OffsetDateTime.of(2020, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC);
    String formatted = PdfDateUtils.format(dt);
    assertEquals("D:20201231235959Z", formatted);
  }

  @Test
  void testFormatParseRoundtrip() {
    OffsetDateTime dt = OffsetDateTime.of(2024, 4, 24, 10, 30, 0, 0, ZoneOffset.ofHours(2));
    String formatted = PdfDateUtils.format(dt);
    Optional<OffsetDateTime> parsed = PdfDateUtils.parse(formatted);
    assertTrue(parsed.isPresent());
    assertEquals(dt, parsed.get());

    OffsetDateTime utc = OffsetDateTime.of(2024, 4, 24, 10, 30, 0, 0, ZoneOffset.UTC);
    String formattedUtc = PdfDateUtils.format(utc);
    assertEquals("D:20240424103000Z", formattedUtc);
    Optional<OffsetDateTime> parsedUtc = PdfDateUtils.parse(formattedUtc);
    assertTrue(parsedUtc.isPresent());
    assertEquals(utc, parsedUtc.get());
  }
}
