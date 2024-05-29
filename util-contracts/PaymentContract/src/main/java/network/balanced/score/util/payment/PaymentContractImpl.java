/*
 * Copyright (c) 2023-2024 Balanced.network.
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

package network.balanced.score.util.payment;

import network.balanced.score.lib.interfaces.Trickler;
import network.balanced.score.lib.utils.BalancedAddressManager;
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.utils.Versions;
import network.balanced.score.lib.utils.EnumerableSetDB;

import score.Address;
import score.Context;
import score.DictDB;
import score.VarDB;
import score.annotation.External;

import java.math.BigInteger;
import java.util.List;

import static network.balanced.score.lib.utils.Check.onlyOwner;
import static network.balanced.score.lib.utils.BalancedAddressManager.getSavings;
import static network.balanced.score.lib.utils.BalancedAddressManager.getBnusd;
import static network.balanced.score.lib.utils.BalancedAddressManager.getDaofund;

public class PaymentContractImpl {
    private static final String VERSION = "version";
    private static final String RECIPIENT = "recipient";
    private static final String DISTRIBUTION_PERIOD = "distribution_period";
    private static final String REWARD_RATE = "reward_rate";
    private static final String LAST_CLAIMED_BLOCK = "last_claimed_block";

    private static final VarDB<Address> recipient = Context.newVarDB(RECIPIENT, Address.class);
    private static final VarDB<BigInteger> distributionPeriod = Context.newVarDB(DISTRIBUTION_PERIOD, BigInteger.class);
    private static final VarDB<BigInteger> rewardRate = Context.newVarDB(REWARD_RATE, BigInteger.class);
    private static final VarDB<BigInteger> lastClaimedBlock = Context.newVarDB(LAST_CLAIMED_BLOCK, BigInteger.class);
    private static final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);

    public PaymentContractImpl(Address _governance) {
        if (BalancedAddressManager.getAddressByName(Names.GOVERNANCE) == null) {
            BalancedAddressManager.setGovernance(_governance);
        }

        if (currentVersion.getOrDefault("").equals(Versions.PAYMENTS)) {
            Context.revert("Can't update same version of code");
        }

        currentVersion.set(Versions.PAYMENTS);
    }

    @External(readonly = true)
    public String name() {
        return Names.PAYMENTS;
    }

    @External(readonly = true)
    public String version() {
        return currentVersion.get();
    }

    @External
    public void updateAddress(String name) {
        BalancedAddressManager.resetAddress(name);
    }

    @External(readonly = true)
    public Address getAddress(String name) {
        return BalancedAddressManager.getAddressByName(name);
    }

    @External
    public void cancelContract() {
        onlyOwner();
        Context.require(rewardRate.get() != null, Names.PAYMENTS + ": Contract is not initiated");
        rewardRate.set(null);

        Address bnUSD = getBnusd();
        BigInteger balance = Context.call(BigInteger.class, bnUSD, "balanceOf", Context.getAddress());
        Context.call(bnUSD, "transfer", getDaofund(), balance, new byte[0]);
    }

    @External
    public void configureContract(BigInteger _distributionPeriod, Address _recipient) {
        onlyOwner();
        Context.require(rewardRate.get() == null, Names.PAYMENTS + ": Contract is already initiated, please cancel current contract to before reuse");
        distributionPeriod.set(_distributionPeriod);
        recipient.set(_recipient);
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        Address token = Context.getCaller();
        Context.require(_from.equals(getDaofund()), Names.PAYMENTS + ": Only Balanced daofund can initiate the contract");
        Context.require(getBnusd().equals(token), Names.PAYMENTS + ": Only accepts bnUSD");
        Context.require(rewardRate.get() == null, Names.PAYMENTS + ": Contract is already initiated, please cancel current contract to before reuse");

        BigInteger currentBlock = BigInteger.valueOf(Context.getBlockHeight());
        lastClaimedBlock.set(currentBlock);
        BigInteger rate = _value.divide(distributionPeriod.get());
        rewardRate.set(rate);
    }

    @External
    public void claimPayment() {
        BigInteger payment = getAvailablePayment();
        BigInteger currentBlock = BigInteger.valueOf(Context.getBlockHeight());
        lastClaimedBlock.set(currentBlock);
        Address bnUSD = getBnusd();
        BigInteger balance = Context.call(BigInteger.class, bnUSD, "balanceOf", Context.getAddress());
        if (balance.compareTo(payment) <= 0) {
            payment = balance;
            rewardRate.set(null);
        }

        if (payment.equals(BigInteger.ZERO)) {
            return;
        }

        Context.call(bnUSD, "transfer", recipient.get(), payment, new byte[0]);
    }


    @External(readonly = true)
    public BigInteger getAvailablePayment() {
        BigInteger currentBlock = BigInteger.valueOf(Context.getBlockHeight());
        BigInteger rate = rewardRate.get();

        BigInteger blockDiff = currentBlock.subtract(lastClaimedBlock.get());
        BigInteger payment = blockDiff.multiply(rate);

        return payment;
    }
}