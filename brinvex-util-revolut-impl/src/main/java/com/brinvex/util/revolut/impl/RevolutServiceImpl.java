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
import com.brinvex.util.revolut.api.model.PortfolioPeriod;
import com.brinvex.util.revolut.api.model.Transaction;
import com.brinvex.util.revolut.api.model.TransactionType;
import com.brinvex.util.revolut.api.model.TransactionSide;
import com.brinvex.util.revolut.api.service.RevolutService;
import com.brinvex.util.revolut.api.service.exception.InvalidDataException;
import com.brinvex.util.revolut.api.service.exception.InvalidStatementException;
import com.brinvex.util.revolut.api.service.exception.RevolutServiceException;
import com.brinvex.util.revolut.impl.parser.AccountStatementParser;
import com.brinvex.util.revolut.impl.parser.ProfitAndLossStatementParser;
import com.brinvex.util.revolut.impl.pdfreader.PdfReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.math.BigDecimal.ZERO;
import static java.util.Comparator.comparing;
import static java.util.Optional.ofNullable;

public class RevolutServiceImpl implements RevolutService {

    private final PdfReader pdfReader = new PdfReader();

    private final AccountStatementParser accountStatementParser = new AccountStatementParser();

    private final ProfitAndLossStatementParser profitAndLossStatementParser = new ProfitAndLossStatementParser();

    @Override
    public PortfolioPeriod processStatements(Stream<Supplier<InputStream>> statementInputStreams) {
        List<PortfolioPeriod> periods = statementInputStreams
                .map(inputStreamSupplier -> {
                    try (InputStream is = inputStreamSupplier.get()) {
                        return parseStatement(is);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .collect(Collectors.toList());
        if (periods.isEmpty()) {
            return null;
        }
        Iterator<List<PortfolioPeriod>> petfPeriodPerAccountIterator = groupPortfolioPeriodsByAccount(periods).values().iterator();
        List<PortfolioPeriod> portfolioPeriods = petfPeriodPerAccountIterator.next();
        PortfolioPeriod somePtfPeriod = portfolioPeriods.get(0);
        if (petfPeriodPerAccountIterator.hasNext()) {
            PortfolioPeriod otherPtfPeriod = petfPeriodPerAccountIterator.next().get(0);
            throw new InvalidStatementException(String.format("Unexpected multiple accounts: %s/%s, %s/%s",
                    somePtfPeriod.getAccountNumber(),
                    somePtfPeriod.getAccountName(),
                    otherPtfPeriod.getAccountNumber(),
                    otherPtfPeriod.getAccountName()
            ));
        }

        try {
            return consolidateAccountPortfolioPeriods(portfolioPeriods);
        } catch (Exception ex) {
            if (ex instanceof RevolutServiceException) {
                throw ex;
            } else {
                throw new RuntimeException(String.format(
                        "account=%s/%s", somePtfPeriod.getAccountNumber(), somePtfPeriod.getAccountName()), ex);
            }
        }
    }

    private PortfolioPeriod parseStatement(InputStream inputStream) {

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
                throw new InvalidStatementException(String.format(
                        "accountNumber=%s, accountName='%s', missingPortfolioPeriod='%s - %s'",
                        accountNumber, accountName, nextPeriodFrom, periodFrom.minusDays(1)));
            }
            if (periodTo.isAfter(result.getPeriodTo())) {
                result.setPeriodTo(periodTo);
            }

            Map<LocalDate, PortfolioBreakdown> breakdownSnapshots = portfolioPeriod.getPortfolioBreakdownSnapshots();
            if (breakdownSnapshots != null) {
                breakdowns.putAll(breakdownSnapshots);
            }

            List<Transaction> periodTransactions = portfolioPeriod.getTransactions();
            if (periodTransactions != null) {
                for (Transaction tran : periodTransactions) {

                    TransactionType tranType = tran.getType();
                    if (tranType.equals(TransactionType.DIVIDEND)) {
                        Object divTranKey = constructDividendTransactionIdentityKey(tran);
                        Transaction oldDivTran = dividendTransactions.get(divTranKey);
                        if (oldDivTran != null) {
                            transactions.remove(constructTransactionIdentityKey(oldDivTran));
                            if (tran.getDate().toLocalTime().equals(LocalTime.MIN)) {
                                tran.setDate(oldDivTran.getDate());
                            }
                            tran.setSecurityName(coalesce(tran.getSecurityName(), oldDivTran.getSecurityName()));
                            tran.setIsin(coalesce(tran.getIsin(), oldDivTran.getIsin()));
                            tran.setCountry(coalesce(tran.getCountry(), oldDivTran.getCountry()));
                            tran.setCurrency(coalesce(tran.getCurrency(), oldDivTran.getCurrency()));
                            tran.setGrossAmount(coalesce(tran.getGrossAmount(), oldDivTran.getGrossAmount()));
                            tran.setWithholdingTax(coalesce(tran.getWithholdingTax(), oldDivTran.getWithholdingTax()));
                            tran.setValue(coalesce(tran.getValue(), oldDivTran.getValue()));
                            tran.setFees(coalesce(tran.getFees(), oldDivTran.getFees()));
                            tran.setCommission(coalesce(tran.getCommission(), oldDivTran.getCommission()));
                        }
                        dividendTransactions.put(divTranKey, tran);
                    } else if (tranType.equals(TransactionType.TRADE_MARKET)) {
                        TransactionSide side = tran.getSide();
                        BigDecimal quantity = tran.getQuantity();
                        BigDecimal fees = ofNullable(tran.getFees()).orElse(ZERO);
                        BigDecimal commission = ofNullable(tran.getCommission()).orElse(ZERO);
                        if (fees.compareTo(ZERO) < 0) {
                            throw new InvalidDataException(String.format("Commission can not be negative: %s", tran));
                        }
                        if (commission.compareTo(ZERO) < 0) {
                            throw new InvalidDataException(String.format("Fees can not be negative: %s", tran));
                        }
                        BigDecimal feesAndCommission = fees.add(commission);

                        BigDecimal tradedValue;
                        if (side == TransactionSide.BUY) {
                            tradedValue = tran.getValue().subtract(feesAndCommission);
                        } else if (side == TransactionSide.SELL) {
                            tradedValue = tran.getValue().add(feesAndCommission);
                        } else {
                            throw new AssertionError(side);
                        }
                        BigDecimal tradedPrice = tradedValue.divide(quantity, 8, RoundingMode.HALF_UP);

                        BigDecimal declaredPrice = tran.getPrice();
                        BigDecimal delta = tradedPrice.subtract(declaredPrice).abs();
                        if (delta.compareTo(new BigDecimal("0.005")) > 0) {
                            throw new InvalidDataException(String.format(
                                    "Suspicious delta=%s calculated from price=%s, quantity=%s, fees=%s, commission=%s, %s",
                                    delta, declaredPrice, quantity, fees, commission, tran));
                        }
                        tran.setPrice(tradedPrice);
                    }

                    Object tranKey = constructTransactionIdentityKey(tran);
                    transactions.put(tranKey, tran);
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
                setScale(transaction.getQuantity(), 8),
                setScale(transaction.getPrice(), 2),
                transaction.getSide(),
                setScale(transaction.getValue(), 2),
                setScale(transaction.getFees(), 2),
                setScale(transaction.getCommission(), 2)
        );
    }

    private Object constructDividendTransactionIdentityKey(Transaction transaction) {
        return Arrays.asList(
                transaction.getDate().toLocalDate(),
                transaction.getSymbol(),
                setScale(transaction.getValue(), 2)
        );
    }

    private BigDecimal setScale(BigDecimal d, int newScale) {
        return d == null ? null : d.setScale(newScale, RoundingMode.HALF_UP);
    }

    private <T> T coalesce(T object1, T object2) {
        return object1 != null ? object1 : object2;
    }


}
