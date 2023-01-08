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
package com.brinvex.util.revolut.api.model;

/**
 * Enumeration representing a type of transaction
 * ({@link TransactionType#DIVIDEND}, {@link TransactionType#TRADE_LIMIT}, {@link TransactionType#CASH_WITHDRAWAL}, ...)
 */
public enum TransactionType {

    CASH_TOP_UP,

    CASH_WITHDRAWAL,

    CUSTODY_FEE,

    DIVIDEND,

    SPINOFF,

    STOCK_SPLIT,

    TRADE_LIMIT,

    TRADE_MARKET,

}
