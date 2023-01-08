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
import java.time.ZonedDateTime;

/**
 * Data object representing Revolut trading account transaction.
 */
public class Transaction implements Serializable {

    private ZonedDateTime date;

    private TransactionType type;

    private String symbol;

    private String country;

    private BigDecimal quantity;

    private BigDecimal price;

    private BigDecimal value;

    private BigDecimal grossAmount;

    private BigDecimal withholdingTax;

    private TransactionSide side;

    private BigDecimal fees;

    private BigDecimal commission;

    private String securityName;

    private String isin;

    private Currency currency;

    public ZonedDateTime getDate() {
        return date;
    }

    public void setDate(ZonedDateTime date) {
        this.date = date;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
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

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public void setGrossAmount(BigDecimal grossAmount) {
        this.grossAmount = grossAmount;
    }

    public BigDecimal getWithholdingTax() {
        return withholdingTax;
    }

    public void setWithholdingTax(BigDecimal withholdingTax) {
        this.withholdingTax = withholdingTax;
    }

    public TransactionSide getSide() {
        return side;
    }

    public void setSide(TransactionSide side) {
        this.side = side;
    }

    public BigDecimal getFees() {
        return fees;
    }

    public void setFees(BigDecimal fees) {
        this.fees = fees;
    }

    public BigDecimal getCommission() {
        return commission;
    }

    public void setCommission(BigDecimal commission) {
        this.commission = commission;
    }

    public String getSecurityName() {
        return securityName;
    }

    public void setSecurityName(String securityName) {
        this.securityName = securityName;
    }

    public String getIsin() {
        return isin;
    }

    public void setIsin(String isin) {
        this.isin = isin;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    @Override
    public String toString() {
        return "Transaction{" +
               "date=" + date +
               ", type=" + type +
               ", symbol='" + symbol + '\'' +
               ", country='" + country + '\'' +
               ", quantity=" + quantity +
               ", price=" + price +
               ", value=" + value +
               ", grossAmount=" + grossAmount +
               ", withholdingTax=" + withholdingTax +
               ", side=" + side +
               ", fees=" + fees +
               ", commission=" + commission +
               ", securityName='" + securityName + '\'' +
               ", isin='" + isin + '\'' +
               ", currency=" + currency +
               '}';
    }
}
