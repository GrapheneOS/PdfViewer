package app.grapheneos.pdfviewer;

import android.text.TextUtils;

import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.Calendar;

public class Utils {

    private static int parseIntSafely(String field) throws ParseException  {
        try {
            return Integer.parseInt(field);
        } catch (NumberFormatException e) {
            throw new ParseException("Error while parsing int", -1);
        }
    }

    // Parse date as per PDF spec (complies with PDF v1.4 to v1.7)
    public static String parseDate(String date) throws ParseException {
        int position = 0;

        // D: prefix is optional for PDF < v1.7; required for PDF v1.7
        if (!date.startsWith("D:")) {
            date = "D:" + date;
        }
        if (date.length() < 6 || date.length() > 23) {
            throw new ParseException("Invalid datetime length", position);
        }

        final Calendar calendar = Calendar.getInstance();
        final int currentYear = calendar.get(Calendar.YEAR);

        // Year is required
        String field = date.substring(position += 2, 6);
        if (!TextUtils.isDigitsOnly(field)) {
            throw new ParseException("Invalid year", position);
        }
        int year = parseIntSafely(field);
        if (year > currentYear) {
            year = currentYear;
        }

        position += 4;

        // Default value for month and day shall be 1 (calendar month starts at 0 in Java 7),
        // all others default to 0
        int month = 0;
        int day = 1;
        int hours = 0;
        int minutes = 0;
        int seconds = 0;

        // All succeeding fields are optional, but each preceding field must be present
        if (date.length() > 8) {
            field = date.substring(position, 8);
            if (!TextUtils.isDigitsOnly(field)) {
                throw new ParseException("Invalid month", position);
            }
            month = parseIntSafely(field) - 1;
            if (month > 11) {
                throw new ParseException("Invalid month", position);
            }
            position += 2;
        }
        if (date.length() > 10) {
            field = date.substring(8, 10);
            if (!TextUtils.isDigitsOnly(field)) {
                throw new ParseException("Invalid day", position);
            }
            day = parseIntSafely(field);
            if (day > 31) {
                throw new ParseException("Invalid day", position);
            }
            position += 2;
        }
        if (date.length() > 12) {
            field = date.substring(10, 12);
            if (!TextUtils.isDigitsOnly(field)) {
                throw new ParseException("Invalid hours", position);
            }
            hours = parseIntSafely(field);
            if (hours > 23) {
                throw new ParseException("Invalid hours", position);
            }
            position += 2;
        }
        if (date.length() > 14) {
            field = date.substring(12, 14);
            if (!TextUtils.isDigitsOnly(field)) {
                throw new ParseException("Invalid minutes", position);
            }
            minutes = parseIntSafely(field);
            if (minutes > 59) {
                throw new ParseException("Invalid minutes", position);
            }
            position += 2;
        }
        if (date.length() > 16) {
            field = date.substring(14, 16);
            if (!TextUtils.isDigitsOnly(field)) {
                throw new ParseException("Invalid seconds", position);
            }
            seconds = parseIntSafely(field);
            if (seconds > 59) {
                throw new ParseException("Invalid seconds", position);
            }
            position += 2;
        }


        if (date.length() > position) {
            int offsetHours = 0;
            int offsetMinutes = 0;

            final char utRel = date.charAt(position);
            if (utRel != '\u002D' && utRel != '\u002B' && utRel != '\u005A') {
                throw new ParseException("Invalid UT relation", position);
            }

            position++;

            if (date.length() > position + 2) {
                field = date.substring(position, position + 2);
                if (!TextUtils.isDigitsOnly(field)) {
                    throw new ParseException("Invalid UTC offset hours", position);
                }
                offsetHours = parseIntSafely(field);
                final int offsetHoursMinutes = offsetHours * 100 + offsetMinutes;

                // Validate UTC offset (UTC-12:00 to UTC+14:00)
                if ((utRel == '\u002D' && offsetHoursMinutes > 1200) ||
                        (utRel == '\u002B' && offsetHoursMinutes > 1400)) {
                    throw new ParseException("Invalid UTC offset hours", position);
                }

                position += 2;

                // Apostrophe shall succeed HH and precede mm
                if (date.charAt(position) != '\'') {
                    throw new ParseException("Expected apostrophe", position);
                }

                position++;

                if (date.length() > position + 2) {
                    field = date.substring(position, position + 2);
                    if (!TextUtils.isDigitsOnly(field)) {
                        throw new ParseException("Invalid UTC offset minutes", position);
                    }
                    offsetMinutes = parseIntSafely(field);
                    if (offsetMinutes > 59) {
                        throw new ParseException("Invalid UTC offset minutes", position);
                    }
                    position += 2;

                    // Apostrophe shall succeed mm
                    if (date.charAt(position) != '\'') {
                        throw new ParseException("Expected apostrophe", position);
                    }
                }
            }


            switch (utRel) {
                case '\u002D':
                    hours -= offsetHours;
                    minutes -= offsetMinutes;
                    break;
                case '\u002B':
                    hours += offsetHours;
                    minutes += offsetMinutes;
                    break;
                default:
                    // "Z" means equal to UTC
                    break;
            }
        }

        calendar.set(year, month, day, hours, minutes, seconds);

        return DateFormat
                .getDateTimeInstance(DateFormat.DEFAULT, DateFormat.LONG)
                .format(calendar.getTime());
    }
}
