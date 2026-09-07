package com.personalfinance.personfinancerest.account.management;

import java.util.Locale;

final class AccountNames {

    private AccountNames() {
    }

    static String displayName(String name) {
        return name.trim();
    }

    static String normalizedName(String name) {
        return displayName(name).toLowerCase(Locale.ROOT);
    }

    static String optionalDisplayName(String value) {
        return value == null ? null : value.trim();
    }
}
