package network.balanced.score.tokens;


import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import network.balanced.score.tokens.tokens.IRC2Mintable;
import score.Address;
import score.Context;
import score.DictDB;
import score.VarDB;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;


public class BalancedDollar extends IRC2Mintable {
    public static final String TAG = "bnUSD";
    private static String TOKEN_NAME = "Balanced Dollar";
    private static String SYMBOL_NAME = "bnUSD";
    public String DEFAULT_PEG = "USD";
    public String DEFAULT_ORACLE_NAME = "BandChain";
    BigInteger INITIAL_PRICE_ESTIMATE = BigInteger.valueOf(125).multiply(pow(BigInteger.TEN, 16));
    BigInteger MIN_UPDATE_TIME = BigInteger.valueOf(30_000_000); // 30 seconds



    private static final String PEG = "peg";
    private static final String GOVERNANCE = "governance";
    private static final String ORACLE_ADDRESS = "oracle_address";
    private static final String ORACLE_NAME = "oracle_name";
    private static final String PRICE_UPDATE_TIME = "price_update_time";
    private static final String LAST_PRICE = "last_price";
    private static final String MIN_INTERVAL = "min_interval";

    public VarDB<String> peg = Context.newVarDB(PEG, String.class);
    public static VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);
    public VarDB<Address> oracleAddress = Context.newVarDB(ORACLE_ADDRESS, Address.class);
    public VarDB<String> oracleName = Context.newVarDB(ORACLE_NAME, String.class);
    public VarDB<BigInteger> priceUpdateTime = Context.newVarDB(PRICE_UPDATE_TIME, BigInteger.class);
    public VarDB<BigInteger> lastPrice = Context.newVarDB(LAST_PRICE, BigInteger.class);
    public VarDB<BigInteger> minInterval = Context.newVarDB(MIN_INTERVAL, BigInteger.class);


    public BalancedDollar(@Optional Address _governance) {
        // null to emulate no args supplied
        super(TOKEN_NAME, SYMBOL_NAME, null, null);

        if (_governance != null) {
            governance.set(_governance);
            peg.set(DEFAULT_PEG);
            oracleName.set(DEFAULT_ORACLE_NAME);
            lastPrice.set(INITIAL_PRICE_ESTIMATE);
            minInterval.set(MIN_UPDATE_TIME);
        }
    }

    @External
    public void setGovernance(Address _address) {
        Context.require(
                Context.getCaller() == Context.getOwner(),
                "Only owner can call this method"
        );
        governance.set(_address);
    }

    @External(readonly = true)
    public Address getGovernance() {
        return governance.get();
    }

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

    @External(readonly = true)
    public Address getOracle() {
        return oracleAddress.get();
    }

    @External
    public void setOracle(Address _address) {
        Context.require(
                Context.getCaller() == governance.get(),
                "Only governance score can call this method"
        );
        oracleAddress.set(_address);
    }


    @External(readonly = true)
    public String getOracleName() {
        return oracleName.get();
    }

    @External
    public void setOracleName(String _name) {
        Context.require(
                Context.getCaller() == governance.get(),
                "Only governance score can call this method"
        );
        oracleName.set(_name);
    }

    @External
    public void setMinInterval(BigInteger _interval) {
        Context.require(
                Context.getCaller() == governance.get(),
                "Only governance score can call this method"
        );
        minInterval.set(_interval);
    }

    @External
    public BigInteger getMinInterval() {
        return minInterval.get();
    }

    @External
    public BigInteger getPriceUpdateTime() {
        return priceUpdateTime.get();
    }

    /**
     * @return the price of the asset in loop. Makes a call to the oracle if
     *         the last recorded price is not recent enough.
     */
    @External
    public BigInteger priceInLoop() {
        BigInteger blockTimeStamp = BigInteger.valueOf(Context.getBlockTimestamp());
        BigInteger priceUpdate = priceUpdateTime.getOrDefault(BigInteger.ZERO);
        if (blockTimeStamp.subtract(priceUpdate).compareTo(minInterval.get()) > 0){
            updateAssetValue();
        }

        return lastPrice.get();
    }

    /**
     * @return  Returns the latest price of the asset in loop.
     */
    @External
    public BigInteger lastPriceInLoop() {
        String base = peg.get();
        String quote = "ICX";
        Address oracleAddress = this.oracleAddress.get();
        HashMap<String, BigInteger> priceData = (HashMap<String, BigInteger>) Context.call(oracleAddress, "get_reference_data", base, quote);
        return priceData.getOrDefault("rate", BigInteger.ZERO);
    }

    @External
    public void govTransfer(Address _from, Address _to, BigInteger _value, @Optional byte[] data) {
        Context.require(
                Context.getCaller() == governance.get(),
                "Only governance score can call this method"
        );
        if (data == null) {
            String dataString = "None";
            data = dataString.getBytes();
        }
        transfer(_from, _to, _value, data);
    }

    /**
     * Calls the oracle method for the asset and updates the asset
     *         value in loop.
     */
    void updateAssetValue() {
        var base = peg.get();
        String quote = "ICX";
        Address oracleAddress = this.oracleAddress.get();

        try {
            HashMap<String, BigInteger> priceData;
            priceData = (HashMap<String, BigInteger>) Context.call(oracleAddress, "get_reference_data", base, quote);
            lastPrice.set(priceData.getOrDefault("rate", BigInteger.ZERO));
            priceUpdateTime.set(BigInteger.valueOf(Context.getBlockTimestamp()));
            OraclePrice(base + quote, oracleName.get(), oracleAddress, priceData.getOrDefault("rate", BigInteger.ZERO));
        } catch (Exception e) {
            Context.revert("{" + base + quote + "}, { " + oracleName.get() + " }, {" + oracleAddress  + " }.");
        }
    }

    @EventLog(indexed = 3)
    void OraclePrice(String market, String oracle_name, Address oracle_address, BigInteger price) {

    }
}
