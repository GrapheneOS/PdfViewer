package app.grapheneos.pdfviewer;

import java.text.DateFormat;
import java.text.ParseException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    // PDF date string format, per section 7.9.4 of PDF 32000-2:2020:
    //
    //     D:YYYYMMDDHHmmSSOHH'mm
    //
    // PDF <= 1.7 required a trailing apostrophe, which is still accepted. The
    // apostrophe between the offset HH and mm may also be omitted, matching pdf.js.
    private static final Pattern PDF_DATE_PATTERN = Pattern.compile(
            "^D:" +
                    "(\\d{4})" +     // Year (required)
                    "(\\d{2})?" +    // Month (optional)
                    "(\\d{2})?" +    // Day (optional)
                    "(\\d{2})?" +    // Hours (optional)
                    "(\\d{2})?" +    // Minutes (optional)
                    "(\\d{2})?" +    // Seconds (optional)
                    "(?:" +
                    "  ([Z+\\-])" +  // Universal time relation
                    "  (?:" +
                    "    (\\d{2})" + // Offset hours
                    "    '?" +       // Splitting apostrophe (optional)
                    "    (?:" +
                    "      (\\d{2})" + // Offset minutes
                    "      '?" +       // Trailing apostrophe (optional, PDF <= 1.7)
                    "    )?" +
                    "  )?" +
                    ")?$",
            Pattern.COMMENTS
    );

    private static int parseIntSafely(String field) throws ParseException  {
        try {
            return Integer.parseInt(field);
        } catch (NumberFormatException e) {
            throw new ParseException("Error while parsing int", -1);
        }
    }

    private static int parseGroup(Matcher matcher, int group, int defaultValue) throws ParseException {
        final String field = matcher.group(group);
        return field == null ? defaultValue : parseIntSafely(field);
    }

    // Resolve the universal time relation into a UTC offset. A missing relation or
    // "Z" means UTC; offsets are capped to the real-world UTC-12:00 to UTC+14:00 range.
    private static ZoneOffset parseOffset(Matcher matcher) throws ParseException {
        final String utRel = matcher.group(7);
        final int offsetHours = parseGroup(matcher, 8, 0);
        final int offsetMinutes = parseGroup(matcher, 9, 0);

        if (utRel == null || "Z".equals(utRel)) {
            if (offsetHours != 0 || offsetMinutes != 0) {
                throw new ParseException("UTC indicator 'Z' must not have a non-zero offset", 0);
            }
            return ZoneOffset.UTC;
        }
        if (offsetMinutes > 59) {
            throw new ParseException("Invalid UTC offset minutes", 0);
        }
        final int magnitude = offsetHours * 100 + offsetMinutes;
        if ("-".equals(utRel)) {
            if (magnitude > 1200) {
                throw new ParseException("Invalid UTC offset", 0);
            }
            return ZoneOffset.ofHoursMinutes(-offsetHours, -offsetMinutes);
        }
        if (magnitude > 1400) {
            throw new ParseException("Invalid UTC offset", 0);
        }
        return ZoneOffset.ofHoursMinutes(offsetHours, offsetMinutes);
    }

    // Parse date as per PDF spec (complies with PDF v1.4 to v2.0)
    public static String parseDate(String date) throws ParseException {
        // D: prefix is optional for PDF < v1.7; required for PDF v1.7+
        if (!date.startsWith("D:")) {
            date = "D:" + date;
        }

        final Matcher matcher = PDF_DATE_PATTERN.matcher(date);
        if (!matcher.matches()) {
            throw new ParseException("Invalid date format", 0);
        }

        // java.time months are 1-based; an absent month or day defaults to 1, the
        // time fields to 0.
        final int year = parseIntSafely(matcher.group(1));
        final int month = parseGroup(matcher, 2, 1);
        final int day = parseGroup(matcher, 3, 1);
        final int hours = parseGroup(matcher, 4, 0);
        final int minutes = parseGroup(matcher, 5, 0);
        final int seconds = parseGroup(matcher, 6, 0);
        final ZoneOffset offset = parseOffset(matcher);

        // LocalDateTime.of validates the fields and rejects impossible dates such
        // as February 31; the offset then resolves it to a definite instant.
        final Date instant;
        try {
            instant = Date.from(
                    LocalDateTime.of(year, month, day, hours, minutes, seconds).toInstant(offset));
        } catch (DateTimeException e) {
            throw new ParseException("Invalid date", 0);
        }

        // Rendered in the device's default time zone and locale.
        return DateFormat
                .getDateTimeInstance(DateFormat.DEFAULT, DateFormat.LONG)
                .format(instant);
    }
}
