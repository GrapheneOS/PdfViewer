package app.grapheneos.pdfviewer

import java.text.DateFormat
import java.text.ParseException
import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Date

// PDF date string format, per section 7.9.4 of PDF 32000-2:2020:
//
//     D:YYYYMMDDHHmmSSOHH'mm
//
// PDF <= 1.7 required a trailing apostrophe, which is still accepted. The
// apostrophe between the offset HH and mm may also be omitted, matching pdf.js.
private val PDF_DATE_PATTERN = Regex(
    """
        ^D:
        (\d{4})        # Year (required)
        (\d{2})?       # Month (optional)
        (\d{2})?       # Day (optional)
        (\d{2})?       # Hours (optional)
        (\d{2})?       # Minutes (optional)
        (\d{2})?       # Seconds (optional)
        (?:
          ([Z+\-])     # Universal time relation
          (?:
            (\d{2})    # Offset hours
            '?         # Splitting apostrophe (optional)
            (?:
              (\d{2})  # Offset minutes
              '?       # Trailing apostrophe (optional, PDF <= 1.7)
            )?
          )?
        )?$
    """.trimIndent(),
    RegexOption.COMMENTS
)

private fun parseIntSafely(field: String): Int =
    field.toIntOrNull() ?: throw ParseException("Error while parsing int", -1)

private fun parseGroup(matchResult: MatchResult, group: Int, defaultValue: Int): Int {
    val field = matchResult.groups[group]?.value ?: return defaultValue
    return parseIntSafely(field)
}

// Resolve the universal time relation into a UTC offset. A missing relation or
// "Z" means UTC; offsets are capped to the real-world UTC-12:00 to UTC+14:00 range.
private fun parseOffset(matchResult: MatchResult): ZoneOffset {
    val utRel = matchResult.groups[7]?.value
    val offsetHours = parseGroup(matchResult, 8, 0)
    val offsetMinutes = parseGroup(matchResult, 9, 0)

    if (utRel == null || utRel == "Z") {
        if (offsetHours != 0 || offsetMinutes != 0) {
            throw ParseException("UTC indicator 'Z' must not have a non-zero offset", 0)
        }
        return ZoneOffset.UTC
    }
    if (offsetMinutes > 59) {
        throw ParseException("Invalid UTC offset minutes", 0)
    }
    val magnitude = offsetHours * 100 + offsetMinutes
    if (utRel == "-") {
        if (magnitude > 1200) {
            throw ParseException("Invalid UTC offset", 0)
        }
        return ZoneOffset.ofHoursMinutes(-offsetHours, -offsetMinutes)
    }
    if (magnitude > 1400) {
        throw ParseException("Invalid UTC offset", 0)
    }
    return ZoneOffset.ofHoursMinutes(offsetHours, offsetMinutes)
}

// Parse date as per PDF spec (complies with PDF v1.4 to v2.0)
@Throws(ParseException::class)
fun parseDate(date: String): String {
    // D: prefix is optional for PDF < v1.7; required for PDF v1.7+
    val input = if (date.startsWith("D:")) date else "D:$date"

    val matchResult = PDF_DATE_PATTERN.matchEntire(input)
        ?: throw ParseException("Invalid date format", 0)

    // java.time months are 1-based; an absent month or day defaults to 1, the
    // time fields to 0.
    val year = parseIntSafely(matchResult.groups[1]!!.value)
    val month = parseGroup(matchResult, 2, 1)
    val day = parseGroup(matchResult, 3, 1)
    val hours = parseGroup(matchResult, 4, 0)
    val minutes = parseGroup(matchResult, 5, 0)
    val seconds = parseGroup(matchResult, 6, 0)
    val offset = parseOffset(matchResult)

    // LocalDateTime.of validates the fields and rejects impossible dates such
    // as February 31; the offset then resolves it to a definite instant.
    val instant: Date = try {
        Date.from(LocalDateTime.of(year, month, day, hours, minutes, seconds).toInstant(offset))
    } catch (_: DateTimeException) {
        throw ParseException("Invalid date", 0)
    }

    // Rendered in the device's default time zone and locale.
    return DateFormat
        .getDateTimeInstance(DateFormat.DEFAULT, DateFormat.LONG)
        .format(instant)
}
