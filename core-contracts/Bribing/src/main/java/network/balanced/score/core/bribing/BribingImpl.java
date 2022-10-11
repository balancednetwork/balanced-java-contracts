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

import network.balanced.score.lib.interfaces.Bribing;
import network.balanced.score.lib.structs.Point;
import network.balanced.score.lib.structs.VotedSlope;

import java.math.BigInteger;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Math.convertToNumber;

public class BribingImpl implements Bribing {
    public static final BigInteger WEEK = BigInteger.valueOf(7L).multiply(MICRO_SECONDS_IN_A_DAY);

    public static final VarDB<Address> rewards = Context.newVarDB("rewards", Address.class);
    //Source->bribeToken->claimedAmount
    public static final BranchDB<String, DictDB<Address, BigInteger>> claimsPerSource = Context.newBranchDB("claimsPerSource", BigInteger.class);
    //Source->bribeToken->availableBribes
    public static final BranchDB<String, DictDB<Address, BigInteger>> rewardPerSource = Context.newBranchDB("rewardPerSource", BigInteger.class);
    //Source->bribeToken->period->amount
    public static final BranchDB<String, BranchDB<Address, DictDB<BigInteger, BigInteger>>> futureBribePerSource = Context.newBranchDB("futureBribePerSource", BigInteger.class);

    //Source->bribeToken->amountPerWeight
    public static final BranchDB<String, DictDB<Address, BigInteger>> bribePerToken = Context.newBranchDB("bribePerToken", BigInteger.class);
    //Source->bribeToken->period
    public static final BranchDB<String, DictDB<Address, BigInteger>> activePeriod = Context.newBranchDB("activePeriod", BigInteger.class);
    //userAddress->Source->bribeToken->timeOfLastClaim
    public static final BranchDB<Address, BranchDB<String, DictDB<Address, BigInteger>>> lastUserClaim = Context.newBranchDB("lastUserClaim", BigInteger.class);

    public static final BranchDB<String, ArrayDB<Address>> bribesPerSource = Context.newBranchDB("bribesPerSource", BigInteger.class);
    public static final BranchDB<Address, ArrayDB<String>> sourcesPerBribe = Context.newBranchDB("sourcesPerBribe", BigInteger.class);
    //Source->bribeToken->HasAvailableBribes
    public static final BranchDB<String, DictDB<Address, Boolean>> bribesInSource = Context.newBranchDB("bribesInSource", Boolean.class);

    private class SourceStatus {
        BigInteger period;
        BigInteger bribesPerToken;
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
    public BigInteger getActivePeriod(String source, Address bribeToken) {
        return activePeriod.at(source).get(bribeToken);
    }

    @External(readonly=true)
    public Address[] bribesPerSource(String source) {
        ArrayDB<Address> bribesDB = bribesPerSource.at(source);
        int size = bribesDB.size();
        Address[] bribes = new Address[size];
        for (int i = 0; i < size; i++) {
            bribes[i] = bribesDB.get(i);
        }

        return bribes;
    }

    @External(readonly=true)
    public String[] sourcesPerBribe(Address reward) {
        ArrayDB<String> sourcesDB = sourcesPerBribe.at(reward);
        int size = sourcesDB.size();
        String[] sources = new String[size];
        for (int i = 0; i < size; i++) {
            sources[i] = sourcesDB.get(i);
        }

        return sources;
    }

    @External(readonly=true)
    public BigInteger getTotalBribes(String source, Address bribeToken) {
        return rewardPerSource.at(source).getOrDefault(bribeToken, BigInteger.ZERO);
    }

    @External(readonly=true)
    public BigInteger getClaimedBribes(String source, Address bribeToken) {
        return rewardPerSource.at(source).getOrDefault(bribeToken, BigInteger.ZERO);
    }

    @External(readonly=true)
    public BigInteger getFutureBribe(String source, Address bribeToken, BigInteger period) {
        period = period.divide(WEEK).multiply(WEEK);
        return futureBribePerSource.at(source).at(bribeToken).getOrDefault(period, BigInteger.ZERO);
    }

    @External(readonly=true)
    public BigInteger claimable(Address user, String source, Address bribeToken) {
        return getBribesAmount(user, source, bribeToken, true);
    }

    @External
    public void updatePeriod(String source, Address bribeToken) {
        updateSource(source, bribeToken, false);
    }

    @External
    public void claimBribe(String source, Address bribeToken) {
        Address user = Context.getCaller();
        BigInteger amount = getBribesAmount(user, source, bribeToken, false);
        Context.require(amount.compareTo(BigInteger.ZERO) > 0, user.toString() + " has no bribe in " + bribeToken.toString() + " to  claim for source: " + source);
        BigInteger prevClaims = claimsPerSource.at(source).getOrDefault(bribeToken, BigInteger.ZERO);
        claimsPerSource.at(source).set(bribeToken, prevClaims.add(amount));
        Context.call(bribeToken, "transfer", user, amount, new byte[0]);
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
        // TODO verify source
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

    private void addBribe(String source, Address bribeToken, BigInteger amount) {
        BigInteger activePeriod = updateSource(source, bribeToken, false).period;
        BigInteger nextPeriod = activePeriod.add(WEEK);
        BigInteger previousFutureBribes= futureBribePerSource.at(source).at(bribeToken).getOrDefault(activePeriod, BigInteger.ZERO);

        futureBribePerSource.at(source).at(bribeToken).set(nextPeriod, previousFutureBribes.add(amount));
        add(source, bribeToken);
    }

    private void scheduledBribes(String source, Address bribeToken, BigInteger total, JsonArray amounts) {
        BigInteger sum = BigInteger.ZERO;
        BigInteger period = updateSource(source, bribeToken, false).period;

        DictDB<BigInteger, BigInteger> futureBribes = futureBribePerSource.at(source).at(bribeToken);
        for (JsonValue jsonAmount : amounts) {
            period = period.add(WEEK);

            BigInteger amount = convertToNumber(jsonAmount);
            sum = sum.add(amount);
            BigInteger prevAmount = futureBribes.getOrDefault(period, BigInteger.ZERO);
            futureBribes.set(period, amount.add(prevAmount));
        }

        Context.require(sum.equals(total), "Scheduled bribes and amount deposited are not equal");
        add(source, bribeToken);
    }

    private BigInteger getBribesAmount(Address user, String source, Address bribeToken, boolean readonly) {
        SourceStatus status = updateSource(source, bribeToken, readonly);
        DictDB<Address, BigInteger> lastUserClaim = BribingImpl.lastUserClaim.at(user).at(source);
        if (lastUserClaim.getOrDefault(bribeToken, BigInteger.ZERO).compareTo(status.period) >= 0) {
            return BigInteger.ZERO;
        }

        if (!readonly) {
            lastUserClaim.set(bribeToken, status.period);
        }

        BigInteger lastVote = Context.call(BigInteger.class, rewards.get(), "getLastUserVote", user, source);

        if (lastVote.compareTo(status.period) >= 0) {
            return BigInteger.ZERO;
        }

        BigInteger slope = Context.call(VotedSlope.class, rewards.get(), "getUserSlope", user, source).slope;
        BigInteger amount = slope.multiply(status.bribesPerToken).divide(EXA);

        return amount;
    }

    private SourceStatus updateSource(String source, Address bribeToken, boolean readOnly) {
        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        SourceStatus status = new SourceStatus();
        status.period = activePeriod.at(source).getOrDefault(bribeToken, BigInteger.ZERO);
        status.bribesPerToken = bribePerToken.at(source).getOrDefault(bribeToken, BigInteger.ZERO);
        BigInteger nextPeriod = status.period.add(WEEK);
        if (blockTimestamp.compareTo(nextPeriod) < 0) {
            return status;
        }

        if (!readOnly) {
            Context.call(rewards.get(), "checkpointSource", source);
        }

        status.period = blockTimestamp.divide(WEEK).multiply(WEEK);

        BigInteger slope = Context.call(Point.class, rewards.get(), "getSourceWeight", source, status.period).slope;
        BigInteger claimedBribes = claimsPerSource.at(source).getOrDefault(bribeToken, BigInteger.ZERO);
        BigInteger totalPreviousAmount = rewardPerSource.at(source).getOrDefault(bribeToken, BigInteger.ZERO);
        BigInteger addedAmount = futureBribePerSource.at(source).at(bribeToken).getOrDefault(status.period, BigInteger.ZERO);
        BigInteger newTotal = totalPreviousAmount.add(addedAmount);
        BigInteger amount = newTotal.subtract(claimedBribes);
        status.bribesPerToken = amount.multiply(EXA).divide(slope);

        if (!readOnly) {
            rewardPerSource.at(source).set(bribeToken, newTotal);
            bribePerToken.at(source).set(bribeToken, status.bribesPerToken);
            activePeriod.at(source).set(bribeToken, status.period);
            futureBribePerSource.at(source).at(bribeToken).set(status.period, BigInteger.ZERO);
        }

        return status;
    }

    private void add(String source, Address bribe) {
        if (bribesInSource.at(source).getOrDefault(bribe, false)) {
            return;
        }

        bribesPerSource.at(source).add(bribe);
        sourcesPerBribe.at(bribe).add(source);
        bribesInSource.at(source).set(bribe, true);
    }
}