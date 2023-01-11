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
