/*
 * Copyright (c) 2022-2022 Balanced.network.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package network.balanced.score.tokens.balanceddollar;

import network.balanced.score.lib.interfaces.BalancedDollar;
import network.balanced.score.lib.tokens.IRC2Burnable;
import network.balanced.score.lib.utils.Names;
import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.utils.Check.*;

public class BalancedDollarImpl extends IRC2Burnable implements BalancedDollar {
    private static final String TOKEN_NAME = Names.BNUSD;
    private static final String SYMBOL_NAME = "bnUSD";
    private final String USD_BASE = "USD";
    private final String ICX_QUOTE = "ICX";

    private static final String GOVERNANCE = "governance";
    private static final String ORACLE_ADDRESS = "oracle_address";
    private static final String ORACLE_NAME = "oracle_name";
    private static final String PRICE_UPDATE_TIME = "price_update_time";
    private static final String LAST_PRICE = "last_price";
    private static final String MIN_INTERVAL = "min_interval";
    private static final String ADMIN_ADDRESS = "admin_address";
    private final String MINTER2 = "ExtraMinter";

    private final VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);
    private final VarDB<Address> oracleAddress = Context.newVarDB(ORACLE_ADDRESS, Address.class);
    private final VarDB<String> oracleName = Context.newVarDB(ORACLE_NAME, String.class);
    private final VarDB<BigInteger> priceUpdateTime = Context.newVarDB(PRICE_UPDATE_TIME, BigInteger.class);
    private final VarDB<BigInteger> lastPrice = Context.newVarDB(LAST_PRICE, BigInteger.class);
    private final VarDB<BigInteger> minInterval = Context.newVarDB(MIN_INTERVAL, BigInteger.class);
    private final VarDB<Address> admin = Context.newVarDB(ADMIN_ADDRESS, Address.class);
    protected final VarDB<Address> minter2 = Context.newVarDB(MINTER2, Address.class);

    public BalancedDollarImpl(Address _governance) {
        super(TOKEN_NAME, SYMBOL_NAME, null);

        if (governance.get() == null) {
            governance.set(_governance);
            String DEFAULT_ORACLE_NAME = "BandChain";
            oracleName.set(DEFAULT_ORACLE_NAME);

            // 30 seconds
            BigInteger MIN_UPDATE_TIME = BigInteger.valueOf(30_000_000);
            minInterval.set(MIN_UPDATE_TIME);
        }
    }

    @External(readonly = true)
    public String getPeg() {
        return USD_BASE;
    }

    @External
    public void setGovernance(Address _address) {
        onlyOwner();
        isContract(_address);
        governance.set(_address);
    }

    @External(readonly = true)
    public Address getGovernance() {
        return governance.get();
    }

    @External
    public void setAdmin(Address _address) {
        only(governance);
        admin.set(_address);
    }

    @External(readonly = true)
    public Address getAdmin() {
        return admin.get();
    }

    @External
    public void setOracle(Address _address) {
        only(governance);
        isContract(_address);
        oracleAddress.set(_address);
    }

    @External(readonly = true)
    public Address getOracle() {
        return oracleAddress.get();
    }

    @External
    public void setOracleName(String _name) {
        only(admin);
        oracleName.set(_name);
    }

    @External(readonly = true)
    public String getOracleName() {
        return oracleName.get();
    }

    @External
    public void setMinInterval(BigInteger _interval) {
        only(admin);
        minInterval.set(_interval);
    }

    @External(readonly = true)
    public BigInteger getMinInterval() {
        return minInterval.get();
    }

    @External(readonly = true)
    public BigInteger getPriceUpdateTime() {
        return priceUpdateTime.getOrDefault(BigInteger.ZERO);
    }

    /**
     * @return the price of the asset in loop. Makes a call to the oracle if the last recorded price is not recent
     * enough.
     */
    @External
    public BigInteger priceInLoop() {
        BigInteger blockTimeStamp = BigInteger.valueOf(Context.getBlockTimestamp());
        BigInteger priceUpdate = getPriceUpdateTime();
        BigInteger lastPriceOfBnusdInIcx = lastPrice.get();
        if (blockTimeStamp.subtract(priceUpdate).compareTo(minInterval.get()) > 0) {
            lastPriceOfBnusdInIcx = updateAssetValue();
        }
        return lastPriceOfBnusdInIcx;
    }

    @SuppressWarnings("unchecked")
    @External(readonly = true)
    public BigInteger lastPriceInLoop() {
        Address oracleAddress = this.oracleAddress.get();
        Map<String, Object> priceData = (Map<String, Object>) Context.call(oracleAddress, "get_reference_data"
                , USD_BASE, ICX_QUOTE);
        return (BigInteger) priceData.get("rate");
    }

    @External
    public void govTransfer(Address _from, Address _to, BigInteger _value, @Optional byte[] _data) {
        only(governance);
        transfer(_from, _to, _value, _data);
    }

    @External
    public void setMinter2(Address _address) {
        onlyOwner();
        isContract(_address);
        minter2.set(_address);
    }

    @External(readonly = true)
    public Address getMinter2() {
        return minter2.get();
    }

    @External
    public void burn(BigInteger _amount) {
        burnFrom(Context.getCaller(), _amount);
    }

    @External
    public void burnFrom(Address _account, BigInteger _amount) {
        onlyEither(minter, minter2);
        super.burn(_account, _amount);
    }

    @External
    public void mint(BigInteger _amount, @Optional byte[] _data) {
        mintTo(Context.getCaller(), _amount, _data);
    }

    @External
    public void mintTo(Address _account, BigInteger _amount, @Optional byte[] _data) {
        onlyEither(minter, minter2);
        mintWithTokenFallback(_account, _amount, _data);
    }

    /**
     * Calls the oracle method for the asset and updates the asset value in loop.
     */
    @SuppressWarnings("unchecked")
    private BigInteger updateAssetValue() {
        Address oracleAddress = this.oracleAddress.get();

        Map<String, Object> priceData = (Map<String, Object>) Context.call(oracleAddress, "get_reference_data",
                USD_BASE, ICX_QUOTE);
        BigInteger priceOfBnusdInIcx = (BigInteger) priceData.get("rate");
        lastPrice.set(priceOfBnusdInIcx);
        priceUpdateTime.set(BigInteger.valueOf(Context.getBlockTimestamp()));
        OraclePrice(USD_BASE + ICX_QUOTE, oracleName.get(), oracleAddress, priceOfBnusdInIcx);
        return priceOfBnusdInIcx;
    }

    @Override
    @External
    public void transfer(Address _to, BigInteger _value, @Optional byte[] _data) {
        Context.revert();
        // if (!_to.equals(stakingAddress.get())) {
        //     Context.call(stakingAddress.get(), "transferUpdateDelegations", Context.getCaller(), _to, _value);
        // }
        // transfer(Context.getCaller(), _to, _value, _data);
    }
    @EventLog(indexed = 3)
    public void OraclePrice(String market, String oracle_name, Address oracle_address, BigInteger price) {
    }
}
