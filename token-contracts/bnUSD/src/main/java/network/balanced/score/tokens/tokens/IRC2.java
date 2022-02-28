package network.balanced.score.tokens.tokens;

import java.math.BigInteger;

import score.*;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;

public abstract class IRC2 implements TokenStandard {
    final private static String NAME = "name";
    final private static String SYMBOL = "symbol";
    final private static String DECIMALS = "decimals";
    final private static String TOTAL_SUPPLY = "total_supply";
    final private static String BALANCES = "balances";
    final private static String ADMIN = "admin";
    final private static String ACCOUNTS = "account";

    static String zero = "0";
    final protected static Address EOA_ZERO = Address.fromString("hx" + zero.repeat(40));


    protected VarDB<String> name = Context.newVarDB(NAME, String.class);
    protected VarDB<String> symbol = Context.newVarDB(SYMBOL, String.class);
    protected VarDB<BigInteger> decimals = Context.newVarDB(DECIMALS, BigInteger.class);
    protected VarDB<BigInteger> total_supply = Context.newVarDB(TOTAL_SUPPLY, BigInteger.class);

    public ArrayDB<Address> addresses = Context.newArrayDB(ACCOUNTS, Address.class);
    protected DictDB<Address, BigInteger> balances = Context.newDictDB(BALANCES, BigInteger.class);
    // public because need to use this in onlyAdmin check
    public static VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);

    private final BigInteger MAX_HOLDER_COUNT = BigInteger.valueOf(400);


    public static BigInteger pow(BigInteger base, int exponent){
        BigInteger res = BigInteger.ONE;

        for(int i = 1; i <= exponent; i++){
            res = res.multiply(base);
        }

        return res;
    }



    /**
     *
     * @param _tokenName: The name of the token.
     * @param _symbolName: The symbol of the token.
     * @param _initialSupply :The total number of tokens to initialize with.
     * 					It is set to total supply in the beginning, 0 by default.
     * @param _decimals The number of decimals. Set to 18 by default.
     */
    public IRC2(@Optional String _tokenName, @Optional String _symbolName, @Optional BigInteger _initialSupply, @Optional BigInteger _decimals){
        BigInteger DEFAULT_INITIAL_SUPPLY = BigInteger.valueOf(0);
        BigInteger DEFAULT_DECIMAL_VALUE = BigInteger.valueOf(18L);

        if(_initialSupply == null){
            _initialSupply = DEFAULT_INITIAL_SUPPLY;
        }
        if(_decimals == null){
            _decimals = DEFAULT_DECIMAL_VALUE;
        }

        Context.require(_decimals.compareTo(BigInteger.valueOf(0)) >= 0, "Decimals cannot be less than zero");
        Context.require(_initialSupply.compareTo(BigInteger.valueOf(0)) >= 0, "Decimals cannot be less than zero");

        // set the total supply to the context variable
        BigInteger supply = _initialSupply.multiply(pow(BigInteger.TEN, _decimals.intValue()));
        total_supply.set(supply);
        this.decimals.set(_decimals);

        // set other values
        final Address caller = Context.getCaller();
        if(_tokenName != null){
            name.set(_tokenName);
        }
        if(_symbolName != null){
            this.symbol.set(_symbolName);
        }
        addresses.add(caller);
        balances.set(caller, supply);
    }

    /**
     *
     * @return Name of the token
     */
    @External(readonly = true)
    public String name() {
        return name.getOrDefault("");
    }

    /**
     * @return Symbol of the token
     */
    @External(readonly = true)
    public String symbol() {
        return symbol.get();
    }

    /**
     *
     * @return Number of decimals
     */
    @External(readonly = true)
    public BigInteger decimals() {
        return decimals.get();
    }

    /**
     *
     * @return total number of tokens in existence.
     */
    @External(readonly = true)
    public BigInteger totalSupply() {
        return total_supply.get();
    }

    /**
     *
     * @param _owner: The account whose balance is to be checked.
     * @return Amount of tokens owned by the `account` with the given address.
     */
    @External(readonly = true)
    public BigInteger balanceOf(Address _owner) {
        return balances.getOrDefault(_owner, BigInteger.valueOf(0));
    }

    @External(readonly = true)
    public Address getAdmin(){
        return admin.get();
    }

    @External
    public void setAdmin(Address _admin){
        Checks.onlyOwner();
        IRC2.admin.set(_admin);
    }


    @External
    public void transfer(Address _to, BigInteger _value, byte[] _data) {
        if(_data == null){
            _data = new byte[0];
        }
        transfer(Context.getCaller(), _to, _value, _data);
    }

    /**
     * Checks if the address is in the arraydb
     * @param arrayDB: ArrayDB of address
     * @param address: Address that we need to check
     * @return: a boolean
     */
    public boolean arrayDbContains(ArrayDB<Address> arrayDB, Address address){
        final int size =  arrayDB.size();
        for (int i = 0; i < size; i++){
            if (arrayDB.get(i).equals(address)){
                return true;
            }
        }

        return false;
    }

    protected static boolean remove_from_arraydb(Address _item, ArrayDB<Address> _array){
        final int size = _array.size();
        if(size < 1){
            return false;
        }
        var top = _array.get(size - 1);
        for(int i = 0; i < size; i++){
            if(_array.get(i).equals(_item)){
                _array.set(i, top);
                _array.pop();
                return true;
            }
        }

        return false;
    }

    /**
     *
     * @param _to: The account to which the token is to be transferred
     * @param _value: The no. of tokens to be transferred
     * @param _data: Any information or message
     */
    protected void transfer(Address _from, Address _to, BigInteger _value, byte[] _data) {
        Context.require(_value.compareTo(BigInteger.valueOf(0)) > 0, ": Transfer value cannot be zero or less than zero");
        Context.require(
                balanceOf(_from).compareTo(_value) >= 0,
                ": Source address must have token greater than transfer amount"
        );

        if(!arrayDbContains(addresses, _to)) {
            addresses.add(_to);
        }

        this.balances.set(_from, balanceOf(_from).subtract(_value));
        this.balances.set(_to, balanceOf(_to).add(_value));
        if (balances.getOrDefault(_from, BigInteger.ZERO).equals(BigInteger.ZERO)){
            remove_from_arraydb(_from, addresses);
        }

        Transfer(_from, _to, _value, _data);

        Context.require(
                addresses.size() < MAX_HOLDER_COUNT.intValue(),
                "The maximum holder count of {MAX_HOLDER_COUNT} has been reached." +
                        "Only transfers of whole balances or moves between current" +
                        "holders is allowed until the total holder count is reduced."
        );

        if(_to.isContract()) {
            Context.call(_to, "tokenFallback", _from, _value, _data);
        }
    }


    /**
     * Creates amount number of tokens, and assigns to account
     * 		Increases the balance of that account and total supply.
     * 		This is an internal function
     * @param account The account at which token is to be created.
     * @param amount Number of tokens to be created at the `account`.
     * @param _data Number of tokens to be created at the `account`.
     */
    protected void mint(Address account, BigInteger amount, @Optional byte[] _data) {
        Checks.onlyAdmin();
        if (amount.signum() < 0) {
            Context.revert("Invalid Value");
        }
        total_supply.set(total_supply.get().add(amount));
        balances.set(account, balanceOf(account).add(amount));

        Mint(account, amount, _data);

        Transfer(EOA_ZERO, account, amount, _data);

        if (account.isContract()) {
            // If the recipient is SCORE, then calls `tokenFallback` to hand over control.
            Context.call(account, "tokenFallBack", amount, _data);
        }
    }

    /**
     * Destroys `amount` number of tokens from `account`
     * 		Decreases the balance of that `account` and total supply.
     * 		This is an internal function
     * @param account The account at which token is to be destroyed.
     * @param amount The `amount` of tokens of `account` to be destroyed.
     */
    protected void burn(Address account, BigInteger amount) {
        Checks.onlyAdmin();
        if (amount.signum() < 0) {
            Context.revert("Invalid Amount Value");
        }

        BigInteger balance = balanceOf(account);
        if (amount.compareTo(balance) < 0) {
            Context.revert("Burn amount { " + amount+ " } larger than available balance.");
        }
        total_supply.set(total_supply.get().subtract(amount));
        balances.set(account, balanceOf(account).subtract(amount));

        Burn(account, amount);

        String data = "None";
        Transfer(EOA_ZERO, account, amount, data.getBytes());
    }



    @EventLog(indexed = 3)
    void Transfer(Address _from, Address _to, BigInteger _value, byte[] _data){

    }

    @EventLog(indexed = 1)
    void Mint(Address account, BigInteger amount, byte[] _data){

    }
    @EventLog(indexed = 1)
    void Burn(Address account, BigInteger amount){

    }
}