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
package com.brinvex.util.revolut.api.service;

import com.brinvex.util.revolut.api.model.PortfolioPeriod;

import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

/**
 * An interface publishing methods for parsing and working with various Revolut data.
 * An implementation class instance should be retrieved using {@link RevolutServiceFactory#getService()} ()}.
 * The factory as well as the default implementation instance is a thread-safe singleton.
 */
public interface RevolutService {

    /**
     * Parses Revolut PDF statement into {@link PortfolioPeriod} object.
     *
     * @param inputStream InputStream of one pdf report file
     * @return {@link PortfolioPeriod} data object, never null
     */
    PortfolioPeriod parseStatement(InputStream inputStream);

    /**
     * Consolidates many various portfolio periods into one-per-account continuous period.
     * <ul>
     *  <li>groups portfolio periods by accountNumber</li>
     *  <li>sorts, deduplicates and merges transactions</li>
     *  <li>sorts and deduplicates portfolio breakdown snapshots</li>
     * </ul>
     *
     * @param portfolioPeriods collection of {@link PortfolioPeriod} data objects to consolidate
     * @return map pairing accountNumber to consolidated continuous {@link PortfolioPeriod}
     */
    Map<String, PortfolioPeriod> consolidate(Collection<PortfolioPeriod> portfolioPeriods);

}
