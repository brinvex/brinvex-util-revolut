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

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class PortfolioBreakdown implements Serializable {

    private LocalDate date;

    private Map<Currency, BigDecimal> cash;

    private List<Holding> holdings;

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Map<Currency, BigDecimal> getCash() {
        return cash;
    }

    public void setCash(Map<Currency, BigDecimal> cash) {
        this.cash = cash;
    }

    public List<Holding> getHoldings() {
        return holdings;
    }

    public void setHoldings(List<Holding> holdings) {
        this.holdings = holdings;
    }

    @Override
    public String toString() {
        return "PortfolioBreakdown{" +
               "date=" + date +
               ", cash=" + cash +
               ", holdings=" + holdings +
               '}';
    }
}
