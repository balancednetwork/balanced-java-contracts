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

package network.balanced.score.core.rewards;

import score.*;
import score.annotation.EventLog;
import score.annotation.Optional;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.POINTS;

public class RewardsVote {

    // 7 * 86400 seconds - all future times are rounded by week
    public static final BigInteger WEEK = BigInteger.valueOf(604800);

    // cannot change weight votes more often than once in 10 days
    public static final BigInteger WEIGHT_VOTE_DELAY = BigInteger.valueOf(10 * 86400);

    @EventLog
    public void CommitOwnership(Address admin) {

    }

    @EventLog
    public void ApplyOwnership(Address admin) {

    }

    @EventLog
    public void AddType(String name, BigInteger typeId) {

    }

    @EventLog
    public void NewTypeWeight(BigInteger typeId, BigInteger time, BigInteger weight, BigInteger totalWeight) {

    }

    @EventLog
    public void NewGaugeWeight(Address gaugeAddress, BigInteger time, BigInteger weight, BigInteger totalWeight) {

    }

    @EventLog
    public void VoteForGauge(BigInteger time, Address user, Address gaugeAddress, BigInteger weight) {

    }

    @EventLog
    public void NewGauge(Address addr, BigInteger gaugeType, BigInteger weight) {

    }

    // Can and will be a smart contract
    private final VarDB<Address> admin = Context.newVarDB("admin", Address.class);
    // Can and will be a smart contract
    private static final VarDB<Address> futureAdmin = Context.newVarDB("future_admin", Address.class);

    // Baln Token
    private final VarDB<Address> balnToken = Context.newVarDB("token", Address.class);
    // Boosted Baln
    private final VarDB<Address> boostedBalnToken = Context.newVarDB("boosted_baln_token", Address.class);

    // Gauge Parameters
    // All numbers are "fixed point" on the basis of 1e18
    private final VarDB<BigInteger> numberOfGaugeTypes = Context.newVarDB("number_of_gauge_types", BigInteger.class);
    private final VarDB<BigInteger> numberOfGauges = Context.newVarDB("number_of_gauges", BigInteger.class);
    private static final DictDB<BigInteger, String> gaugeTypeNames = Context.newDictDB("gauge_type_names",
            String.class);

    // Needed for enumeration
    private static final ArrayDB<Address> gauges = Context.newArrayDB("gauges", Address.class);

    // We increment values by 1 prior to storing them here, so we can rely on a value of zero as meaning the gauge has
    // not been set
    private static final DictDB<Address, BigInteger> gaugeTypes = Context.newDictDB("gauge_types", BigInteger.class);

    // user -> GaugeAddress -> VotedSlope
    private static final BranchDB<Address, DictDB<Address, VotedSlope>> voteUserSlopes = Context.newBranchDB(
            "vote_user_slopes", VotedSlope.class);
    // Total vote power used by user
    private static final DictDB<Address, BigInteger> voteUserPower = Context.newDictDB("vote_user_power",
            BigInteger.class);
    // Last user vote's timestamp for each gauge address
    private static final BranchDB<Address, DictDB<Address, BigInteger>> lastUserVote = Context.newBranchDB(
            "last_user_vote", BigInteger.class);

    // Past and scheduled points for gauge weight, sum of weights per type, total weight
    // Point is for bias + slope
    // changes* are for changes in slope
    // time* are for the last change timestamp
    // timestamps are rounded to whole weeks

    // GaugeAddress -> Time -> Point
    private final BranchDB<Address, DictDB<BigInteger, Point>> pointsWeight = Context.newBranchDB("points_weight",
            Point.class);
    // GaugeAddress -> Time -> Slope
    private final BranchDB<Address, DictDB<BigInteger, BigInteger>> changesWeight = Context.newBranchDB(
            "changes_weight", BigInteger.class);
    // GaugeAddress -> last scheduled time (next week)
    private static final DictDB<Address, BigInteger> timeWeight = Context.newDictDB("time_weight", BigInteger.class);

    // TypeId -> Time -> Point
    private final BranchDB<BigInteger, DictDB<BigInteger, Point>> pointsSum = Context.newBranchDB("points_sum",
            Point.class);
    // TypeId -> Time -> Slope
    private final BranchDB<BigInteger, DictDB<BigInteger, BigInteger>> changesSum = Context.newBranchDB("changes_sum"
            , BigInteger.class);
    // TypeId -> Last scheduled time (next week)
    private static final ArrayDB<BigInteger> timeSum = Context.newArrayDB("time_sum", BigInteger.class);

    // Time -> total weight
    private static final DictDB<BigInteger, BigInteger> pointsTotal = Context.newDictDB("points_total",
            BigInteger.class);
    // Last scheduled time
    private static final VarDB<BigInteger> timeTotal = Context.newVarDB("time_total", BigInteger.class);

    // typeId -> Time -> Type weight
    private static final BranchDB<BigInteger, DictDB<BigInteger, BigInteger>> pointsTypeWeight = Context.newBranchDB(
            "points_type_weight", BigInteger.class);
    // TypeId -> last scheduled time (next week)
    private static final ArrayDB<BigInteger> timeTypeWeight = Context.newArrayDB("time_type_weight", BigInteger.class);

    private static final Address ZERO_ADDRESS = new Address(new byte[Address.LENGTH]);

    /**
     * Contract constructor
     *
     * @param balnToken   Baln token contract address
     * @param boostedBaln Boosted Baln token contract address
     */
    public RewardsVote(Address balnToken, Address boostedBaln) {

        admin.set(Context.getCaller());
        this.balnToken.set(balnToken);
        this.boostedBalnToken.set(boostedBaln);
        timeTotal.set(BigInteger.valueOf(Context.getBlockTimestamp()).divide(WEEK).multiply(WEEK));
    }

    /**
     * Transfer ownership of Gauge Controller to addr
     *
     * @param addr Address to have ownership transferred to
     */
    public void commitTransferOwnership(Address addr) {
        Context.require(Context.getCaller().equals(admin.get()));
        futureAdmin.set(addr);
        CommitOwnership(addr);
    }

    /**
     * Apply pending ownership transfer
     */
    public void applyTransferOwnership() {
        Context.require(Context.getCaller().equals(admin.get()));
        Address admin = futureAdmin.get();
        Context.require(!admin.equals(ZERO_ADDRESS));
        this.admin.set(admin);
        ApplyOwnership(admin);
    }

    /**
     * Get gauge type for address
     *
     * @param addr Gauge address
     * @return Gauge type id
     */
    public BigInteger gaugeTypes(Address addr) {
        BigInteger gaugeType = gaugeTypes.get(addr);
        Context.require(gaugeType != null);
        return gaugeType.subtract(BigInteger.ONE);
    }

    /**
     * Fill historic type weights week-over-week for missed checkins and return the type weight for the future week
     *
     * @param gaugeType Gauge type id
     * @return Type weight
     */
    public BigInteger getTypeWeight(BigInteger gaugeType) {
        BigInteger time = timeTypeWeight.get(gaugeType.intValue());
        if (time.compareTo(BigInteger.ZERO) <= 0) {
            return BigInteger.ZERO;
        }

        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        BigInteger weight = pointsTypeWeight.at(gaugeType).getOrDefault(time, BigInteger.ZERO);
        for (int i = 0; i < 500; i++) {
            if (time.compareTo(blockTimestamp) > 0) {
                break;
            }
            time = time.add(WEEK);
            pointsTypeWeight.at(gaugeType).set(time, weight);
            if (time.compareTo(blockTimestamp) > 0) {
                timeTypeWeight.set(gaugeType.intValue(), time);
            }
        }
        return weight;
    }

    /**
     * Fill sum of gauge weights for the same type week-over-week for missed checkins and return the sum for the
     * future week
     *
     * @param gaugeType Gauge type id
     * @return Sum of weights
     */
    public BigInteger getSum(BigInteger gaugeType) {
        BigInteger time = timeSum.get(gaugeType.intValue());
        if (time.compareTo(BigInteger.ZERO) <= 0) {
            return BigInteger.ZERO;
        }

        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        Point pt = pointsSum.at(gaugeType).get(time);
        for (int i = 0; i < 500; i++) {
            if (time.compareTo(BigInteger.ZERO) > 0) {
                break;
            }
            time = time.add(WEEK);
            BigInteger dBias = pt.slope.multiply(WEEK);
            if (pt.bias.compareTo(dBias) > 0) {
                pt.bias = pt.bias.subtract(dBias);
                BigInteger dSlope = changesSum.at(gaugeType).get(time);
                pt.slope = pt.slope.subtract(dSlope);
            } else {
                pt.bias = BigInteger.ZERO;
                pt.slope = BigInteger.ZERO;
            }
            pointsSum.at(gaugeType).set(time, pt);
            if (time.compareTo(blockTimestamp) > 0) {
                timeSum.set(gaugeType.intValue(), time);
            }
        }
        return pt.bias;
    }

    /**
     * Fill historic total weights week-over-week for missed checkins and return the total for the future week
     *
     * @return Total weight
     */
    public BigInteger getTotal() {
        BigInteger time = timeTotal.get();
        BigInteger numberOfGaugeTypes = this.numberOfGaugeTypes.get();

        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        if (time.compareTo(blockTimestamp) > 0) {
            // If we already have check pointed - still need to change the value
            time = time.subtract(WEEK);
        }
        BigInteger pt = pointsTotal.get(time);

        for (int gaugeType = 0; gaugeType < 100; gaugeType++) {
            if (gaugeType == numberOfGaugeTypes.intValue()) {
                break;
            }
            getSum(BigInteger.valueOf(gaugeType));
            getTypeWeight(BigInteger.valueOf(gaugeType));
        }

        for (int i = 0; i < 500; i++) {
            if (time.compareTo(blockTimestamp) > 0) {
                break;
            }
            time = time.add(WEEK);
            pt = BigInteger.ZERO;
            // Scales as numberOfTypes * numberOfUncheckedWeeks (hopefully 1 at most)
            for (int gaugeType = 0; gaugeType < 100; gaugeType++) {
                if (gaugeType == numberOfGaugeTypes.intValue()) {
                    break;
                }
                BigInteger typeSum = pointsSum.at(BigInteger.valueOf(gaugeType)).get(time).bias;
                BigInteger typeWeight = pointsTypeWeight.at(BigInteger.valueOf(gaugeType)).get(time);
                pt = pt.add(typeSum.multiply(typeWeight));
            }
            pointsTotal.set(time, pt);

            if (time.compareTo(blockTimestamp) > 0) {
                timeTotal.set(time);
            }
        }
        return pt;
    }

    /**
     * Fill historic gauge weights week-over-week for missed checkins and return the total for the future week
     *
     * @param gaugeAddr Address of the gauge
     * @return Gauge weight
     */
    public BigInteger getWeight(Address gaugeAddr) {

        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        BigInteger time = timeWeight.get(gaugeAddr);
        if (time.compareTo(BigInteger.ZERO) <= 0) {
            return BigInteger.ZERO;
        }

        Point pt = pointsWeight.at(gaugeAddr).get(time);
        for (int i = 0; i < 500; i++) {
            if (time.compareTo(blockTimestamp) > 0) {
                break;
            }
            time = time.add(WEEK);
            BigInteger dBias = pt.slope.multiply(WEEK);
            if (pt.bias.compareTo(dBias) > 0) {
                pt.bias = pt.bias.subtract(dBias);
                BigInteger dSlope = changesWeight.at(gaugeAddr).get(time);
                pt.slope = pt.slope.subtract(dSlope);
            } else {
                pt.bias = BigInteger.ZERO;
                pt.slope = BigInteger.ZERO;
            }
            pointsWeight.at(gaugeAddr).set(time, pt);
            if (time.compareTo(blockTimestamp) > 0) {
                timeWeight.set(gaugeAddr, time);
            }
        }
        return pt.bias;
    }

    /**
     * Add gauge addr of type gaugeType with weight
     *
     * @param addr      Gauge address
     * @param gaugeType Gauge type
     * @param weight    Gauge weight
     */
    public void addGauge(Address addr, BigInteger gaugeType, @Optional BigInteger weight) {

        Context.require(Context.getCaller().equals(admin.get()));
        Context.require(gaugeType.compareTo(BigInteger.ZERO) >= 0 &&
                gaugeType.compareTo(numberOfGaugeTypes.getOrDefault(BigInteger.ZERO)) < 0);
        // dev: cannot add the same gauge twice
        Context.require(gaugeTypes.get(addr) != null);

        BigInteger numberOfGauges = this.numberOfGauges.get();
        this.numberOfGauges.set(numberOfGauges.add(BigInteger.ONE));
        gauges.set(numberOfGauges.intValue(), addr);

        gaugeTypes.set(addr, gaugeType.add(BigInteger.ONE));
        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        BigInteger nextTime = blockTimestamp.add(WEEK).divide(WEEK).multiply(WEEK);

        if (weight.compareTo(BigInteger.ZERO) > 0) {
            BigInteger typeWeight = getTypeWeight(gaugeType);
            BigInteger oldSum = getSum(gaugeType);
            BigInteger oldTotal = getTotal();

            // TODO: This needs to work with how storing data works in java scores
            pointsSum.at(gaugeType).get(nextTime).bias = weight.add(oldSum);
            timeSum.set(gaugeType.intValue(), nextTime);
            pointsTotal.set(nextTime, oldTotal.add(typeWeight.multiply(weight)));
            timeTotal.set(nextTime);

            pointsWeight.at(addr).get(nextTime).bias = weight;
        }

        if (timeSum.get(gaugeType.intValue()).equals(BigInteger.ZERO)) {
            timeSum.set(gaugeType.intValue(), nextTime);
        }
        timeWeight.set(addr, nextTime);

        NewGauge(addr, gaugeType, weight);
    }

    /**
     * Checkpoint to fill data common for all gauges
     */
    public void checkpoint() {
        getTotal();
    }

    /**
     * Checkpoint to fill data for both a specific gauge and common for all gauges
     *
     * @param addr Gauge address
     */
    public void checkpointGauge(Address addr) {
        getWeight(addr);
        getTotal();
    }

    /**
     * Get Gauge relative weight (not more than 1.0) normalized to 1e18 (e.g. 1.0 = 1e18). Inflation which will be
     * received by it is inflation_rate * relative_weight / 1e18
     *
     * @param addr Gauge address
     * @param time Relative weight at the specified timestamp in the past or present
     * @return Value of relative weight normalized to 1e18
     */
    private BigInteger gaugeRelativeWeight(Address addr, BigInteger time) {
        BigInteger t = time.divide(WEEK).multiply(WEEK);
        BigInteger totalWeight = pointsTotal.getOrDefault(t, BigInteger.ZERO);

        if (totalWeight.compareTo(BigInteger.ZERO) <= 0) {
            return BigInteger.ZERO;
        }

        BigInteger gaugeType = gaugeTypes.get(addr).subtract(BigInteger.ONE);
        BigInteger typeWeight = pointsTypeWeight.at(gaugeType).get(t);
        BigInteger gaugeWeight = pointsWeight.at(addr).get(t).bias;
        return EXA.multiply(typeWeight).multiply(gaugeWeight).divide(totalWeight);
    }

    /**
     * Get Gauge relative weight (not more than 1.0) normalized to 1e18 (e.g. 1.0 == 1e18). Inflation which will be
     * received by it is inflation_rate * relative_weight / 1e18
     *
     * @param addr Gauge address
     * @param time Relative weight at the specified timestamp in the past or present
     * @return Value of relative weight normalized to 1e18
     */
    public BigInteger gaugeRelativeWeightRead(Address addr, @Optional BigInteger time) {
        if (time.equals(BigInteger.ZERO)) {
            time = BigInteger.valueOf(Context.getBlockTimestamp());
        }
        return gaugeRelativeWeight(addr, time);
    }

    /**
     * Get gauge weight normalized to 1e18 and also fill all the unfilled values for type and gauge records
     * Any address can call, however nothing is recorded if the values are filled already
     *
     * @param addr Gauge address
     * @param time Relative weight at the specified timestamp in the past or present
     * @return Value of relative weight normalized to 1e18
     */
    public BigInteger gaugeRelativeWeightWrite(Address addr, @Optional BigInteger time) {
        if (time.equals(BigInteger.ZERO)) {
            time = BigInteger.valueOf(Context.getBlockTimestamp());
        }
        getWeight(addr);
        // Also calculates getSum
        getTotal();
        return gaugeRelativeWeight(addr, time);
    }

    /**
     * Change type weight
     *
     * @param typeId Type id
     * @param weight New type weight
     */
    private void changeTypeWeight(BigInteger typeId, BigInteger weight) {
        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());

        BigInteger oldWeight = getTypeWeight(typeId);
        BigInteger oldSum = getSum(typeId);
        BigInteger totalWeight = getTotal();
        BigInteger nextTime = blockTimestamp.add(WEEK).divide(WEEK).multiply(WEEK);

        totalWeight = totalWeight.add(oldSum.multiply(weight)).multiply(oldSum.multiply(oldWeight));
        pointsTotal.set(nextTime, totalWeight);
        pointsTypeWeight.at(typeId).set(nextTime, weight);
        timeTotal.set(nextTime);
        timeTypeWeight.set(typeId.intValue(), nextTime);

        NewTypeWeight(typeId, nextTime, weight, totalWeight);
    }

    /**
     * Add gauge type with name and weight
     *
     * @param name   Name of gauge type
     * @param weight Weight of gauge type
     */
    public void addType(String name, @Optional BigInteger weight) {

        Context.require(Context.getCaller().equals(admin.get()));
        BigInteger typeId = this.numberOfGaugeTypes.get();
        gaugeTypeNames.set(typeId, name);
        numberOfGaugeTypes.set(typeId.add(BigInteger.ONE));
        if (!weight.equals(BigInteger.ZERO)) {
            changeTypeWeight(typeId, weight);
            AddType(name, typeId);
        }
    }

    /**
     * Change gauge type typeId weight to weight
     *
     * @param typeId Gauge type id
     * @param weight New gauge weight
     */
    public void changeTypeWeightExternal(BigInteger typeId, BigInteger weight) {
        Context.require(Context.getCaller().equals(admin.get()));
        changeTypeWeight(typeId, weight);
    }

    /**
     * Change gauge weight
     * Only needed when testing in reality
     */
    public void changeGaugeWeight(Address addr, BigInteger weight) {
        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());

        BigInteger gaugeType = gaugeTypes.get(addr).subtract(BigInteger.ONE);
        BigInteger oldGaugeWeight = getWeight(addr);
        BigInteger typeWeight = getTypeWeight(gaugeType);
        BigInteger oldSum = getSum(gaugeType);
        BigInteger totalWeight = getTotal();
        BigInteger nextTime = blockTimestamp.add(WEEK).divide(WEEK).multiply(WEEK);

        pointsWeight.at(addr).get(nextTime).bias = weight;
        timeWeight.set(addr, nextTime);

        BigInteger newSum = oldSum.add(weight).subtract(oldGaugeWeight);
        pointsSum.at(gaugeType).get(nextTime).bias = newSum;
        timeSum.set(gaugeType.intValue(), nextTime);

        totalWeight = totalWeight.add(newSum.multiply(typeWeight)).subtract(oldSum.multiply(typeWeight));
        pointsTotal.set(nextTime, totalWeight);
        timeTotal.set(nextTime);

        NewGaugeWeight(addr, blockTimestamp, weight, totalWeight);
    }

    /**
     * Change weight of gauge addr to weight
     *
     * @param addr   Gauge contract address
     * @param weight New Gauge weight
     */
    public void changeGaugeWeightExternal(Address addr, BigInteger weight) {
        Context.require(Context.getCaller().equals(admin.get()));
        changeGaugeWeight(addr, weight);
    }

    /**
     * Allocate voting power for changing pool weights
     *
     * @param gaugeAddr  Gauge which caller votes for
     * @param userWeight Weight for a gauge in bps (unit of 0.01%). Minimal if 0.01%. Ignored if 0
     */
    public void voteForGaugeWeights(Address gaugeAddr, BigInteger userWeight) {
        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());

        Address boostedBaln = this.boostedBalnToken.get();
        BigInteger slope = Context.call(BigInteger.class, boostedBaln, "getLastUserSlope", Context.getCaller());
        BigInteger lockEnd = Context.call(BigInteger.class, boostedBaln, "getLockedEnd", Context.getCaller());
        BigInteger numberOfGauges = this.numberOfGauges.get();
        BigInteger nextTime = blockTimestamp.add(WEEK).divide(WEEK).multiply(WEEK);

        Context.require(lockEnd.compareTo(nextTime) > 0, "Your token lock expires too soon");
        Context.require(userWeight.compareTo(BigInteger.ZERO) >= 0 && userWeight.compareTo(POINTS) <= 0, "You used " +
                "all your voting power");
        Context.require(blockTimestamp.compareTo(lastUserVote.at(Context.getCaller()).getOrDefault(gaugeAddr,
                BigInteger.ZERO).add(WEIGHT_VOTE_DELAY)) >= 0, "Cannot vote so often");

        BigInteger gaugeType = gaugeTypes.get(gaugeAddr).subtract(BigInteger.ONE);
        Context.require(gaugeType.compareTo(BigInteger.ZERO) >= 0, "Gauge not added");

        // Prepare slopes and biases in memory
        VotedSlope oldSlope = voteUserSlopes.at(Context.getCaller()).get(gaugeAddr);
        BigInteger oldDt = BigInteger.ZERO;

        if (oldSlope.end.compareTo(nextTime) > 0) {
            oldDt = oldSlope.end.subtract(nextTime);
        }
        BigInteger oldBias = oldSlope.slope.multiply(oldDt);
        VotedSlope newSlope = new VotedSlope(slope.multiply(userWeight).divide(POINTS), userWeight, lockEnd);

        // Raises when expired
        BigInteger newDt = lockEnd.subtract(nextTime);
        BigInteger newBias = newSlope.slope.multiply(newDt);

        // Check and update powers (weights) used
        BigInteger powerUsed = voteUserPower.get(Context.getCaller());
        powerUsed = powerUsed.add(newSlope.power).subtract(oldSlope.power);
        voteUserPower.set(Context.getCaller(), powerUsed);
        Context.require(powerUsed.compareTo(BigInteger.ZERO) >= 0 && powerUsed.compareTo(POINTS) <= 0, "Used too much" +
                " power");

        // Remove old and schedule new slope changes
        // Remove slope changes for old slopes
        // Schedule recording of initial slope for next time
        BigInteger oldWeightBias = getWeight(gaugeAddr);
        BigInteger oldWeightSlope = pointsWeight.at(gaugeAddr).get(nextTime).slope;
        BigInteger oldSumBias = getSum(gaugeType);
        BigInteger oldSumSlope = pointsSum.at(gaugeType).get(nextTime).slope;

        pointsWeight.at(gaugeAddr).get(nextTime).bias = oldWeightBias.add(newBias).max(oldBias).subtract(oldBias);
        pointsSum.at(gaugeType).get(nextTime).bias = oldSumBias.add(newBias).max(oldBias).subtract(oldBias);

        if (oldSlope.end.compareTo(nextTime) > 0) {
            pointsWeight.at(gaugeAddr).get(nextTime).slope =
                    oldWeightSlope.add(newSlope.slope).max(oldSlope.slope).subtract(oldSlope.slope);
            pointsSum.at(gaugeType).get(nextTime).slope =
                    oldSumSlope.add(newSlope.slope).max(oldSlope.slope).subtract(oldSlope.slope);
        } else {
            DictDB<BigInteger, Point> pointsWeight = this.pointsWeight.at(gaugeAddr);
            pointsWeight.get(nextTime).slope = pointsWeight.get(nextTime).slope.add(newSlope.slope);
            DictDB<BigInteger, Point> pointsSum = this.pointsSum.at(gaugeType);
            pointsSum.get(nextTime).slope = pointsSum.get(nextTime).slope.add(newSlope.slope);
        }

        DictDB<BigInteger, BigInteger> changesWeight = this.changesWeight.at(gaugeAddr);
        DictDB<BigInteger, BigInteger> changesSum = this.changesSum.at(gaugeType);
        if (oldSlope.end.compareTo(blockTimestamp) > 0) {
            // Cancel old slope changes if the still didn't happen
            changesWeight.set(newSlope.end,
                    changesWeight.getOrDefault(newSlope.end, BigInteger.ZERO).subtract(newSlope.slope));
            changesSum.set(newSlope.end,
                    changesSum.getOrDefault(newSlope.end, BigInteger.ZERO).subtract(newSlope.slope));
        }

        // Add slope changes for new slope
        changesWeight.set(newSlope.end, changesWeight.getOrDefault(newSlope.end, BigInteger.ZERO).add(newSlope.slope));
        changesSum.set(newSlope.end, changesSum.getOrDefault(newSlope.end, BigInteger.ZERO).add(newSlope.slope));

        getTotal();
        voteUserSlopes.at(Context.getCaller()).set(gaugeAddr, newSlope);

        // Record last action time
        lastUserVote.at(Context.getCaller()).set(gaugeAddr, blockTimestamp);

        VoteForGauge(blockTimestamp, Context.getCaller(), gaugeAddr, userWeight);
    }

    /**
     * Get current gauge weight
     *
     * @param addr Gauge address
     * @return Gauge weight
     */
    public BigInteger getGaugeWeight(Address addr) {
        return pointsWeight.at(addr).get(timeWeight.get(addr)).bias;
    }

    /**
     * Get current type weight
     *
     * @param typeId Type id
     * @return Type weight
     */
    public BigInteger getTypeWeightExternal(BigInteger typeId) {
        return pointsTypeWeight.at(typeId).get(timeTypeWeight.get(typeId.intValue()));
    }

    /**
     * Get current total (type-weighted) weight
     *
     * @return Total weight
     */
    public BigInteger getTotalWeight() {
        return pointsTotal.getOrDefault(timeTotal.get(), BigInteger.ZERO);
    }

    /**
     * Get sum of gauge weights per type
     *
     * @param typeId Type id
     * @return Sum of gauge weights
     */
    public BigInteger getWeightsSumPerType(BigInteger typeId) {
        return pointsSum.at(typeId).get(timeSum.get(typeId.intValue())).bias;
    }
}
