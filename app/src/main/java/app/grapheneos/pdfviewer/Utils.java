package app.grapheneos.pdfviewer;

import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    // PDF date string format. Based on the format described in section 7.9.4 of
    // the PDF 32000-2:2020 specification:
    //
    //     D:YYYYMMDDHHmmSSOHH'mm
    //
    // The PDF 1.7 reference defined the same format with a terminating
    // apostrophe, and PDF processors are recommended to accept date strings
    // that follow that older convention. The apostrophe between HH and mm is
    // also tolerated as missing for additional leniency, matching pdf.js.
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

        final Calendar calendar = Calendar.getInstance();
        final int currentYear = calendar.get(Calendar.YEAR);

        int year = parseIntSafely(matcher.group(1));
        if (year > currentYear) {
            year = currentYear;
        }

        // Calendar month starts at 0 in Java; defaults for month and day are 1
        // per the spec, all other fields default to 0.
        final int month = parseGroup(matcher, 2, 1) - 1;
        if (month < 0 || month > 11) {
            throw new ParseException("Invalid month", 0);
        }
        final int day = parseGroup(matcher, 3, 1);
        if (day < 1 || day > 31) {
            throw new ParseException("Invalid day", 0);
        }
        int hours = parseGroup(matcher, 4, 0);
        if (hours > 23) {
            throw new ParseException("Invalid hours", 0);
        }
        int minutes = parseGroup(matcher, 5, 0);
        if (minutes > 59) {
            throw new ParseException("Invalid minutes", 0);
        }
        final int seconds = parseGroup(matcher, 6, 0);
        if (seconds > 59) {
            throw new ParseException("Invalid seconds", 0);
        }

        final String utRel = matcher.group(7);
        if (utRel != null) {
            final int offsetHours = parseGroup(matcher, 8, 0);
            final int offsetMinutes = parseGroup(matcher, 9, 0);
            if (offsetMinutes > 59) {
                throw new ParseException("Invalid UTC offset minutes", 0);
            }
            final int offsetHoursMinutes = offsetHours * 100 + offsetMinutes;
            // Validate UTC offset (UTC-12:00 to UTC+14:00; "Z" means UTC)
            switch (utRel) {
                case "-":
                    if (offsetHoursMinutes > 1200) {
                        throw new ParseException("Invalid UTC offset", 0);
                    }
                    hours -= offsetHours;
                    minutes -= offsetMinutes;
                    break;
                case "+":
                    if (offsetHoursMinutes > 1400) {
                        throw new ParseException("Invalid UTC offset", 0);
                    }
                    hours += offsetHours;
                    minutes += offsetMinutes;
                    break;
                case "Z":
                    if (offsetHoursMinutes != 0) {
                        throw new ParseException("UTC indicator 'Z' must not have a non-zero offset", 0);
                    }
                    break;
            }
        }

        calendar.set(year, month, day, hours, minutes, seconds);

        return DateFormat
                .getDateTimeInstance(DateFormat.DEFAULT, DateFormat.LONG)
                .format(calendar.getTime());
    }
}
