/*
 * Copyright (c) 2021-2023 Balanced.network.
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
import foundation.icon.xcall.NetworkAddress;
import network.balanced.score.lib.interfaces.BoostedBalnXCall;
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.utils.TokenTransfer;
import network.balanced.score.lib.utils.Versions;
import network.balanced.score.lib.utils.XCallUtils;
import network.balanced.score.tokens.db.LockedBalance;
import network.balanced.score.tokens.db.Point;
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

import static network.balanced.score.lib.utils.BalancedAddressManager.getBaln;
import static network.balanced.score.lib.utils.BalancedAddressManager.getXCall;
import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Constants.EOA_ZERO;
import static network.balanced.score.lib.utils.Math.convertToNumber;
import static network.balanced.score.lib.utils.NonReentrant.globalReentryLock;
import static network.balanced.score.tokens.Constants.WEEK_IN_MICRO_SECONDS;

public class BoostedBalnImpl extends AbstractBoostedBaln {

    public BoostedBalnImpl(Address _governance, String symbol) {
        super(_governance, Names.BOOSTED_BALN, symbol);
        if (currentVersion.getOrDefault("").equals(Versions.BOOSTED_BALN)) {
            Context.revert("Can't Update same version of code");
        }
        currentVersion.set(Versions.BOOSTED_BALN);
    }

    @External(readonly = true)
    public String version() {
        return currentVersion.getOrDefault("");
    }

    @External
    public void setMinimumLockingAmount(BigInteger value) {
        onlyOwner();
        Context.require(value.signum() > 0, "Invalid value for minimum locking amount");

        this.minimumLockingAmount.set(value);
    }

    @External(readonly = true)
    public BigInteger getMinimumLockingAmount() {
        return this.minimumLockingAmount.get();
    }

    @External
    public void setPenaltyAddress(Address penaltyAddress) {
        onlyOwner();
        this.penaltyAddress.set(penaltyAddress);
    }

    @External(readonly = true)
    public Address getPenaltyAddress() {
        return this.penaltyAddress.get();
    }

    @External(readonly = true)
    public Map<String, BigInteger> getLocked(Address _owner) {
        LockedBalance balance = getLockedBalance(getStringNetworkAddress(_owner));
        return Map.of("amount", balance.amount, "end", balance.getEnd());
    }

    @External(readonly = true)
    public Map<String, BigInteger> getLockedV2(String _owner) {
        LockedBalance balance = getLockedBalance(_owner);
        return Map.of("amount", balance.amount, "end", balance.getEnd());
    }

    @External(readonly = true)
    public BigInteger getTotalLocked() {
        return Context.call(BigInteger.class, getBaln(), "balanceOf", Context.getAddress());
    }

    @External(readonly = true)
    public List<String> getUsers(int start, int end) {
        Context.require(end - start <= 100, "Get users :Fetch only 100 users at a time");

        List<String> result = new ArrayList<>();
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
        return users.contains(getStringNetworkAddress(_owner));
    }

    @External(readonly = true)
    public boolean hasLockedV2(String _owner) {
        return users.contains(_owner);
    }

    @External(readonly = true)
    public BigInteger getLastUserSlope(Address address) {
        String user = getStringNetworkAddress(address);
        BigInteger userPointEpoch = this.userPointEpoch.get(user);
        return getUserPointHistory(user, userPointEpoch).slope;
    }

    @External(readonly = true)
    public BigInteger getLastUserSlopeV2(String address) {
        BigInteger userPointEpoch = this.userPointEpoch.get(address);
        return getUserPointHistory(address, userPointEpoch).slope;
    }

    @External(readonly = true)
    public BigInteger userPointHistoryTimestamp(Address address, BigInteger index) {
        return getUserPointHistory(getStringNetworkAddress(address), index).getTimestamp();
    }

    @External(readonly = true)
    public BigInteger userPointHistoryTimestampV2(String address, BigInteger index) {
        return getUserPointHistory(address, index).getTimestamp();
    }

    @External(readonly = true)
    public BigInteger lockedEnd(Address address) {
        return getLockedBalance(getStringNetworkAddress(address)).getEnd();
    }

    @External(readonly = true)
    public BigInteger lockedEndV2(String address) {
        return getLockedBalance(address).getEnd();
    }

    @External
    public void checkpoint() {
        checkStatus();
        this.checkpoint(getStringNetworkAddress(EOA_ZERO), new LockedBalance(), new LockedBalance());
    }

    public void checkpoint(String from) {
        checkStatus();
        this.checkpoint(getStringNetworkAddress(EOA_ZERO), new LockedBalance(), new LockedBalance());
    }

    @External
    public void xTokenFallback(String _from, BigInteger _value, byte[] _data) {
        checkStatus();
        Address token = Context.getCaller();
        Context.require(token.equals(getBaln()), "Token Fallback: Only Baln deposits are allowed");
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
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        checkStatus();
        Address token = Context.getCaller();
        Context.require(token.equals(getBaln()), "Token Fallback: Only Baln deposits are allowed");
        Context.require(_value.signum() > 0, "Token Fallback: Token value should be a positive number");

        String unpackedData = new String(_data);
        Context.require(!unpackedData.isEmpty(), "Token Fallback: Data can't be empty");

        JsonObject json = Json.parse(unpackedData).asObject();
        String method = json.get("method").asString();
        JsonObject params = json.get("params").asObject();
        BigInteger unlockTime = convertToNumber(params.get("unlockTime"), BigInteger.ZERO);
        String stringFrom = getStringNetworkAddress(_from);
        switch (method) {
            case "increaseAmount":
                this.increaseAmount(stringFrom, _value, unlockTime);
                break;
            case "createLock":
                BigInteger minimumLockingAmount = this.minimumLockingAmount.get();
                Context.require(_value.compareTo(minimumLockingAmount) >= 0, "insufficient locking amount. minimum " +
                        "amount is: " + minimumLockingAmount);
                this.createLock(stringFrom, _value, unlockTime);
                break;
            default:
                throw new UserRevertedException("Token fallback: Unimplemented token fallback action");
        }
    }

    @External
    public void increaseUnlockTime(BigInteger unlockTime) {
        increaseUnlockTimeInternal(getStringNetworkAddress(Context.getCaller()), unlockTime);
    }

    public void xIncreaseUnlockTime(String from, BigInteger unlockTime){
        increaseUnlockTimeInternal(from, unlockTime);
    }

    private void increaseUnlockTimeInternal(String stingSender, BigInteger unlockTime){
        checkStatus();
        globalReentryLock();
        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());

        LockedBalance locked = getLockedBalance(stingSender);
        unlockTime = unlockTime.divide(WEEK_IN_MICRO_SECONDS).multiply(WEEK_IN_MICRO_SECONDS);

        Context.require(locked.amount.compareTo(BigInteger.ZERO) > 0, "Increase unlock time: Nothing is locked");
        Context.require(locked.getEnd().compareTo(blockTimestamp) > 0, "Increase unlock time: Lock expired");
        Context.require(unlockTime.compareTo(locked.end.toBigInteger()) > 0, "Increase unlock time: Can only increase" +
                " lock duration");
        Context.require(unlockTime.compareTo(blockTimestamp.add(MAX_TIME)) <= 0, "Increase unlock time: Voting lock " +
                "can be 4 years max");

        this.depositFor(stingSender, BigInteger.ZERO, unlockTime, locked, INCREASE_UNLOCK_TIME);
    }

    @External
    public void kick(Address user) {
        kickInternal(getStringNetworkAddress(user));
    }

    public void xKick(String from){
        kickInternal(from);
    }

    private void kickInternal(String stringUser){
        checkStatus();
        BigInteger bBalnBalance = xBalanceOf(stringUser, BigInteger.ZERO);
        if (bBalnBalance.equals(BigInteger.ZERO)) {
            onKick(stringUser);
        } else {
            onBalanceUpdate(stringUser, bBalnBalance);
        }
    }

    @External
    public void withdraw() {
        withdrawInternal(getStringNetworkAddress(Context.getCaller()));
    }


    private void withdrawInternal(String senderAddress){
        checkStatus();
        globalReentryLock();
        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        LockedBalance balanceLocked = getLockedBalance(senderAddress);
        Context.require(blockTimestamp.compareTo(balanceLocked.getEnd()) >= 0, "Withdraw: The lock haven't expire");
        BigInteger value = balanceLocked.amount;

        LockedBalance oldLocked = balanceLocked.newLockedBalance();
        balanceLocked.end = UnsignedBigInteger.ZERO;
        balanceLocked.amount = BigInteger.ZERO;

        locked.set(senderAddress, balanceLocked);
        BigInteger supplyBefore = this.supply.get();
        this.supply.set(supplyBefore.subtract(value));

        this.checkpoint(senderAddress, oldLocked, balanceLocked);

        TokenTransfer.transfer(getBaln(), senderAddress, value, "withdraw".getBytes());

        users.remove(senderAddress);
        WithdrawV2(senderAddress, value, blockTimestamp);
        Supply(supplyBefore, supplyBefore.subtract(value));
        onKick(senderAddress);
    }

    @External
    public void handleCallMessage(String _from, byte[] _data, @Optional String[] _protocols) {
        checkStatus();
        only(getXCall());
        XCallUtils.verifyXCallProtocols(_from, _protocols);
        BoostedBalnXCall.process(this, _from, _data);
    }

    public void xWithdrawEarly(String _from) {
        withdrawEarlyInternal(_from);
    }

    public void xWithdraw(String _from) {
        withdrawInternal(_from);
    }

    @External
    public void withdrawEarly() {
        withdrawEarlyInternal(getStringNetworkAddress(Context.getCaller()));
    }

    private void withdrawEarlyInternal(String senderAddress){
        checkStatus();
        globalReentryLock();
        Address sender = Context.getCaller();
        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());

        LockedBalance lockedBalance = getLockedBalance(senderAddress);
        Context.require(blockTimestamp.compareTo(lockedBalance.getEnd()) < 0, "Withdraw: The lock has expired, use withdraw " +
                "method");
        BigInteger value = lockedBalance.amount;
        BigInteger maxPenalty = value.divide(BigInteger.TWO);
        BigInteger variablePenalty = balanceOf(sender, null);
        BigInteger penaltyAmount = variablePenalty.min(maxPenalty);
        BigInteger returnAmount = value.subtract(penaltyAmount);

        LockedBalance oldLocked = lockedBalance.newLockedBalance();
        lockedBalance.end = UnsignedBigInteger.ZERO;
        lockedBalance.amount = BigInteger.ZERO;
        locked.set(senderAddress, lockedBalance);
        BigInteger supplyBefore = this.supply.get();
        this.supply.set(supplyBefore.subtract(value));

        this.checkpoint(senderAddress, oldLocked, lockedBalance);


        Context.call(getBaln(), "transfer", this.penaltyAddress.get(), penaltyAmount,
                "withdrawPenalty".getBytes());
        TokenTransfer.transfer(getBaln(), senderAddress, returnAmount, "withdrawEarly".getBytes());

        users.remove(senderAddress);
        Withdraw(sender, value, blockTimestamp);
        Supply(supplyBefore, supplyBefore.subtract(value));
        onKick(senderAddress);
    }

    @External(readonly = true)
    public BigInteger balanceOf(Address _owner, @Optional BigInteger timestamp) {
        UnsignedBigInteger uTimestamp;
        String ownerAddress = getStringNetworkAddress(_owner);
        if (timestamp == null || timestamp.equals(BigInteger.ZERO)) {
            uTimestamp = UnsignedBigInteger.valueOf(Context.getBlockTimestamp());
        } else {
            uTimestamp = new UnsignedBigInteger(timestamp);
        }

        BigInteger epoch = this.userPointEpoch.getOrDefault(ownerAddress, BigInteger.ZERO);
        if (epoch.equals(BigInteger.ZERO)) {
            return BigInteger.ZERO;
        } else {
            Point lastPoint = getUserPointHistory(ownerAddress, epoch);
            UnsignedBigInteger _delta = uTimestamp.subtract(lastPoint.timestamp);
            return lastPoint.bias
                    .subtract(lastPoint.slope.multiply(_delta.toBigInteger()))
                    .max(BigInteger.ZERO);

        }
    }

    @External(readonly = true)
    public BigInteger xBalanceOf(String _owner, @Optional BigInteger timestamp) {
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
        String ownerAddress = getStringNetworkAddress(_owner);
        UnsignedBigInteger blockHeight = UnsignedBigInteger.valueOf(Context.getBlockHeight());
        UnsignedBigInteger blockTimestamp = UnsignedBigInteger.valueOf(Context.getBlockTimestamp());

        Context.require(block.compareTo(blockHeight.toBigInteger()) <= 0, "BalanceOfAt: Invalid given block height");
        BigInteger userEpoch = this.findUserPointHistory(ownerAddress, block);
        Point uPoint = this.userPointHistory.at(NetworkAddress.valueOf (ownerAddress) ).getOrDefault(userEpoch, new Point());

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
    public BigInteger xBalanceOfAt(String _owner, BigInteger block) {
        UnsignedBigInteger blockHeight = UnsignedBigInteger.valueOf(Context.getBlockHeight());
        UnsignedBigInteger blockTimestamp = UnsignedBigInteger.valueOf(Context.getBlockTimestamp());

        Context.require(block.compareTo(blockHeight.toBigInteger()) <= 0, "BalanceOfAt: Invalid given block height");
        BigInteger userEpoch = this.findUserPointHistory(_owner, block);
        Point uPoint = this.userPointHistory.at(NetworkAddress.valueOf(_owner, NATIVE_NID) ).getOrDefault(userEpoch, new Point());

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
    public BigInteger userPointEpoch(Address address) {
        String userAddress = getStringNetworkAddress(address);
        return this.userPointEpoch.getOrDefault(userAddress, BigInteger.ZERO);
    }

    @External(readonly = true)
    public BigInteger xUserPointEpoch(String address) {
        return this.userPointEpoch.getOrDefault(address, BigInteger.ZERO);
    }
}
