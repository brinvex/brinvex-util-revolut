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

import com.brinvex.util.revolut.api.model.PortfolioBreakdown;
import com.brinvex.util.revolut.api.service.RevolutService;
import com.brinvex.util.revolut.api.model.PortfolioPeriod;
import com.brinvex.util.revolut.api.model.Transaction;
import com.brinvex.util.revolut.api.model.TransactionType;
import com.brinvex.util.revolut.api.service.exception.NonContinuousPeriodsException;
import com.brinvex.util.revolut.api.service.exception.RevolutServiceException;
import com.brinvex.util.revolut.impl.parser.AccountStatementParser;
import com.brinvex.util.revolut.impl.parser.ProfitAndLossStatementParser;
import com.brinvex.util.revolut.impl.pdfreader.PdfReader;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;

public class RevolutServiceImpl implements RevolutService {

    private final PdfReader pdfReader = new PdfReader();

    private final AccountStatementParser accountStatementParser = new AccountStatementParser();

    private final ProfitAndLossStatementParser profitAndLossStatementParser = new ProfitAndLossStatementParser();

    @Override
    public PortfolioPeriod parseStatement(InputStream inputStream) {

        List<String> lines = pdfReader.readPdfLines(inputStream);

        String accountStatementTitle = "Account Statement";
        String profitAndLossTitle = "Profit and Loss Statement";

        String line0 = lines.get(0);
        String line1 = lines.get(1);
        PortfolioPeriod portfolioPeriod;
        if (accountStatementTitle.equals(line0) || accountStatementTitle.equals(line1)) {
            portfolioPeriod = accountStatementParser.parseTradingAccountStatement(lines);
        } else if (profitAndLossTitle.equals(line0) || profitAndLossTitle.equals(line1)) {
            portfolioPeriod = profitAndLossStatementParser.parseProfitAndLossStatement(lines);
        } else {
            throw new IllegalArgumentException(String.format("Could not detect statement type '%s', '%s'", line0, line1));
        }

        return portfolioPeriod;
    }

    @Override
    public Map<String, PortfolioPeriod> consolidate(Collection<PortfolioPeriod> portfolioPeriods) {
        Map<String, List<PortfolioPeriod>> ptfPeriodsByAccount = groupPortfolioPeriodsByAccount(portfolioPeriods);

        Map<String, PortfolioPeriod> consolidatedPtfPeriods = new LinkedHashMap<>();
        for (Map.Entry<String, List<PortfolioPeriod>> e : ptfPeriodsByAccount.entrySet()) {
            String accountNumber = e.getKey();
            List<PortfolioPeriod> accountPortfolioPeriods = e.getValue();
            try {
                PortfolioPeriod portfolioPeriod = consolidateAccountPortfolioPeriods(accountPortfolioPeriods);
                consolidatedPtfPeriods.put(accountNumber, portfolioPeriod);
            } catch (Exception ex) {
                if (ex instanceof RevolutServiceException) {
                    throw ex;
                } else {
                    throw new IllegalArgumentException(String.format("accountNumber=%s", accountNumber), ex);
                }
            }
        }
        return consolidatedPtfPeriods;
    }

    private PortfolioPeriod consolidateAccountPortfolioPeriods(List<PortfolioPeriod> accountPortfolioPeriods) {
        accountPortfolioPeriods.sort(comparing(PortfolioPeriod::getPeriodFrom).thenComparing(PortfolioPeriod::getPeriodTo));

        PortfolioPeriod result = new PortfolioPeriod();
        String accountNumber;
        String accountName;
        {
            PortfolioPeriod portfolioPeriod0 = accountPortfolioPeriods.get(0);
            accountNumber = portfolioPeriod0.getAccountNumber();
            accountName = portfolioPeriod0.getAccountName();
            result.setAccountNumber(accountNumber);
            result.setAccountName(accountName);
            result.setPeriodFrom(portfolioPeriod0.getPeriodFrom());
            result.setPeriodTo(portfolioPeriod0.getPeriodTo());
        }

        Map<Object, Transaction> transactions = new LinkedHashMap<>();
        Map<Object, Transaction> dividendTransactions = new LinkedHashMap<>();
        Map<LocalDate, PortfolioBreakdown> breakdowns = new LinkedHashMap<>();

        for (PortfolioPeriod portfolioPeriod : accountPortfolioPeriods) {
            LocalDate periodFrom = portfolioPeriod.getPeriodFrom();
            LocalDate periodTo = portfolioPeriod.getPeriodTo();

            LocalDate nextPeriodFrom = result.getPeriodTo().plusDays(1);
            if (nextPeriodFrom.isBefore(periodFrom)) {
                throw new NonContinuousPeriodsException(String.format(
                        "accountNumber=%s, accountName='%s', missingPortfolioPeriod='%s - %s'",
                            accountNumber, accountName, nextPeriodFrom, periodFrom.minusDays(1)));
            }
            if (periodTo.isAfter(result.getPeriodTo())) {
                result.setPeriodTo(periodTo);
            }

            breakdowns.putAll(portfolioPeriod.getPortfolioBreakdownSnapshots());

            List<Transaction> periodTransactions = portfolioPeriod.getTransactions();
            if (periodTransactions != null) {
                for (Transaction transaction : periodTransactions) {
                    Object transactionIdentityKey = constructTransactionIdentityKey(transaction);

                    if (transaction.getType().equals(TransactionType.DIVIDEND)) {
                        Object dividendTransactionIdentityKey = constructDividendTransactionIdentityKey(transaction);
                        Transaction oldDivTransaction = dividendTransactions.get(dividendTransactionIdentityKey);
                        if (oldDivTransaction == null) {
                            dividendTransactions.put(dividendTransactionIdentityKey, transaction);
                            transactions.put(transactionIdentityKey, transaction);
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
                        transactions.put(transactionIdentityKey, transaction);
                    }
                }
            }
        }
        result.setPortfolioBreakdownSnapshots(new TreeMap<>(breakdowns));

        result.setTransactions(transactions.values()
                .stream()
                .sorted(comparing(Transaction::getDate))
                .collect(Collectors.toCollection(ArrayList::new))
        );

        return result;
    }

    private Map<String, List<PortfolioPeriod>> groupPortfolioPeriodsByAccount(Collection<PortfolioPeriod> portfolioPeriods) {
        Map<String, List<PortfolioPeriod>> ptfPeriodsByAccount = new LinkedHashMap<>();
        for (PortfolioPeriod portfolioPeriod : portfolioPeriods) {
            String accountNumber = portfolioPeriod.getAccountNumber();
            String accountName = portfolioPeriod.getAccountName();
            List<PortfolioPeriod> accountPortfolioPeriods = ptfPeriodsByAccount.computeIfAbsent(accountNumber, k -> new ArrayList<>());
            accountPortfolioPeriods.add(portfolioPeriod);
            String accountName0 = accountPortfolioPeriods.get(0).getAccountName();
            if (!accountName0.equals(accountName)) {
                throw new IllegalArgumentException(String.format("accountName mismatch: '%s' != '%s', accountNumber=%s",
                        accountName0, accountName, accountNumber
                ));
            }
        }
        return ptfPeriodsByAccount;
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
