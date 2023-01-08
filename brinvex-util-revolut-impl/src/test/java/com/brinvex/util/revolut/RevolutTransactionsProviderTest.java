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

import com.brinvex.util.revolut.api.RevolutTransactionsProvider;
import com.brinvex.util.revolut.api.RevolutTransactionsProviderFactory;
import com.brinvex.util.revolut.api.model.Currency;
import com.brinvex.util.revolut.api.model.TradingAccountTransactions;
import com.brinvex.util.revolut.api.model.Transaction;
import com.brinvex.util.revolut.api.model.TransactionType;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.System.out;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevolutTransactionsProviderTest {

    private static final String DATA_FOLDER = "c:/prj/brinvex/brinvex-dev1/test-data/brinvex-util-revolut-impl";

    private final RevolutTransactionsProvider transactionsProvider = RevolutTransactionsProviderFactory.INSTANCE.getTransactionsProvider();

    @Test
    void parseTradingAccountTransactions_oneTradingAccountStatement() throws IOException {
        String filePath = DATA_FOLDER + "/trading-account-statement/trading-account-statement_2022-01-01_2022-12-31_en_7c6251.pdf";
        File file = Path.of(filePath).toFile();
        if (!file.exists()) {
            out.printf("File not found: '%s'", file);
            return;
        }
        try (InputStream is = new FileInputStream(file)) {
            out.printf("Parsing file '%s'%n", file);
            TradingAccountTransactions tradingAccountTransactions = transactionsProvider.parseTradingAccountTransactions(is);
            out.println("accountNumber=" + tradingAccountTransactions.getAccountNumber());
            out.println("accountName=" + tradingAccountTransactions.getAccountName());
            List<Transaction> transactions = tradingAccountTransactions.getTransactions();
            for (int i = 0, transactionsSize = transactions.size(); i < transactionsSize; i++) {
                Transaction transaction = transactions.get(i);
                out.printf("Parsed transaction[%s]: %s\n", i, transaction);
            }
        }
    }

    @Test
    void parseTradingAccountTransactions_oneProfitAndLossStatement() throws IOException {
        String filePath = DATA_FOLDER + "/profit-loss-statement/trading-pnl-statement_2021-01-01_2022-01-01_en-us_6e8044.pdf";

        File file = Path.of(filePath).toFile();
        if (!file.exists()) {
            out.printf("File not found: '%s'", file);
            return;
        }
        try (InputStream is = new FileInputStream(file)) {
            out.printf("Parsing file '%s'%n", file);
            TradingAccountTransactions profitAndLossStatementTransactions = transactionsProvider.parseTradingAccountTransactions(is);
            out.println("accountNumber=" + profitAndLossStatementTransactions.getAccountNumber());
            out.println("accountName=" + profitAndLossStatementTransactions.getAccountName());
            List<Transaction> transactions = profitAndLossStatementTransactions.getTransactions();
            for (int i = 0, transactionsSize = transactions.size(); i < transactionsSize; i++) {
                Transaction transaction = transactions.get(i);
                out.printf("Parsed transaction[%s]: %s\n", i, transaction);
            }
        }
    }


    @Test
    void mergeTradingAccountTransactions_oneAccountStatement() throws IOException {

        TradingAccountTransactions accountTransactions1;
        Path path = Path.of(DATA_FOLDER + "/trading-account-statement/trading-account-statement_2022-01-01_2022-12-31_en_7c6251.pdf");
        if (!path.toFile().exists()) {
            out.printf("File not found: '%s'", path);
            return;
        }
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            accountTransactions1 = transactionsProvider.parseTradingAccountTransactions(fis);
        }
        Map<String, TradingAccountTransactions> allAccountTransactions = transactionsProvider.consolidateTradingAccountTransactions(List.of(accountTransactions1));

        assertEquals(1, allAccountTransactions.size());

        Map.Entry<String, TradingAccountTransactions> e = allAccountTransactions.entrySet().iterator().next();
        String accountNumber = e.getKey();
        TradingAccountTransactions accountTransactions = e.getValue();

        assertTrue(accountNumber.matches("REW......9"));
        assertEquals(accountNumber, accountTransactions.getAccountNumber());
        assertTrue(accountTransactions.getAccountName().matches("..k.....s.."));

        List<Transaction> transactions = accountTransactions.getTransactions();
        assertEquals(138, transactions.size());

        assertEquals(transactions.stream().sorted(Comparator.comparing(Transaction::getDate)).collect(Collectors.toList()), transactions);

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
                    break;
                }
            }
        }
    }

    @Test
    void mergeTradingAccountTransactions_manyEqualAccountStatements() throws IOException {

        Path pdfPath = Path.of(DATA_FOLDER + "/trading-account-statement/trading-account-statement_2022-01-01_2022-12-31_en_7c6251.pdf");
        TradingAccountTransactions transactions1;
        if (!pdfPath.toFile().exists()) {
            out.printf("File not found: '%s'", pdfPath);
            return;
        }
        try (FileInputStream fis = new FileInputStream(pdfPath.toFile())) {
            transactions1 = transactionsProvider.parseTradingAccountTransactions(fis);
        }
        Map<String, TradingAccountTransactions> tradingAccountTransactions = transactionsProvider.consolidateTradingAccountTransactions(
                List.of(transactions1, transactions1));

        assertEquals(1, tradingAccountTransactions.size());

        Map.Entry<String, TradingAccountTransactions> e = tradingAccountTransactions.entrySet().iterator().next();
        String accountNumber = e.getKey();
        TradingAccountTransactions accountTransactions = e.getValue();

        assertTrue(accountNumber.matches("REW......9"));
        assertEquals(accountNumber, accountTransactions.getAccountNumber());
        assertTrue(accountTransactions.getAccountName().matches("..k.....s.."));

        List<Transaction> transactions = accountTransactions.getTransactions();
        assertEquals(138, transactions.size());

        assertEquals(transactions.stream().sorted(Comparator.comparing(Transaction::getDate)).collect(Collectors.toList()), transactions);

        for (Transaction transaction : transactions) {
            assertNotNull(transaction.getValue());
            assertNotNull(transaction.getFees());
            assertNotNull(transaction.getCommission());

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
                    break;
                }
            }
        }
    }

    @Test
    void mergeTradingAccountTransactions_manyAccountStatements() throws IOException {

        Path pdfPath1 = Path.of(DATA_FOLDER + "/trading-account-statement/trading-account-statement_2021-01-01_2021-12-31_en_b094bc.pdf");
        Path pdfPath2 = Path.of(DATA_FOLDER + "/trading-account-statement/trading-account-statement_2022-01-01_2022-12-31_en_7c6251.pdf");
        for(Path p : List.of(pdfPath1, pdfPath2)) {
            if (!p.toFile().exists()) {
                out.printf("File not found: '%s'", p);
                return;
            }
        }
        TradingAccountTransactions accountTransactions1;
        try (FileInputStream fis = new FileInputStream(pdfPath1.toFile())) {
            accountTransactions1 = transactionsProvider.parseTradingAccountTransactions(fis);
        }
        TradingAccountTransactions accountTransactions2;
        try (FileInputStream fis = new FileInputStream(pdfPath2.toFile())) {
            accountTransactions2 = transactionsProvider.parseTradingAccountTransactions(fis);
        }
        Map<String, TradingAccountTransactions> tradingAccountTransactions = transactionsProvider.consolidateTradingAccountTransactions(
                List.of(accountTransactions1, accountTransactions2));

        assertEquals(1, tradingAccountTransactions.size());

        Map.Entry<String, TradingAccountTransactions> e = tradingAccountTransactions.entrySet().iterator().next();
        String accountNumber = e.getKey();
        TradingAccountTransactions accountTransactions = e.getValue();

        assertTrue(accountNumber.matches("REW......9"));
        assertEquals(accountNumber, accountTransactions.getAccountNumber());
        assertTrue(accountTransactions.getAccountName().matches("..k.....s.."));

        List<Transaction> transactions = accountTransactions.getTransactions();
        assertEquals(296, transactions.size());

        assertEquals(transactions.stream().sorted(Comparator.comparing(Transaction::getDate)).collect(Collectors.toList()), transactions);

        for (Transaction transaction : transactions) {
            assertNotNull(transaction.getValue());
            assertNotNull(transaction.getFees());
            assertNotNull(transaction.getCommission());

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

    @Test
    void mergeTradingAccountTransactions_manyProfitAndLosesStatements() throws IOException {

        Path pdfPath1 = Path.of(DATA_FOLDER + "/profit-loss-statement/trading-pnl-statement_2021-01-01_2022-01-01_en-us_6e8044.pdf");
        Path pdfPath2 = Path.of(DATA_FOLDER + "/profit-loss-statement/trading-pnl-statement_2022-01-01_2023-01-01_en-us_b1efb2.pdf");
        for(Path p : List.of(pdfPath1, pdfPath2)) {
            if (!p.toFile().exists()) {
                out.printf("File not found: '%s'", p);
                return;
            }
        }
        TradingAccountTransactions accountTransactions1;
        try (FileInputStream fis = new FileInputStream(pdfPath1.toFile())) {
            accountTransactions1 = transactionsProvider.parseTradingAccountTransactions(fis);
        }
        TradingAccountTransactions accountTransactions2;
        try (FileInputStream fis = new FileInputStream(pdfPath2.toFile())) {
            accountTransactions2 = transactionsProvider.parseTradingAccountTransactions(fis);
        }
        Map<String, TradingAccountTransactions> accountTransactions11 = transactionsProvider.consolidateTradingAccountTransactions(List.of(accountTransactions1));
        Map<String, TradingAccountTransactions> accountTransactions22 = transactionsProvider.consolidateTradingAccountTransactions(List.of(accountTransactions2));
        Map<String, TradingAccountTransactions> accountTransactions = transactionsProvider.consolidateTradingAccountTransactions(List.of(accountTransactions1, accountTransactions2));

        assertEquals(1, accountTransactions11.size());
        assertEquals(1, accountTransactions22.size());
        assertEquals(1, accountTransactions.size());

        TradingAccountTransactions transactions1 = accountTransactions11.entrySet().iterator().next().getValue();
        TradingAccountTransactions transactions2 = accountTransactions22.entrySet().iterator().next().getValue();
        TradingAccountTransactions transactions = accountTransactions.entrySet().iterator().next().getValue();

        assertEquals(67, transactions1.getTransactions().size());
        assertEquals(80, transactions2.getTransactions().size());
        assertEquals(147, transactions.getTransactions().size());


        for (Transaction dividend : transactions.getTransactions()) {
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
    }

    @Test
    void mergeTradingAccountTransactions_manyVariousStatements() throws IOException {

        Path pdfPath1 = Path.of(DATA_FOLDER + "/trading-account-statement/trading-account-statement_2021-01-01_2021-12-31_en_b094bc.pdf");
        Path pdfPath2 = Path.of(DATA_FOLDER + "/trading-account-statement/trading-account-statement_2022-01-01_2022-12-31_en_7c6251.pdf");
        Path pdfPath3 = Path.of(DATA_FOLDER + "/profit-loss-statement/trading-pnl-statement_2021-01-01_2022-01-01_en-us_6e8044.pdf");
        Path pdfPath4 = Path.of(DATA_FOLDER + "/profit-loss-statement/trading-pnl-statement_2022-01-01_2023-01-01_en-us_b1efb2.pdf");
        for(Path p : List.of(pdfPath1, pdfPath2, pdfPath3, pdfPath4)) {
            if (!p.toFile().exists()) {
                out.printf("File not found: '%s'", p);
                return;
            }
        }
        TradingAccountTransactions accountTransactions1;
        try (FileInputStream fis = new FileInputStream(pdfPath1.toFile())) {
            accountTransactions1 = transactionsProvider.parseTradingAccountTransactions(fis);
        }
        TradingAccountTransactions accountTransactions2;
        try (FileInputStream fis = new FileInputStream(pdfPath2.toFile())) {
            accountTransactions2 = transactionsProvider.parseTradingAccountTransactions(fis);
        }
        TradingAccountTransactions accountTransactions3;
        try (FileInputStream fis = new FileInputStream(pdfPath3.toFile())) {
            accountTransactions3 = transactionsProvider.parseTradingAccountTransactions(fis);
        }
        TradingAccountTransactions accountTransactions4;
        try (FileInputStream fis = new FileInputStream(pdfPath4.toFile())) {
            accountTransactions4 = transactionsProvider.parseTradingAccountTransactions(fis);
        }

        Map<String, TradingAccountTransactions> allAccountTransactions = transactionsProvider.consolidateTradingAccountTransactions(List.of(
                accountTransactions1,
                accountTransactions2,
                accountTransactions3,
                accountTransactions4
        ));

        assertEquals(1, allAccountTransactions.size());

        TradingAccountTransactions accountTransactions = allAccountTransactions.entrySet().iterator().next().getValue();

        assertEquals(158, accountTransactions1.getTransactions().size());
        assertEquals(138, accountTransactions2.getTransactions().size());
        assertEquals(67, accountTransactions3.getTransactions().size());
        assertEquals(80, accountTransactions4.getTransactions().size());
        assertEquals(296, accountTransactions.getTransactions().size());

        for (Transaction transaction : accountTransactions.getTransactions()) {
            assertNotNull(transaction.getDate());

            if (transaction.getType().equals(TransactionType.DIVIDEND)) {
                assertNotNull(transaction.getSecurityName());
                assertNotNull(transaction.getSymbol());
                assertNotNull(transaction.getIsin());
                assertNotNull(transaction.getCountry());
                assertTrue(transaction.getGrossAmount().compareTo(BigDecimal.ZERO) > 0);
                assertTrue(transaction.getWithholdingTax().compareTo(BigDecimal.ZERO) >= 0);
                assertTrue(transaction.getValue().compareTo(BigDecimal.ZERO) > 0);
            }
        }
    }

}
