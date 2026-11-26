package com.geointelli.ai.property.service.util;

import java.util.Locale;

/**
 * Text typed in the admin user search (GET /api/admin/users/search): matches the start of an email or a name.
 * "Starts with" is a range on the lowercase search columns of V53, which sort by code point (collation "C"): the
 * values starting with {@code prefix} are those from {@code prefix} (included) to {@link #upperBound} (excluded).
 */
public final class UserSearch {

    /** Fewest characters searched: a single letter would list a large share of the accounts. */
    public static final int MIN_LENGTH = 2;
    /** Longest email address SMTP allows; nothing longer can start an email. */
    static final int MAX_LENGTH = 254;
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 25;

    private UserSearch() {
    }

    /**
     * The typed text as searched: trimmed, runs of spaces made one, lowercase. Every character is literal.
     * @throws IllegalArgumentException (400) under 2 or over 254 characters
     */
    public static String prefix(String q) {
        String value = q == null ? "" : q.strip().replaceAll("\\s+", " ");
        if (value.length() < MIN_LENGTH)
            throw new IllegalArgumentException("q must have at least " + MIN_LENGTH + " characters");
        if (value.length() > MAX_LENGTH)
            throw new IllegalArgumentException("q must be at most " + MAX_LENGTH + " characters");
        return value.toLowerCase(Locale.ROOT);
    }

    /**
     * The first value after every value that starts with {@code prefix}, in code point order: the prefix with its
     * last code point increased by one (surrogates skipped; a last code point already at U+10FFFF is dropped first).
     */
    public static String upperBound(String prefix) {
        int end = prefix.length();
        while (end > 0) {
            int last = prefix.codePointBefore(end);
            int start = end - Character.charCount(last);
            if (last < Character.MAX_CODE_POINT) {
                int next = last + 1 == Character.MIN_SURROGATE ? Character.MAX_SURROGATE + 1 : last + 1;
                return prefix.substring(0, start) + new String(Character.toChars(next));
            }
            end = start;
        }
        throw new IllegalArgumentException("no value follows " + prefix);
    }

    /**
     * Number of suggestions asked for.
     * @throws IllegalArgumentException (400) outside 1 to 25
     */
    public static int size(int size) {
        if (size < 1 || size > MAX_SIZE)
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        return size;
    }
}
