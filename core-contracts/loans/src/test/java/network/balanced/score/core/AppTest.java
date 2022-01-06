package network.balanced.score.core;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import com.iconloop.score.token.irc2.IRC2Mintable;
import com.eclipsesource.json.Json;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import score.Context;
import score.Address;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

class User {
    public BigInteger collateral;
    public BigInteger loan;

    public User() {
        this.collateral = BigInteger.ZERO;
        this.loan = BigInteger.ZERO;
    }
}

class ReferenceLoan {
    public BigInteger totalLoan = BigInteger.ZERO;
    public BigInteger totalCollateral= BigInteger.ZERO;
    public BigInteger price = BigInteger.valueOf(2);

    private Map<Address, User> users = new HashMap<>();

    public void depositAndBorrow(Address address, BigInteger collateral, BigInteger loan) {
        User loanTaker = users.getOrDefault(address, new User());
        loanTaker.loan = loanTaker.loan.add(loan);
        loanTaker.collateral = loanTaker.collateral.add(collateral);

        totalCollateral = totalCollateral.add(collateral);
        totalLoan = totalLoan.add(loan);
        users.put(address, loanTaker);
    }

    public void raisePrice(BigInteger collateralToSell) {
        BigInteger totalCollateralChange = BigInteger.ZERO;
        BigInteger totalLoanChange = BigInteger.ZERO;
        for(Map.Entry<Address, User>  entry : users.entrySet()) {
            Address address = entry.getKey();
            User loanTaker = entry.getValue();
            BigInteger rebalanceShare = loanTaker.loan.multiply(collateralToSell).divide(totalLoan);

            loanTaker.collateral = loanTaker.collateral.subtract(rebalanceShare);
            totalCollateralChange = totalCollateralChange.subtract(rebalanceShare);

            loanTaker.loan = loanTaker.loan.subtract(rebalanceShare.multiply(price));
            totalLoanChange = totalLoanChange.subtract(rebalanceShare.multiply(price));
        }

        totalCollateral = totalCollateral.add(totalCollateralChange);
        totalLoan = totalLoan.add(totalLoanChange);
    }

    public void lowerPrice(BigInteger loanToAdd) {
        BigInteger totalCollateralChange = BigInteger.ZERO;
        BigInteger totalLoanChange = BigInteger.ZERO;
        for(Map.Entry<Address, User>  entry : users.entrySet()) {
            Address address = entry.getKey();
            User loanTaker = entry.getValue();
            BigInteger rebalanceShare = loanTaker.loan.multiply(loanToAdd).divide(totalLoan);

            loanTaker.collateral = loanTaker.collateral.add(rebalanceShare.divide(price));
            totalCollateralChange = totalCollateralChange.add(rebalanceShare.divide(price));

            loanTaker.loan = loanTaker.loan.add(rebalanceShare);
            totalLoanChange = totalLoanChange.add(rebalanceShare);
        }

        totalCollateral = totalCollateral.add(totalCollateralChange);
        totalLoan = totalLoan.add(totalLoanChange);
    }

    public Map<String, BigInteger> getPosition(Address address) {
        User loanTaker = users.getOrDefault(address, new User());
        return Map.of(
                "Loan", loanTaker.loan,
                "Collateral", loanTaker.collateral
            );
    }
}

@DisplayName("Loans Tests")
class LoansTests extends TestBase {

    private static final ServiceManager sm = getServiceManager();

    private static final Account owner = sm.createAccount();
    private static Score loans;
    private static Score sicx;
    private static Score bnusd;

    // Loans score deployment settings.
    private static final String nameLoans = "Loans";
    private final ArrayList<Account> accounts = new ArrayList<>();
    private static final BigInteger MINT_AMOUNT = BigInteger.TEN.pow(40);
    private ReferenceLoan referenceLoan;

    // Sicx score deployment settings.
    private static final String nameSicx = "Staked icx";
    private static final String symbolSicx = "SICX";
    private static final int decimalsSicx = 18;
    private static final BigInteger initalsupplySicx = BigInteger.TEN.pow(50);

    // Bnusd score deployment settings.
    private static final String nameBnusd = "Balanced usd";
    private static final String symbolBnusd = "BNUSD";
    private static final int decimalsBnusd = 18;
    private static final BigInteger initalsupplyBnusd = BigInteger.TEN.pow(50);

    public static class IRC2BasicToken extends IRC2Mintable {
        public IRC2BasicToken(String _name, String _symbol, int _decimals, BigInteger _totalSupply) {
            super(_name, _symbol, _decimals);
            mintTo(Context.getCaller(), _totalSupply);
        }
    }

    private void setupAccounts() {
        int numberOfAccounts = 10;
        for (int accountNumber = 0; accountNumber < numberOfAccounts; accountNumber++) {
            Account account = sm.createAccount();
            accounts.add(account);
            sicx.invoke(owner, "mintTo", account.getAddress(), MINT_AMOUNT);
        }
    }

    private void takeLoan(Account account, int collateral, int loan) {
        byte[] params = tokenData("depositAndBorrow", Map.of("amount",  loan));
        sicx.invoke(account, "transfer", loans.getAddress(), BigInteger.valueOf(collateral).multiply(BigInteger.TEN.pow(18)), params);
        referenceLoan.depositAndBorrow(account.getAddress(),  BigInteger.valueOf(collateral).multiply(BigInteger.TEN.pow(18)), BigInteger.valueOf(loan).multiply(BigInteger.TEN.pow(18)));
    }

    private void lowerPrice(BigInteger loanToAdd) { 
        loans.invoke(owner, "lowerPrice", loanToAdd.multiply(BigInteger.TEN.pow(18)));  
        referenceLoan.lowerPrice(loanToAdd.multiply(BigInteger.TEN.pow(18)));
    }

    private void raisePrice(BigInteger collateralToSell) {
        loans.invoke(owner, "raisePrice", collateralToSell.multiply(BigInteger.TEN.pow(18)));
        referenceLoan.raisePrice(collateralToSell.multiply(BigInteger.TEN.pow(18)));
    }

    public byte[] tokenData(String method, Map<String, Object> params) {
        Map<String, Object> map = new HashMap<>();
        map.put("method", method);
        map.put("params", params);
        JSONObject data = new JSONObject(map);
        return data.toString().getBytes();
    }

    @BeforeEach
    public void setup() throws Exception {
        loans = sm.deploy(owner, Loans.class, nameLoans);
        sicx = sm.deploy(owner, IRC2BasicToken.class, nameSicx, symbolSicx, decimalsSicx, initalsupplySicx);
        bnusd = sm.deploy(owner, IRC2BasicToken.class, nameBnusd, symbolBnusd, decimalsBnusd, initalsupplyBnusd);
        setupAccounts();
        loans.invoke(owner, "setSicx", sicx.getAddress());
        referenceLoan = new ReferenceLoan();
    }


    // @Test
    // void singleLoan() {
    //     byte[] params = tokenData("depositAndBorrow", Map.of("amount",  100));
    //     sicx.invoke(owner, "transfer", loans.getAddress(), BigInteger.TEN.pow(21), params);
    //     System.out.println(loans.call("getPosition", owner.getAddress()));
    //     //System.out.println(stabilityFund.call("getDaofund"));

    // }

    // @Test
    // void testLoan() {
    //     Account account1 = accounts.get(0);
    //     Account account2 = accounts.get(1);
    //     Account account3 = accounts.get(2);
    //     Account account4 = accounts.get(3);

    //     takeLoan(account1, 1020, 100);
    //     takeLoan(account2, 2000, 400);
    //     raisePrice(BigInteger.valueOf(125));
    //     takeLoan(account3, 1000, 100);
    //     raisePrice(BigInteger.valueOf(35));
    //     takeLoan(account4, 1000, 200);
    // }

     @Test
    void testLoan2() {

        Account account1 = accounts.get(0);
        Account account2 = accounts.get(1);
        Account account3 = accounts.get(2);
        Account account4 = accounts.get(3);
        Account account5 = accounts.get(4);
        Account account6 = accounts.get(5);
        Account account7 = accounts.get(6);
        Account account8 = accounts.get(7);

        takeLoan(account1, 1000, 100);
        Map<String, BigInteger> ratio = (Map<String, BigInteger>)loans.call("get");
        System.out.println("ratio:" + ratio.get("RebalanceCollateral").divide(ratio.get("Loan")).toString());
        takeLoan(account2, 2000, 400);
        raisePrice(BigInteger.valueOf(120));
        takeLoan(account3, 1000, 100);
        raisePrice(BigInteger.valueOf(30));
        takeLoan(account4, 4030, 250);
        takeLoan(account5, 2300, 111);
        lowerPrice(BigInteger.valueOf(100));
        takeLoan(account6, 400, 40);
        takeLoan(account7, 7000, 1800);

    }

    @AfterEach
    void comparePositions() {
        Map<String, BigInteger> ratio = (Map<String, BigInteger>)loans.call("get");
        System.out.println("ratio:" + ratio.get("RebalanceCollateral").multiply(BigInteger.TEN.pow(3)).divide(ratio.get("Loan")).toString());
        System.out.println(ratio);
        System.out.println(referenceLoan.totalLoan.toString());
        for (Account account : accounts) {
            Map<String, BigInteger> position = (Map<String, BigInteger>)loans.call("getPosition", account.getAddress());
            Map<String, BigInteger> referencePosition = referenceLoan.getPosition(account.getAddress());

            if (!position.get("Collateral").equals(referencePosition.get("Collateral"))){
                System.out.println ("collateral");
                System.out.println (position.get("Collateral").toString() + " == " + (referencePosition.get("Collateral")).toString());
            }
            
            if (!position.get("Loan").equals(referencePosition.get("Loan"))) {
                System.out.println ("loans");
                System.out.println (position.get("Loan").toString() + " == " + (referencePosition.get("Loan")).toString());
            }
           
        }
    }
  
}