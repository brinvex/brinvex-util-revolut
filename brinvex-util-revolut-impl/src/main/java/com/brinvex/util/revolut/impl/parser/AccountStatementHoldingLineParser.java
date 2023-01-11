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

import com.brinvex.util.revolut.api.model.Currency;
import com.brinvex.util.revolut.api.model.Holding;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.brinvex.util.revolut.impl.parser.ParseUtil.parseDecimal;
import static com.brinvex.util.revolut.impl.parser.ParseUtil.parseMoney;

public class AccountStatementHoldingLineParser {

    private static class LazyHolder {

        private static final Pattern LINE_PATTERN = Pattern.compile(
                "(?<symbol>\\S+)" +
                "\\s+(?<company>.+)" +
                "\\s+(?<isin>\\S{12})" +
                "\\s+(?<quantity>(\\d+,)*\\d+(\\.\\d+)?)" +
                "\\s+(?<price>-?\\$(\\d+,)*\\d+(\\.\\d+)?)" +
                "\\s+(?<value>-?\\$(\\d+,)*\\d+(\\.\\d+)?)" +
                "\\s+\\d+(\\.\\d+)?\\s*%"
        );
    }

    public Holding parseTradingAccountStatementHoldingLine(String line) {

        Matcher matcher = LazyHolder.LINE_PATTERN.matcher(line);
        boolean matchFound = matcher.find();
        if (!matchFound) {
            throw new IllegalStateException(String.format("Could not parse holding line: '%s'", line));
        }

        Holding holding = new Holding();
        holding.setCurrency(Currency.USD);
        holding.setSymbol(matcher.group("symbol"));
        holding.setCompany(matcher.group("company"));
        holding.setIsin(matcher.group("isin"));
        holding.setQuantity(parseDecimal(matcher.group("quantity")));
        holding.setPrice(parseMoney(matcher.group("price")));
        holding.setValue(parseMoney(matcher.group("value")));
        return holding;
    }

}


