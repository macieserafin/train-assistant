package pl.macieserafin.service;

import java.util.List;

final class SupportedCarriers {
    static final List<String> CODES = List.of("PR", "SKMT");

    private SupportedCarriers() {}

    static boolean includes(String code) {
        return code != null && CODES.contains(code);
    }
}
