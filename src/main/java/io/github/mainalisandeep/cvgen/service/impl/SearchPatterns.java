package io.github.mainalisandeep.cvgen.service.impl;

import java.util.Locale;

/**
 * Builds case-insensitive "contains" patterns for admin search boxes.
 * <p>
 * The input is escaped so a search for {@code 50%} or {@code first_last} matches those characters
 * literally instead of acting as wildcards. Use with {@link #ESCAPE}.
 */
final class SearchPatterns {

    static final char ESCAPE = '\\';

    private SearchPatterns() {
    }

    /** @return the lowercase pattern, or {@code null} when there is nothing to search for */
    static String containsIgnoreCase(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String escaped = query.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
