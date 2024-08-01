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
import com.brinvex.util.revolut.api.model.PortfolioValue;
import com.brinvex.util.revolut.api.model.Transaction;
import com.brinvex.util.revolut.api.model.TransactionSide;
import com.brinvex.util.revolut.api.model.TransactionType;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.math.BigDecimal.ZERO;
import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.Optional.ofNullable;

@SuppressWarnings("DuplicatedCode")
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
        PortfolioPeriod somePtfPeriod = periods.get(0);

        try {
            return consolidateAccountPortfolioPeriods(periods);
        } catch (Exception ex) {
            if (ex instanceof RevolutServiceException) {
                throw ex;
            } else {
                throw new RuntimeException(String.format(
                        "account=%s/%s", somePtfPeriod.getAccountNumber(), somePtfPeriod.getAccountName()), ex);
            }
        }
    }

    @Override
    public Map<LocalDate, PortfolioValue> getPortfolioValues(Stream<Supplier<InputStream>> statementInputStreams) {
        List<PortfolioValue> ptfValues = statementInputStreams
                .map(inputStreamSupplier -> {
                    try (InputStream is = inputStreamSupplier.get()) {
                        return getPortfolioValues(is);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .flatMap(Collection::stream)
                .toList();

        TreeMap<LocalDate, PortfolioValue> results = new TreeMap<>();
        for (PortfolioValue ptfValue : ptfValues) {
            LocalDate day = ptfValue.getDay();
            results.putIfAbsent(day, ptfValue);
        }
        return results;
    }

    private List<PortfolioValue> getPortfolioValues(InputStream inputStream) {
        List<String> lines = pdfReader.readPdfLines(inputStream);

        String accountStatementTitle = "Account Statement";
        String profitAndLossTitle1 = "Profit and Loss Statement";
        String profitAndLossTitle2 = "EUR Profit and Loss Statement";

        String line0 = lines.get(0);
        String line1 = lines.get(1);
        List<PortfolioValue> portfolioValues;
        if (accountStatementTitle.equals(line0) || accountStatementTitle.equals(line1)) {
            portfolioValues = accountStatementParser.parsePortfolioValueFromTradingAccountStatement(lines);
        } else if (profitAndLossTitle1.equals(line0) || profitAndLossTitle1.equals(line1)
                || (profitAndLossTitle2.equals(line0) || profitAndLossTitle2.equals(line1))
        ) {
            //no-op
            portfolioValues = emptyList();
        } else {
            throw new IllegalArgumentException(String.format("Could not detect statement type '%s', '%s'", line0, line1));
        }

        return portfolioValues;
    }

    private PortfolioPeriod parseStatement(InputStream inputStream) {

        List<String> lines = pdfReader.readPdfLines(inputStream);

        String accountStatementTitle = "Account Statement";
        String profitAndLossTitle1 = "Profit and Loss Statement";
        String profitAndLossTitle2 = "EUR Profit and Loss Statement";

        String line0 = lines.get(0);
        String line1 = lines.get(1);
        PortfolioPeriod portfolioPeriod;
        if (accountStatementTitle.equals(line0) || accountStatementTitle.equals(line1)) {
            portfolioPeriod = accountStatementParser.parseTradingAccountStatement(lines);
        } else if (profitAndLossTitle1.equals(line0) || profitAndLossTitle1.equals(line1)
                || (profitAndLossTitle2.equals(line0) || profitAndLossTitle2.equals(line1))
        ) {
            portfolioPeriod = profitAndLossStatementParser.parseProfitAndLossStatement(lines);
        } else {
            throw new IllegalArgumentException(String.format("Could not detect statement type '%s', '%s'", line0, line1));
        }

        return portfolioPeriod;
    }

    private PortfolioPeriod consolidateAccountPortfolioPeriods(List<PortfolioPeriod> accountPortfolioPeriods) {
        accountPortfolioPeriods.sort(comparing(PortfolioPeriod::getPeriodFrom).thenComparing(PortfolioPeriod::getPeriodTo));

        PortfolioPeriod result = new PortfolioPeriod();
        Set<String> accountNumbers = new LinkedHashSet<>();
        Set<String> accountNames = new LinkedHashSet<>();
        {
            PortfolioPeriod portfolioPeriod0 = accountPortfolioPeriods.get(0);
            result.setPeriodFrom(portfolioPeriod0.getPeriodFrom());
            result.setPeriodTo(portfolioPeriod0.getPeriodTo());
        }

        Map<Object, Transaction> transactions = new LinkedHashMap<>();
        Map<Object, Transaction> dividendTransactions = new LinkedHashMap<>();
        Map<LocalDate, PortfolioBreakdown> breakdowns = new LinkedHashMap<>();

        for (PortfolioPeriod portfolioPeriod : accountPortfolioPeriods) {
            String accountNumber = portfolioPeriod.getAccountNumber();
            String accountName = portfolioPeriod.getAccountName();
            accountNumbers.add(accountNumber);
            accountNames.add(accountName);

            LocalDate periodFrom = portfolioPeriod.getPeriodFrom();
            LocalDate periodTo = portfolioPeriod.getPeriodTo();

            LocalDate nextPeriodFrom = result.getPeriodTo().plusDays(1);
            if (nextPeriodFrom.isBefore(periodFrom)) {
                throw new InvalidStatementException(String.format(
                        "accountNumber=%s, accountName='%s', missingPeriod='%s - %s'",
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
                for (int i = 0, n = periodTransactions.size(); i < n; i++) {
                    Transaction tran = periodTransactions.get(i);
                    Transaction nextTran = i == n - 1 ? null : periodTransactions.get(i + 1);

                    TransactionType tranType = tran.getType();
                    if (tranType.equals(TransactionType.DIVIDEND)) {
                        if (nextTran != null
                                && nextTran.getType().equals(TransactionType.DIVIDEND)
                                && nextTran.getSymbol().equals(tran.getSymbol())
                                && nextTran.getDate().equals(tran.getDate())
                                && Objects.equals(nextTran.getIsin(), tran.getIsin())
                                && Objects.equals(nextTran.getSecurityName(), tran.getSecurityName())
                                && Objects.equals(nextTran.getCountry(), tran.getCountry())
                                && Objects.equals(nextTran.getCurrency(), tran.getCurrency())
                        ) {
                            if (tran.getGrossAmount() != null && nextTran.getGrossAmount() != null) {
                                tran.setGrossAmount(tran.getGrossAmount().add(nextTran.getGrossAmount()));
                            }
                            if (tran.getWithholdingTax() != null && nextTran.getWithholdingTax() != null) {
                                tran.setWithholdingTax(tran.getWithholdingTax().add(nextTran.getWithholdingTax()));
                            }
                            if (tran.getValue() != null && nextTran.getValue() != null) {
                                tran.setValue(tran.getValue().add(nextTran.getValue()));
                            }
                            if (tran.getFees() != null && nextTran.getFees() != null) {
                                tran.setFees(tran.getFees().add(nextTran.getFees()));
                            }
                            if (tran.getCommission() != null && nextTran.getCommission() != null) {
                                tran.setCommission(tran.getCommission().add(nextTran.getCommission()));
                            }
                            i++;
                        }
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
        result.setAccountNumber(String.join(",", accountNumbers));
        result.setAccountName(String.join(",", accountNames));
        result.setPortfolioBreakdownSnapshots(new TreeMap<>(breakdowns));

        result.setTransactions(transactions.values()
                .stream()
                .sorted(comparing(Transaction::getDate))
                .collect(Collectors.toCollection(ArrayList::new))
        );

        return result;
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
