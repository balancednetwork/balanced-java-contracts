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

import network.balanced.score.tokens.workertoken.utils.Checks;
import network.balanced.score.tokens.workertoken.utils.IRC2;
import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;

import static network.balanced.score.tokens.workertoken.utils.Checks.onlyAdmin;
import static network.balanced.score.tokens.workertoken.utils.Checks.onlyGovernance;

public class WorkerToken extends IRC2 {
    public static String TAG = "BALW";

    private static final String TOKEN_NAME = "Balanced Worker Token";
    private static final String SYMBOL_NAME = "BALW";
    private static final BigInteger INITIAL_SUPPLY = BigInteger.valueOf(100);
    private static final BigInteger DECIMALS = BigInteger.valueOf(6);
    private static final String ACCOUNTS = "accounts";
    private static final String GOVERNANCE = "governance";
    private static final String BALN_TOKEN = "baln_token";
    private static final String BALN = "baln";

    public static final VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);
    private final VarDB<Address> balnToken = Context.newVarDB(BALN_TOKEN, Address.class);
    private final VarDB<BigInteger> baln = Context.newVarDB(BALN, BigInteger.class);

    public WorkerToken(@Optional Address _governance) {
        super(TOKEN_NAME, SYMBOL_NAME, INITIAL_SUPPLY, DECIMALS);
        if (governance != null) {
            WorkerToken.governance.set(_governance);
        }
    }

    /**
     *
     * @param _address: Address that we need to set to governance
     */
    @External
    public void setGovernance(Address _address){
        Checks.onlyOwner();
        Context.require( _address.isContract(), TAG + "Address provided is an EOA address. A contract address is required.");
        governance.set(_address);
    }


    /**
     *
     * @return Governance address
     */
    @External(readonly = true)
    public Address getGovernance(){
        return governance.get();
    }

    /**
     *
     * @param _admin: Sets the authorized access
     */
    @External
    public void setAdmin(Address _admin){
        onlyGovernance();
        WorkerToken.admin.set(_admin);
    }

    @External
    public void setBaln(Address _address){
        onlyAdmin();
        balnToken.set(_address);
    }

    @External(readonly = true)
    public Address getBaln(){
        return balnToken.get();
    }

    @External
    public void adminTransfer(Address _from, Address _to, BigInteger _value, @Optional byte[] _data){
        onlyAdmin();
        if(_data != null){
            _data = new byte[0];
        }

        transfer(_from, _to, _value, _data);
    }

    @External
    public void distribute(){
        final int size = addresses.size();
        BigInteger totalTokens = totalSupply();

        // dist = balance of worker token contract
        BigInteger dist = (BigInteger) Context.call(balnToken.get(), "balanceOf", Context.getAddress());
        Address balnToken = this.balnToken.get();
        for(int i = 0; i < size; i++){
            Address address = addresses.get(i);
            BigInteger balance = balances.getOrDefault(address, BigInteger.ZERO);
            if (balance.compareTo(BigInteger.ZERO) > 0){
                // multiply first cause integer division
                BigInteger amount = dist.multiply(balance).divide(totalTokens);
                dist = dist.subtract(amount);
                totalTokens = totalTokens.subtract(balance);
                Context.call(balnToken, "transfer", address, amount, new byte[0]);
            }
        }
    }

    /**
     *
     * @param _from: Token origination address.
     * @param _value: Number of tokens sent.
     * @param _data: unused ignored
     */
    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data){
        Context.require(
                Context.getCaller().equals(balnToken.get()),
                "The Worker Token contract can only accept BALN tokens." +
                        "Deposit not accepted from" + Context.getCaller() +
                        "Only accepted from BALN = " + baln.get());
        baln.set(baln.getOrDefault(BigInteger.ZERO).add(_value));
    }

}
