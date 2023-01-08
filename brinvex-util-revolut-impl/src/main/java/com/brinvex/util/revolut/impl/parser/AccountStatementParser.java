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
public class AccountStatementParser {

    private static class LazyHolder {

        private static final Pattern ACC_STATEMENT_ACCOUNT_NAME_PATTERN = Pattern.compile(
                "Account\\s+name\\s+(?<accountName>.+)");

        private static final Pattern ACC_STATEMENT_ACCOUNT_NUMBER_PATTERN = Pattern.compile(
                "Account\\s+number\\s+(?<accountNumber>.+)");

        private static final Pattern ACC_STATEMENT_TRANSACTION_SECTION_START_PATTERN = Pattern.compile(
                "Transactions");

        private static final Pattern ACC_STATEMENT_TRANSACTION_HEADER_PATTERN = Pattern.compile(
                "Date\\s+Symbol\\s+Type\\s+Quantity\\s+Price\\s+Side\\s+Value\\s+Fees\\s+Commission");

        private static final Pattern ACC_STATEMENT_TRANSACTION_SECTION_END_PATTERN = Pattern.compile(
                "Report\\s+lost\\s+or\\s+stolen\\s+card");
    }

    private final AccountStatementTransactionLineParser accStatementTransactionLineParser = new AccountStatementTransactionLineParser();

    public TradingAccountTransactions parseTradingAccountStatement(List<String> lines) {
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

        List<Transaction> transactions = parseTradingAccountStatementTransactions(lines);

        TradingAccountTransactions tradingAccountTransactions = new TradingAccountTransactions();
        tradingAccountTransactions.setAccountName(accountName);
        tradingAccountTransactions.setAccountNumber(accountNumber);
        tradingAccountTransactions.setTransactions(transactions);

        return tradingAccountTransactions;
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
                    if (LazyHolder.ACC_STATEMENT_TRANSACTION_SECTION_START_PATTERN.matcher(line).matches()) {
                        transactionLinesStarted = true;
                    }
                    continue;
                }
                if (LazyHolder.ACC_STATEMENT_TRANSACTION_HEADER_PATTERN.matcher(line).matches()) {
                    continue;
                }
                if (LazyHolder.ACC_STATEMENT_TRANSACTION_SECTION_END_PATTERN.matcher(line).matches()) {
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

    private String stripToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

}
