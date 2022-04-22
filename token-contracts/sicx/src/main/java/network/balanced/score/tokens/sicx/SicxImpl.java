package network.balanced.score.tokens.sicx;

import network.balanced.score.lib.interfaces.Sicx;
import network.balanced.score.lib.tokens.IRC2Burnable;
import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Check.onlyOwner;

public class SicxImpl extends IRC2Burnable implements Sicx {
    public static final String TAG = "sICX";
    private static final String TOKEN_NAME = "Staked ICX";
    private static final String SYMBOL_NAME = "sICX";
    private static final BigInteger DECIMALS = BigInteger.valueOf(18);
    private static final String STAKING = "staking";

    public static final VarDB<Address> stakingAddress = Context.newVarDB(STAKING, Address.class);


    public SicxImpl(Address _admin) {
        super(TOKEN_NAME, SYMBOL_NAME, DECIMALS);
        if (getStaking() == null) {
            stakingAddress.set(_admin);
        }
    }

    @External(readonly = true)
    public String getPeg() {
        return TAG;
    }

    @External
    public void setStaking(Address _address) {
        onlyOwner();
        stakingAddress.set(_address);
    }

    @External(readonly = true)
    public Address getStaking() {
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

    @Override
    @External
    public void transfer(Address _to, BigInteger _value, @Optional byte[] _data) {
        if (!_to.equals(stakingAddress.get())) {
            Context.call(stakingAddress.get(), "transferUpdateDelegations", Context.getCaller(), _to, _value);
        }
        transfer(Context.getCaller(), _to, _value, _data);
    }

}
