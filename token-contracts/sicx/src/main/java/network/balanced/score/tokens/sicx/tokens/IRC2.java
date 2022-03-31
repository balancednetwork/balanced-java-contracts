package network.balanced.score.tokens.sicx.tokens;

import score.Address;
import score.Context;
import score.DictDB;
import score.VarDB;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import network.balanced.score.tokens.sicx.Sicx;


import java.math.BigInteger;

public abstract class IRC2 implements TokenStandard {
    final private static String NAME = "name";
    final private static String SYMBOL = "symbol";
    final private static String DECIMALS = "decimals";
    final private static String TOTAL_SUPPLY = "total_supply";
    final private static String BALANCES = "balances";
    final private static String ADMIN = "admin";

    protected VarDB<String> name = Context.newVarDB(NAME, String.class);
    protected VarDB<String> symbol = Context.newVarDB(SYMBOL, String.class);
    protected VarDB<BigInteger> decimals = Context.newVarDB(DECIMALS, BigInteger.class);
    protected VarDB<BigInteger> total_supply = Context.newVarDB(TOTAL_SUPPLY, BigInteger.class);

    protected DictDB<Address, BigInteger> balances = Context.newDictDB(BALANCES, BigInteger.class);
    // public because need to use this in onlyAdmin check
    public static VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);

    final protected static Address EOA_ZERO = Address.fromString("hx0000000000000000000000000000000000000000");

    public static BigInteger pow(BigInteger base, int exponent) {
        BigInteger res = BigInteger.ONE;

        for (int i = 1; i <= exponent; i++) {
            res = res.multiply(base);
        }

        return res;
    }

    public IRC2(@Optional String _tokenName, @Optional String _symbolName, @Optional BigInteger _initialSupply, @Optional BigInteger _decimals) {
        BigInteger DEFAULT_INITIAL_SUPPLY = BigInteger.valueOf(0);
        BigInteger DEFAULT_DECIMAL_VALUE = BigInteger.valueOf(18L);

        if (_initialSupply == null) {
            _initialSupply = DEFAULT_INITIAL_SUPPLY;
        }
        if (_decimals == null) {
            _decimals = DEFAULT_DECIMAL_VALUE;
        }

        Context.require(_decimals.compareTo(BigInteger.valueOf(0)) >= 0, "Decimals cannot be less than zero");
        Context.require(_initialSupply.compareTo(BigInteger.valueOf(0)) >= 0, "Initial Supply cannot be less than zero");
        if (_symbolName.length() <= 0) {
            Context.revert("Invalid Symbol name");
        }
        if (_symbolName.length() <= 0) {
            Context.revert("Invalid Symbol name");
        }
        if (_tokenName.length() <= 0) {
            Context.revert("Invalid Token name");
        }
        // set the total supply to the context variable
        BigInteger supply = _initialSupply.multiply(pow(BigInteger.TEN, _decimals.intValue()));
        total_supply.set(supply);
        this.decimals.set(_decimals);

        // set other values
        final Address caller = Context.getCaller();
        name.set(_tokenName);
        this.symbol.set(_symbolName);
        balances.set(caller, supply);
    }

    @External(readonly = true)
    public String name() {
        return name.getOrDefault("");
    }

    @External(readonly = true)
    public String symbol() {
        return symbol.getOrDefault("");
    }

    @External(readonly = true)
    public BigInteger decimals() {
        return decimals.get();
    }

    @External(readonly = true)
    public BigInteger totalSupply() {
        return total_supply.get();
    }

    @External(readonly = true)
    public BigInteger balanceOf(Address _owner) {
        return balances.getOrDefault(_owner, BigInteger.valueOf(0));
    }


    @External(readonly = true)
    public Address getAdmin() {
        return admin.get();
    }

    @External
    public void setAdmin(Address _admin) {
        Checks.onlyOwner();
        admin.set(_admin);
    }

    @EventLog(indexed = 3)
    void Transfer(Address _from, Address _to, BigInteger _value, byte[] _data) {

    }

    @EventLog(indexed = 1)
    void Mint(Address account, BigInteger amount, byte[] _data) {

    }

    @EventLog(indexed = 1)
    void Burn(Address account, BigInteger amount) {

    }

    @External
    public void transfer(Address _to, BigInteger _value, byte[] _data) {
        if (_data == null) {
            _data = "None".getBytes();
        }
        if (!_to.equals(Sicx.stakingAddress.get())) {
            Context.call(Sicx.stakingAddress.get(), "transferUpdateDelegations", Context.getCaller(), _to, _value);
        }
        _transfer(Context.getCaller(), _to, _value, _data);
    }

    protected void _transfer(Address _from, Address _to, BigInteger _value, byte[] _data) {
        Context.require(_value.compareTo(BigInteger.valueOf(0)) > 0, ": Transferring value cannot be less than or equal to 0.");
        Context.require(
                balanceOf(_from).compareTo(_value) >= 0,
                ": Insufficient balance."
        );

        this.balances.set(_from, balances.getOrDefault(_from, BigInteger.ZERO).subtract(_value));
        this.balances.set(_to, balances.getOrDefault(_to, BigInteger.ZERO).add(_value));

        Transfer(_from, _to, _value, _data);

        if (_to.isContract()) {
            Context.call(_to, "tokenFallback", _from, _value, _data);
        }

    }

    protected void _mint(Address account, BigInteger amount, @Optional byte[] _data) {
        Checks.onlyAdmin();
        if (amount.compareTo(BigInteger.ZERO) <= 0) {
            Context.revert("Invalid Value");
        }
        total_supply.set(total_supply.getOrDefault(BigInteger.ZERO).add(amount));
        balances.set(account, balances.getOrDefault(account, BigInteger.ZERO).add(amount));

        Mint(account, amount, _data);

        Transfer(EOA_ZERO, account, amount, _data);
        if (account.isContract()) {
            // If the recipient is SCORE, then calls `tokenFallback` to hand over control.
            Context.call(account, "tokenFallback", EOA_ZERO, amount, _data);
        }
    }

    protected void _burn(Address account, BigInteger amount) {
        Checks.onlyAdmin();
        if (amount.compareTo(BigInteger.ZERO) <= 0) {
            Context.revert("Invalid Value");
        }

        BigInteger newTotalSupply = total_supply.getOrDefault(BigInteger.ZERO).subtract(amount);
        BigInteger newUserBalance = balances.getOrDefault(account, BigInteger.ZERO).subtract(amount);
        if (newTotalSupply.compareTo(BigInteger.ZERO) < 0) {
            Context.revert("Total Supply can not be set to negative");
        }
        if (newUserBalance.compareTo(BigInteger.ZERO) <= 0) {
            Context.revert("User Balance can not be set to negative");
        }

        total_supply.set(newTotalSupply);
        balances.set(account, newUserBalance);

        Burn(account, amount);

        String data = "None";
        Transfer(account, EOA_ZERO, amount, data.getBytes());
    }
}
