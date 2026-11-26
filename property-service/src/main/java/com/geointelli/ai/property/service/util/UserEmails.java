package com.geointelli.ai.property.service.util;

/** Email given to look an account up (admin user lookup, lender organization members). */
public final class UserEmails {

    /** Longest email address SMTP allows. */
    static final int MAX_LENGTH = 254;

    private UserEmails() {
    }

    /**
     * The email to search for, trimmed; matching ignores case.
     * @throws IllegalArgumentException (400) when it is missing, longer than 254 characters or has no {@code @}
     */
    public static String forLookup(String email) {
        String value = email == null ? "" : email.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("email is required");
        if (value.length() > MAX_LENGTH)
            throw new IllegalArgumentException("email must be at most " + MAX_LENGTH + " characters");
        if (value.indexOf('@') <= 0 || value.endsWith("@"))
            throw new IllegalArgumentException("email must be an email address");
        return value;
    }
}
