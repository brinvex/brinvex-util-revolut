package com.brinvex.util.revolut.api.model;

import java.io.Serializable;
import java.math.BigDecimal;

public class Holding implements Serializable {

    private String symbol;

    private String company;

    private String isin;

    private BigDecimal quantity;

    private BigDecimal price;

    private BigDecimal value;

    private Currency currency;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getIsin() {
        return isin;
    }

    public void setIsin(String isin) {
        this.isin = isin;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    @Override
    public String toString() {
        return "Holding{" +
               "symbol='" + symbol + '\'' +
               ", company='" + company + '\'' +
               ", isin='" + isin + '\'' +
               ", quantity=" + quantity +
               ", price=" + price +
               ", value=" + value +
               ", currency=" + currency +
               '}';
    }
}
