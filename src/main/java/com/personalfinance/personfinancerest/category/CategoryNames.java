package com.personalfinance.personfinancerest.category;

import java.util.Locale;

final class CategoryNames {

    private CategoryNames() {
    }

    static String displayName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }

    static String normalizedName(String name) {
        return displayName(name).toLowerCase(Locale.ROOT);
    }
}
