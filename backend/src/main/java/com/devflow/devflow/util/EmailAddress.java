package com.devflow.devflow.util;

import java.util.Locale;

/** Central normalization rule for email addresses stored and searched by DevFlow. */
public final class EmailAddress {
    private EmailAddress() {}

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
