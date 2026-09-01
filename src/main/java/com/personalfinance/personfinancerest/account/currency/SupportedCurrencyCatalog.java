package com.personalfinance.personfinancerest.account.currency;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

final class SupportedCurrencyCatalog {

    static final String SOURCE_PUBLISHED_DATE = "2026-01-01";

    private static final String RESOURCE = "/reference/supported-account-currencies.txt";
    private static final List<String> CODES = loadCodes();
    private static final Set<String> CODE_SET = Set.copyOf(CODES);

    private SupportedCurrencyCatalog() {
    }

    static List<String> codes() {
        return CODES;
    }

    static boolean supports(String code) {
        return code != null && CODE_SET.contains(code.toUpperCase(Locale.ROOT));
    }

    private static List<String> loadCodes() {
        InputStream input = SupportedCurrencyCatalog.class.getResourceAsStream(RESOURCE);
        if (input == null) {
            throw new IllegalStateException("Missing supported account currency resource: " + RESOURCE);
        }

        TreeSet<String> codes = new TreeSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .flatMap(line -> List.of(line.split(",")).stream())
                    .map(String::trim)
                    .forEach(code -> addCode(codes, code));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read supported account currencies", exception);
        }

        if (codes.isEmpty()) {
            throw new IllegalStateException("Supported account currency resource is empty");
        }
        return List.copyOf(codes);
    }

    private static void addCode(Set<String> codes, String code) {
        if (!code.matches("[A-Z]{3}")) {
            throw new IllegalStateException("Invalid supported account currency code: " + code);
        }
        if (!codes.add(code)) {
            throw new IllegalStateException("Duplicate supported account currency code: " + code);
        }
    }
}
