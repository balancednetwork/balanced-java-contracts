/*
 * Copyright (c) 2021-2021 Balanced.network.
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

package network.balanced.score.tokens;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import network.balanced.score.tokens.utils.MathUtils;
import score.*;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;
import java.util.Map;

public class BoostedBaln {
    public static final BigInteger MAXTIME = BigInteger.valueOf(4L).multiply(TimeConstants.YEAR);
    private static final BigInteger MULTIPLIER = pow10(18);

    private static final int DEPOSIT_FOR_TYPE = 0;
    private static final int CREATE_LOCK_TYPE = 1;
    private static final int INCREASE_LOCK_AMOUNT = 2;
    private static final int INCREASE_UNLOCK_TIME = 3;

    private final String name;
    private final String symbol;
    private final int decimals;

    private final NonReentrant nonReentrant = new NonReentrant("Boosted_Baln_Reentrancy");

    private final Address tokenAddress;
    private final VarDB<BigInteger> supply = Context.newVarDB("Boosted_Baln_Supply", BigInteger.class);

    private final DictDB<Address, byte[]> locked = Context.newDictDB("Boosted_Baln_locked", byte[].class);

    private final VarDB<BigInteger> epoch = Context.newVarDB("Boosted_Baln_epoch", BigInteger.class);
    private final DictDB<BigInteger, byte[]> pointHistory = Context.newDictDB("Boosted_baln_point_history",
            byte[].class);
    private final BranchDB<Address, DictDB<BigInteger, byte[]>> userPointHistory = Context.newBranchDB(
            "Boosted_baln_user_point_history", byte[].class);
    private final DictDB<Address, BigInteger> userPointEpoch = Context.newDictDB("Boosted_Baln_user_point_epoch",
            BigInteger.class);
    private final DictDB<BigInteger, BigInteger> slopeChanges = Context.newDictDB("Boosted_Baln_slope_changes",
            BigInteger.class);

    private final VarDB<Address> admin = Context.newVarDB("Boosted_Baln_admin", Address.class);
    private final VarDB<Address> futureAdmin = Context.newVarDB("Boosted_baln_future_admin", Address.class);

    @EventLog
    public void CommitOwnership(Address admin) {
    }

    @EventLog
    public void ApplyOwnership(Address admin) {
    }

    @EventLog(indexed = 2)
    public void Deposit(Address provider, BigInteger locktime, BigInteger value, int type, BigInteger timestamp) {
    }

    @EventLog(indexed = 1)
    public void Withdraw(Address provider, BigInteger value, BigInteger timestamp) {
    }

    @EventLog
    public void Supply(BigInteger prevSupply, BigInteger supply) {
    }

    public BoostedBaln(Address tokenAddress, String name, String symbol) {
        this.admin.set(Context.getCaller());
        this.tokenAddress = tokenAddress;

        Point point = new Point();
        point.block = BigInteger.valueOf(Context.getBlockHeight());
        point.timestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        this.pointHistory.set(BigInteger.ZERO, point.toByteArray());

        this.decimals = ((BigInteger) Context.call(tokenAddress, "decimals", new Object[0])).intValue();
        this.name = name;
        this.symbol = symbol;
        Context.require(this.decimals <= 72, "Decimals should be less than 72");

        if (this.supply.get() == null) {
            this.supply.set(BigInteger.ZERO);
        }

        if (this.epoch.get() == null) {
            this.epoch.set(BigInteger.ZERO);
        }
    }

    private static BigInteger pow10(int exponent) {
        BigInteger result = BigInteger.ONE;
        for (int i = 0; i < exponent; i++) {
            result = result.multiply(BigInteger.TEN);
        }
        return result;
    }

    @External
    public void commitTransferOwnership(Address address) {
        ownerRequired("Commit Transfer Ownership");
        futureAdmin.set(address);
        CommitOwnership(address);
    }

    @External
    public void applyTransferOwnership() {
        ownerRequired("Apply transfer ownership");
        Address futureAdmin = this.futureAdmin.get();
        Context.require(futureAdmin != null, "Apply transfer ownership: Admin not set");
        this.admin.set(futureAdmin);
        this.futureAdmin.set(null);
        ApplyOwnership(futureAdmin);
    }

    @External(readonly = true)
    public Map<String, BigInteger> getLocked(Address _owner) {
        byte[] bytes = locked.get(_owner);
        LockedBalance balance = LockedBalance.toLockedBalance(bytes);
        return Map.of("amount", balance.amount, "end", balance.end);
    }

    @External(readonly = true)
    public BigInteger getLastUserSlope(Address address) {
        BigInteger userPointEpoch = this.userPointEpoch.get(address);
        return Point.toPoint((this.userPointHistory.at(address)).get(userPointEpoch)).slope;
    }

    @External(readonly = true)
    public BigInteger userPointHistoryTimestamp(Address address, BigInteger index) {
        return Point.toPoint((this.userPointHistory.at(address)).get(index)).timestamp;
    }

    @External(readonly = true)
    public BigInteger lockedEnd(Address address) {
        return LockedBalance.toLockedBalance(this.locked.get(address)).end;
    }

    private void checkpoint(Address address, LockedBalance oldLocked, LockedBalance newLocked) {
        Point uOld = new Point();
        Point uNew = new Point();
        BigInteger oldDSlope = BigInteger.ZERO;
        BigInteger newDSlope = BigInteger.ZERO;
        BigInteger epoch = this.epoch.get();

        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        BigInteger blockHeight = BigInteger.valueOf(Context.getBlockHeight());

        if (!address.equals(Constants.ZERO_ADDRESS)) {
            //            Calculate slopes and biases
            //            Kept at zero when they have to
            if (oldLocked.end.compareTo(blockTimestamp) > 0 && oldLocked.amount.compareTo(BigInteger.ZERO) > 0) {
                uOld.slope = oldLocked.amount.divide(MAXTIME);
                BigInteger delta = MathUtils.safeSubtract(oldLocked.end, blockTimestamp);
                uOld.bias = uOld.slope.multiply(delta);
            }

            if (newLocked.end.compareTo(blockTimestamp) > 0 && newLocked.amount.compareTo(BigInteger.ZERO) > 0) {
                uNew.slope = newLocked.amount.divide(MAXTIME);
                BigInteger delta = MathUtils.safeSubtract(newLocked.end, blockTimestamp);
                uNew.bias = uNew.slope.multiply(delta);
            }

            //          Read values of scheduled changes in the slope
            //          oldLocked.end can be in the past and in the future
            //          newLocked.end can ONLY be in the FUTURE unless everything expired: than zeros
            oldDSlope = this.slopeChanges.getOrDefault(oldLocked.end, BigInteger.ZERO);
            if (!newLocked.end.equals(BigInteger.ZERO)) {
                if (newLocked.end.equals(oldLocked.end)) {
                    newDSlope = oldDSlope;
                } else {
                    newDSlope = this.slopeChanges.getOrDefault(newLocked.end, BigInteger.ZERO);
                }
            }
        }

        Point lastPoint = new Point(BigInteger.ZERO, BigInteger.ZERO, blockTimestamp, blockHeight);
        if (epoch.compareTo(BigInteger.ZERO) > 0) {
            lastPoint = Point.toPoint(this.pointHistory.get(epoch));
        }
        BigInteger lastCheckPoint = lastPoint.timestamp;

        //      initialLastPoint is used for extrapolation to calculate block number
        //      (approximately, for *At methods) and save them
        //      as we cannot figure that out exactly from inside the contract
        Point initialLastPoint = lastPoint.newPoint();
        BigInteger blockSlope = BigInteger.ZERO;
        if (blockTimestamp.compareTo(lastPoint.timestamp) > 0) {
            blockSlope = MULTIPLIER.multiply(blockHeight.subtract(lastPoint.block))
                                   .divide(blockTimestamp.subtract(lastPoint.timestamp));
            //          If last point is already recorded in this block, slope = 0
            //          But that's ok because we know the block in such case
        }

        //      Go over week's to fill history and calculate what the current point is
        BigInteger timeIterator = lastCheckPoint.divide(TimeConstants.WEEK).multiply(TimeConstants.WEEK);

        for (int index = 0; index < 255; ++index) {
            timeIterator = timeIterator.add(TimeConstants.WEEK);
            BigInteger dSlope = BigInteger.ZERO;
            if (timeIterator.compareTo(blockTimestamp) > 0) {
                timeIterator = blockTimestamp;
            } else {
                dSlope = this.slopeChanges.getOrDefault(timeIterator, BigInteger.ZERO);
            }

            lastPoint.bias = lastPoint.bias.subtract(lastPoint.slope.multiply(timeIterator.subtract(lastCheckPoint)));
            lastPoint.slope = lastPoint.slope.add(dSlope);

            if (lastPoint.bias.compareTo(BigInteger.ZERO) < 0) {
                lastPoint.bias = BigInteger.ZERO;
            }

            if (lastPoint.slope.compareTo(BigInteger.ZERO) < 0) {
                lastPoint.slope = BigInteger.ZERO;
            }

            lastCheckPoint = timeIterator;
            lastPoint.timestamp = timeIterator;
            BigInteger dtime = timeIterator.subtract(initialLastPoint.timestamp);
            lastPoint.block = initialLastPoint.block.add(blockSlope.multiply(dtime).divide(MULTIPLIER));
            epoch = epoch.add(BigInteger.ONE);

            if (timeIterator.equals(blockTimestamp)) {
                lastPoint.block = blockHeight;
                break;
            } else {
                pointHistory.set(epoch, lastPoint.toByteArray());
            }
        }

        this.epoch.set(epoch);
        if (!address.equals(Constants.ZERO_ADDRESS)) {
            lastPoint.slope = lastPoint.slope.add(uNew.slope.subtract(uOld.slope));
            lastPoint.bias = lastPoint.bias.add(uNew.bias.subtract(uOld.bias));

            if (lastPoint.slope.compareTo(BigInteger.ZERO) < 0) {
                lastPoint.slope = BigInteger.ZERO;
            }
            if (lastPoint.bias.compareTo(BigInteger.ZERO) < 0) {
                lastPoint.bias = BigInteger.ZERO;
            }
        }

        this.pointHistory.set(epoch, lastPoint.toByteArray());

        if (!address.equals(Constants.ZERO_ADDRESS)) {
            if (oldLocked.end.compareTo(blockTimestamp) > 0) {
                oldDSlope = oldDSlope.add(uOld.slope);
                if (newLocked.end.equals(oldLocked.end)) {
                    oldDSlope = oldDSlope.subtract(uNew.slope);
                }
                this.slopeChanges.set(oldLocked.end, oldDSlope);
            }

            if (newLocked.end.compareTo(blockTimestamp) > 0 && newLocked.end.compareTo(oldLocked.end) > 0) {
                newDSlope = newDSlope.subtract(uNew.slope);
                this.slopeChanges.set(newLocked.end, newDSlope);
            }

            BigInteger userEpoch = this.userPointEpoch.getOrDefault(address, BigInteger.ZERO).add(BigInteger.ONE);
            this.userPointEpoch.set(address, userEpoch);
            uNew.timestamp = blockTimestamp;
            uNew.block = blockHeight;
            this.userPointHistory.at(address).set(userEpoch, uNew.toByteArray());
        }
    }

    private void depositFor(Address address, BigInteger value, BigInteger unlockTime, LockedBalance lockedBalance,
                            int type) {
        LockedBalance locked = lockedBalance.newLockedBalance();
        BigInteger supplyBefore = this.supply.get();
        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());

        this.supply.set(supplyBefore.add(value));
        LockedBalance oldLocked = locked.newLockedBalance();

        locked.amount = locked.amount.add(value);
        if (!unlockTime.equals(BigInteger.ZERO)) {
            locked.end = unlockTime;
        }

        this.locked.set(address, locked.toByteArray());
        this.checkpoint(address, oldLocked, locked);

        Deposit(address, value, locked.end, type, blockTimestamp);
        Supply(supplyBefore, supplyBefore.add(value));
    }

    @External
    public void checkpoint() {
        this.checkpoint(Constants.ZERO_ADDRESS, new LockedBalance(), new LockedBalance());
    }

    private void depositFor(Address address, BigInteger value) {
        this.nonReentrant.updateLock(true);
        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        LockedBalance locked = LockedBalance.toLockedBalance(this.locked.get(address));

        Context.require(value.compareTo(BigInteger.ZERO) > 0, "Deposit for: Need non zero value");
        Context.require(locked.amount.compareTo(BigInteger.ZERO) > 0, "Deposit for: No existing lock found");
        Context.require(locked.end.compareTo(blockTimestamp) > 0, "Deposit for: Cannot add to expired lock. Withdraw");

        this.depositFor(address, value, BigInteger.ZERO, locked, DEPOSIT_FOR_TYPE);
        this.nonReentrant.updateLock(false);
    }

    private void assertNotContract(Address address) {
        Context.require(!address.isContract(), "Assert Not contract: Smart contract depositors not allowed");
    }

    private void createLock(Address sender, BigInteger value, BigInteger unlockTime) {
        this.nonReentrant.updateLock(true);
        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        this.assertNotContract(sender);

        unlockTime = unlockTime.divide(TimeConstants.WEEK).multiply(TimeConstants.WEEK);
        LockedBalance locked = LockedBalance.toLockedBalance(this.locked.get(sender));

        Context.require(value.compareTo(BigInteger.ZERO) > 0, "Create Lock: Need non zero value");
        Context.require(locked.amount.equals(BigInteger.ZERO), "Create Lock: Withdraw old tokens first");
        Context.require(unlockTime.compareTo(blockTimestamp) > 0, "Create Lock: Can only lock until time in the " +
                "future");
        Context.require(unlockTime.compareTo(blockTimestamp.add(MAXTIME)) <= 0,
                "Create Lock: Voting Lock can be 4 " + "years max");

        this.depositFor(sender, value, unlockTime, locked, CREATE_LOCK_TYPE);
        this.nonReentrant.updateLock(false);
    }

    private void increaseAmount(Address sender, BigInteger value) {
        this.nonReentrant.updateLock(true);
        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());

        this.assertNotContract(sender);
        LockedBalance locked = LockedBalance.toLockedBalance(this.locked.get(sender));

        Context.require(value.compareTo(BigInteger.ZERO) > 0, "Increase amount: Need non zero value");
        Context.require(locked.amount.compareTo(BigInteger.ZERO) > 0, "Increase amount: No existing lock found");
        Context.require(locked.end.compareTo(blockTimestamp) > 0, "Increase amount: Cannot add to expired lock. " +
                "Withdraw");

        this.depositFor(sender, value, BigInteger.ZERO, locked, INCREASE_LOCK_AMOUNT);
        this.nonReentrant.updateLock(false);
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        Address token = Context.getCaller();
        Context.require(token.equals(this.tokenAddress), "Token Fallback: Only BALN deposits are allowed");

        Context.require(_value.signum() > 0, "Token Fallback: Token value should be a positive number");
        String unpackedData = new String(_data);
        Context.require(!unpackedData.equals(""), "Token Fallback: Data can't be empty");

        JsonObject json = Json.parse(unpackedData).asObject();

        String method = json.get("method").asString();
        JsonObject params = json.get("params").asObject();

        switch (method) {
            case "increaseAmount":
                this.increaseAmount(_from, _value);
                break;
            case "createLock":
                BigInteger unlockTime = BigInteger.valueOf(params.get("unlockTime").asLong());
                this.createLock(_from, _value, unlockTime);
                break;
            case "depositFor":
                Address sender = Address.fromString(params.get("address").asString());
                this.depositFor(sender, _value);
                break;
            default:
                Context.revert("Token fallback: Unimplemented tokenfallback action");
                break;
        }
    }

    @External
    public void increaseUnlockTime(BigInteger unlockTime) {
        this.nonReentrant.updateLock(true);
        Address sender = Context.getCaller();
        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());

        this.assertNotContract(sender);
        LockedBalance locked = LockedBalance.toLockedBalance(this.locked.get(sender));
        unlockTime = unlockTime.divide(TimeConstants.WEEK).multiply(TimeConstants.WEEK);

        Context.require(locked.amount.compareTo(BigInteger.ZERO) > 0, "Increase unlock time: Nothing is locked");
        Context.require(locked.end.compareTo(blockTimestamp) > 0, "Increase unlock time: Lock expired");
        Context.require(unlockTime.compareTo(locked.end) > 0, "Increase unlock time: Can only increase lock duration");
        Context.require(unlockTime.compareTo(blockTimestamp.add(MAXTIME)) <= 0,
                "Increase unlock time: Voting lock " + "can be 4 years max");

        this.depositFor(sender, BigInteger.ZERO, unlockTime, locked, INCREASE_UNLOCK_TIME);
        this.nonReentrant.updateLock(false);
    }

    @External
    public void withdraw() {
        this.nonReentrant.updateLock(true);
        Address sender = Context.getCaller();
        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());

        LockedBalance locked = LockedBalance.toLockedBalance(this.locked.get(sender));
        Context.require(blockTimestamp.compareTo(locked.end) >= 0, "Withdraw: The lock didn't expire");
        BigInteger value = locked.amount;

        LockedBalance oldLocked = locked.newLockedBalance();
        locked.end = BigInteger.ZERO;
        locked.amount = BigInteger.ZERO;
        this.locked.set(sender, locked.toByteArray());
        BigInteger supplyBefore = this.supply.get();
        this.supply.set(supplyBefore.subtract(value));

        this.checkpoint(sender, oldLocked, locked);
        Context.call(this.tokenAddress, "transfer", sender, value, "withdraw".getBytes());

        Withdraw(sender, value, blockTimestamp);
        Supply(supplyBefore, supplyBefore.subtract(value));
        this.nonReentrant.updateLock(false);
    }

    private BigInteger findBlockEpoch(BigInteger block, BigInteger maxEpoch) {
        BigInteger min = BigInteger.ZERO;
        BigInteger max = maxEpoch;

        for (int index = 0; index < 256 && min.compareTo(max) < 0; ++index) {
            BigInteger mid = min.add(max).add(BigInteger.ONE).divide(BigInteger.TWO);
            if (Point.toPoint(this.pointHistory.get(mid)).block.compareTo(block) <= 0) {
                min = mid;
            } else {
                max = mid.subtract(BigInteger.ONE);
            }
        }

        return min;
    }

    private BigInteger findUserPointHistory(Address address, BigInteger block) {
        BigInteger min = BigInteger.ZERO;
        BigInteger max = this.userPointEpoch.getOrDefault(address, BigInteger.ZERO);

        for (int index = 0; index < 256 && min.compareTo(max) < 0; ++index) {
            BigInteger mid = min.add(max).add(BigInteger.ONE).divide(BigInteger.TWO);
            if (Point.toPoint((this.userPointHistory.at(address)).get(mid)).block.compareTo(block) <= 0) {
                min = mid;
            } else {
                max = mid.subtract(BigInteger.ONE);
            }
        }
        return min;
    }

    @External(readonly = true)
    public BigInteger balanceOf(Address address, @Optional BigInteger timestamp) {
        BigInteger blockTimestamp;
        if (timestamp.equals(BigInteger.ZERO)) {
            blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());
            timestamp = blockTimestamp;
        }

        BigInteger epoch = this.userPointEpoch.getOrDefault(address, BigInteger.ZERO);
        if (epoch.equals(BigInteger.ZERO)) {
            return BigInteger.ZERO;
        } else {
            Point lastPoint = Point.toPoint(this.userPointHistory.at(address).get(epoch));
            BigInteger _delta = MathUtils.safeSubtract(timestamp, lastPoint.timestamp);
            lastPoint.bias = lastPoint.bias.subtract(lastPoint.slope.multiply(_delta));
            if (lastPoint.bias.compareTo(BigInteger.ZERO) < 0) {
                lastPoint.bias = BigInteger.ZERO;
            }

            return lastPoint.bias;
        }
    }

    @External(readonly = true)
    public BigInteger balanceOfAt(Address address, BigInteger block) {
        BigInteger blockHeight = BigInteger.valueOf(Context.getBlockHeight());
        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());

        Context.require(block.compareTo(blockHeight) <= 0, "BalanceOfAt: Invalid given block height");
        BigInteger userEpoch = this.findUserPointHistory(address, block);
        Point uPoint = Point.toPoint(this.userPointHistory.at(address).get(userEpoch));

        BigInteger maxEpoch = this.epoch.get();
        BigInteger epoch = this.findBlockEpoch(block, maxEpoch);
        Point point0 = Point.toPoint(this.pointHistory.get(epoch));
        BigInteger dBlock;
        BigInteger dTime;

        if (epoch.compareTo(maxEpoch) < 0) {
            Point point1 = Point.toPoint(this.pointHistory.get(epoch.add(BigInteger.ONE)));
            dBlock = point1.block.subtract(point0.block);
            dTime = point1.timestamp.subtract(point0.timestamp);
        } else {
            dBlock = blockHeight.subtract(point0.block);
            dTime = blockTimestamp.subtract(point0.timestamp);
        }

        BigInteger blockTime = point0.timestamp;
        if (!dBlock.equals(BigInteger.ZERO)) {
            blockTime = blockTime.add(dTime.multiply(block.subtract(point0.block)).divide(dBlock));
        }
        BigInteger delta = MathUtils.safeSubtract(blockTime, uPoint.timestamp);
        uPoint.bias = uPoint.bias.subtract(uPoint.slope.multiply(delta));
        return uPoint.bias.compareTo(BigInteger.ZERO) >= 0 ? uPoint.bias : BigInteger.ZERO;
    }

    private BigInteger supplyAt(Point point, BigInteger time) {
        Point lastPoint = point.newPoint();
        BigInteger timestampIterator = lastPoint.timestamp.divide(TimeConstants.WEEK).multiply(TimeConstants.WEEK);

        for (int index = 0; index < 255; ++index) {
            timestampIterator = timestampIterator.add(TimeConstants.WEEK);
            BigInteger dSlope = BigInteger.ZERO;
            if (timestampIterator.compareTo(time) > 0) {
                timestampIterator = time;
            } else {
                dSlope = this.slopeChanges.getOrDefault(timestampIterator, BigInteger.ZERO);
            }
            BigInteger delta = MathUtils.safeSubtract(timestampIterator, lastPoint.timestamp);
            lastPoint.bias = lastPoint.bias.subtract(lastPoint.slope.multiply(delta));
            if (timestampIterator.equals(time)) {
                break;
            }

            lastPoint.slope = lastPoint.slope.add(dSlope);
            lastPoint.timestamp = timestampIterator;
        }

        if (lastPoint.bias.compareTo(BigInteger.ZERO) < 0) {
            lastPoint.bias = BigInteger.ZERO;
        }
        return lastPoint.bias;
    }

    @External(readonly = true)
    public BigInteger totalSupply(@Optional BigInteger time) {
        BigInteger blockTimestamp;
        if (time.equals(BigInteger.ZERO)) {
            blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());
            time = blockTimestamp;
        }

        BigInteger epoch = this.epoch.get();
        Point lastPoint = Point.toPoint(this.pointHistory.get(epoch));
        return this.supplyAt(lastPoint, time);
    }

    @External(readonly = true)
    public BigInteger totalSupplyAt(BigInteger block) {
        BigInteger blockHeight = BigInteger.valueOf(Context.getBlockHeight());
        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());

        Context.require(block.compareTo(blockHeight) <= 0, "TotalSupplyAt: Invalid given block height");
        BigInteger epoch = this.epoch.get();
        BigInteger targetEpoch = findBlockEpoch(block, epoch);

        Point point = Point.toPoint(this.pointHistory.get(targetEpoch));
        BigInteger dTime = BigInteger.ZERO;
        if (targetEpoch.compareTo(epoch) < 0) {
            Point pointNext = Point.toPoint(this.pointHistory.get(targetEpoch.add(BigInteger.ONE)));
            if (!point.block.equals(pointNext.block)) {
                dTime = block.subtract(point.block)
                             .multiply(pointNext.timestamp.subtract(point.timestamp))
                             .divide(pointNext.block.subtract(point.block));
            }
        } else {
            if (!point.block.equals(blockHeight)) {
                dTime = block.subtract(point.block)
                             .multiply(blockTimestamp.subtract(point.timestamp))
                             .divide(blockHeight.subtract(point.block));
            }
        }

        return this.supplyAt(point, point.timestamp.add(dTime));
    }

    @External(readonly = true)
    public Address admin() {
        return this.admin.get();
    }

    @External(readonly = true)
    public Address futureAdmin() {
        return this.futureAdmin.get();
    }

    @External(readonly = true)
    public String name() {
        return this.name;
    }

    @External(readonly = true)
    public String symbol() {
        return this.symbol;
    }

    @External(readonly = true)
    public BigInteger userPointEpoch(Address address) {
        return this.userPointEpoch.getOrDefault(address, BigInteger.ZERO);
    }

    @External(readonly = true)
    public int decimals() {
        return this.decimals;
    }

    private void ownerRequired(String method) {
        Context.require(Context.getCaller() == this.admin.get(), method + " :: Only admin can call this method");
    }
}

