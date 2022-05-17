package network.balanced.score.tokens.sicx;

import network.balanced.score.lib.tokens.IRC2PresetFixedSupply;
import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Check.only;
import static network.balanced.score.lib.utils.Check.onlyOwner;

public class SicxImpl extends IRC2PresetFixedSupply {
    public static final String TAG = "sICX";
    private static final String TOKEN_NAME = "Staked ICX";
    private static final String SYMBOL_NAME = "sICX";
    private static final BigInteger INITIAL_SUPPLY = BigInteger.ZERO;
    private static final BigInteger DECIMALS = BigInteger.valueOf(18);
    private static final String PEG = "peg";
    private static final String STAKING = "staking";
    final private static String ADMIN = "admin";


    public final VarDB<String> peg = Context.newVarDB(PEG, String.class);
    public static final VarDB<Address> stakingAddress = Context.newVarDB(STAKING, Address.class);
    public static VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);


    public SicxImpl(Address _admin) {
        super(TOKEN_NAME, SYMBOL_NAME, INITIAL_SUPPLY, DECIMALS);
        stakingAddress.set(_admin);
        peg.set("sICX");
    }

    @External(readonly = true)
    public String getPeg() {
        return peg.getOrDefault("");
    }

    @External
    public void setStakingAddress(Address _address) {
        onlyOwner();
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

    @External(readonly = true)
    public Address getAdmin() {
        return admin.get();
    }

    @External
    public void setAdmin(Address _admin) {
        onlyOwner();
        admin.set(_admin);
    }

    @Override
    @External
    public void transfer(Address _to, BigInteger _value, byte[] _data) {
        if (_data == null) {
            _data = "None".getBytes();
        }
        if (!_to.equals(SicxImpl.stakingAddress.get())) {
            Context.call(SicxImpl.stakingAddress.get(), "transferUpdateDelegations", Context.getCaller(), _to, _value);
        }
        transfer(Context.getCaller(), _to, _value, _data);
    }

    @External
    public void mint(BigInteger _amount, @Optional byte[] _data) {
        only(admin);
        if (_data == null) {
            String data = "None";
            _data = data.getBytes();
        }
        mint(Context.getCaller(), _amount, _data);
    }

    @External
    public void mintTo(Address _account, BigInteger _amount, @Optional byte[] _data) {
        only(admin);
        if (_data == null) {
            String data = "None";
            _data = data.getBytes();
        }
        mint(_account, _amount, _data);
    }

    @External
    public void burn(BigInteger _amount) {
        only(admin);
        burn(Context.getCaller(), _amount);
    }

    @External
    public void burnFrom(Address _account, BigInteger _amount) {
        only(admin);
        burn(_account, _amount);
    }

}
