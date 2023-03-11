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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * An interface publishing methods for working with Revolut data.
 * An implementation class instance should be retrieved using {@link RevolutServiceFactory#getService()}.
 * The factory as well as the default implementation instance is a thread-safe singleton.
 */
public interface RevolutService {

    /**
     * Process given Revolut statements into a {@link PortfolioPeriod} data object.
     * <ul>
     *  <li>sorts, deduplicates and merges transactions</li>
     *  <li>sorts and deduplicates portfolio breakdown snapshots</li>
     * </ul>
     * All given statements must belong to one account number.
     *
     * @param statementInputStreams stream of statement inputStreams
     * @return {@link PortfolioPeriod}
     */
    PortfolioPeriod processStatements(Stream<Supplier<InputStream>> statementInputStreams);

    /**
     * See {@link RevolutService#processStatements(Stream)}
     */
    default PortfolioPeriod processStatements(Collection<Path> statementFilePaths) {
        return processStatements(statementFilePaths
                .stream()
                .map(f -> () -> {
                    try {
                        return new FileInputStream(f.toFile());
                    } catch (FileNotFoundException e) {
                        throw new UncheckedIOException(e);
                    }
                }));

    }

}
