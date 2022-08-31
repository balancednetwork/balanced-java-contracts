/*
 * Copyright (c) 2021-2022 Balanced.network.
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
import network.balanced.score.tokens.db.LockedBalance;
import network.balanced.score.tokens.db.Point;
import network.balanced.score.lib.structs.SupplyDetails;
import network.balanced.score.tokens.utils.UnsignedBigInteger;
import score.Address;
import score.Context;
import score.UserRevertedException;
import score.annotation.External;
import score.annotation.Optional;
import scorex.util.ArrayList;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.lib.utils.Constants.EOA_ZERO;
import static network.balanced.score.lib.utils.Math.convertToNumber;
import static network.balanced.score.tokens.Constants.WEEK_IN_MICRO_SECONDS;

public class BoostedBalnImpl extends AbstractBoostedBaln {

    public BoostedBalnImpl(Address tokenAddress, Address rewardAddress, Address dividendsAddress,  String name, String symbol) {
        super(tokenAddress, rewardAddress, dividendsAddress,  name, symbol);
    }

    @External
    public void setMinimumLockingAmount(BigInteger value) {
        ownerRequired();
        Context.require(value.signum() > 0, "Invalid value for minimum locking amount");

        this.minimumLockingAmount.set(value);
    }

    @External(readonly = true)
    public BigInteger getMinimumLockingAmount() {
        return this.minimumLockingAmount.get();
    }

    @External
    public void setPenaltyAddress(Address penaltyAddress) {
        ownerRequired();
        this.penaltyAddress.set(penaltyAddress);
    }

    @External(readonly = true)
    public Address getPenaltyAddress() {
        return this.penaltyAddress.get();
    }

    @External
    public void commitTransferOwnership(Address address) {
        ownerRequired();
        futureAdmin.set(address);
        CommitOwnership(address);
    }

    @External
    public void applyTransferOwnership() {
        ownerRequired();
        Address futureAdmin = this.futureAdmin.get();
        Context.require(futureAdmin != null, "Apply transfer ownership: Admin not set");
        this.admin.set(futureAdmin);
        this.futureAdmin.set(null);
        ApplyOwnership(futureAdmin);
    }

    @External(readonly = true)
    public Map<String, BigInteger> getLocked(Address _owner) {
        LockedBalance balance = getLockedBalance(_owner);
        return Map.of("amount", balance.amount, "end", balance.getEnd());
    }

    @External(readonly = true)
    public BigInteger getTotalLocked() {
        return Context.call(BigInteger.class, this.tokenAddress.get(), "balanceOf", Context.getAddress());
    }

    @External(readonly = true)
    public List<Address> getUsers(int start, int end) {
        Context.require(end - start <= 100, "Get users :Fetch only 100 users at a time");

        List<Address> result = new ArrayList<>();
        int _end = Math.min(end, users.length());

        for (int index = start; index < _end; index++) {
            result.add(users.at(index));
        }
        return result;
    }

    @External(readonly = true)
    public int activeUsersCount() {
        return users.length();
    }

    @External(readonly = true)
    public boolean hasLocked(Address _owner) {
        return users.contains(_owner);
    }

    @External(readonly = true)
    public BigInteger getLastUserSlope(Address address) {
        BigInteger userPointEpoch = this.userPointEpoch.get(address);
        return getUserPointHistory(address, userPointEpoch).slope;
    }

    @External(readonly = true)
    public BigInteger userPointHistoryTimestamp(Address address, BigInteger index) {
        return getUserPointHistory(address, index).getTimestamp();
    }

    @External(readonly = true)
    public BigInteger lockedEnd(Address address) {
        return getLockedBalance(address).getEnd();
    }

    @External
    public void checkpoint() {
        this.checkpoint(EOA_ZERO, new LockedBalance(), new LockedBalance());
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        Address token = Context.getCaller();
        Context.require(token.equals(this.tokenAddress.get()), "Token Fallback: Only Baln deposits are allowed");

        Context.println("value is: " + _value);
        Context.require(_value.signum() > 0, "Token Fallback: Token value should be a positive number");
        String unpackedData = new String(_data);
        Context.require(!unpackedData.isEmpty(), "Token Fallback: Data can't be empty");

        JsonObject json = Json.parse(unpackedData).asObject();

        String method = json.get("method").asString();
        JsonObject params = json.get("params").asObject();
        BigInteger unlockTime = convertToNumber(params.get("unlockTime"), BigInteger.ZERO);

        switch (method) {
            case "increaseAmount":
                this.increaseAmount(_from, _value, unlockTime);
                break;
            case "createLock":
                BigInteger minimumLockingAmount = this.minimumLockingAmount.get();
                Context.require(_value.compareTo(minimumLockingAmount) >= 0, "insufficient locking amount. minimum " +
                        "amount is: " + minimumLockingAmount);
                this.createLock(_from, _value, unlockTime);
                break;
            default:
                throw new UserRevertedException("Token fallback: Unimplemented token fallback action");
        }
    }

    @External
    public void increaseUnlockTime(BigInteger unlockTime) {
        this.nonReentrant.updateLock(true);
        Address sender = Context.getCaller();
        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());

        this.assertNotContract(sender);
        LockedBalance locked = getLockedBalance(sender);
        unlockTime = unlockTime.divide(WEEK_IN_MICRO_SECONDS).multiply(WEEK_IN_MICRO_SECONDS);

        Context.require(locked.amount.compareTo(BigInteger.ZERO) > 0, "Increase unlock time: Nothing is locked");
        Context.require(locked.getEnd().compareTo(blockTimestamp) > 0, "Increase unlock time: Lock expired");
        Context.require(unlockTime.compareTo(locked.end.toBigInteger()) > 0, "Increase unlock time: Can only increase" +
                " lock duration");
        Context.require(unlockTime.compareTo(blockTimestamp.add(MAX_TIME)) <= 0, "Increase unlock time: Voting lock " +
                "can be 4 years max");

        this.depositFor(sender, BigInteger.ZERO, unlockTime, locked, INCREASE_UNLOCK_TIME);
        this.nonReentrant.updateLock(false);
    }

    @External
    public void kick(Address user) {
        BigInteger bBalnBalance = balanceOf(user, BigInteger.ZERO);
        Context.require(bBalnBalance.equals(BigInteger.ZERO), user + " BBaln locking has not expired");
        LockedBalance locked = getLockedBalance(user).newLockedBalance();
        BigInteger previousLockedAmount = locked.amount;
        onKick(user, previousLockedAmount, this.supply.get(), "User kicked".getBytes());
    }

    @External
    public void withdraw() {
        this.nonReentrant.updateLock(true);
        Address sender = Context.getCaller();
        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());

        LockedBalance locked = getLockedBalance(sender);
        //require(blockTimestamp.compareTo(locked.getEnd()) >= 0, "Withdraw: The lock haven't expire");
        BigInteger value = locked.amount;

        LockedBalance oldLocked = locked.newLockedBalance();
        locked.end = UnsignedBigInteger.ZERO;
        locked.amount = BigInteger.ZERO;
        this.locked.set(sender, locked);
        BigInteger supplyBefore = this.supply.get();
        BigInteger currentSupply = supplyBefore.subtract(value);
        this.supply.set(currentSupply);

        this.checkpoint(sender, oldLocked, locked);

        if (blockTimestamp.compareTo(oldLocked.getEnd()) < 0) {
            if (value.mod(BigInteger.TWO).equals(BigInteger.ZERO)) {
                value = value.divide(BigInteger.TWO);
            } else {
                value = value.divide(BigInteger.TWO).add(BigInteger.ONE);
            }
            Context.call(this.tokenAddress.get(), "transfer", this.penaltyAddress.get(), value, "withdraw".getBytes());
        }

        Context.call(this.tokenAddress.get(), "transfer", sender, value, "withdraw".getBytes());
        users.remove(sender);
        Withdraw(sender, value, blockTimestamp);
        Supply(supplyBefore, currentSupply);
        this.nonReentrant.updateLock(false);
        onBalanceUpdate(sender, oldLocked.amount, currentSupply);
    }

    @External(readonly = true)
    public BigInteger balanceOf(Address _owner, @Optional BigInteger timestamp) {
        UnsignedBigInteger uTimestamp;
        if (timestamp == null || timestamp.equals(BigInteger.ZERO)) {
            uTimestamp = UnsignedBigInteger.valueOf(Context.getBlockTimestamp());
        } else {
            uTimestamp = new UnsignedBigInteger(timestamp);
        }

        BigInteger epoch = this.userPointEpoch.getOrDefault(_owner, BigInteger.ZERO);
        if (epoch.equals(BigInteger.ZERO)) {
            return BigInteger.ZERO;
        } else {
            Point lastPoint = getUserPointHistory(_owner, epoch);
            UnsignedBigInteger _delta = uTimestamp.subtract(lastPoint.timestamp);
            return lastPoint.bias
                    .subtract(lastPoint.slope.multiply(_delta.toBigInteger()))
                    .max(BigInteger.ZERO);
        }
    }

    @External(readonly = true)
    public BigInteger balanceOfAt(Address _owner, BigInteger block) {
        UnsignedBigInteger blockHeight = UnsignedBigInteger.valueOf(Context.getBlockHeight());
        UnsignedBigInteger blockTimestamp = UnsignedBigInteger.valueOf(Context.getBlockTimestamp());

        Context.require(block.compareTo(blockHeight.toBigInteger()) <= 0, "BalanceOfAt: Invalid given block height");
        BigInteger userEpoch = this.findUserPointHistory(_owner, block);
        Point uPoint = this.userPointHistory.at(_owner).getOrDefault(userEpoch, new Point());

        BigInteger maxEpoch = this.epoch.get();
        BigInteger epoch = this.findBlockEpoch(block, maxEpoch);
        Point point0 = this.pointHistory.getOrDefault(epoch, new Point());
        UnsignedBigInteger dBlock;
        UnsignedBigInteger dTime;

        if (epoch.compareTo(maxEpoch) < 0) {
            Point point1 = this.pointHistory.getOrDefault(epoch.add(BigInteger.ONE), new Point());
            dBlock = point1.block.subtract(point0.block);
            dTime = point1.timestamp.subtract(point0.timestamp);
        } else {
            dBlock = blockHeight.subtract(point0.block);
            dTime = blockTimestamp.subtract(point0.timestamp);
        }

        UnsignedBigInteger blockTime = point0.timestamp;
        if (!dBlock.equals(UnsignedBigInteger.ZERO)) {
            blockTime = blockTime.add(dTime.multiply(new UnsignedBigInteger(block).subtract(point0.block))
                    .divide(dBlock));
        }
        UnsignedBigInteger delta = blockTime.subtract(uPoint.timestamp);
        return uPoint.bias.subtract(uPoint.slope.multiply(delta.toBigInteger())).max(BigInteger.ZERO);
    }

    @External(readonly = true)
    public BigInteger totalSupply(@Optional BigInteger time) {
        BigInteger blockTimestamp;
        if (time == null || time.equals(BigInteger.ZERO)) {
            blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());
            time = blockTimestamp;
        }

        BigInteger epoch = this.epoch.get();
        Point lastPoint = this.pointHistory.getOrDefault(epoch, new Point());
        return this.supplyAt(lastPoint, time);
    }

    @External(readonly = true)
    public BigInteger totalSupplyAt(BigInteger block) {
        UnsignedBigInteger blockHeight = UnsignedBigInteger.valueOf(Context.getBlockHeight());
        UnsignedBigInteger blockTimestamp = UnsignedBigInteger.valueOf(Context.getBlockTimestamp());
        UnsignedBigInteger uBlock = new UnsignedBigInteger(block);
        Context.require(uBlock.compareTo(blockHeight) <= 0, "TotalSupplyAt: Invalid given block height");
        BigInteger epoch = this.epoch.get();
        BigInteger targetEpoch = findBlockEpoch(block, epoch);

        Point point = this.pointHistory.getOrDefault(targetEpoch, new Point());
        UnsignedBigInteger dTime = UnsignedBigInteger.ZERO;
        if (targetEpoch.compareTo(epoch) < 0) {
            Point pointNext = this.pointHistory.getOrDefault(targetEpoch.add(BigInteger.ONE), new Point());
            if (!point.block.equals(pointNext.block)) {
                dTime = uBlock.subtract(point.block).multiply(pointNext.timestamp.subtract(point.timestamp))
                        .divide(pointNext.block.subtract(point.block));
            }
        } else {
            if (!point.block.equals(blockHeight)) {
                dTime = uBlock.subtract(point.block).multiply(blockTimestamp.subtract(point.timestamp))
                        .divide(blockHeight.subtract(point.block));
            }
        }

        return this.supplyAt(point, point.timestamp.add(dTime).toBigInteger());
    }

    @External(readonly = true)
    public SupplyDetails getPrincipalSupply(Address _user) {
        SupplyDetails response = new SupplyDetails();
        response.decimals = decimals();
        response.principalUserBalance = balanceOf(_user, BigInteger.ZERO);
        response.principalTotalSupply = totalSupply(BigInteger.ZERO);
        return response;
    }

    @External(readonly = true)
    public BigInteger userPointEpoch(Address address) {
        return this.userPointEpoch.getOrDefault(address, BigInteger.ZERO);
    }

    private void ownerRequired() {
        Context.require(Context.getCaller().equals(this.admin.get()), "Owner is required");
    }

}
