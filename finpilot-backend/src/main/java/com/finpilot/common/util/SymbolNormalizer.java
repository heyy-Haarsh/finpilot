package com.finpilot.common.util;

public final class SymbolNormalizer {

    private SymbolNormalizer() {
    }

    public static String normalize(String symbol) {
        return symbol == null ? null : symbol.trim().toUpperCase();
    }
}
