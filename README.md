# Brinvex-Util-Revolut

## Introduction

_Brinvex-Util-Revolut_ is a compact Java library which enables developers 
to easily extract and work with data from Revolut trading account reports.

Working with Revolut trading account files is often a tedious work. 
Various account statements and reports coming from mobile app or webapp 
contain different bunch of data with different level of details. 
One example of many nuances Revolut has is that the _profit-and-loss-pdf_ report 
contains information about dividends withholding tax but
_profit-and-loss-csv_, _account-statement-pdf_, _account-statement-csv_ do not.

**Brinvex-Util-Revolut extracts and consolidates data coming from various
Revolut report formats and makes them available in simple consistent form for further processing.**

## How to use it
 
- Add dependencies
````
<dependency>
    <groupId>com.brinvex.util</groupId>
    <artifactId>brinvex-util-revolut-api</artifactId>
    <version>2.0.3</version>
</dependency>
<dependency>
    <groupId>com.brinvex.util</groupId>
    <artifactId>brinvex-util-revolut-impl</artifactId>
    <version>2.0.3</version>
    <scope>runtime</scope>
</dependency>
````
- Parse individual _trading-account-statement_ or _profit-and-loss-statement_ PDF files. 
````
RevolutService revolutSvc = RevolutServiceFactory.INSTANCE.getService(); 

FileInputStream fis1 = new FileInputStream("c:/tmp/trading-account-statement-2021.pdf");
PortfolioPeriod p1 = revolutSvc.parseStatement(fis1);

FileInputStream fis2 = new FileInputStream("c:/tmp/trading-account-statement-2022.pdf");
PortfolioPeriod p2 = revolutSvc.parseStatement(fis2);

FileInputStream fis3 = new FileInputStream("c:/tmp/pnl-statement-2021.pdf")
PortfolioPeriod p3 = revolutSvc.parseStatement(fis3);

FileInputStream fis4 = new FileInputStream("c:/tmp/pnl-statement-2022.pdf")
PortfolioPeriod p4 = revolutSvc.parseStatement(fis4);   
````
- Consolidate all PortfolioPeriod objects into one  

````
Map<String, PortfolioPeriod> ptfs = revolutSvc.consolidate(List.of(p1, p2, p3, p4));
````
- Enjoy the result and use it as you need

![Datamodel diagram](diagrams/datamodel_2.png)

### Requirements
- Java 11 or above

### License

- The _Brinvex-Util-Revolut_ is released under version 2.0 of the Apache License.
