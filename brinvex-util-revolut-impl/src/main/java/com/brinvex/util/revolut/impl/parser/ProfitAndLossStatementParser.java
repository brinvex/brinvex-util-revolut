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

import com.brinvex.util.revolut.api.model.PortfolioPeriod;
import com.brinvex.util.revolut.api.model.Transaction;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("DuplicatedCode")
public class ProfitAndLossStatementParser {

    private static class LazyHolder {

        private static final Pattern ACCOUNT_NAME_PATTERN = Pattern.compile(
                "Account\\s+name\\s+(?<accountName>.+)");

        private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile(
                "Account\\s+number\\s+(?<accountNumber>.+)");

        private static final Pattern PERIOD_PATTERN = Pattern.compile(
                "Period\\s+(?<periodFrom>\\d{2}\\s[A-Za-z]{3}\\s\\d{4})\\s-\\s(?<periodTo>\\d{2}\\s[A-Za-z]{3}\\s\\d{4})");

        private static final Pattern TRANSACTION_SECTION_START_PATTERN = Pattern.compile(
                "Dividends");

        private static final Pattern TRANSACTION_HEADER_PATTERN = Pattern.compile(
                "Date\\s+Symbol\\s+Security\\s+name\\s+ISIN\\s+Country\\s+Gross\\s+Amount\\s+Withholding\\s+Tax\\s+Net\\s+Amount");

        private static final Pattern TRANSACTION_SECTION_END_PATTERN = Pattern.compile(
                "Total\\s+.*");

        private static final DateTimeFormatter PERIOD_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");
    }

    private final ProfitAndLossDividendLineParser profitAndLossDividendLineParser = new ProfitAndLossDividendLineParser();

    public PortfolioPeriod parseProfitAndLossStatement(List<String> lines) {

        String accountName = null;
        String accountNumber = null;
        LocalDate periodFrom = null;
        LocalDate periodTo = null;
        for (String line : lines) {
            line = stripToEmpty(line);
            if (line.isBlank()) {
                continue;
            }
            {
                Matcher matcher = LazyHolder.ACCOUNT_NAME_PATTERN.matcher(line);
                if (matcher.find()) {
                    accountName = matcher.group("accountName");
                }
            }
            {
                Matcher matcher = LazyHolder.ACCOUNT_NUMBER_PATTERN.matcher(line);
                if (matcher.find()) {
                    accountNumber = matcher.group("accountNumber");
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
            if (accountName != null && accountNumber != null && periodFrom != null && periodTo != null) {
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

        List<Transaction> transactions = parseProfitAndLossStatementDividendTransactions(lines);

        PortfolioPeriod portfolioPeriod;
        {
            portfolioPeriod = new PortfolioPeriod();
            portfolioPeriod.setAccountName(accountName);
            portfolioPeriod.setAccountNumber(accountNumber);
            portfolioPeriod.setPeriodFrom(periodFrom);
            portfolioPeriod.setPeriodTo(periodTo);
            portfolioPeriod.setTransactions(transactions);
            portfolioPeriod.setPortfolioBreakdownSnapshots(new LinkedHashMap<>());
        }
        return portfolioPeriod;
    }

    private List<Transaction> parseProfitAndLossStatementDividendTransactions(List<String> lines) {
        List<Transaction> dividends = new ArrayList<>();
        boolean dividendsLinesStarted = false;
        for (int i = 0, linesSize = lines.size(); i < linesSize; i++) {
            String line = stripToEmpty(lines.get(i));
            try {
                if (line.isBlank()) {
                    continue;
                }
                if (!dividendsLinesStarted) {
                    if (LazyHolder.TRANSACTION_SECTION_START_PATTERN.matcher(line).matches()) {
                        dividendsLinesStarted = true;
                    }
                    continue;
                }
                if (LazyHolder.TRANSACTION_HEADER_PATTERN.matcher(line).matches()) {
                    continue;
                }
                if (LazyHolder.TRANSACTION_SECTION_END_PATTERN.matcher(line).matches()) {
                    break;
                }

                Transaction dividendTransaction = profitAndLossDividendLineParser.parseProfitAndLossDividendLine(line);
                dividends.add(dividendTransaction);

            } catch (Exception e) {
                throw new IllegalStateException(String.format("Exception while parsing %s.line: '%s'", (i + 1), line), e);
            }
        }
        return dividends;
    }

    private String stripToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

}
