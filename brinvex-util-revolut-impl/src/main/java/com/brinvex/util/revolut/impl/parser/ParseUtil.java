package com.brinvex.util.revolut.impl.parser;

import java.math.BigDecimal;

class ParseUtil {

    private static final String CCY_SYMBOL = "$";

    public static BigDecimal parseMoney(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        if (!s.contains(CCY_SYMBOL)) {
            throw new IllegalArgumentException(String.format("Missing currency symbol: '%s'", s));
        }
        String normalized = s
                .replace(CCY_SYMBOL, "")
                .replace(",", "");
        return new BigDecimal(normalized);
    }

    public static BigDecimal parseDecimal(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String normalized = s.replace(",", "");
        return new BigDecimal(normalized);
    }


}
