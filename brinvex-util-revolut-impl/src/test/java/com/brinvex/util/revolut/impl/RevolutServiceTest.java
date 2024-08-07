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

import com.brinvex.util.revolut.api.model.Currency;
import com.brinvex.util.revolut.api.model.Holding;
import com.brinvex.util.revolut.api.model.PortfolioBreakdown;
import com.brinvex.util.revolut.api.model.PortfolioPeriod;
import com.brinvex.util.revolut.api.model.PortfolioValue;
import com.brinvex.util.revolut.api.model.Transaction;
import com.brinvex.util.revolut.api.model.TransactionType;
import com.brinvex.util.revolut.api.service.RevolutService;
import com.brinvex.util.revolut.api.service.RevolutServiceFactory;
import com.brinvex.util.revolut.api.service.exception.InvalidStatementException;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.Month;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.System.out;
import static java.util.Comparator.comparing;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevolutServiceTest {

    private static final String TEST_DATA_FOLDER = "c:/prj/bx-util/bx-util-revolut/test-data";

    private final RevolutService revolutSvc = RevolutServiceFactory.INSTANCE.getService();

    @Test
    void processStatements_parse1() {
        List<Path> testFilePaths = getTestFilePaths();
        if (!testFilePaths.isEmpty()) {
            LocalDate properDataEnd = LocalDate.parse("2024-01-01");
            PortfolioPeriod portfolioPeriod = revolutSvc.processStatements(testFilePaths);
            assertNotNull(portfolioPeriod);
            out.printf("%s/%s%n", portfolioPeriod.getAccountNumber(), portfolioPeriod.getAccountName());
            List<Transaction> transactions = portfolioPeriod.getTransactions();
            assertFalse(transactions.isEmpty());
            for (int i = 0, transactionsSize = transactions.size(); i < transactionsSize; i++) {
                Transaction transaction = transactions.get(i);
                out.printf("[%s]: %s\n", i, transaction);
                if (transaction.getType().equals(TransactionType.DIVIDEND)) {
                    if (transaction.getDate().toLocalDate().isBefore(properDataEnd)) {
                        assertNotNull(transaction.getWithholdingTax(), transaction::toString);
                    }
                }
            }
        }
    }

    @Test
    void processStatements_parse2() {
        List<Path> testFilePaths = getTestFilePaths(f -> f.contains("trading-pnl-statement"));
        if (!testFilePaths.isEmpty()) {
            PortfolioPeriod portfolioPeriod = revolutSvc.processStatements(testFilePaths);
            assertNotNull(portfolioPeriod);
            out.printf("%s/%s%n", portfolioPeriod.getAccountNumber(), portfolioPeriod.getAccountName());
            List<Transaction> transactions = portfolioPeriod.getTransactions();
            assertFalse(transactions.isEmpty());
            BigDecimal tolerance = new BigDecimal("0.01");
            BigDecimal taxRatioMin = new BigDecimal("0.01");
            BigDecimal taxRatioMax = new BigDecimal("0.45");
            for (int i = 0, transactionsSize = transactions.size(); i < transactionsSize; i++) {
                Transaction transaction = transactions.get(i);
                out.printf("[%s]: %s\n", i, transaction);
                if (transaction.getType().equals(TransactionType.DIVIDEND)) {
                    BigDecimal withholdingTax = transaction.getWithholdingTax();
                    BigDecimal grossAmount = transaction.getGrossAmount();
                    BigDecimal value = transaction.getValue();
                    assertNotNull(withholdingTax, transaction::toString);
                    assertNotNull(transaction.getIsin(), transaction::toString);
                    assertTrue(grossAmount.subtract(withholdingTax).subtract(value).abs().compareTo(tolerance) < 0, transaction::toString);

                    if (withholdingTax.compareTo(BigDecimal.ZERO) != 0) {
                        BigDecimal taxPct = withholdingTax.divide(grossAmount, 2, RoundingMode.HALF_UP);
                        assertTrue(taxPct.compareTo(taxRatioMin) > 0, transaction::toString);
                        assertTrue(taxPct.compareTo(taxRatioMax) < 0, transaction::toString);
                    }
                }
            }
        }
    }

    @Test
    void processStatements_parse4() {
        List<Path> testFilePaths = getTestFilePaths(f -> f.contains("trading-pnl-statement_2024-01-01_2024-07-02"));
        if (!testFilePaths.isEmpty()) {
            PortfolioPeriod portfolioPeriod = revolutSvc.processStatements(testFilePaths);
            assertNotNull(portfolioPeriod);
            out.printf("%s/%s%n", portfolioPeriod.getAccountNumber(), portfolioPeriod.getAccountName());
            List<Transaction> transactions = portfolioPeriod.getTransactions();
            assertFalse(transactions.isEmpty());
            BigDecimal tolerance = new BigDecimal("0.01");
            BigDecimal taxRatioMin = new BigDecimal("0.01");
            BigDecimal taxRatioMax = new BigDecimal("0.45");
            for (int i = 0, transactionsSize = transactions.size(); i < transactionsSize; i++) {
                Transaction transaction = transactions.get(i);
                out.printf("[%s]: %s\n", i, transaction);
                if (transaction.getType().equals(TransactionType.DIVIDEND)) {
                    BigDecimal withholdingTax = transaction.getWithholdingTax();
                    BigDecimal grossAmount = transaction.getGrossAmount();
                    BigDecimal value = transaction.getValue();
                    assertNotNull(withholdingTax, transaction::toString);
                    assertNotNull(transaction.getIsin(), transaction::toString);
                    assertTrue(grossAmount.subtract(withholdingTax).subtract(value).abs().compareTo(tolerance) < 0, transaction::toString);

                    if (withholdingTax.compareTo(BigDecimal.ZERO) != 0) {
                        BigDecimal taxPct = withholdingTax.divide(grossAmount, 2, RoundingMode.HALF_UP);
                        assertTrue(taxPct.compareTo(taxRatioMin) > 0, transaction::toString);
                        assertTrue(taxPct.compareTo(taxRatioMax) < 0, transaction::toString);
                    }
                }
            }
        }
    }


    @Test
    void processStatements_oneAccountStatement() {
        List<Path> testFilePaths = getTestFilePaths("trading-account-statement_2022-01-01_2023-01-11_en_bad2be.pdf"::equals);
        if (!testFilePaths.isEmpty()) {
            PortfolioPeriod portfolioPeriod = revolutSvc.processStatements(testFilePaths);

            assertTrue(portfolioPeriod.getAccountNumber().startsWith("RE"));

            List<Transaction> transactions = portfolioPeriod.getTransactions();
            assertEquals(141, transactions.size());

            assertEquals(1, portfolioPeriod.getPortfolioBreakdownSnapshots().size());
            assetBreakdownsAreConsistent(portfolioPeriod.getPortfolioBreakdownSnapshots().values());

            assertTransactionsAreSorted(transactions);
            assertTransactionsAttributesAreConsistent(transactions);
        }
    }

    @Test
    void processStatements_manyEqualAccountStatements() {
        List<Path> testFilePaths = getTestFilePaths("trading-account-statement_2022-01-01_2023-01-11_en_bad2be.pdf"::equals);
        if (!testFilePaths.isEmpty()) {
            PortfolioPeriod portfolioPeriod = revolutSvc.processStatements(List.of(testFilePaths.get(0), testFilePaths.get(0)));

            assertTrue(portfolioPeriod.getAccountNumber().startsWith("RE"));

            List<Transaction> transactions = portfolioPeriod.getTransactions();

            assertEquals(1, portfolioPeriod.getPortfolioBreakdownSnapshots().size());
            assetBreakdownsAreConsistent(portfolioPeriod.getPortfolioBreakdownSnapshots().values());

            assertTransactionsAreSorted(transactions);
            assertTransactionsAttributesAreConsistent(transactions);
        }
    }

    @Test
    void processStatements_afterMigrationToEU_year() {
        List<Path> testFilePaths = getTestFilePaths("trading-account-statement_2023-01-01_2023-09-15_en_56e32a.pdf"::equals);
        if (!testFilePaths.isEmpty()) {
            PortfolioPeriod portfolioPeriod = revolutSvc.processStatements(List.of(testFilePaths.get(0)));

            List<Transaction> transactions = portfolioPeriod.getTransactions();

            assertEquals(1, portfolioPeriod.getPortfolioBreakdownSnapshots().size());
            assetBreakdownsAreConsistent(portfolioPeriod.getPortfolioBreakdownSnapshots().values());

            assertTrue(transactions.size() > 10);
            assertTransactionsAreSorted(transactions);
            assertTransactionsAttributesAreConsistent(transactions);
        }
    }

    @Test
    void processStatements_afterMigrationToEU_month() {
        List<Path> testFilePaths = getTestFilePaths("trading-account-statement_2023-09-01_2023-09-15_en_f25725.pdf"::equals);
        if (!testFilePaths.isEmpty()) {
            PortfolioPeriod portfolioPeriod = revolutSvc.processStatements(List.of(testFilePaths.get(0)));

            List<Transaction> transactions = portfolioPeriod.getTransactions();

            assertEquals(1, portfolioPeriod.getPortfolioBreakdownSnapshots().size());
            assetBreakdownsAreConsistent(portfolioPeriod.getPortfolioBreakdownSnapshots().values());

            assertEquals(6, transactions.size());
            assertTransactionsAreSorted(transactions);
            assertTransactionsAttributesAreConsistent(transactions);
        }
    }

    @Test
    void processStatements_nonContinuousPeriods() {
        List<Path> testFilePaths = getTestFilePaths(fileName ->
                "trading-account-statement_2021-01-01_2021-12-31_en_b094bc.pdf".equals(fileName) ||
                        "trading-account-statement_2022-01-01_2022-12-31_en_7c6251.pdf".equals(fileName)
        );
        if (testFilePaths.size() == 2) {
            assertThrows(InvalidStatementException.class, () -> revolutSvc.processStatements(testFilePaths));
        }
    }

    @Test
    void processStatements_dividendDuplicates() {
        PortfolioPeriod portfolioPeriod = revolutSvc.processStatements(getTestFilePaths());

        Map<String, Transaction> dividends = new LinkedHashMap<>();
        for (Transaction t : portfolioPeriod.getTransactions()) {
            if (!t.getType().equals(TransactionType.DIVIDEND)) {
                continue;
            }
            String k = String.format("%s/%s/%s", t.getSymbol(), t.getDate().toLocalDate(), t.getValue());
            Transaction oldDiv = dividends.put(k, t);
            assertNull(oldDiv, () -> String.format("\n%s\n%s", oldDiv, t));
        }
    }

    @Test
    void getPortfolioValues() {
        Map<LocalDate, PortfolioValue> portfolioValues = revolutSvc.getPortfolioValues(getTestFilePaths());
        assertNotNull(portfolioValues);
    }

    @Test
    void processStatements_parse3() {
        List<Path> testFilePaths = getTestFilePaths(f ->
                f.contains("2024-01-01_2024-02-01") ||
                        f.contains("2024-02-01_2024-03-01") ||
                        f.contains("2024-03-01_2024-04-01")
        );
        if (!testFilePaths.isEmpty()) {
            Map<LocalDate, PortfolioValue> portfolioPeriod = revolutSvc.getPortfolioValues(testFilePaths);
            assertNotNull(portfolioPeriod);
            assertEquals(5, portfolioPeriod.size());
        }
    }

    @Test
    void processStatements_dividendIsin() {
        List<Path> testFilePaths = getTestFilePaths();
        if (!testFilePaths.isEmpty()) {
            List<Transaction> divTrans = revolutSvc.processStatements(testFilePaths)
                    .getTransactions()
                    .stream()
                    .filter(t -> TransactionType.DIVIDEND.equals(t.getType()))
                    .toList();
            List<Transaction> noIsinDivTrans = divTrans.stream().filter(t -> t.getIsin() == null || t.getIsin().isBlank()).toList();
            assertEquals(0, noIsinDivTrans.size(), noIsinDivTrans::toString);
        }
    }

    @Test
    void processStatements_dividendMerge() {
        List<Path> testFilePaths = getTestFilePaths();
        if (!testFilePaths.isEmpty()) {
            List<Transaction> trans1 = revolutSvc.processStatements(testFilePaths)
                    .getTransactions()
                    .stream()
                    .filter(t -> "COP".equals(t.getSymbol()))
                    .toList();

            List<Transaction> trans2 = trans1.stream()
                    .filter(t -> t.getDate().getYear() == 2024 && t.getDate().getMonth() == Month.MARCH)
                    .toList();

            assertEquals(1, trans2.size());
            assertEquals(trans2.get(0).getGrossAmount().toString(), "16.38");
            assertEquals(trans2.get(0).getValue().toString(), "13.92");
        }
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

    private void assertTransactionsAreSorted(List<Transaction> transactions) {
        List<Transaction> sortedTransactions = transactions
                .stream()
                .sorted(comparing(Transaction::getDate))
                .collect(Collectors.toList());
        assertEquals(sortedTransactions, transactions);
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

    private List<Path> getTestFilePaths() {
        return getTestFilePaths(fileName -> true);
    }

    private List<Path> getTestFilePaths(Collection<String> fileNames) {
        return getTestFilePaths(f -> fileNames == null || fileNames.contains(f));
    }

    private List<Path> getTestFilePaths(String... fileNames) {
        return getTestFilePaths(Set.of(fileNames));
    }

    private List<Path> getTestFilePaths(Predicate<String> fileNameFilter) {
        String testDataFolder = TEST_DATA_FOLDER;

        List<Path> testStatementFilePaths;
        Path testFolderPath = Paths.get(testDataFolder);
        File testFolder = testFolderPath.toFile();
        if (!testFolder.exists() || !testFolder.isDirectory()) {
            out.printf(String.format("Test data folder not found: '%s'", testDataFolder));
        }
        try (Stream<Path> filePaths = Files.walk(testFolderPath)) {
            testStatementFilePaths = filePaths
                    .filter(p -> fileNameFilter.test(p.getFileName().toString()))
                    .filter(p -> p.toFile().isFile())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (testStatementFilePaths.isEmpty()) {
            out.printf(String.format("No files found in test data folder: '%s'", testDataFolder));
        }
        return testStatementFilePaths;
    }
}
