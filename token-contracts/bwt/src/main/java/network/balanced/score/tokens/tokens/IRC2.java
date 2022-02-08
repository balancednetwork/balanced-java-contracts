package network.balanced.score.tokens.tokens;

import java.math.BigInteger;

import score.*;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;

public abstract class IRC2 implements TokenStandard {
    private static String NAME = "name";
    private static String SYMBOL = "symbol";
    private static String DECIMALS = "decimals";
    private static String TOTAL_SUPPLY = "total_supply";
    private static String BALANCES = "balances";
    private static String ADMIN = "admin";
    private static String ACCOUNTS = "account";


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
     * @param _initialSupply:The total number of tokens to initialize with.
     * 					It is set to total supply in the beginning, 0 by default.
     * @param _decimals: The number of decimals. Set to 18 by default.
     */
    public IRC2(String _tokenName, String _symbolName, @Optional BigInteger _initialSupply, @Optional BigInteger _decimals){
        BigInteger DEFAULT_INITIAL_SUPPLY = BigInteger.valueOf(0);
        BigInteger DEFAULT_DECIMAL_VALUE = BigInteger.valueOf(0x12);

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
        name.set(_tokenName);
        this.symbol.set(_symbolName);
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
        _transfer(Context.getCaller(), _to, _value, _data);
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
    public void _transfer(Address _from, Address _to, BigInteger _value, byte[] _data) {
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



    @EventLog(indexed = 3)
    void Transfer(Address _from, Address _to, BigInteger _value, byte[] _data){

    }
}
