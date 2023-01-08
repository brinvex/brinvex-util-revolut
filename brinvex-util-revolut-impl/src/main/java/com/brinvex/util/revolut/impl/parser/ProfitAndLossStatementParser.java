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

import com.brinvex.util.revolut.api.model.TradingAccountTransactions;
import com.brinvex.util.revolut.api.model.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("DuplicatedCode")
public class ProfitAndLossStatementParser {

    private static class LazyHolder {

        private static final Pattern ACC_STATEMENT_ACCOUNT_NAME_PATTERN = Pattern.compile(
                "Account\\s+name\\s+(?<accountName>.+)");

        private static final Pattern ACC_STATEMENT_ACCOUNT_NUMBER_PATTERN = Pattern.compile(
                "Account\\s+number\\s+(?<accountNumber>.+)");

        private static final Pattern PNL_STATEMENT_TRANSACTION_SECTION_START_PATTERN = Pattern.compile(
                "Dividends");

        private static final Pattern PNL_STATEMENT_TRANSACTION_HEADER_PATTERN = Pattern.compile(
                "Date\\s+Symbol\\s+Security\\s+name\\s+ISIN\\s+Country\\s+Gross\\s+Amount\\s+Withholding\\s+Tax\\s+Net\\s+Amount");

        private static final Pattern PNL_STATEMENT_TRANSACTION_SECTION_END_PATTERN = Pattern.compile(
                "Total\\s+.*");
    }

    private final ProfitAndLossDividendLineParser profitAndLossDividendLineParser = new ProfitAndLossDividendLineParser();

    public TradingAccountTransactions parseProfitAndLossStatement(List<String> lines) {

        String accountName = null;
        String accountNumber = null;
        for (String line : lines) {
            line = stripToEmpty(line);
            if (line.isBlank()) {
                continue;
            }
            {
                Matcher matcher = LazyHolder.ACC_STATEMENT_ACCOUNT_NAME_PATTERN.matcher(line);
                if (matcher.find()) {
                    accountName = matcher.group("accountName");
                }
            }
            {
                Matcher matcher = LazyHolder.ACC_STATEMENT_ACCOUNT_NUMBER_PATTERN.matcher(line);
                if (matcher.find()) {
                    accountNumber = matcher.group("accountNumber");
                }

            }
            if (accountName != null && accountNumber != null) {
                break;
            }
        }
        if (accountName == null) {
            throw new IllegalStateException("Account name not found");
        }
        if (accountNumber == null) {
            throw new IllegalStateException("Account number not found");
        }

        List<Transaction> transactions = parseProfitAndLossStatementDividendTransactions(lines);

        TradingAccountTransactions tradingAccountTransactions = new TradingAccountTransactions();
        tradingAccountTransactions.setAccountName(accountName);
        tradingAccountTransactions.setAccountNumber(accountNumber);
        tradingAccountTransactions.setTransactions(transactions);

        return tradingAccountTransactions;
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
                    if (LazyHolder.PNL_STATEMENT_TRANSACTION_SECTION_START_PATTERN.matcher(line).matches()) {
                        dividendsLinesStarted = true;
                    }
                    continue;
                }
                if (LazyHolder.PNL_STATEMENT_TRANSACTION_HEADER_PATTERN.matcher(line).matches()) {
                    continue;
                }
                if (LazyHolder.PNL_STATEMENT_TRANSACTION_SECTION_END_PATTERN.matcher(line).matches()) {
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
