/*
 * Copyright © 2023 Brinvex (dev@brinvex.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
