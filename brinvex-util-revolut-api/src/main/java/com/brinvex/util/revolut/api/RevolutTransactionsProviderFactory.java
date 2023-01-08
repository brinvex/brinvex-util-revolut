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

import java.util.ServiceLoader;

/**
 * A factory for {@link RevolutTransactionsProvider} based on Java SPI.
 */
public enum RevolutTransactionsProviderFactory {

    INSTANCE;

    private RevolutTransactionsProvider transactionsProvider;

    public RevolutTransactionsProvider getTransactionsProvider() {
        if (transactionsProvider == null) {
            ServiceLoader<RevolutTransactionsProvider> loader = ServiceLoader.load(RevolutTransactionsProvider.class);
            for (RevolutTransactionsProvider provider : loader) {
                this.transactionsProvider = provider;
                break;
            }
        }
        if (transactionsProvider == null) {
            throw new IllegalStateException(String.format("No %s implementation found", RevolutTransactionsProvider.class));
        }
        return transactionsProvider;
    }
}
