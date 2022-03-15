package network.balanced.score.core;

import score.VarDB;
import score.Context;
import score.Address;

import java.util.Map;
import static java.util.Map.entry;

import java.math.BigInteger;

public class Asset {
    public VarDB<Address> address;
    public VarDB<Long> added;

    public VarDB<Boolean> active;
    public VarDB<Boolean> isCollateral;
   
    public VarDB<BigInteger> burnedTokens;
    public VarDB<BigInteger> badDebt;
    public VarDB<BigInteger> liquidationPool;
    public VarDB<Boolean> dead;
    private String dbKey;

    public Asset(String key) {
        dbKey = key;

        address = (VarDB<Address>)Context.newBranchDB("address", Address.class).at(dbKey);
        added = (VarDB<Long>)Context.newBranchDB("added", Long.class).at(dbKey);

        active = (VarDB<Boolean>)Context.newBranchDB("active", Boolean.class).at(dbKey);
        isCollateral = (VarDB<Boolean>)Context.newBranchDB("is_collateral", Boolean.class).at(dbKey);

        burnedTokens = (VarDB<BigInteger>)Context.newBranchDB("burned", BigInteger.class).at(dbKey);
        badDebt = (VarDB<BigInteger>)Context.newBranchDB("bad_debt", BigInteger.class).at(dbKey);
        liquidationPool = (VarDB<BigInteger>)Context.newBranchDB("liquidation_pool", BigInteger.class).at(dbKey);
        dead = (VarDB<Boolean>)Context.newBranchDB("dead_market", Boolean.class).at(dbKey);
    }

    public String symbol() {
        return (String) Loans.call(address.get(), "symbol");
    }

    public BigInteger totalSupply() {
        return (BigInteger) Loans.call(address.get(), "totalSupply");
    }

    public BigInteger balanceOf(Address address) {
        return (BigInteger) Loans.call(this.address.get(), "balanceOf", address);
    } 

    public String getPeg() {
        return (String) Loans.call(address.get(), "getPeg");
    }

    public BigInteger priceInLoop() {
        return (BigInteger) Loans.call(address.get(), "priceInLoop");
    }

    public BigInteger lastPriceInLoop() {
        return (BigInteger) Loans.call(address.get(), "lastPriceInLoop");
    }

    public void mint(Address to, BigInteger amount) {
        Loans.call(address.get(), "mintTo", to, amount);
    }

    public void burn(BigInteger amount) {
        Loans.call(address.get(), "burn", amount);
        burnedTokens.set(burnedTokens.getOrDefault(BigInteger.ZERO).add(amount));
    }

    public void burnFrom(Address from, BigInteger amount) {
        Loans.call(address.get(), "burnFrom", from, amount);
        burnedTokens.set(burnedTokens.getOrDefault(BigInteger.ZERO).add(amount));
    }

    public Address getAddress() {
        return address.get();
    }

    public boolean isActive() {
        return active.getOrDefault(false);
    }

    public boolean isCollateral() {
        return isCollateral.getOrDefault(false);
    }

    public boolean isDead() {
        if (!active.getOrDefault(false) || isCollateral.getOrDefault(false)) {
            return false;
        }

        BigInteger outStanding = totalSupply().subtract(badDebt.get());
        BigInteger poolValue = liquidationPool.get().multiply(priceInLoop()).divide(AssetDB.get("sICX").priceInLoop());
        BigInteger netBadDebt = badDebt.get().subtract(poolValue);
        Boolean isDead = netBadDebt.compareTo(outStanding.divide(BigInteger.valueOf(2))) == 1;

        if (dead.getOrDefault(false) != isDead) {
            dead.set(isDead);
        }

        return isDead;
    }

    public LinkedListDB getBorrowers() {
        return new LinkedListDB("borrowers", dbKey);
    }

    public void removeBorrowers(int positionId) {
        getBorrowers().remove(positionId);
    }

    public Map<String, Object> toMap() {
        return Map.ofEntries(
            entry("symbol", symbol()),
            entry("address", address.get()),
            entry("peg", getPeg()),
            entry("added", added.get()),
            entry("is_collateral", isCollateral.getOrDefault(false)),
            entry("active", active.getOrDefault(false)),
            entry("borrowers", getBorrowers().size()),
            entry("total_supply", totalSupply()),
            entry("total_burned", burnedTokens.getOrDefault(BigInteger.ZERO)),
            entry("bad_debt", badDebt.getOrDefault(BigInteger.ZERO)),
            entry("liquidation_pool", liquidationPool.getOrDefault(BigInteger.ZERO)),
            entry("dead_market", dead.getOrDefault(false))
        );
    }
}