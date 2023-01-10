# Brinvex-Util-Revolut

## Introduction

_Brinvex-Util-Revolut_ is a compact Java library which enables developers 
to easily extract and work with data from Revolut trading account reports.

Working with Revolut trading account files is often a tedious work. 
Various account statements and reports coming from mobile app or webapp 
contain different bunch of data with different level of details. 
For example the _profit-and-loss-pdf_ report contains information about 
dividends withholding tax but
_profit-and-loss-csv_, _account-statement-pdf_, _account-statement-csv_ do not.

**Brinvex-Util-Revolut extracts and consolidates data coming from various
Revolut report formats and makes them available in simple consistent form for further processing.**

## How to use it
 
- Add dependencies
````
    <dependency>
        <groupId>com.brinvex.util</groupId>
        <artifactId>brinvex-util-revolut-api</artifactId>
        <version>1.0.1</version>
    </dependency>
    <dependency>
        <groupId>com.brinvex.util</groupId>
        <artifactId>brinvex-util-revolut-impl</artifactId>
        <version>1.0.1</version>
        <scope>runtime</scope>
    </dependency>
````
- Parse individual _trading-account-statement_ or _profit-and-loss-statement_ PDF files. 
Make sure they span over the same time period.
````
    RevolutTransactionsProviderFactory factory = RevolutTransactionsProviderFactory.INSTANCE 
    RevolutTransactionsProvider transactionsProvider = factory.getTransactionsProvider();

    TradingAccountTransactions tradingAccTransactions;
    try (FileInputStream fis = new FileInputStream("c:/tmp/trading-account-statement-2022.pdf")) {
        tradingAccTransactions = transactionsProvider.parseTradingAccountTransactions(fis);
    }
    
    TradingAccountTransactions pnlTransactions;
    try (FileInputStream fis = new FileInputStream("c:/tmp/pnl-statement-2022.pdf")) {
        pnlTransactions = transactionsProvider.parseTradingAccountTransactions(fis);
    }
````
- Join, merge, clean and group transactions by accountNumber  

````
    List<TradingAccountTransactions> allTransactions = List.of(tradingAccTransactions, pnlTransactions)
    Map<String, TradingAccountTransactions> consolidatedTransactions = 
            transactionProvider.consolidateTradingAccountTransactions(allTransactions);
````
- Enjoy the result and use it as you need
````
    for (TradingAccountTransactions t : consolidatedTransactions.values()) {
        ZonedDateTime date = t.getDate()
        String accountNumber = t.getAccountNumber();
        String accountName = t.getAccountName();
        BigDecimal value = t.getValue();
        BigDecimal price = t.getPrice();
        BigDecimal quantity = t.getQuantity();
        BigDecimal withholdingTax = t.getWithholdingTax();
        BigDecimal fees = t.getFees();
        BigDecimal commision = t.getCommission();
        ...
    }
````

### Requirements
- Java 11 or above

### License

- The _Brinvex-Util-Revolut_ is released under version 2.0 of the Apache License.
