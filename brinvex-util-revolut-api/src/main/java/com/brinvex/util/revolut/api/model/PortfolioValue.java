package com.brinvex.util.revolut.api.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.StringJoiner;

public class PortfolioValue implements Serializable {

    private String accountNumber;

    private String accountName;

    private LocalDate day;

    private BigDecimal cashValue;

    private BigDecimal stocksValue;

    private BigDecimal totalValue;

    private Currency currency;

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

    public LocalDate getDay() {
        return day;
    }

    public void setDay(LocalDate day) {
        this.day = day;
    }

    public BigDecimal getCashValue() {
        return cashValue;
    }

    public void setCashValue(BigDecimal cashValue) {
        this.cashValue = cashValue;
    }

    public BigDecimal getStocksValue() {
        return stocksValue;
    }

    public void setStocksValue(BigDecimal stocksValue) {
        this.stocksValue = stocksValue;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(BigDecimal totalValue) {
        this.totalValue = totalValue;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", PortfolioValue.class.getSimpleName() + "[", "]")
                .add("accountNumber='" + accountNumber + "'")
                .add("accountName='" + accountName + "'")
                .add("day=" + day)
                .add("cashValue=" + cashValue)
                .add("stocksValue=" + stocksValue)
                .add("totalValue=" + totalValue)
                .add("currency=" + currency)
                .toString();
    }
}
