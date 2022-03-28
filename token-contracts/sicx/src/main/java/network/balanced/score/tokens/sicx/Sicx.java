package network.balanced.score.tokens.sicx;

import network.balanced.score.tokens.sicx.tokens.Checks;
import network.balanced.score.tokens.sicx.tokens.IRC2Mintable;
import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;

import java.math.BigInteger;

public class Sicx extends IRC2Mintable {
    public static final String TAG = "sICX";
    private static final String TOKEN_NAME = "Staked ICX";
    private static final String SYMBOL_NAME = "sICX";
    private static final String PEG = "peg";
    private static final String STAKING = "staking";

    public final VarDB<String> peg = Context.newVarDB(PEG, String.class);
    public static final VarDB<Address> stakingAddress = Context.newVarDB(STAKING, Address.class);

    public Sicx(Address _admin) {
        super(TOKEN_NAME, SYMBOL_NAME, null, null);
        stakingAddress.set(_admin);
        peg.set("sICX");
    }

    @External(readonly = true)
    public String getPeg() {
        return peg.getOrDefault("");
    }

    @External
    public void setStakingAddress(Address _address) {
        Checks.onlyOwner();
        stakingAddress.set(_address);
    }

    @External(readonly = true)
    public Address getStakingAddress() {
        return stakingAddress.get();
    }

    @External(readonly = true)
    public BigInteger priceInLoop() {
        return (BigInteger) Context.call(stakingAddress.get(), "getTodayRate");
    }

    @External(readonly = true)
    public BigInteger lastPriceInLoop() {
        return priceInLoop();
    }

}
