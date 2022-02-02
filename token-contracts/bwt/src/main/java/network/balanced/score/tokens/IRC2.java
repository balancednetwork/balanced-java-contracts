package network.balanced.score.tokens;

import java.math.BigInteger;

import score.*;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;

public class IRC2 implements TokenStandard{
    private String _NAME = "name";
    private String _SYMBOL = "symbol";
    private String _DECIMALS = "decimals";
    private String _TOTAL_SUPPLY = "total_supply";
    private String _BALANCES = "balances";
    private String _ADMIN = "admin";
    private String _ACCOUNTS = "account";


    private VarDB<String> _name = Context.newVarDB(_NAME, String.class);
    private VarDB<String> _symbol = Context.newVarDB(_SYMBOL, String.class);
    private VarDB<BigInteger> _decimals = Context.newVarDB(_DECIMALS, BigInteger.class);
    private VarDB<BigInteger> _total_supply = Context.newVarDB(_TOTAL_SUPPLY, BigInteger.class);

    //TODO: Check this out later
    private ArrayDB<Address> _addresses = Context.newArrayDB(_ACCOUNTS, Address.class);
    private DictDB<Address, BigInteger> _balances = Context.newDictDB(_BALANCES, BigInteger.class);
    private VarDB<Address> _admin = Context.newVarDB(_ADMIN, Address.class);

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
        BigInteger supply = _initialSupply.multiply(pow(_initialSupply, _decimals.intValue()));
        _total_supply.set(supply);
        this._decimals.set(_decimals);

        // set other values
        final Address caller = Context.getCaller();
        _name.set(_tokenName);
        this._symbol.set(_symbolName);
        _addresses.add(caller);
        _balances.set(caller, supply);
    }

    /**
     *
     * @return Name of the token
     */
    @External(readonly = true)
    @Override
    public String name() {
        return _name.getOrDefault("");
    }

    /**
     *
     * @return Symbol of the token
     */
    @External(readonly = true)
    @Override
    public String symbol() {
        return _symbol.get();
    }

    /**
     *
     * @return Number of decimals
     */
    @External(readonly = true)
    @Override
    public BigInteger decimals() {
        return _decimals.get();
    }

    /**
     *
     * @return total number of tokens in existence.
     */
    @External(readonly = true)
    @Override
    public BigInteger totalSupply() {
        return _total_supply.get();
    }

    /**
     *
     * @param _owner: The account whose balance is to be checked.
     * @return Amount of tokens owned by the `account` with the given address.
     */
    @External(readonly = true)
    @Override
    public BigInteger balanceOf(Address _owner) {
        return _balances.getOrDefault(_owner, BigInteger.valueOf(0));
    }

    @External(readonly = true)
    public Address getAdmin(){
        return _admin.get();
    }

    @External
    public void setAdmin(Address _admin){
        Checks.onlyOwner();
        this._admin.set(_admin);
    }


    @Override
    public void transfer(Address _to, BigInteger _value, byte[] _data) {

    }

    /**
     * Checks if the address is in the arraydb
     * @param arrayDB: ArrayDB of address
     * @param address: Address that we need to check
     * @return: a boolean
     */
    private boolean arrayDbContains(ArrayDB<Address> arrayDB, Address address){
        final int size =  arrayDB.size();
        for (int i = 0; i < size; i++){
            if (arrayDB.get(i).equals(address)){
                return true;
            }
        }

        return false;
    }

    private static boolean remove_from_arraydb(Address _item, ArrayDB<Address> _array){
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

        if(!arrayDbContains(_addresses, _to)) {
            _addresses.add(_to);
        }

        this._balances.set(_from, balanceOf(_from).subtract(_value));
        this._balances.set(_to, balanceOf(_to).add(_value));
        if (_balances.getOrDefault(_from, BigInteger.ZERO).equals(BigInteger.ZERO)){
            remove_from_arraydb(_from, _addresses);
        }

        Transfer(_from, _to, _value, _data);

        Context.require(
                _addresses.size() > MAX_HOLDER_COUNT.intValue(),
                "The maximum holder count of {MAX_HOLDER_COUNT} has been reached." +
                        "Only transfers of whole balances or moves between current" +
                        "holders is allowed until the total holder count is reduced."
        );

        if(_to.isContract()) {
            Context.call(_to, "tokenFallBack", _from, _value, _data);
        }
    }

    @EventLog(indexed = 4)
    void Transfer(Address _from, Address _to, BigInteger _value, byte[] _data){

    }
}
