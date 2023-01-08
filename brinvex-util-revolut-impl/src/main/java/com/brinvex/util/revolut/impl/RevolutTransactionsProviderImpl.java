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
package com.brinvex.util.revolut.impl;

import com.brinvex.util.revolut.api.RevolutTransactionsProvider;
import com.brinvex.util.revolut.api.model.TradingAccountTransactions;
import com.brinvex.util.revolut.api.model.Transaction;
import com.brinvex.util.revolut.api.model.TransactionType;
import com.brinvex.util.revolut.impl.parser.AccountStatementParser;
import com.brinvex.util.revolut.impl.parser.ProfitAndLossStatementParser;
import com.brinvex.util.revolut.impl.pdfreader.PdfReader;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RevolutTransactionsProviderImpl implements RevolutTransactionsProvider {

    private final PdfReader pdfReader = new PdfReader();

    private final AccountStatementParser accountStatementParser = new AccountStatementParser();

    private final ProfitAndLossStatementParser profitAndLossStatementParser = new ProfitAndLossStatementParser();

    @Override
    public TradingAccountTransactions parseTradingAccountTransactions(InputStream inputStream) {

        List<String> lines = pdfReader.readPdfLines(inputStream);

        String accountStatementTitle = "Account Statement";
        String profitAndLossTitle = "Profit and Loss Statement";

        String line0 = lines.get(0);
        String line1 = lines.get(1);
        TradingAccountTransactions taTransactions;
        if (accountStatementTitle.equals(line0) || accountStatementTitle.equals(line1)) {
            taTransactions = accountStatementParser.parseTradingAccountStatement(lines);
        } else if (profitAndLossTitle.equals(line0) || profitAndLossTitle.equals(line1)) {
            taTransactions = profitAndLossStatementParser.parseProfitAndLossStatement(lines);
        } else {
            throw new IllegalArgumentException(String.format("Could not detect statement '%s', '%s'", line0, line1));
        }

        return taTransactions;
    }

    @Override
    public Map<String, TradingAccountTransactions> consolidateTradingAccountTransactions(Collection<TradingAccountTransactions> transactionsChunks) {

        Map<String, String> accountNames = new LinkedHashMap<>();
        Map<String, Map<Object, Transaction>> accountTransactions = new LinkedHashMap<>();
        Map<String, Map<Object, Transaction>> accountDividendTransactions = new LinkedHashMap<>();

        for (TradingAccountTransactions transactionsChunk : transactionsChunks) {
            String accountNumber = transactionsChunk.getAccountNumber();
            String accountName = transactionsChunk.getAccountName();

            accountNames.merge(accountNumber, accountName, (accountName1, accountName2) -> {
                if (accountName1.equals(accountName2)) {
                    return accountName1;
                }
                throw new IllegalArgumentException(String.format("Account name mismatch: '%s' != '%s', accountNumber=%s",
                        accountName1, accountName2, accountNumber
                ));
            });

            Map<Object, Transaction> transactions = accountTransactions.computeIfAbsent(accountNumber, k -> new LinkedHashMap<>());
            Map<Object, Transaction> dividendTransactions = accountDividendTransactions.computeIfAbsent(accountNumber, k -> new LinkedHashMap<>());

            for (Transaction transaction : transactionsChunk.getTransactions()) {
                Object transactionIdentityKey = constructTransactionIdentityKey(transaction);

                if (transaction.getType().equals(TransactionType.DIVIDEND)) {
                    Object dividendTransactionIdentityKey = constructDividendTransactionIdentityKey(transaction);
                    Transaction oldDivTransaction = dividendTransactions.get(dividendTransactionIdentityKey);
                    if (oldDivTransaction == null) {
                        dividendTransactions.put(dividendTransactionIdentityKey, transaction);
                        transactions.putIfAbsent(transactionIdentityKey, transaction);
                    } else {
                        if (oldDivTransaction.getDate().toLocalTime().equals(LocalTime.MIN)) {
                            oldDivTransaction.setDate(transaction.getDate());
                        }
                        oldDivTransaction.setSecurityName(coalesce(oldDivTransaction.getSecurityName(), transaction.getSecurityName()));
                        oldDivTransaction.setIsin(coalesce(oldDivTransaction.getIsin(), transaction.getIsin()));
                        oldDivTransaction.setCountry(coalesce(oldDivTransaction.getCountry(), transaction.getCountry()));
                        oldDivTransaction.setCurrency(coalesce(oldDivTransaction.getCurrency(), transaction.getCurrency()));
                        oldDivTransaction.setGrossAmount(coalesce(oldDivTransaction.getGrossAmount(), transaction.getGrossAmount()));
                        oldDivTransaction.setWithholdingTax(coalesce(oldDivTransaction.getWithholdingTax(), transaction.getWithholdingTax()));
                        oldDivTransaction.setValue(coalesce(oldDivTransaction.getValue(), transaction.getValue()));
                        oldDivTransaction.setFees(coalesce(oldDivTransaction.getFees(), transaction.getFees()));
                        oldDivTransaction.setCommission(coalesce(oldDivTransaction.getFees(), transaction.getCommission()));
                    }
                } else {
                    transactions.putIfAbsent(transactionIdentityKey, transaction);
                }
            }
        }

        LinkedHashMap<String, TradingAccountTransactions> results = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : accountNames.entrySet()) {
            String accountNumber = e.getKey();
            String accountName = e.getValue();
            List<Transaction> transactions = accountTransactions.get(accountNumber)
                    .values()
                    .stream()
                    .sorted(Comparator.comparing(Transaction::getDate))
                    .collect(Collectors.toCollection(ArrayList::new));

            TradingAccountTransactions tradingAccountTransactions = new TradingAccountTransactions();
            results.put(accountNumber, tradingAccountTransactions);
            tradingAccountTransactions.setAccountNumber(accountNumber);
            tradingAccountTransactions.setAccountName(accountName);
            tradingAccountTransactions.setTransactions(transactions);
        }

        return results;
    }

    private Object constructTransactionIdentityKey(Transaction transaction) {
        return Arrays.asList(
                transaction.getType(),
                transaction.getDate(),
                transaction.getSymbol(),
                normalizeBigDecimalScale(transaction.getQuantity(), 8),
                normalizeBigDecimalScale(transaction.getPrice(), 2),
                transaction.getSide(),
                normalizeBigDecimalScale(transaction.getValue(), 2),
                normalizeBigDecimalScale(transaction.getFees(), 2),
                normalizeBigDecimalScale(transaction.getCommission(), 2),
                transaction.getCurrency()
        );
    }

    private Object constructDividendTransactionIdentityKey(Transaction transaction) {
        return Arrays.asList(
                transaction.getDate().toLocalDate(),
                transaction.getSymbol(),
                normalizeBigDecimalScale(transaction.getValue(), 2),
                transaction.getCurrency()
        );
    }

    private BigDecimal normalizeBigDecimalScale(BigDecimal d, int newScale) {
        return d == null ? null : d.setScale(newScale, RoundingMode.HALF_UP);
    }

    private <T> T coalesce(T object1, T object2) {
        return object1 != null ? object1 : object2;
    }


}
