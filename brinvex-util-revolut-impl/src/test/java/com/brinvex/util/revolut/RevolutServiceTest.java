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
package com.brinvex.util.revolut;

import com.brinvex.util.revolut.api.model.Currency;
import com.brinvex.util.revolut.api.model.Holding;
import com.brinvex.util.revolut.api.model.PortfolioBreakdown;
import com.brinvex.util.revolut.api.model.PortfolioPeriod;
import com.brinvex.util.revolut.api.model.Transaction;
import com.brinvex.util.revolut.api.model.TransactionSide;
import com.brinvex.util.revolut.api.model.TransactionType;
import com.brinvex.util.revolut.api.service.RevolutService;
import com.brinvex.util.revolut.api.service.RevolutServiceFactory;
import com.brinvex.util.revolut.api.service.exception.NonContinuousPeriodsException;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.lang.System.out;
import static java.util.Comparator.comparing;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevolutServiceTest {

    private final RevolutService revolutSvc = RevolutServiceFactory.INSTANCE.getService();

    @Test
    void parseTradingAccountTransactions_oneTradingAccountStatement() {
        doIfFileExists("/trading-account-statement_2022-01-01_2023-01-11_en_bad2be.pdf", inputStream -> {
            PortfolioPeriod portfolioPeriod = revolutSvc.parseStatement(inputStream);
            out.println("accountNumber=" + portfolioPeriod.getAccountNumber());
            out.println("accountName=" + portfolioPeriod.getAccountName());
            List<Transaction> transactions = portfolioPeriod.getTransactions();
            for (int i = 0, transactionsSize = transactions.size(); i < transactionsSize; i++) {
                Transaction transaction = transactions.get(i);
                out.printf("Parsed transaction[%s]: %s\n", i, transaction);
            }
        });
    }

    @Test
    void parseTradingAccountTransactions_oneProfitAndLossStatement() {
        doIfFileExists("/profit-loss-statement/trading-pnl-statement_2021-01-01_2022-01-01_en-us_6e8044.pdf", inputStream -> {
            PortfolioPeriod portfolioPeriod = revolutSvc.parseStatement(inputStream);
            out.println("accountNumber=" + portfolioPeriod.getAccountNumber());
            out.println("accountName=" + portfolioPeriod.getAccountName());
            List<Transaction> transactions = portfolioPeriod.getTransactions();
            for (int i = 0, transactionsSize = transactions.size(); i < transactionsSize; i++) {
                Transaction transaction = transactions.get(i);
                out.printf("Parsed transaction[%s]: %s\n", i, transaction);
            }
        });
    }


    @Test
    void consolidate_oneAccountStatement() {
        doIfFileExists("/trading-account-statement_2022-01-01_2023-01-11_en_bad2be.pdf", inputStream -> {
            PortfolioPeriod ptfPeriod = revolutSvc.parseStatement(inputStream);
            Map<String, PortfolioPeriod> accountsToPtfPeriods = revolutSvc.consolidate(List.of(ptfPeriod));

            assertEquals(1, accountsToPtfPeriods.size());
            PortfolioPeriod accountPtfPeriod = accountsToPtfPeriods.values().iterator().next();

            assertTrue(accountPtfPeriod.getAccountNumber().startsWith("RE"));

            List<Transaction> transactions = accountPtfPeriod.getTransactions();
            assertEquals(ptfPeriod.getTransactions().size(), transactions.size());

            assertEquals(1, accountPtfPeriod.getPortfolioBreakdownSnapshots().size());
            assetBreakdownsAreConsistent(accountPtfPeriod.getPortfolioBreakdownSnapshots().values());

            assertTransactionsAreSorted(transactions);
            assertTransactionsAttributesAreConsistent(transactions);
        });
    }


    @Test
    void consolidate_manyEqualAccountStatements() {
        doIfFileExists("/trading-account-statement_2022-01-01_2023-01-11_en_bad2be.pdf", inputStream -> {
            PortfolioPeriod ptfPeriod = revolutSvc.parseStatement(inputStream);
            Map<String, PortfolioPeriod> accountsToPtfPeriods = revolutSvc.consolidate(
                    List.of(ptfPeriod, ptfPeriod));

            assertEquals(1, accountsToPtfPeriods.size());
            PortfolioPeriod accountPtfPeriod = accountsToPtfPeriods.values().iterator().next();

            assertTrue(accountPtfPeriod.getAccountNumber().startsWith("RE"));

            List<Transaction> transactions = accountPtfPeriod.getTransactions();
            assertEquals(ptfPeriod.getTransactions().size(), transactions.size());

            assertEquals(1, accountPtfPeriod.getPortfolioBreakdownSnapshots().size());
            assetBreakdownsAreConsistent(accountPtfPeriod.getPortfolioBreakdownSnapshots().values());

            assertTransactionsAreSorted(transactions);

            assertTransactionsAttributesAreConsistent(transactions);
        });
    }

    @Test
    void consolidate_nonContinuousPeriods() {
        doIfFilesExist(
                "trading-account-statement_2021-01-01_2021-12-31_en_b094bc.pdf",
                "trading-account-statement_2022-01-01_2022-12-31_en_7c6251.pdf",
                (inputStream1, inputStream2) -> {
                    PortfolioPeriod ptfPeriod1 = revolutSvc.parseStatement(inputStream1);
                    PortfolioPeriod ptfPeriod2 = revolutSvc.parseStatement(inputStream2);
                    assertThrows(NonContinuousPeriodsException.class, () -> revolutSvc.consolidate(List.of(ptfPeriod1, ptfPeriod2)));
                });
    }

    @Test
    void consolidate_manyAccountStatements() {
        doIfFilesExist(
                "trading-account-statement_2021-01-01_2022-01-31_en_2bda4d.pdf",
                "trading-account-statement_2022-01-01_2023-01-11_en_bad2be.pdf",
                (inputStream1, inputStream2) -> {
                    PortfolioPeriod ptfPeriod1 = revolutSvc.parseStatement(inputStream1);
                    PortfolioPeriod ptfPeriod2 = revolutSvc.parseStatement(inputStream2);
                    Map<String, PortfolioPeriod> accountsToPtfPeriods = revolutSvc.consolidate(List.of(ptfPeriod1, ptfPeriod2));

                    assertEquals(1, accountsToPtfPeriods.size());
                    PortfolioPeriod accountPtfPeriod = accountsToPtfPeriods.values().iterator().next();

                    assertTrue(accountPtfPeriod.getAccountNumber().startsWith("RE"));

                    List<Transaction> transactions = accountPtfPeriod.getTransactions();
                    assertTrue(transactions.size() >= ptfPeriod1.getTransactions().size());
                    assertTrue(transactions.size() >= ptfPeriod2.getTransactions().size());

                    assertEquals(2, accountPtfPeriod.getPortfolioBreakdownSnapshots().size());
                    assetBreakdownsAreConsistent(accountPtfPeriod.getPortfolioBreakdownSnapshots().values());

                    assertTransactionsAreSorted(transactions);

                    assertTransactionsAttributesAreConsistent(transactions);
                });
    }


    @Test
    void consolidate_manyProfitAndLosesStatements() {
        doIfFilesExist(
                "trading-pnl-statement_2021-01-01_2022-01-01_en-us_6e8044.pdf",
                "trading-pnl-statement_2022-01-01_2023-01-01_en-us_b1efb2.pdf",
                (inputStream1, inputStream2) -> {
                    PortfolioPeriod ptfPeriod1 = revolutSvc.parseStatement(inputStream1);
                    PortfolioPeriod ptfPeriod2 = revolutSvc.parseStatement(inputStream2);
                    Map<String, PortfolioPeriod> accountsToPtfPeriods = revolutSvc.consolidate(List.of(ptfPeriod1, ptfPeriod2));

                    assertEquals(1, accountsToPtfPeriods.size());
                    PortfolioPeriod accountPtfPeriod = accountsToPtfPeriods.values().iterator().next();

                    assertTrue(accountPtfPeriod.getAccountNumber().startsWith("RE"));

                    List<Transaction> transactions = accountPtfPeriod.getTransactions();
                    assertTrue(transactions.size() >= ptfPeriod1.getTransactions().size());
                    assertTrue(transactions.size() >= ptfPeriod2.getTransactions().size());

                    assertEquals(0, accountPtfPeriod.getPortfolioBreakdownSnapshots().size());
                    assetBreakdownsAreConsistent(accountPtfPeriod.getPortfolioBreakdownSnapshots().values());

                    assertTransactionsAreSorted(transactions);

                    for (Transaction dividend : transactions) {
                        assertNotNull(dividend.getDate());
                        assertNotNull(dividend.getSymbol());
                        assertNotNull(dividend.getSecurityName());
                        assertNotNull(dividend.getIsin());
                        assertNotNull(dividend.getCountry());
                        assertTrue(dividend.getGrossAmount().compareTo(BigDecimal.ZERO) > 0);
                        assertTrue(dividend.getWithholdingTax().compareTo(BigDecimal.ZERO) >= 0);
                        assertTrue(dividend.getValue().compareTo(BigDecimal.ZERO) > 0);
                        assertEquals(Currency.USD, dividend.getCurrency());
                    }
                });
    }

    @Test
    void consolidate_manyVariousStatements() {
        doIfFilesExist(
                "trading-account-statement_2021-01-01_2022-01-31_en_2bda4d.pdf",
                "trading-account-statement_2022-01-01_2022-12-31_en_7c6251.pdf",
                (inputStream1, inputStream2) -> doIfFilesExist(
                        "trading-pnl-statement_2021-01-01_2022-01-01_en-us_6e8044.pdf",
                        "trading-pnl-statement_2022-01-01_2023-01-01_en-us_b1efb2.pdf",
                        (inputStream3, inputStream4) -> {
                            PortfolioPeriod ptfPeriod1 = revolutSvc.parseStatement(inputStream1);
                            PortfolioPeriod ptfPeriod2 = revolutSvc.parseStatement(inputStream2);
                            PortfolioPeriod ptfPeriod3 = revolutSvc.parseStatement(inputStream3);
                            PortfolioPeriod ptfPeriod4 = revolutSvc.parseStatement(inputStream4);

                            Map<String, PortfolioPeriod> accountsToPtfPeriods = revolutSvc.consolidate(
                                    List.of(ptfPeriod1, ptfPeriod2, ptfPeriod3, ptfPeriod4));

                            assertEquals(1, accountsToPtfPeriods.size());
                            PortfolioPeriod accountPtfPeriod = accountsToPtfPeriods.values().iterator().next();

                            assertTrue(accountPtfPeriod.getAccountNumber().startsWith("RE"));

                            List<Transaction> transactions = accountPtfPeriod.getTransactions();
                            assertTrue(transactions.size() >= ptfPeriod1.getTransactions().size());
                            assertTrue(transactions.size() >= ptfPeriod2.getTransactions().size());
                            assertTrue(transactions.size() >= ptfPeriod3.getTransactions().size());
                            assertTrue(transactions.size() >= ptfPeriod4.getTransactions().size());

                            assertEquals(1, ptfPeriod1.getPortfolioBreakdownSnapshots().size());
                            assertEquals(1, ptfPeriod2.getPortfolioBreakdownSnapshots().size());
                            assertEquals(0, ptfPeriod3.getPortfolioBreakdownSnapshots().size());
                            assertEquals(0, ptfPeriod4.getPortfolioBreakdownSnapshots().size());
                            assertEquals(2, accountPtfPeriod.getPortfolioBreakdownSnapshots().size());

                            assetBreakdownsAreConsistent(ptfPeriod1.getPortfolioBreakdownSnapshots().values());
                            assetBreakdownsAreConsistent(ptfPeriod2.getPortfolioBreakdownSnapshots().values());
                            assetBreakdownsAreConsistent(ptfPeriod3.getPortfolioBreakdownSnapshots().values());
                            assetBreakdownsAreConsistent(ptfPeriod4.getPortfolioBreakdownSnapshots().values());
                            assetBreakdownsAreConsistent(accountPtfPeriod.getPortfolioBreakdownSnapshots().values());
                        }));
    }

    @Test
    void consolidate_unitPrice() {

        LocalDate now = LocalDate.now();
        BigDecimal quantity = new BigDecimal("500");
        BigDecimal declaredPrice = new BigDecimal("1.67");
        BigDecimal exactPrice = new BigDecimal("1.6688");
        BigDecimal value = new BigDecimal("835.48");
        BigDecimal fees = BigDecimal.ZERO;
        BigDecimal commission = new BigDecimal("1.08");

        PortfolioPeriod ptf = new PortfolioPeriod();
        ptf.setAccountNumber("test");
        ptf.setAccountName("test");
        ptf.setPeriodFrom(now);
        ptf.setPeriodTo(now);
        Transaction tran;
        {
            tran = new Transaction();
            tran.setType(TransactionType.TRADE_MARKET);
            tran.setSide(TransactionSide.BUY);
            tran.setQuantity(quantity);
            tran.setPrice(declaredPrice);
            tran.setValue(value);
            tran.setFees(fees);
            tran.setCommission(commission);
        }
        ptf.setTransactions(List.of(tran));

        PortfolioPeriod consolidatedPtf = revolutSvc.consolidate(List.of(ptf)).values().iterator().next();

        Transaction consTran = consolidatedPtf.getTransactions().get(0);
        assertEquals(declaredPrice, consTran.getPrice().setScale(2, RoundingMode.HALF_UP));
        assertEquals(0, exactPrice.compareTo(consTran.getPrice()));
        assertEquals(value, consTran.getValue());
        assertEquals(fees, consTran.getFees());
        assertEquals(commission, consTran.getCommission());

    }

    private void doIfFileExists(String filePath, Consumer<InputStream> test) {
        String testDataFolder = "c:/prj/brinvex/brinvex-dev1/test-data/brinvex-util-revolut-impl";
        File file = Path.of(testDataFolder, filePath).toFile();
        if (file.exists() && file.isFile()) {
            try (InputStream is = new FileInputStream(file)) {
                test.accept(is);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            out.printf("File not found: '%s'", file);
        }
    }

    private void doIfFilesExist(String filePath1, String filePath2, BiConsumer<InputStream, InputStream> test) {
        String testDataFolder = "c:/prj/brinvex/brinvex-dev1/test-data/brinvex-util-revolut-impl";
        File file1 = Path.of(testDataFolder, filePath1).toFile();
        if (!file1.exists() || !file1.isFile()) {
            out.printf("File not found: '%s'", file1);
            return;
        }
        File file2 = Path.of(testDataFolder, filePath2).toFile();
        if (!file2.exists() || !file2.isFile()) {
            out.printf("File not found: '%s'", file2);
            return;
        }
        try (InputStream is1 = new FileInputStream(file1)) {
            try (InputStream is2 = new FileInputStream(file2)) {
                test.accept(is1, is2);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void assertTransactionsAreSorted(List<Transaction> transactions) {
        List<Transaction> sortedTransactions = transactions
                .stream()
                .sorted(comparing(Transaction::getDate))
                .collect(Collectors.toList());
        assertEquals(sortedTransactions, transactions);
    }

    private void assertTransactionsAttributesAreConsistent(List<Transaction> transactions) {
        for (Transaction transaction : transactions) {
            assertNotNull(transaction.getValue());
            assertNotNull(transaction.getFees());
            assertNotNull(transaction.getCommission());
            assertEquals(Currency.USD, transaction.getCurrency());

            switch (transaction.getType()) {
                case CASH_TOP_UP: {
                    assertTrue(transaction.getValue().compareTo(BigDecimal.ZERO) > 0);
                    assertNull(transaction.getSymbol());
                    assertNull(transaction.getQuantity());
                    assertNull(transaction.getPrice());
                    assertNull(transaction.getSide());
                    break;
                }
                case CASH_WITHDRAWAL: {
                    assertTrue(transaction.getValue().compareTo(BigDecimal.ZERO) < 0);
                    assertNull(transaction.getSymbol());
                    assertNull(transaction.getQuantity());
                    assertNull(transaction.getPrice());
                    assertNull(transaction.getSide());
                    break;
                }
                case CUSTODY_FEE: {
                    assertTrue(transaction.getValue().compareTo(BigDecimal.ZERO) < 0);
                    assertNull(transaction.getQuantity());
                    assertNull(transaction.getPrice());
                    assertNull(transaction.getSide());
                    break;
                }
                case DIVIDEND: {
                    assertTrue(transaction.getValue().compareTo(BigDecimal.ZERO) > 0);
                    assertNull(transaction.getQuantity());
                    assertNull(transaction.getPrice());
                    assertNull(transaction.getSide());
                    break;
                }
                case SPINOFF:
                case STOCK_SPLIT: {
                    assertEquals(0, transaction.getValue().compareTo(BigDecimal.ZERO));
                    assertNotNull(transaction.getQuantity());
                    assertNull(transaction.getPrice());
                    assertNull(transaction.getSide());
                    break;
                }
                case TRADE_LIMIT:
                case TRADE_MARKET: {
                    assertTrue(transaction.getQuantity().compareTo(BigDecimal.ZERO) > 0);
                    assertTrue(transaction.getPrice().compareTo(BigDecimal.ZERO) > 0);
                    assertTrue(transaction.getValue().compareTo(BigDecimal.ZERO) > 0);
                }
            }
        }
    }

    private static void assetBreakdownsAreConsistent(Collection<PortfolioBreakdown> values) {
        for (PortfolioBreakdown portfolioBreakdown : values) {
            assertNotNull(portfolioBreakdown.getCash().get(Currency.USD));
            assertNotNull(portfolioBreakdown.getDate());
            for (Holding holding : portfolioBreakdown.getHoldings()) {
                assertNotNull(holding.getSymbol());
                assertNotNull(holding.getCompany());
                assertNotNull(holding.getIsin());
                assertNotNull(holding.getQuantity());
                assertNotNull(holding.getPrice());
                assertNotNull(holding.getValue());
                assertNotNull(holding.getCurrency());
            }
        }
    }


}
