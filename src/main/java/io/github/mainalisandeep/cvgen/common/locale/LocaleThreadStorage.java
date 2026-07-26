package io.github.mainalisandeep.cvgen.common.locale;

import java.util.Locale;

/**
 * Per-request locale holder.
 * <p>
 * {@link LocaleFilter} populates it on every inbound request and clears it afterwards;
 * clearing matters because request threads are pooled and a stale entry would leak
 * one user's locale into the next request served by the same thread.
 */
public final class LocaleThreadStorage {

    public static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    private static final ThreadLocal<Locale> LOCALE = new ThreadLocal<>();

    private LocaleThreadStorage() {
    }

    public static void setLocale(Locale locale) {
        if (locale == null) {
            clear();
            return;
        }
        LOCALE.set(locale);
    }

    /**
     * Never null - falls back to {@link #DEFAULT_LOCALE} outside a request
     * (async executors, scheduled jobs, tests).
     */
    public static Locale getLocale() {
        Locale locale = LOCALE.get();
        return locale == null ? DEFAULT_LOCALE : locale;
    }

    public static void clear() {
        LOCALE.remove();
    }
}
