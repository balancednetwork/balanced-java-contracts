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

package network.balanced.score.core.rewards.weight;

import java.math.BigInteger;
import java.util.Map;

import score.Address;
import score.ArrayDB;
import score.BranchDB;
import score.Context;
import score.DictDB;
import score.VarDB;
import scorex.util.HashMap;
import network.balanced.score.lib.utils.EnumerableSetDB;

import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.core.rewards.RewardsImpl.boostedBaln;
import static network.balanced.score.core.rewards.RewardsImpl.getEventLogger;
import static network.balanced.score.lib.utils.ArrayDBUtils.removeFromArraydb;
import static network.balanced.score.lib.utils.ArrayDBUtils.arrayDbContains;

public class SourceWeightController {
    // 7 * 86400 seconds - all future times are rounded by week
    public static final BigInteger WEEK = MICRO_SECONDS_IN_A_DAY.multiply(BigInteger.valueOf(7));
    // Cannot change weight votes more often than once in 10 days
    public static final BigInteger WEIGHT_VOTE_DELAY = MICRO_SECONDS_IN_A_DAY.multiply(BigInteger.TEN);

    // sourceTypeNames: public(HashMap.get(int128, String[64]))
    private static final EnumerableSetDB<String> sourceTypeNames = new EnumerableSetDB<String>("sourceTypeNames", String.class);

    // we increment values by 1 prior to storing them here so we can rely on a value
    // of zero as meaning the source has not been set
    private static final DictDB<String, Integer> sourceTypes = Context.newDictDB("sourceTypes", Integer.class);
    private static final DictDB<String, Boolean> isVotable = Context.newDictDB("votable", Boolean.class);

    private static final BranchDB<Address, DictDB<String, VotedSlope>> voteUserSlopes = Context.newBranchDB("voteUserSlopes", VotedSlope.class);

    private static final DictDB<Address, BigInteger> voteUserPower = Context.newDictDB("voteUserPower", BigInteger.class);

    private static final BranchDB<Address, DictDB<String, BigInteger>> lastUserVote = Context.newBranchDB("lastUserVote", BigInteger.class);
    private static final BranchDB<Address, ArrayDB<String>> activeUserWeights = Context.newBranchDB("activeUserWeights", String.class);

    // Past and scheduled points for source weight, sum of weights per type, total weight
    // Point is for bias+slope
    // changes* are for changes in slope
    // time* are for the last change timestamp
    // timestamps are rounded to whole weeks

    private static final BranchDB<String, DictDB<BigInteger, Point>> pointsWeight = Context.newBranchDB("pointsWeight", Point.class);

    private static final BranchDB<String, DictDB<BigInteger, BigInteger>> changesWeight = Context.newBranchDB("changesWeight", BigInteger.class);

    private static final DictDB<String, BigInteger> timeWeight = Context.newDictDB("timeWeight", BigInteger.class);

    private static final BranchDB<Integer, DictDB<BigInteger, Point>> pointsSum = Context.newBranchDB("pointsSum", Point.class);

    private static final BranchDB<Integer, DictDB<BigInteger, BigInteger>> changesSum = Context.newBranchDB("changesSum", BigInteger.class);

    private static final DictDB<Integer, BigInteger> timeSum = Context.newDictDB("timeSum", BigInteger.class);


    // time -> total weight
    private static final DictDB<BigInteger, BigInteger> pointsTotal = Context.newDictDB("pointsTotal", BigInteger.class);

    // last scheduled time
    private static final VarDB<BigInteger> timeTotal = Context.newVarDB("timeTotal", BigInteger.class);

    private static final BranchDB<Integer, DictDB<BigInteger, BigInteger>> pointsTypeWeight = Context.newBranchDB("pointsTypeWeight", BigInteger.class);
    private static final DictDB<Integer, BigInteger> timeTypeWeight = Context.newDictDB("timeTypeWeight", BigInteger.class);

    public SourceWeightController(Address bBalnAddress) {
        boostedBaln.set(bBalnAddress);
        timeTotal.set(getWeekTimestamp());
    }

    private static BigInteger getTypeWeight(int sourceType) {
        /**
        *@notice Fill historic type weights week-over-week for missed checkins
                and return the type weight for the future week
        *@param sourceType Source type id
        *@return Type weight
        */
        BigInteger time = timeTypeWeight.getOrDefault(sourceType, BigInteger.ZERO);
        if (time.compareTo(BigInteger.ZERO) <= 0) {
            return BigInteger.ZERO;
        }

        BigInteger timestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        BigInteger weight = pointsTypeWeight.at(sourceType).get(time);
        for (int i = 0; i < 500; i++) {
            if (time.compareTo(timestamp) > 0) {
                break;
            }

            time = time.add(WEEK);
            pointsTypeWeight.at(sourceType).set(time, weight);
            if (time.compareTo(timestamp) > 0) {
                timeTypeWeight.set(sourceType, time);
            }
        }

        return weight;
    }


    private static BigInteger getSum(int sourceType) {
        /**
        *@notice Fill sum of source weights for the same type week-over-week for
                missed checkins and return the sum for the future week
        *@param sourceType Source type id
        *@return Sum of weights
        */
        BigInteger time = timeSum.getOrDefault(sourceType, BigInteger.ZERO);
        BigInteger timeStamp = BigInteger.valueOf(Context.getBlockTimestamp());
        if (time.compareTo(BigInteger.ZERO) <= 0) {
            return BigInteger.ZERO;
        }

        Point pt = pointsSum.at(sourceType).getOrDefault(time, new Point());
        for (int i = 0; i < 500; i++) {
            if (time.compareTo(timeStamp) > 0) {
                break;
            }

            time = time.add(WEEK);
            BigInteger dBias = pt.slope.multiply(WEEK);
            if (pt.bias.compareTo(dBias) > 0) {
                pt.bias = pt.bias.subtract(dBias);
                BigInteger dSlope = changesSum.at(sourceType).getOrDefault(time, BigInteger.ZERO);
                pt.slope = pt.slope.subtract(dSlope);
            } else {
                pt.bias = BigInteger.ZERO;
                pt.slope = BigInteger.ZERO;
            }

            pointsSum.at(sourceType).set(time, pt);
            if (time.compareTo(timeStamp) > 0) {
                timeSum.set(sourceType, time);
            }
        }

        return pt.bias;
    }

    private static BigInteger getTotal() {
        /**
        *@notice Fill historic total weights week-over-week for missed checkins
                and return the total for the future week
        *@return Total weight
        */
        BigInteger time = timeTotal.get();
        BigInteger timestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        if (time.compareTo(timestamp) > 0) {
            // If we have already checkpointed - still need to change the value
            time = time.subtract(WEEK);
        }
        BigInteger pt = pointsTotal.getOrDefault(time, BigInteger.ZERO);

        int nrSourceTypes = sourceTypeNames.length();
        for (int id = 0; id < nrSourceTypes; id++) {
            getSum(id);
            getTypeWeight(id);
        }

        for (int i = 0; i < 500; i++) {
            if (time.compareTo(timestamp) > 0) {
                break;
            }

            time = time.add(WEEK);
            pt = BigInteger.ZERO;
            for (int id = 0; id < nrSourceTypes; id++) {
                BigInteger typeSum = pointsSum.at(id).getOrDefault(time, new Point()).bias;
                BigInteger typeWeight = pointsTypeWeight.at(id).getOrDefault(time, BigInteger.ZERO);

                pt = pt.add(typeSum.multiply(typeWeight));
            }

            pointsTotal.set(time, pt);

            if (time.compareTo(timestamp) > 0) {
                timeTotal.set(time);
            }
        }

        return pt;
    }

    private static BigInteger getWeight(String source) {
        /**
        *@notice Fill historic source weights week-over-week for missed checkins
                and return the total for the future week
        *@param source Name of the source
        *@return Source weight
        */
        BigInteger time = timeWeight.getOrDefault(source, BigInteger.ZERO);
        BigInteger timestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        if (time.compareTo(BigInteger.ZERO) <= 0) {
            return BigInteger.ZERO;
        }

        Point pt = pointsWeight.at(source).getOrDefault(time, new Point());
        for (int i = 0; i < 500; i++) {
            if (time.compareTo(timestamp) > 0) {
                break;
            }
            time = time.add(WEEK);
            BigInteger dBias = pt.slope.multiply(WEEK);
            if (pt.bias.compareTo(dBias) > 0) {
                pt.bias = pt.bias.subtract(dBias);
                BigInteger dSlope = changesWeight.at(source).getOrDefault(time, BigInteger.ZERO);
                pt.slope = pt.slope.subtract(dSlope);
            } else {
                pt.bias = BigInteger.ZERO;
                pt.slope = BigInteger.ZERO;
            }

            pointsWeight.at(source).set(time, pt);
            if (time.compareTo(timestamp) > 0) {
                timeWeight.set(source, time);
            }
        }

        return pt.bias;
    }

    public static void addSource(String name, int sourceType, BigInteger weight) {
        /**
        *@notice Add source `name` of type `sourceType` with weight `weight`
        *@param name Source name
        *@param sourceType Source type
        *@param weight Source weight
        */

        Context.require(sourceTypeNames.at(sourceType) != null, "Not a valid sourceType");
        Context.require(sourceTypes.get(name) == null, "Source with name " + name + " already exists");

        sourceTypes.set(name, sourceType + 1);
        BigInteger nextTime = getNextWeekTimestamp();

        if (weight.compareTo(BigInteger.ZERO) > 0) {
            BigInteger typeWeight = getTypeWeight(sourceType);
            BigInteger oldSum = getSum(sourceType);
            BigInteger oldTotal = getTotal();

            pointsSum.at(sourceType).getOrDefault(nextTime, new Point()).bias = weight.add(oldSum);
            timeSum.set(sourceType, nextTime);
            pointsTotal.set(nextTime, oldTotal.add(typeWeight.multiply(weight)));
            timeTotal.set(nextTime);

            Point weightPoint = pointsWeight.at(name).getOrDefault(nextTime, new Point());
            weightPoint.bias = weight;
            pointsWeight.at(name).set(nextTime, weightPoint);
        }

        if (timeSum.get(sourceType) == null) {
            timeSum.set(sourceType, nextTime);

        }

        timeWeight.set(name, nextTime);
        getEventLogger().NewSource(name, sourceType, weight);
    }

    public static void checkpoint() {
        /**
        *@notice Checkpoint to fill data common for all sources
        */
        getTotal();
    }

    public static void checkpointSource(String name) {
        /**
        *@notice Checkpoint to fill data for both a specific source and common for all sources
        *@param addr Source address
        */
        getWeight(name);
        getTotal();
    }

    public static BigInteger getRelativeWeight(String name, BigInteger time) {
        /**
        *@notice Get Source relative weight (not more than 1.0) normalized to 1e18
                (e.g. 1.0 == 1e18). Inflation which will be received by it is
                inflationRate * relativeWeight / 1e18
        *@param name Source name
        *@param time Relative weight at the specified timestamp in the past or present
        *@return Value of relative weight normalized to 1e18
        */
        time = getWeekTimestamp(time);
        BigInteger totalWeight = pointsTotal.getOrDefault(time, BigInteger.ZERO);
        if (totalWeight.compareTo(BigInteger.ZERO) <= 0) {
            return BigInteger.ZERO;
        }

        int sourceType = sourceTypes.get(name) - 1;
        BigInteger typeWeight = pointsTypeWeight.at(sourceType).get(time);
        BigInteger sourceWeight = pointsWeight.at(name).get(time).bias;
        return EXA.multiply(typeWeight).multiply(sourceWeight).divide(totalWeight);
    }

    public static BigInteger updateRelativeWeight(String name, BigInteger time) {
        /**
        *@notice Get source weight normalized to 1e18 and also fill all the unfilled
                values for type and source records
        *@dev Any address can call, however nothing is recorded if the values are filled already
        *@param name Source name
        *@param time Relative weight at the specified timestamp in the past or present
        *@return Value of relative weight normalized to 1e18
        */
        getWeight(name);
        getTotal();  // Also calculates getSum
        return getRelativeWeight(name, time);
    }

    public static void changeTypeWeight(int typeId, BigInteger weight) {
        /**
        *@notice Change type weight
        *@param typeId Type id
        *@param weight New type weight
        */
        BigInteger oldWeight = getTypeWeight(typeId);
        BigInteger oldSum = getSum(typeId);
        BigInteger totalWeight = getTotal();
        BigInteger timestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        BigInteger nextTime = (timestamp.add(WEEK)).divide(WEEK).multiply(WEEK);

        totalWeight = totalWeight.add(oldSum.multiply(weight)).subtract(oldSum.multiply(oldWeight));
        pointsTotal.set(nextTime, totalWeight);
        pointsTypeWeight.at(typeId).set(nextTime, weight);
        timeTotal.set(nextTime);
        timeTypeWeight.set(typeId, nextTime);

        getEventLogger().NewTypeWeight(typeId, nextTime, oldWeight, totalWeight);
    }

    public static void addType(String name, BigInteger weight) {
        /**
        *notice Add source type with name `Name` and weight `weight`
        *param Name Name of source type
        *param weight Weight of source type = 0
        */
        int typeId = sourceTypeNames.length();
        sourceTypeNames.add(name);
        Context.require(sourceTypeNames.at(typeId).equals(name));
        if (!weight.equals(BigInteger.ZERO)) {
            changeTypeWeight(typeId, weight);
        }

        getEventLogger().AddType(name, typeId);
    }

    public static void voteForSourceWeights(String sourceName, BigInteger userWeight) {
        /**
        *@notice Allocate voting power for changing pool weights
        *@param sourceName Source which `user` votes for
        *@param UserWeight Weight for a source in bps (units of 0.01%). Minimal is 0.01%. Ignored if 0
        */
        Context.require(isVotable.getOrDefault(sourceName, true) || userWeight.equals(BigInteger.ZERO), sourceName + " is not a votable source, you can only remove weight");

        Address bBalnAddress = boostedBaln.get();
        Address user = Context.getCaller();
        BigInteger slope = Context.call(BigInteger.class, bBalnAddress, "getLastUserSlope", user);
        BigInteger lockEnd = Context.call(BigInteger.class, bBalnAddress, "lockedEnd", user);
        BigInteger timestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        BigInteger nextTime = getNextWeekTimestamp();

        Context.require(lockEnd.compareTo(nextTime) > 0, "Your token lock expires too soon");
        Context.require((userWeight.compareTo(BigInteger.ZERO) >= 0) && (userWeight.compareTo(BigInteger.valueOf(10000)) <= 0), "Weight has to be between 0 and 10000");
        BigInteger nextUserVote = lastUserVote.at(user).getOrDefault(sourceName, BigInteger.ZERO).add(WEIGHT_VOTE_DELAY);
        Context.require(timestamp.compareTo(nextUserVote) >= 0, "Cannot vote so often");

        int sourceType = sourceTypes.get(sourceName) - 1;
        Context.require( sourceType >= 0, "Source not added");
        // Prepare slopes and biases in memory
        VotedSlope oldSlope = voteUserSlopes.at(user).getOrDefault(sourceName, new VotedSlope());
        BigInteger oldDt = BigInteger.ZERO;
        if (oldSlope.end.compareTo(nextTime) > 0) {
            oldDt = oldSlope.end.subtract(nextTime);
        }

        BigInteger oldBias = oldSlope.slope.multiply(oldDt);
        VotedSlope newSlope = new VotedSlope(
            slope.multiply(userWeight).divide(BigInteger.valueOf(10000)),
            userWeight,
            lockEnd
        );
        BigInteger newDt = lockEnd.subtract(nextTime); // dev: raises when expired
        BigInteger newBias = newSlope.slope.multiply(newDt);

        // Check and update powers (weights) used
        BigInteger powerUsed = voteUserPower.getOrDefault(user, BigInteger.ZERO);
        powerUsed = powerUsed.add(newSlope.power).subtract(oldSlope.power);
        Context.require((powerUsed.compareTo(BigInteger.ZERO) >= 0) && (powerUsed.compareTo(BigInteger.valueOf(10000)) <= 0),  "Used too much power");
        voteUserPower.set(user, powerUsed);

        // Remove old and schedule new slope changes
        // Remove slope changes for old slopes
        // Schedule recording of initial slope for nextTime
        BigInteger oldWeightBias = getWeight(sourceName);
        BigInteger oldWeightSlope = pointsWeight.at(sourceName).getOrDefault(nextTime, new Point()).slope;
        BigInteger oldSumBias = getSum(sourceType);
        BigInteger oldSumSlope = pointsSum.at(sourceType).getOrDefault(nextTime, new Point()).slope;

        Point weightPoint = pointsWeight.at(sourceName).getOrDefault(nextTime, new Point());
        weightPoint.bias = (oldWeightBias.add(newBias)).max(oldBias).subtract(oldBias);
        Point sumPoint = pointsSum.at(sourceType).getOrDefault(nextTime, new Point());
        sumPoint.bias = (oldSumBias.add(newBias)).max(oldBias).subtract(oldBias);

        if (oldSlope.end.compareTo(nextTime) > 0) {
            weightPoint.slope = (oldWeightSlope.add(newSlope.slope)).max(oldSlope.slope).subtract(oldSlope.slope);
            sumPoint.slope = (oldSumSlope.add(newSlope.slope)).max(oldSlope.slope).subtract(oldSlope.slope);
        } else {
            weightPoint.slope = weightPoint.slope.add(newSlope.slope);
            sumPoint.slope = sumPoint.slope.add(newSlope.slope);
        }

        if (oldSlope.end.compareTo(timestamp) > 0) {
            // Cancel old slope changes if they still didn'time happen
            BigInteger oldWeight = changesWeight.at(sourceName).get(oldSlope.end);
            changesWeight.at(sourceName).set(oldSlope.end, oldWeight.subtract(oldSlope.slope));
            BigInteger oldSum = changesSum.at(sourceType).get(oldSlope.end);
            changesSum.at(sourceType).set(oldSlope.end, oldSum.subtract(oldSlope.slope));
        }

        // Add slope changes for new slopes
        BigInteger newWeight = changesWeight.at(sourceName).getOrDefault(newSlope.end, BigInteger.ZERO);
        changesWeight.at(sourceName).set(newSlope.end, newWeight.subtract(newSlope.slope));
        BigInteger newSum = changesSum.at(sourceType).getOrDefault(newSlope.end, BigInteger.ZERO);
        changesSum.at(sourceType).set(newSlope.end, newSum.subtract(newSlope.slope));


        pointsWeight.at(sourceName).set(nextTime, weightPoint);
        pointsSum.at(sourceType).set(nextTime, sumPoint);

        getTotal();

        voteUserSlopes.at(user).set(sourceName, newSlope);

        // Record last action time
        if (userWeight.equals(BigInteger.ZERO)) {
            removeFromArraydb(sourceName, activeUserWeights.at(user));
        } else {
            if (!arrayDbContains(activeUserWeights.at(user), sourceName)) {
                activeUserWeights.at(user).add(sourceName);
            }
        }

        lastUserVote.at(user).set(sourceName, timestamp);
        getEventLogger().VoteForSource(sourceName, user, newWeight, nextTime);
    }

    public static void setVotable(String name, boolean votable) {
        isVotable.set(name, votable);
    }

    public static boolean isVotable(String name) {
        return isVotable.getOrDefault(name, true);
    }

    public static BigInteger getSourceWeight(String sourceName) {
        /**
        *@notice Get current source weight
        *@param sourceName Source name
        *@return Source weight
        */
        return pointsWeight.at(sourceName).get(timeWeight.get(sourceName)).bias;
    }

    public static BigInteger getCurrentTypeWeight(int typeId) {
        /**
        *@notice Get current type weight
        *@param typeId Type id
        *@return Type weight
        */
        return pointsTypeWeight.at(typeId).get(timeTypeWeight.get(typeId));
    }

    public static BigInteger getTotalWeight() {
        /**
        *@notice Get current total (type-weighted) weight
        *@return Total weight
        */
        return pointsTotal.get(timeTotal.get());
    }

    public static BigInteger getWeightsSumPerType(int typeId) {
        /**
        *@notice Get sum of source weights per type
        *@param typeId Type id
        *@return Sum of source weights
        */
        return pointsSum.at(typeId).get(timeSum.get(typeId)).bias;
    }

    public static Map<String, Map<String, BigInteger>> getUserVoteData(Address user) {
        ArrayDB<String> userSources = activeUserWeights.at(user);
        int nrOfSources = userSources.size();
        Map<String, Map<String, BigInteger>> data = new HashMap<>();
        for (int i = 0; i < nrOfSources; i++) {
            String source = userSources.get(i);
            VotedSlope votedSlope = voteUserSlopes.at(user).get(source);
            BigInteger lastVote = lastUserVote.at(user).get(source);
            Map<String, BigInteger> sourceData = new HashMap<>();
            sourceData.put("slope", votedSlope.slope);
            sourceData.put("power", votedSlope.power);
            sourceData.put("end", votedSlope.end);
            sourceData.put("lastVote", lastVote);

            data.put(source, sourceData);
        }

        return data;
    }

    public static int getSourceType(String sourceName) {
        return sourceTypes.get(sourceName) - 1;
    }

    public static int getTypeId(String name) {
        return sourceTypeNames.indexOf(name);
    }

    private static BigInteger getWeekTimestamp() {
        return BigInteger.valueOf(Context.getBlockTimestamp()).divide(WEEK).multiply(WEEK);
    }

    private static BigInteger getWeekTimestamp(BigInteger time) {
        return time.divide(WEEK).multiply(WEEK);
    }

    private static BigInteger getNextWeekTimestamp() {
        return (BigInteger.valueOf(Context.getBlockTimestamp()).add(WEEK)).divide(WEEK).multiply(WEEK);
    }

}
