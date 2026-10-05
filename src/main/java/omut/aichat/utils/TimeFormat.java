package omut.aichat.utils;

import java.util.Locale;

/**
 * Shared duration formatting for chat UI and export.
 * <p>
 * Sub-second values render as whole milliseconds ("847 ms");
 * longer durations render as fractional seconds with one decimal
 * ("1.5 s", "12.0 s").
 */
public final class TimeFormat {

    private TimeFormat() {}

    public static String shortDuration(long millis) {
        if (millis < 1000) return millis + " ms";
        return String.format(Locale.US, "%.1f s", millis / 1000.0);
    }
}