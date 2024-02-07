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

package network.balanced.score.util.trickler;

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

public class TricklerImpl implements Trickler {

    private static final String VERSION = "version";
    private static final String DISTRIBUTION_PERIOD = "distribution_period";
    private static final String TOKENS_ALLOW_LIST = "tokens_allow_list";
    private static final String STARTING_BLOCK = "starting_block";
    private static final String TOKEN_REWARD_RATE = "token_reward_rate";
    private static final String LAST_CLAIMED_BLOCK = "last_claimed_block";

    public static final BigInteger DEFAULT_DISTRIBUTION_PERIOD = BigInteger.valueOf(43200 * 30 * 6); // 6 Months
    private static final VarDB<BigInteger> distributionPeriod = Context.newVarDB(DISTRIBUTION_PERIOD, BigInteger.class);

    private static final EnumerableSetDB<Address> tokensAllowList = new EnumerableSetDB<>(TOKENS_ALLOW_LIST, Address.class);
    private static final DictDB<Address, BigInteger> tokenBlockRewardRate = Context.newDictDB(TOKEN_REWARD_RATE, BigInteger.class);
    private static final DictDB<Address, BigInteger> lastClaimedBlock = Context.newDictDB(LAST_CLAIMED_BLOCK, BigInteger.class);
    private static final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);

    public TricklerImpl(Address _governance) {

        if (BalancedAddressManager.getAddressByName(Names.GOVERNANCE) == null) {
            BalancedAddressManager.setGovernance(_governance);
            distributionPeriod.set(DEFAULT_DISTRIBUTION_PERIOD);
        }

        if (currentVersion.getOrDefault("").equals(Versions.TRICKLER)) {
            Context.revert("Can't update same version of code");
        }

        currentVersion.set(Versions.TRICKLER);
    }

    @External(readonly = true)
    public String name() {
        return Names.TRICKLER;
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
    public void setDistributionPeriod(BigInteger blocks) {
        onlyOwner();
        distributionPeriod.set(blocks);
    }

    @External(readonly = true)
    public BigInteger getDistributionPeriod() {
        return distributionPeriod.get();
    }

    @External
    public void addAllowedToken(Address token) {
        onlyOwner();
        tokensAllowList.add(token);
    }

    @External
    public void removeAllowedToken(Address token) {
        onlyOwner();
        tokensAllowList.remove(token);
    }

    @External(readonly = true)
    public List<Address> getAllowListTokens() {
        return tokensAllowList.range(BigInteger.ZERO, BigInteger.valueOf(tokensAllowList.length()));
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        Address token = Context.getCaller();
        Context.require(tokensAllowList.contains(token), Names.TRICKLER + ": Can't accept this token");

        claimRewards(token);
        BigInteger balance = Context.call(BigInteger.class, token, "balanceOf", Context.getAddress());
        BigInteger newTokensRate = balance.divide(distributionPeriod.get());
        tokenBlockRewardRate.set(token, newTokensRate);
    }

    @External
    public void claimRewards(Address token) {
        Context.require(tokensAllowList.contains(token), Names.TRICKLER + ": Token is not listed");
        BigInteger currentBlock = BigInteger.valueOf(Context.getBlockHeight());
        BigInteger reward = getRewards(token);
        lastClaimedBlock.set(token, currentBlock);

        BigInteger balance = Context.call(BigInteger.class, token, "balanceOf", Context.getAddress());
        if (balance.compareTo(reward) <= 0) {
            reward = balance;
            tokenBlockRewardRate.set(token, BigInteger.ZERO);
        }

        if (reward.equals(BigInteger.ZERO)) {
            return;
        }

        Context.call(token, "transfer", getSavings(), reward, new byte[0]);
    }

    @External
    public void claimAllRewards() {
        List<Address> tokens = getAllowListTokens();
        for (Address token : tokens) {
            claimRewards(token);
        }
    }

    @External(readonly = true)
    public BigInteger getRewards(Address token) {
        BigInteger currentBlock = BigInteger.valueOf(Context.getBlockHeight());
        BigInteger currentRate = tokenBlockRewardRate.getOrDefault(token, BigInteger.ZERO);

        BigInteger blockDiff = currentBlock.subtract(lastClaimedBlock.getOrDefault(token, currentBlock));
        BigInteger reward = blockDiff.multiply(currentRate);

        return reward;
    }
}