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

package network.balanced.score.tokens.workertoken;

import network.balanced.score.lib.interfaces.WorkerToken;
import network.balanced.score.lib.tokens.IRC2PresetFixedSupply;
import network.balanced.score.lib.utils.Names;
import score.Address;
import score.ArrayDB;
import score.Context;
import score.VarDB;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.ArrayDBUtils.arrayDbContains;
import static network.balanced.score.lib.utils.ArrayDBUtils.removeFromArraydb;
import static network.balanced.score.lib.utils.Check.*;

public class WorkerTokenImpl extends IRC2PresetFixedSupply implements WorkerToken {
    public static String TAG = "BALW";

    private static final String TOKEN_NAME = Names.WORKERTOKEN;
    private static final String SYMBOL_NAME = "BALW";
    private static final BigInteger INITIAL_SUPPLY = BigInteger.valueOf(100);
    private static final BigInteger DECIMALS = BigInteger.valueOf(6);

    private static final String ACCOUNTS = "accounts";
    private static final String GOVERNANCE = "governance";
    private static final String BALN_TOKEN = "baln_token";
    private static final String ADMIN = "admin";

    private final ArrayDB<Address> addresses = Context.newArrayDB(ACCOUNTS, Address.class);
    private final VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);
    private final VarDB<Address> balnToken = Context.newVarDB(BALN_TOKEN, Address.class);
    private final VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);

    public WorkerTokenImpl(Address _governance) {
        super(TOKEN_NAME, SYMBOL_NAME, INITIAL_SUPPLY, DECIMALS);
        if (governance.get() == null) {
            this.governance.set(_governance);
            addresses.add(Context.getCaller());
        }
    }

    /**
     * @param _address: Address that we need to set to governance
     */
    @External
    public void setGovernance(Address _address) {
        onlyOwner();
        isContract(_address);
        governance.set(_address);
    }

    /**
     * @return Governance address
     */
    @External(readonly = true)
    public Address getGovernance() {
        return governance.get();
    }

    /**
     * @param _admin: Sets the authorized access
     */
    @External
    public void setAdmin(Address _admin) {
        only(governance);
        this.admin.set(_admin);
    }

    @External(readonly = true)
    public Address getAdmin() {
        return admin.get();
    }

    @External
    public void setBaln(Address _address) {
        only(admin);
        isContract(_address);
        balnToken.set(_address);
    }

    @External(readonly = true)
    public Address getBaln() {
        return balnToken.get();
    }

    @External
    public void adminTransfer(Address _from, Address _to, BigInteger _value, @Optional byte[] _data) {
        only(admin);
        transferAndUpdateAddressList(_from, _to, _value, _data);
    }

    @Override
    @External
    public void transfer(Address _to, BigInteger _value, @Optional byte[] _data) {
        Address _from = Context.getCaller();
        transferAndUpdateAddressList(_from, _to, _value, _data);
    }

    private void transferAndUpdateAddressList(Address _from, Address _to, BigInteger _value, @Optional byte[] _data) {
        transfer(_from, _to, _value, _data);

        if (balances.getOrDefault(_from, BigInteger.ZERO).equals(BigInteger.ZERO)) {
            removeFromArraydb(_from, addresses);
        }

        if (!arrayDbContains(addresses, _to)) {
            addresses.add(_to);
        }

        int MAX_HOLDER_COUNT = 400;
        Context.require(addresses.size() <= MAX_HOLDER_COUNT,
                TAG + ": The maximum holder count of " + MAX_HOLDER_COUNT + " has been reached. Only transfers of " +
                        "whole balances or moves between current holders is allowed until the total holder count is " +
                        "reduced."
        );
    }

    @External
    public void distribute() {
        final int size = addresses.size();
        BigInteger totalTokens = totalSupply();

        // dist = balance of worker token contract
        Address balnToken = this.balnToken.get();
        BigInteger dist = (BigInteger) Context.call(balnToken, "balanceOf", Context.getAddress());
        for (int i = 0; i < size; i++) {
            Address address = addresses.get(i);
            BigInteger balance = balances.getOrDefault(address, BigInteger.ZERO);
            if (balance.compareTo(BigInteger.ZERO) > 0) {
                // multiply first cause integer division
                BigInteger amount = dist.multiply(balance).divide(totalTokens);
                dist = dist.subtract(amount);
                totalTokens = totalTokens.subtract(balance);
                Context.call(balnToken, "transfer", address, amount, new byte[0]);
            }
        }
    }

    /**
     * @param _from:  Token origination address.
     * @param _value: Number of tokens sent.
     * @param _data:  unused ignored
     */
    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        Context.require(Context.getCaller().equals(balnToken.get()), TAG + ": The Worker Token contract can only " +
                "accept BALN tokens. Deposit not accepted from" + Context.getCaller() + "Only accepted from BALN = " + balnToken.get());
    }
}
