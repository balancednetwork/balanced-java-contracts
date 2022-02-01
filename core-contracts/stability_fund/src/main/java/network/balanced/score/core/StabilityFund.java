package network.balanced.score.core;

import score.Context;
import score.VarDB;
import score.Address;
import score.annotation.External;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import java.math.BigInteger;


public class StabilityFund {

    // Contract name.
    private final String name;

    // Balanced contract addresses.
    private final VarDB<Address> admin = Context.newVarDB("admin", Address.class);
    private final VarDB<Address> sicx = Context.newVarDB("sICX", Address.class);
    private final VarDB<Address> bnusd = Context.newVarDB("bnUSD", Address.class);
    private final VarDB<Address> rebalancing = Context.newVarDB("rebalancing", Address.class);
    private final VarDB<Address> dex = Context.newVarDB("dex", Address.class);
    private final VarDB<Address> governance = Context.newVarDB("governance", Address.class);
    private final VarDB<Address> daofund = Context.newVarDB("daofund", Address.class);

    public StabilityFund(String name, Address governance, Address admin) {
        this.name = name;
        this.governance.set(governance);
        this.admin.set(admin);
    }

    @External(readonly = true)
    public String name() {
        return name;
    }

    @External
    public void setDaofund(Address address) {
        Context.require(Context.getCaller() == this.admin.get());
        Context.require(address.isContract());
        this.daofund.set(address);
    }

    @External(readonly = true)
    public Address getDaofund() {
        return this.daofund.get();
    }

    @External
    public void setSicx(Address address) {
        Context.require(Context.getCaller() == this.admin.get());
        Context.require(address.isContract());
        this.sicx.set(address);
    }

    @External(readonly = true)
    public Address getSicx() {
        return this.sicx.get();
    }

    @External 
    public void setbnUSD(Address address) {
        Context.require(Context.getCaller() == this.admin.get());
        Context.require(address.isContract());
        this.bnusd.set(address);
    }

    @External(readonly = true)
    public Address getbnUSD() {
        return this.bnusd.get();
    }

    @External
    public void setRebalancing(Address address) {
        Context.require(Context.getCaller() == this.admin.get());
        Context.require(address.isContract());
        this.rebalancing.set(address);
    }

    @External(readonly = true)
    public Address getRebalancing() {
        return this.rebalancing.get();
    }

    @External
    public void setDex(Address address) {
        Context.require(Context.getCaller() == this.admin.get());
        Context.require(address.isContract());
        this.dex.set(address);
    }

    @External(readonly = true)
    public Address getDex() {
        return this.dex.get();
    }

    @External
    public void raisePrice(BigInteger amount) {
        Context.require(Context.getCaller() == this.rebalancing.get());
        byte[] data = createSwapData(bnusd.get());
        transferToken(this.sicx.get(), this.dex.get(), amount, data);
    }
    
    @External
    public void lowerPrice(BigInteger amount) {
        Context.require(Context.getCaller() == this.rebalancing.get());
        byte[] data = createSwapData(sicx.get());
        transferToken(this.bnusd.get(), this.dex.get(), amount, data);
    }

    @External(readonly = true)
    public String getStabilityFundBalance() {
        JsonObject balances = Json.object();
        balances.add("sicx", getTokenBalance(this.sicx.get()).toString());
        balances.add("bnusd", getTokenBalance(this.bnusd.get()).toString());
        return balances.toString();
    }

    @External
    public void withdrawStabilityFunds(Address token, BigInteger maximumToWithdraw) {
        Context.require(Context.getCaller() == this.governance.get());
        transferToken(token, this.daofund.get(), maximumToWithdraw.min(getTokenBalance(token)), new byte[0]);
    }

    @External
    public void claimFunding() {
        Context.call(this.daofund.get(), "claim");
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        return;
    }

    @External(readonly = true)
    public BigInteger getTokenBalance(Address token) {
        return (BigInteger) Context.call(token, "balanceOf", Context.getAddress());
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
