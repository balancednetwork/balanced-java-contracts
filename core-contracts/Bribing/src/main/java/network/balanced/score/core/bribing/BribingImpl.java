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

package network.balanced.score.core.bribing;

import score.*;
import score.annotation.External;

import java.math.BigInteger;
import java.util.Map;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Math.convertToNumber;

public class BribingImpl {
    public static final BigInteger WEEK = BigInteger.valueOf(7L).multiply(MICRO_SECONDS_IN_A_DAY);

    public static final VarDB<Address> rewards = Context.newVarDB("rewards", Address.class);
    //Source->RewardToken->claimedAmount
    public static final BranchDB<String, DictDB<Address, BigInteger>> claimsPerSource = Context.newBranchDB("claimsPerSource", BigInteger.class);
    //Source->RewardToken->availableBribes
    public static final BranchDB<String, DictDB<Address, BigInteger>> rewardPerSource = Context.newBranchDB("rewardPerSource", BigInteger.class);
    //Source->RewardToken->period->amount
    public static final BranchDB<String, BranchDB<Address, DictDB<BigInteger, BigInteger>>> futureRewardPerSource = Context.newBranchDB("futureRewardPerSource", BigInteger.class);

    //Source->RewardToken->amountPerWeight
    public static final BranchDB<String, DictDB<Address, BigInteger>> rewardPerToken = Context.newBranchDB("rewardPerToken", BigInteger.class);
    //Source->RewardToken->period
    public static final BranchDB<String, DictDB<Address, BigInteger>> activePeriod = Context.newBranchDB("activePeriod", BigInteger.class);
    //userAddress->Source->RewardToken->timeOfLastClaim
    public static final BranchDB<Address, BranchDB<String, DictDB<Address, BigInteger>>> lastUserClaim = Context.newBranchDB("lastUserClaim", BigInteger.class);

    public static final BranchDB<String, ArrayDB<Address>> rewardsPerSource = Context.newBranchDB("rewardsPerSource", BigInteger.class);
    public static final BranchDB<Address, ArrayDB<String>> sourcesPerReward = Context.newBranchDB("sourcesPerReward", BigInteger.class);
    //Source->RewardToken->HasAvailableRewards
    public static final BranchDB<String, DictDB<Address, Boolean>> rewardsInSource = Context.newBranchDB("rewardsInSource", Boolean.class);

    private class SourceStatus {
        BigInteger period;
        BigInteger rewardsPerToken;
    }

    public BribingImpl(Address rewards) {
        if (BribingImpl.rewards.get() == null) {
            isContract(rewards);
            BribingImpl.rewards.set(rewards);
        }
    }


    @External(readonly=true)
    public Address getRewards() {
        return rewards.get();
    }

    @External
    public void setRewards(Address address) {
        onlyOwner();
        rewards.set(address);
    }

    @External(readonly=true)
    public BigInteger getActivePeriod(String source, Address rewardToken) {
        return activePeriod.at(source).get(rewardToken);
    }

    // function rewardsPerSource(String source) external view returns (Address[] memory) {
    //     return rewardsPerSource[source];
    // }

    // function sourcesPerReward(Address reward) external view returns (Address[] memory) {
    //     return sourcesPerReward[reward];
    // }

    @External(readonly=true)
    public BigInteger claimable(Address user, String source, Address rewardToken) {
        return getRewardsAmount(user, source, rewardToken, true);
    }

    @External
    public void updatePeriod(String source, Address rewardToken) {
        updateSource(source, rewardToken, false);
    }

    @External
    public void claimBribe(String source, Address rewardToken) {
        Address user = Context.getCaller();
        BigInteger amount = getRewardsAmount(user, source, rewardToken, false);
        Context.require(amount.compareTo(BigInteger.ZERO) > 0, user.toString() + " has no bribe in " + rewardToken.toString() + " to  claim for source: " + source);
        BigInteger prevClaims = claimsPerSource.at(source).getOrDefault(rewardToken, BigInteger.ZERO);
        claimsPerSource.at(source).set(rewardToken, prevClaims.add(amount));
        Context.call(rewardToken, "transfer", user, amount, new byte[0]);
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        Address token = Context.getCaller();
        Context.require(_value.signum() > 0, "Token Fallback: Token value should be a positive number");

        String unpackedData = new String(_data);
        Context.require(!unpackedData.isEmpty(), "Token Fallback: Data can't be empty");

        JsonObject json = Json.parse(unpackedData).asObject();
        String method = json.get("method").asString();
        JsonObject params = json.get("params").asObject();
        String source = params.getString("source", "");

        switch (method) {
            case "addBribe":
                addBribe(source, token, _value);
                break;
            case "scheduledBribes":
                JsonArray amounts = params.get("amounts").asArray();
                scheduledBribes(source, token, _value, amounts);
                break;
            default:
                throw new UserRevertedException("Token fallback: Unimplemented token fallback action");
        }
    }

    private void addBribe(String source, Address rewardToken, BigInteger amount) {
        BigInteger activePeriod = updateSource(source, rewardToken, false).period;
        BigInteger nextPeriod = activePeriod.add(WEEK);
        BigInteger previousFutureRewards = futureRewardPerSource.at(source).at(rewardToken).getOrDefault(activePeriod, BigInteger.ZERO);

        futureRewardPerSource.at(source).at(rewardToken).set(nextPeriod, previousFutureRewards.add(amount));
        add(source, rewardToken);
    }

    private void scheduledBribes(String source, Address rewardToken, BigInteger total, JsonArray amounts) {
        BigInteger sum = BigInteger.ZERO;
        BigInteger period = updateSource(source, rewardToken, false).period;

        DictDB<BigInteger, BigInteger> futureRewards = futureRewardPerSource.at(source).at(rewardToken);
        for (JsonValue jsonAmount : amounts) {
            period = period.add(WEEK);

            BigInteger amount = convertToNumber(jsonAmount);
            sum = sum.add(amount);
            BigInteger prevAmount = futureRewards.getOrDefault(period, BigInteger.ZERO);
            futureRewards.set(period, amount.add(prevAmount));
        }

        Context.require(sum.equals(total), "Scheduled rewards and amount deposited are not equal");
        add(source, rewardToken);
    }

    private BigInteger getRewardsAmount(Address user, String source, Address rewardToken, boolean readonly) {
        SourceStatus status = updateSource(source, rewardToken, readonly);
        DictDB<Address, BigInteger> lastUserClaim = BribingImpl.lastUserClaim.at(user).at(source);
        if (lastUserClaim.getOrDefault(rewardToken, BigInteger.ZERO).compareTo(status.period) >= 0) {
            return BigInteger.ZERO;
        }

        if (!readonly) {
            lastUserClaim.set(rewardToken, status.period);
        }

        BigInteger lastVote = Context.call(BigInteger.class, rewards.get(), "lastUserVote", user, source);

        if (lastVote.compareTo(status.period) >= 0) {
            return BigInteger.ZERO;
        }

        BigInteger slope = Context.call(BigInteger.class, rewards.get(), "voteUserSlopes", user, source);
        BigInteger amount = slope.multiply(status.rewardsPerToken).divide(EXA);

        return amount;
    }

    private SourceStatus updateSource(String source, Address rewardToken, boolean readOnly) {
        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        SourceStatus status = new SourceStatus();
        status.period = activePeriod.at(source).getOrDefault(rewardToken, BigInteger.ZERO);
        status.rewardsPerToken = rewardPerToken.at(source).getOrDefault(rewardToken, BigInteger.ZERO);
        BigInteger nextPeriod = status.period.add(WEEK);
        if (blockTimestamp.compareTo(nextPeriod) < 0) {
            return status;
        }

        if (!readOnly) {
            Context.call(rewards.get(), "checkPointSource", source);
        }

        status.period = blockTimestamp.divide(WEEK).multiply(WEEK);

        BigInteger slope = Context.call(BigInteger.class, rewards.get(), "pointsWeight", source, status.period);
        BigInteger claimedRewards = claimsPerSource.at(source).getOrDefault(rewardToken, BigInteger.ZERO);
        BigInteger totalPreviousAmount = rewardPerSource.at(source).getOrDefault(rewardToken, BigInteger.ZERO);
        BigInteger addedAmount = futureRewardPerSource.at(source).at(rewardToken).getOrDefault(status.period, BigInteger.ZERO);
        BigInteger newTotal = totalPreviousAmount.add(addedAmount);
        BigInteger amount = newTotal.subtract(claimedRewards);
        status.rewardsPerToken = amount.multiply(EXA).divide(slope);

        if (!readOnly) {
            rewardPerSource.at(source).set(rewardToken, newTotal);
            rewardPerToken.at(source).set(rewardToken, status.rewardsPerToken);
            activePeriod.at(source).set(rewardToken, status.period);
            futureRewardPerSource.at(source).at(rewardToken).set(status.period, BigInteger.ZERO);
        }

        return status;
    }

    private void add(String source, Address reward) {
        if (rewardsInSource.at(source).getOrDefault(reward, false)) {
            return;
        }

        rewardsPerSource.at(source).add(reward);
        sourcesPerReward.at(reward).add(source);
        rewardsInSource.at(source).set(reward, true);
    }
}