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
package com.brinvex.util.revolut.api;

import com.brinvex.util.revolut.api.model.TradingAccountTransactions;

import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

/**
 * An interface publishing methods for parsing and working with various Revolut data.
 * An implementation class instance should be retrieved using {@link RevolutTransactionsProviderFactory#getTransactionsProvider()}.
 * The factory as well as the implementation instance is a thread-safe singleton.
 */
public interface RevolutTransactionsProvider {

    /**
     * Reads and parses data of Revolut trading account PDF report.
     *
     * @param inputStream InputStream of one pdf report file
     * @return {@link TradingAccountTransactions} data object containing
     * account identification and list of all transactions found in given report
     */
    TradingAccountTransactions parseTradingAccountTransactions(InputStream inputStream);

    /**
     * Various Revolut trading account reports contain data with different level of details.
     * For example the profit-and-loss report contains information
     * about dividends withholding tax but the account-statement report does not.
     * <p>
     * This method consolidates a data from various reports into a consistent form by:
     * <ul>
     *  <li>grouping transactions by accountNumber</li>
     *  <li>merging transactions details coming from different reports</li>
     *  <li>removing duplicated transactions</li>
     *  <li>sorting transactions by date</li>
     * </ul>
     *
     * @param tradingAccountTransactions collection of {@link TradingAccountTransactions} data objects to consolidate
     * @return map pairing accountNumbers to consolidated transactions on those accounts
     */
    Map<String, TradingAccountTransactions> consolidateTradingAccountTransactions(Collection<TradingAccountTransactions> tradingAccountTransactions);
}
