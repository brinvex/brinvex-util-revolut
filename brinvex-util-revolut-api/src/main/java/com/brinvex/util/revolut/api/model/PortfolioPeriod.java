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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class PortfolioPeriod implements Serializable {

    private String accountNumber;

    private String accountName;

    private LocalDate periodFrom;

    private LocalDate periodTo;

    private Map<LocalDate, PortfolioBreakdown> breakdownSnapshots;

    private List<Transaction> transactions;

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public LocalDate getPeriodFrom() {
        return periodFrom;
    }

    public void setPeriodFrom(LocalDate periodFrom) {
        this.periodFrom = periodFrom;
    }

    public LocalDate getPeriodTo() {
        return periodTo;
    }

    public void setPeriodTo(LocalDate periodTo) {
        this.periodTo = periodTo;
    }

    public Map<LocalDate, PortfolioBreakdown> getPortfolioBreakdownSnapshots() {
        return breakdownSnapshots;
    }

    public void setPortfolioBreakdownSnapshots(Map<LocalDate, PortfolioBreakdown> portfolioBreakdownSnapshots) {
        this.breakdownSnapshots = portfolioBreakdownSnapshots;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<Transaction> transactions) {
        this.transactions = transactions;
    }

    @Override
    public String toString() {
        return "PortfolioPeriod{" +
               "accountNumber='" + accountNumber + '\'' +
               ", accountName='" + accountName + '\'' +
               ", periodFrom=" + periodFrom +
               ", periodTo=" + periodTo +
               ", breakdownSnapshots=" + breakdownSnapshots +
               ", transactions=" + transactions +
               '}';
    }
}
