package network.balanced.score.core.router;

import com.eclipsesource.json.JsonValue;
import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import java.math.BigInteger;
import java.util.ArrayList;

public class Router {
    private static String DEX_ADDRESS = "dex_address";
    private static String SICX_ADDRESS = "sicx_address";
    private static String STAKING_ADDRESS = "staking_address";
    private static String GOVERNANCE_ADDRESS = "governance_address";
    private static String ADMIN = "admin";
    private static BigInteger MAX_NUMBER_OF_ITERATIONS = BigInteger.valueOf(4L); // Unbounded iterations won"t pass audit
    private static Address MINT_ADDRESS = Address.fromString("hx0000000000000000000000000000000000000000");
    private final String TAG = "Balanced Router";

    VarDB<Address> governance = Context.newVarDB(GOVERNANCE_ADDRESS, Address.class);
    VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);
    VarDB<Address> sicx = Context.newVarDB(SICX_ADDRESS, Address.class);
    VarDB<Address> staking = Context.newVarDB(STAKING_ADDRESS, Address.class);
    VarDB<Address> dex = Context.newVarDB(DEX_ADDRESS, Address.class);

    public Router(@Optional Address _governance) {
        if(_governance != null) {
            governance.set(_governance);
            admin.set(_governance);
            dex.set(Address.fromString("cxa0af3165c08318e988cb30993b3048335b94af6c"));
            sicx.set(Address.fromString("cx2609b924e33ef00b648a409245c7ea394c467824"));
            staking.set(Address.fromString("cx43e2eec79eb76293c298f2b17aec06097be606e0"));
        }
    }

    @External(readonly = true)
    public String name() {
        return TAG;
    }

    /**
     *  Gets the current admin address. This user can call using the
     *         `@only_admin` decorator.
     * @return Returns the address of the current admin
     */
    @External(readonly = true)
    public Address getAdmin() {
        return admin.get();
    }

    /**
     *
     * @param _admin  The new admin address to set.
     *         Can make calls with the `@only_admin` decorator.
     *         Should be called before DEX use.
     */
    @External
    public void setAdmin(Address _admin) {
        Context.require(
                Context.getCaller() == governance.get(),
                "Only governance score can call this method"
        );
        admin.set(_admin);
    }

    /**
     *
     * @return Address of the dex score
     */
    @External(readonly = true)
    public Address getDex() {
        return dex.get();
    }

    /**
     *
     * @param _dex The new DEX address to set.
     */
    @External
    public void setDex(Address _dex) {
        Context.require(
                Context.getCaller() == governance.get(),
                "Only governance score can call this method"
        );
        dex.set(_dex);
    }

    @External(readonly = true)
    public Address getSicx() {
        return sicx.get();
    }

    @External
    public void setSicx(Address _address) {
        Context.require(
                Context.getCaller() == admin.get(),
                "Only governance address can call this method"
        );
        Context.require(_address.isContract(), TAG +
                "Address provided is an EOA address. A contract address is required.");
        sicx.set(_address);
    }

    /**
     * Sets new Governance contract address. Should be called before dex use.
     * @param _address New contract address to set.
     */
    @External
    public void setGovernance(Address _address) {
        Context.require(
                Context.getCaller() == Context.getOwner(),
                "Only g governance call this method"
        );
        Context.require(_address.isContract(), TAG +
                "Address provided is an EOA address. A contract address is required.");
        governance.set(_address);
    }

    @External(readonly = true)
    public Address getGovernance() {
        return governance.get();
    }

    /**
     *
     * @return Gets the address of the Staking contract.
     */
    @External(readonly = true)
    public Address getStaking() {
        return staking.get();
    }

    /**
     * Sets new Governance contract address. Should be called before dex use.
     * @param _address New contract address to set.
     */
    @External
    public void setStaking(Address _address) {
        Context.require(
                Context.getCaller() == admin.get(),
                "Only governance address can call this method"
        );
        Context.require(_address.isContract(), TAG +
                "Address provided is an EOA address. A contract address is required.");
        staking.set(_address);
    }


    void swap(Address _fromToken, Address _toToken) {
        if(_fromToken == null) {
            Context.require(
                    _toToken == sicx.get(),
                    TAG + ": ICX can only be traded for sICX"
            );
            BigInteger balance = Context.getBalance(Context.getAddress());
            Context.transfer(staking.get(), balance);
        }
        else if(_fromToken == sicx.get() && _toToken == null) {
            BigInteger balance = (BigInteger) Context.call(_fromToken, "balanceOf");
            String data = "{\"method\":\"_swap_icx\"}";
            Context.call(_fromToken, "transfer", balance, data.getBytes());
        }
        else {
            BigInteger balance = (BigInteger) Context.call(_fromToken, "balanceOf");
            String data = "" +
                    "{\"method\":\"_swap\",\"params\":{\"toToken\":\""
                    + _toToken
                    + "\"}}";
            Context.call(_fromToken, "transfer", balance, data.getBytes());
        }
    }

    void route(Address _from, Address _startToken, Address[] _path, BigInteger _minReceive) {
        Address currentToken = _startToken;

        for(var token: _path) {
            swap(currentToken, token);
            currentToken = token;
        }

        if(currentToken == null) {
            BigInteger balance = Context.getBalance(Context.getAddress());
            Context.require(
                    balance.compareTo(_minReceive) >= 0,
                    TAG + ":Below minimum receive amount of" + _minReceive
            );
            Context.transfer(_from, balance);
        }
        else {
            BigInteger balance = (BigInteger) Context.call(currentToken, "balanceOf", Context.getAddress());
            Context.require(
                    balance.compareTo(_minReceive) >= 0,
                    TAG + ":Below minimum receive amount of" + _minReceive
            );
            String data = "";
            Context.call(currentToken, "transfer", _from, balance, data.getBytes());
        }
    }

    /**
     * Uses DEX for exchange in the path where highest return can be obtained
     * @param _path
     * @param _minReceive
     */
    @Payable
    @External
    public void route(Address[] _path, @Optional BigInteger _minReceive) {
        if (_minReceive == null) {
            _minReceive = BigInteger.ZERO;
        }

        Context.require(
                _path.length <= MAX_NUMBER_OF_ITERATIONS.intValue(),
                "Passed max swaps of" + MAX_NUMBER_OF_ITERATIONS
        );

        route(Context.getCaller(), null, _path, _minReceive);
    }

    /**
     *  This is invoked when a token is transferred to this score.
     *      It expects a JSON object with the following format:
     *      ```
     *      {"method": "METHOD_NAME", "params":{...}}
     *      ```
     *      Token transfers to this contract are rejected unless any of the
     *      following methods are passed in the object:
     *      1) `_deposit` - Calls the `_deposit()` function
     *      2) `_swap_icx` - Calls the `_swap_icx()` function
     *      3) `_swap` - Calls the `_swap()` function
     *      All calls to this function update snapshots and process dividends.
     *
     * @param _from The address calling `transfer` on the other contract
     * @param _value Amount of token transferred
     * @param _data Data called by the transfer, json object expected.
     */
    @External
    public void tokenFallBack(Address _from, BigInteger _value, byte[] _data) {
        if (_from == dex.get() || _from == MINT_ADDRESS) {
            return ;
        }

        String unpackedData = new String(_data);
        Context.require(!unpackedData.equals(""), "Token Fallback: Data can't be empty");

        JsonObject json = Json.parse(unpackedData).asObject();

        String method = json.get("method").asString();
        JsonObject params = json.get("params").asObject();

        Context.require(
                method.contains("_swap"),
                TAG + "Fallback directly not allowed."
        );
        BigInteger minimumReceive = BigInteger.ZERO;
        if (params.contains("minimumReceive")) {
            minimumReceive = BigInteger.valueOf(params.get("minimumReceive").asInt());
            Context.require(
                    minimumReceive.signum() >= 0,
                    TAG + "Must specify a positive number for minimum to receive"
            );
        }
        Address receiver;
        if(params.contains("receiver")) {
            receiver = Address.fromString(params.get("receiver").asString());
        }
        else  {
            receiver = _from;
        }

        Context.require(
                params.get("path").asArray().size() > MAX_NUMBER_OF_ITERATIONS.intValue(),
                TAG +"Passed max swaps of " + MAX_NUMBER_OF_ITERATIONS
                );

        ArrayList<Address> path = new ArrayList<>();

        for(JsonValue addressJsonValue: params.get("path").asArray()) {
            if (addressJsonValue != null) {
                path.add(Address.fromString(addressJsonValue.asString()));
            }
        }

        Address fromToken = Context.getCaller();
        route(receiver, fromToken, (Address[]) path.toArray(), minimumReceive);
    }
}
