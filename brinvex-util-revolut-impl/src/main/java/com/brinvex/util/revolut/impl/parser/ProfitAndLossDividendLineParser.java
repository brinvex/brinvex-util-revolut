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
import com.brinvex.util.revolut.api.model.Transaction;
import com.brinvex.util.revolut.api.model.TransactionType;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.brinvex.util.revolut.impl.parser.ParseUtil.parseMoney;

public class ProfitAndLossDividendLineParser {

    private static class LazyHolder {

        private static final Pattern LINE_PATTERN = Pattern.compile(
                "(?<date>\\d{4}-\\d{2}-\\d{2})" +
                "\\s+(?<symbol>\\S+)" +
                "\\s+(?<securityName>.+)" +
                "\\s+(?<isin>\\S{12})" +
                "\\s+(?<country>\\S{2}+)" +
                "\\s+(?<grossAmount>-?\\$(\\d+,)*\\d+(\\.\\d+)?)" +
                "\\s+(?<withholdingTax>-?\\$(\\d+,)*\\d+(\\.\\d+)?)" +
                "\\s+(?<netAmount>-?\\$(\\d+,)*\\d+(\\.\\d+)?)"
        );
    }

    public Transaction parseProfitAndLossDividendLine(String line) {

        Matcher matcher = LazyHolder.LINE_PATTERN.matcher(line);
        boolean matchFound = matcher.find();
        if (!matchFound) {
            throw new IllegalStateException(String.format("Could not parse dividend line: '%s'", line));
        }

        Transaction dividendTransaction = new Transaction();
        dividendTransaction.setCurrency(Currency.USD);
        dividendTransaction.setType(TransactionType.DIVIDEND);
        dividendTransaction.setDate(LocalDate.parse(matcher.group("date")).atStartOfDay(ZoneId.of("GMT")).withFixedOffsetZone());
        dividendTransaction.setSymbol(matcher.group("symbol"));
        dividendTransaction.setSecurityName(matcher.group("securityName"));
        dividendTransaction.setIsin(matcher.group("isin"));
        dividendTransaction.setCountry(matcher.group("country"));
        dividendTransaction.setQuantity(null);
        dividendTransaction.setPrice(null);
        dividendTransaction.setGrossAmount(parseMoney(matcher.group("grossAmount")));
        dividendTransaction.setWithholdingTax(parseMoney(matcher.group("withholdingTax")));
        dividendTransaction.setValue(parseMoney(matcher.group("netAmount")));
        dividendTransaction.setFees(null);
        dividendTransaction.setCommission(null);
        return dividendTransaction;
    }
}


