package com.wexresprise.margytube;

import android.content.Context;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Formats {
    private static final Pattern DURATION =
            Pattern.compile("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?");
    private static final Locale RU = new Locale("ru");

    private Formats() {
    }

    /** 1200 -> "1,2 тыс."; keeps Russian decimal comma. */
    public static String compact(long value) {
        if (value < 0) {
            return "";
        }
        if (value < 1000) {
            return String.valueOf(value);
        }
        String[] units = {"тыс.", "млн", "млрд"};
        double v = value;
        int unit = -1;
        while (v >= 1000 && unit < units.length - 1) {
            v /= 1000;
            unit++;
        }
        String text = v >= 100 ? String.format(Locale.US, "%.0f", v)
                : String.format(Locale.US, "%.1f", v);
        text = text.replace('.', ',');
        if (text.endsWith(",0")) {
            text = text.substring(0, text.length() - 2);
        }
        return text + " " + units[unit];
    }

    public static String views(Context c, long value) {
        if (value < 0) {
            return "";
        }
        int quantity;
        if (value < 1000) {
            quantity = (int) value;
        } else {
            quantity = 5; // "1,2 тыс. просмотров" — Russian many-form for thousands
        }
        return c.getResources().getQuantityString(R.plurals.views, quantity, compact(value));
    }

    public static String comments(Context c, long value) {
        int quantity = value < 1000 ? (int) value : 5;
        return c.getResources().getQuantityString(R.plurals.comments_count, quantity,
                compact(value));
    }

    /** ISO-8601 PT#H#M#S -> 12:34 or 1:02:03. */
    public static String duration(String iso) {
        if (iso == null) {
            return "";
        }
        Matcher m = DURATION.matcher(iso);
        if (!m.matches()) {
            return "";
        }
        try {
            long h = m.group(1) == null ? 0 : Long.parseLong(m.group(1));
            long min = m.group(2) == null ? 0 : Long.parseLong(m.group(2));
            long sec = m.group(3) == null ? 0 : Long.parseLong(m.group(3));
            if (h > 0) {
                return String.format(Locale.US, "%d:%02d:%02d", h, min, sec);
            }
            return String.format(Locale.US, "%d:%02d", min, sec);
        } catch (NumberFormatException e) {
            return "";
        }
    }

    public static String relative(String iso) {
        if (iso == null || iso.isEmpty()) {
            return "";
        }
        try {
            Instant then = OffsetDateTime.parse(iso).toInstant();
            long days = Duration.between(then, Instant.now()).toDays();
            if (days <= 0) {
                return "сегодня";
            }
            if (days == 1) {
                return "вчера";
            }
            if (days < 7) {
                return days + " дн. назад";
            }
            if (days < 30) {
                return (days / 7) + " нед. назад";
            }
            if (days < 365) {
                return (days / 30) + " мес. назад";
            }
            return (days / 365) + " г. назад";
        } catch (Exception e) {
            return "";
        }
    }

    public static String date(String iso) {
        if (iso == null || iso.isEmpty()) {
            return "";
        }
        try {
            return DateTimeFormatter.ofPattern("d MMM yyyy", RU)
                    .format(OffsetDateTime.parse(iso));
        } catch (Exception e) {
            return "";
        }
    }
}
