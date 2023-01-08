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
import com.brinvex.util.revolut.api.model.TransactionSide;
import com.brinvex.util.revolut.api.model.TransactionType;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AccountStatementTransactionLineParser {

    private static class LazyHolder {

        private static final Pattern TRANSACTION_DATE_SYMBOL_TYPE_PATTERN = Pattern.compile(
                "(?<date>\\d{2}\\s+\\w{3}\\s+\\d{4}\\s+\\d{2}:\\d{2}:\\d{2}\\s+[A-Z]{3})" +
                "(\\s+(?<symbol>.+))?" +
                "\\s+(?<type>(Custody fee)|(Dividend)|(Cash top-up)|(Cash withdrawal)|(Trade - Market)|(Trade - Limit)|(Stock split)|(Spinoff))" +
                "\\s+(?<numbersPart>.*)"
        );

        private static final Pattern VALUE_FEES_COMMISSION_PATTERN = Pattern.compile(
                "" +
                "\\s*(?<value>-?\\$(\\d+,)?\\d+(\\.\\d+)?)" +
                "\\s+(?<fees>-?\\$(\\d+,)?\\d+(\\.\\d+)?)" +
                "\\s+(?<commission>-?\\$(\\d+,)?\\d+(\\.\\d+)?)"
        );

        private static final Pattern QTY_VALUE_FEES_COMMISSIONS_PATTERN = Pattern.compile(
                "" +
                "\\s*(?<quantity>-?(\\d+,)?\\d+(\\.\\d+)?)" +
                "\\s+(?<value>-?\\$(\\d+,)?\\d+(\\.\\d+)?)" +
                "\\s+(?<fees>-?\\$(\\d+,)?\\d+(\\.\\d+)?)" +
                "\\s+(?<commission>-?\\$(\\d+,)?\\d+(\\.\\d+)?)"
        );

        private static final Pattern TRADE_PATTERN = Pattern.compile(
                "" +
                "\\s*(?<quantity>-?(\\d+,)?\\d+(\\.\\d+)?)" +
                "\\s+(?<price>-?\\$?(\\d+,)?\\d+(\\.\\d+)?)" +
                "\\s+(?<side>(Buy)|(Sell))" +
                "\\s+(?<value>-?\\$(\\d+,)?\\d+(\\.\\d+)?)" +
                "\\s+(?<fees>-?\\$(\\d+,)?\\d+(\\.\\d+)?)" +
                "\\s+(?<commission>-?\\$(\\d+,)?\\d+(\\.\\d+)?)"
        );

        private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss O");
    }

    public Transaction parseTradingAccountTransactionLine(String line) {
        Transaction transaction = new Transaction();
        transaction.setCurrency(Currency.USD);

        TransactionType transactionType;
        String numbersPart;
        {
            Matcher matcher = LazyHolder.TRANSACTION_DATE_SYMBOL_TYPE_PATTERN.matcher(line);
            boolean matchFound = matcher.find();
            if (!matchFound) {
                throw new IllegalStateException(String.format("Could not parse transaction line: '%s'", line));
            }
            transactionType = parseTransactionType(matcher.group("type"));
            numbersPart = matcher.group("numbersPart");

            transaction.setDate(ZonedDateTime.parse(matcher.group("date"), LazyHolder.DATETIME_FORMATTER));
            transaction.setSymbol(matcher.group("symbol"));
            transaction.setType(transactionType);
        }
        {
            Pattern pattern;
            switch (transactionType) {
                case CASH_TOP_UP:
                case CASH_WITHDRAWAL:
                case CUSTODY_FEE:
                case DIVIDEND:
                    pattern = LazyHolder.VALUE_FEES_COMMISSION_PATTERN;
                    break;
                case SPINOFF:
                case STOCK_SPLIT:
                    pattern = LazyHolder.QTY_VALUE_FEES_COMMISSIONS_PATTERN;
                    break;
                case TRADE_LIMIT:
                case TRADE_MARKET:
                    pattern = LazyHolder.TRADE_PATTERN;
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + transactionType);
            }

            Matcher matcher = pattern.matcher(numbersPart);
            boolean matchFound = matcher.find();
            if (!matchFound) {
                throw new IllegalStateException(String.format("Could not parse transaction line: '%s'", line));
            }

            transaction.setValue(parserBigDecimal(matcher.group("value")));
            transaction.setFees(parserBigDecimal(matcher.group("fees")));
            transaction.setCommission(parserBigDecimal(matcher.group("commission")));
            if (pattern == LazyHolder.QTY_VALUE_FEES_COMMISSIONS_PATTERN) {
                transaction.setQuantity(parserBigDecimal(matcher.group("quantity")));
            } else if (pattern == LazyHolder.TRADE_PATTERN) {
                transaction.setQuantity(parserBigDecimal(matcher.group("quantity")));
                transaction.setPrice(parserBigDecimal(matcher.group("price")));
                transaction.setSide(TransactionSide.valueOf(matcher.group("side").toUpperCase()));
            }
        }
        return transaction;
    }

    private BigDecimal parserBigDecimal(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String normalized = s.trim()
                .replace(",", "")
                .replace("$", "");
        return new BigDecimal(normalized);
    }

    private TransactionType parseTransactionType(String transactionTypeStr) {
        Objects.requireNonNull(transactionTypeStr);
        switch (transactionTypeStr) {
            case "Custody fee":
                return TransactionType.CUSTODY_FEE;
            case "Dividend":
                return TransactionType.DIVIDEND;
            case "Cash top-up":
                return TransactionType.CASH_TOP_UP;
            case "Cash withdrawal":
                return TransactionType.CASH_WITHDRAWAL;
            case "Trade - Market":
                return TransactionType.TRADE_MARKET;
            case "Trade - Limit":
                return TransactionType.TRADE_LIMIT;
            case "Stock split":
                return TransactionType.STOCK_SPLIT;
            case "Spinoff":
                return TransactionType.SPINOFF;
            default:
                throw new IllegalArgumentException(String.format("Unsupported transactionType: '%s'", transactionTypeStr));
        }
    }


}


