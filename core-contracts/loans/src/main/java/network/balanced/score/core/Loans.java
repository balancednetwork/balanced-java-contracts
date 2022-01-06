package network.balanced.score.core;

import score.Context;
import score.VarDB;
import score.DictDB;
import score.Address;
import score.annotation.External;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import java.math.BigInteger;

import java.util.Map;

import score.ByteArrayObjectWriter;
import score.Context;
import score.ObjectReader;

import java.math.BigInteger;


public class Loans {

    class LoanTaker {
        public BigInteger collateral;
        public BigInteger rebalanceTokens;
        public BigInteger lockedLoan;

        public LoanTaker() {
            this.collateral = BigInteger.ZERO;
            this.rebalanceTokens = BigInteger.ZERO;
            this.lockedLoan = BigInteger.ZERO;
        }
    }

    // Contract name.
    private final String name;
    
    private static final BigInteger POINTS = BigInteger.TEN.pow(18);

    //Mock variables
    private static final BigInteger PRICE = BigInteger.valueOf(2);
    private static final BigInteger LTV = BigInteger.valueOf(35).pow(17);
    private static final BigInteger BASE_LTV = BigInteger.valueOf(5).pow(17);

    // Balanced contract addresses.
    private final VarDB<Address> sicx = Context.newVarDB("sICX", Address.class);
    private final VarDB<Address> bnusd = Context.newVarDB("bnUSD", Address.class);
    private final VarDB<Address> dex = Context.newVarDB("dex", Address.class);


    private final VarDB<BigInteger> rebalanceCollateral = Context.newVarDB("RCollateral", BigInteger.class);
    private final VarDB<BigInteger> rebalaceLoan = Context.newVarDB("RLoan", BigInteger.class);
    private final VarDB<BigInteger> totalRebalanceShares = Context.newVarDB("TRShares", BigInteger.class);

    private final DictDB<Address, LoanTaker> loanTakers = Context.newDictDB("Loan_Takers",LoanTaker.class);
    
    public Loans(String name) {
        this.name = name;
        rebalanceCollateral.set(BigInteger.ZERO);
        rebalaceLoan.set(BigInteger.ZERO);
        totalRebalanceShares.set(BigInteger.ZERO);
    }

    @External(readonly = true)
    public String name() {
        return name;
    }

    @External
    public void setSicx(Address address) {
        
        this.sicx.set(address);
    }

    @External(readonly = true)
    public Address getSicx() {
        return this.sicx.get();
    }

    @External 
    public void setbnUSD(Address address) {
        this.bnusd.set(address);
    }

    @External(readonly = true)
    public Address getbnUSD() {
        return this.bnusd.get();
    }

    @External
    public void setDex(Address address) {
        this.dex.set(address);
    }

    @External(readonly = true)
    public Address getDex() {
        return this.dex.get();
    }

    @External(readonly = true)
    public Map<String, BigInteger> get() {
        return Map.of(
                "RebalanceCollateral", rebalanceCollateral.get(),
                "Loan", rebalaceLoan.get()
        );
    }


    @External(readonly = true)
    public Map<String, BigInteger> getPosition(Address address) {
        LoanTaker user  = loanTakers.getOrDefault(address, new LoanTaker());

        return Map.of(
                "Collateral", getUserCollateral(user),
                "Loan", getUserLoan(user)
        );
    }

    @External
    public void raisePrice(BigInteger amount) {
        // Context.require(Context.getCaller() == this.rebalancing.get());
        // byte[] data = createSwapData(bnusd.get());
        // transferToken(this.sicx.get(), this.dex.get(), amount, data);
        rebalanceCollateral.set(rebalanceCollateral.get().subtract(amount));
        rebalaceLoan.set(rebalaceLoan.get().subtract(amount.multiply(PRICE)));
    }
    
    @External
    public void lowerPrice(BigInteger amount) {
        // Context.require(Context.getCaller() == this.rebalancing.get());
        // byte[] data = createSwapData(sicx.get());
        // transferToken(this.bnusd.get(), this.dex.get(), amount, data);
        rebalanceCollateral.set(rebalanceCollateral.get().add(amount.divide(PRICE)));
        rebalaceLoan.set(rebalaceLoan.get().add(amount));
    }

    // @External
    // public void withdraw(BigInteger collateral, BigInteger repaidAmount) {
    //     LoanTaker user  = loanTakers.get(Context.getCaller());
    //     Biginteger collateralInPool = getUserCollateral(user);
    //     BigInteger loan = getUserLoan(user);   //ignore lockedLoan
        
    //     Context.require(repaidAmount <= loan);
    //     Context.require(collateralInPool.add(user.collateral) >= collateral);
        
    //     BigInteger repaidRebalanceTokens = repaidAmount.multiply(user.rebalancingTokens).divide(loan);
    //     BigInteger removedRebalanceCollateral = repaidAmount.multiply(collateralInPool).divide(loan);

    //     totalRebalanceShares.set(totalRebalanceShares.get().subtract(repaidRebalanceTokens));
    //     rebalanceCollateral.set(rebalanceCollateral.get().subtract(removedRebalanceCollateral));
    //     rebalaceLoan.set(rebalaceLoan.get().subtract(repaidAmount));
    //     collateral = collateral.subtract(removedRebalanceCollateral);

    //     if (collateral.compareTo(BigInteger.ZERO)) {
    //         user.collateral = user.collateral.subtract(collateral);
    //     }
    // }

    private void depositAndBorrow(Address _from, BigInteger collateral, BigInteger loanSize) {
        LoanTaker user  = loanTakers.getOrDefault(_from, new LoanTaker());

        BigInteger collateralForRebalancing = calculateCollateralForRebalancing(loanSize);
        BigInteger rebalancingTokens = calculateRebalancingTokens(loanSize);
    
        //Mint and send to user
        user.collateral = user.collateral.add(collateral.subtract(collateralForRebalancing));
        user.rebalanceTokens = user.rebalanceTokens.add(rebalancingTokens);
        loanTakers.set(_from, user);
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        Address token = Context.getCaller();
        //Context.require(token.equals(sicx.get()), "Token Fallback: Only BALN deposits are allowed");

        Context.require(_value.signum() > 0, "Token Fallback: Token value should be a positive number");
        String unpackedData = new String(_data);
        Context.require(!unpackedData.equals(""), "Token Fallback: Data can't be empty");

        JsonObject json = Json.parse(unpackedData).asObject();

        String method = json.get("method").asString();
        JsonObject params = json.get("params").asObject();

        switch (method) {
            case "depositAndBorrow":
                BigInteger amount = BigInteger.valueOf(params.get("amount").asLong()).multiply(BigInteger.TEN.pow(18));
                depositAndBorrow(_from, _value, amount);
                break;
            default:
                Context.revert("Token fallback: Unimplemented tokenfallback action");
                break;
        }
    }

    private BigInteger calculateRebalancingTokens(BigInteger loanSize) {
        BigInteger rebalanceTokens = loanSize;
        if (!totalRebalanceShares.get().equals(BigInteger.ZERO)) {
            rebalanceTokens = totalRebalanceShares.get().multiply(loanSize).divide(rebalaceLoan.get());
        }

        totalRebalanceShares.set(totalRebalanceShares.get().add(rebalanceTokens));
        rebalaceLoan.set(rebalaceLoan.get().add(loanSize));

        return rebalanceTokens;
    }

    private BigInteger calculateCollateralForRebalancing(BigInteger loanSize) {
        BigInteger collateralForRebalancing = loanSize;
        if (!rebalanceCollateral.get().equals(BigInteger.ZERO)) {
            collateralForRebalancing = loanSize.multiply(rebalanceCollateral.get()).divide(rebalaceLoan.get());
        }

        rebalanceCollateral.set(rebalanceCollateral.get().add(collateralForRebalancing));

        return collateralForRebalancing;
    }

    private BigInteger getUserLoan(LoanTaker user) {
        return user.rebalanceTokens.multiply(rebalaceLoan.get()).divide(totalRebalanceShares.get());
    }

    private BigInteger getUserCollateral(LoanTaker user) {
        BigInteger poolCollateral =  user.rebalanceTokens.multiply(rebalanceCollateral.get()).divide(totalRebalanceShares.get());
        return poolCollateral.add(user.collateral);
    }

    private void transferToken(Address token, Address to, BigInteger amount, byte[] data) {
        Context.call(token, "transfer", to, amount, data);
    }

    private byte[] createSwapData(Address toToken) {
        JsonObject data = Json.object();
        data.add("method", "_swap");
        data.add("params", Json.object().add("toToken", toToken.toString()));
        return data.toString().getBytes();
    }
}
