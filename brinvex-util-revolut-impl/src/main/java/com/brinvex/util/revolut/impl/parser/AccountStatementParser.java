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
import com.brinvex.util.revolut.api.model.PortfolioBreakdown;
import com.brinvex.util.revolut.api.model.PortfolioPeriod;
import com.brinvex.util.revolut.api.model.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.brinvex.util.revolut.impl.parser.ParseUtil.parseMoney;

@SuppressWarnings("DuplicatedCode")
public class AccountStatementParser {

    private static class LazyHolder {

        private static final Pattern ACCOUNT_NAME_PATTERN = Pattern.compile(
                "Account\\s+name\\s+(?<accountName>.+)");

        private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile(
                "Account\\s+number\\s+(?<accountNumber>.+)");

        private static final Pattern PERIOD_PATTERN = Pattern.compile(
                "Period\\s+(?<periodFrom>\\d{2}\\s[A-Za-z]{3}\\s\\d{4})\\s-\\s(?<periodTo>\\d{2}\\s[A-Za-z]{3}\\s\\d{4})");

        private static final Pattern HOLDINGS_SECTION_START_PATTERN = Pattern.compile(
                "Portfolio\\s+breakdown");

        private static final Pattern HOLDINGS_HEADER_PATTERN = Pattern.compile(
                "Symbol\\s+Company\\s+ISIN\\s+Quantity\\s+Price\\s+Value\\s+%\\s+of\\s+Portfolio");

        private static final Pattern HOLDINGS_SECTION_END_PATTERN = Pattern.compile(
                "Stocks\\s+value.*");

        private static final Pattern CASH_PATTERN = Pattern.compile(
                "Cash\\s+value\\s+(?<cash>-?\\$(\\d+,)*\\d+(\\.\\d+)?)\\s+\\d+(\\.\\d+)?\\s*%");

        private static final Pattern TRANSACTIONS_SECTION_START_PATTERN = Pattern.compile(
                "Transactions");

        private static final Pattern TRANSACTIONS_HEADER_PATTERN = Pattern.compile(
                "Date\\s+Symbol\\s+Type\\s+Quantity\\s+Price\\s+Side\\s+Value\\s+Fees\\s+Commission");

        private static final Pattern TRANSACTIONS_SECTION_END_PATTERN = Pattern.compile(
                "Report\\s+lost\\s+or\\s+stolen\\s+card");

        private static final DateTimeFormatter PERIOD_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");
    }

    private final AccountStatementHoldingLineParser accStatementHoldingLineParser = new AccountStatementHoldingLineParser();

    private final AccountStatementTransactionLineParser accStatementTransactionLineParser = new AccountStatementTransactionLineParser();

    public PortfolioPeriod parseTradingAccountStatement(List<String> lines) {
        String accountName = null;
        String accountNumber = null;
        LocalDate periodFrom = null;
        LocalDate periodTo = null;
        BigDecimal cash = null;
        for (String line : lines) {
            line = stripToEmpty(line);
            if (line.isBlank()) {
                continue;
            }
            {
                Matcher matcher = LazyHolder.ACCOUNT_NAME_PATTERN.matcher(line);
                if (matcher.find()) {
                    accountName = matcher.group("accountName");
                    continue;
                }
            }
            {
                Matcher matcher = LazyHolder.ACCOUNT_NUMBER_PATTERN.matcher(line);
                if (matcher.find()) {
                    accountNumber = matcher.group("accountNumber");
                    continue;
                }
            }
            {
                Matcher matcher = LazyHolder.PERIOD_PATTERN.matcher(line);
                if (matcher.find()) {
                    periodFrom = LocalDate.parse(matcher.group("periodFrom"), LazyHolder.PERIOD_DATE_FORMATTER);
                    periodTo = LocalDate.parse(matcher.group("periodTo"), LazyHolder.PERIOD_DATE_FORMATTER);
                    continue;
                }
            }
            {
                Matcher matcher = LazyHolder.CASH_PATTERN.matcher(line);
                if (matcher.find()) {
                    cash = parseMoney(matcher.group("cash"));
                    continue;
                }
            }
            if (accountName != null && accountNumber != null && periodFrom != null && periodTo != null && cash != null) {
                break;
            }
        }
        if (accountName == null) {
            throw new IllegalStateException("Account name not found");
        }
        if (accountNumber == null) {
            throw new IllegalStateException("Account number not found");
        }
        if (periodFrom == null || periodTo == null) {
            throw new IllegalStateException("Period not found");
        }
        if (cash == null) {
            throw new IllegalStateException("Cash not found");
        }

        List<Transaction> transactions = parseTradingAccountStatementTransactions(lines);
        List<Holding> holdings = parseTradingAccountStatementHoldings(lines);

        PortfolioPeriod portfolioPeriod;
        {
            portfolioPeriod = new PortfolioPeriod();
            portfolioPeriod.setAccountName(accountName);
            portfolioPeriod.setAccountNumber(accountNumber);
            portfolioPeriod.setPeriodFrom(periodFrom);
            portfolioPeriod.setPeriodTo(periodTo);
            portfolioPeriod.setTransactions(transactions);

            PortfolioBreakdown portfolioBreakdown = new PortfolioBreakdown();
            portfolioBreakdown.setHoldings(holdings);
            portfolioBreakdown.setDate(periodTo);
            portfolioBreakdown.setCash(Map.of(Currency.USD, cash));
            portfolioPeriod.setPortfolioBreakdownSnapshots(Map.of(portfolioBreakdown.getDate(), portfolioBreakdown));
        }
        return portfolioPeriod;
    }

    private List<Transaction> parseTradingAccountStatementTransactions(List<String> lines) {
        List<Transaction> transactions = new ArrayList<>();
        boolean transactionLinesStarted = false;
        for (int i = 0, linesSize = lines.size(); i < linesSize; i++) {
            String line = stripToEmpty(lines.get(i));
            try {
                if (line.isBlank()) {
                    continue;
                }
                if (!transactionLinesStarted) {
                    if (LazyHolder.TRANSACTIONS_SECTION_START_PATTERN.matcher(line).matches()) {
                        transactionLinesStarted = true;
                    }
                    continue;
                }
                if (LazyHolder.TRANSACTIONS_HEADER_PATTERN.matcher(line).matches()) {
                    continue;
                }
                if (LazyHolder.TRANSACTIONS_SECTION_END_PATTERN.matcher(line).matches()) {
                    break;
                }
                Transaction transaction = accStatementTransactionLineParser.parseTradingAccountTransactionLine(line);
                transactions.add(transaction);
            } catch (Exception e) {
                throw new IllegalStateException(String.format("Exception while parsing %s.line: '%s'", (i + 1), line), e);
            }
        }
        return transactions;
    }

    private List<Holding> parseTradingAccountStatementHoldings(List<String> lines) {
        List<Holding> holdings = new ArrayList<>();
        boolean transactionLinesStarted = false;
        for (int i = 0, linesSize = lines.size(); i < linesSize; i++) {
            String line = stripToEmpty(lines.get(i));
            try {
                if (line.isBlank()) {
                    continue;
                }
                if (!transactionLinesStarted) {
                    if (LazyHolder.HOLDINGS_SECTION_START_PATTERN.matcher(line).matches()) {
                        transactionLinesStarted = true;
                    }
                    continue;
                }
                if (LazyHolder.HOLDINGS_HEADER_PATTERN.matcher(line).matches()) {
                    continue;
                }
                if (LazyHolder.HOLDINGS_SECTION_END_PATTERN.matcher(line).matches()) {
                    break;
                }
                Holding holding = accStatementHoldingLineParser.parseTradingAccountStatementHoldingLine(line);
                holdings.add(holding);
            } catch (Exception e) {
                throw new IllegalStateException(String.format("Exception while parsing %s.line: '%s'", (i + 1), line), e);
            }
        }
        return holdings;
    }

    private String stripToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

}
