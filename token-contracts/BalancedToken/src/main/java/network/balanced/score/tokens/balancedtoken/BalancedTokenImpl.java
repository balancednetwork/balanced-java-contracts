/*
 * Copyright (c) 2022-2023 Balanced.network.
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

package network.balanced.score.tokens.balancedtoken;

import network.balanced.score.lib.interfaces.BalancedToken;
import network.balanced.score.lib.tokens.IRC2Burnable;
import network.balanced.score.lib.utils.BalancedAddressManager;
import network.balanced.score.lib.utils.Versions;
import score.*;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
import static network.balanced.score.lib.utils.Math.pow;
import static network.balanced.score.tokens.balancedtoken.Constants.*;

public class BalancedTokenImpl extends IRC2Burnable implements BalancedToken {

    private final VarDB<Address> dexScore = Context.newVarDB(DEX_SCORE, Address.class);
    private final VarDB<Address> bnusdScore = Context.newVarDB(BNUSD_SCORE, Address.class);
    private final VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);
    private final VarDB<Address> oracle = Context.newVarDB(ORACLE, Address.class);

    private final VarDB<String> oracleName = Context.newVarDB(ORACLE_NAME, String.class);
    private final VarDB<BigInteger> priceUpdateTime = Context.newVarDB(PRICE_UPDATE_TIME, BigInteger.class);
    private final VarDB<BigInteger> lastPrice = Context.newVarDB(LAST_PRICE, BigInteger.class);
    private final VarDB<BigInteger> minInterval = Context.newVarDB(MIN_INTERVAL, BigInteger.class);

    private final VarDB<Boolean> stakingEnabled = Context.newVarDB(STAKING_ENABLED, Boolean.class);
    private final BranchDB<Address, DictDB<Integer, BigInteger>> stakedBalances = Context.newBranchDB(STAKED_BALANCES
            , BigInteger.class);
    private final VarDB<BigInteger> minimumStake = Context.newVarDB(MINIMUM_STAKE, BigInteger.class);
    private final VarDB<BigInteger> unstakingPeriod = Context.newVarDB(UNSTAKING_PERIOD, BigInteger.class);
    private final VarDB<BigInteger> totalStakedBalance = Context.newVarDB(TOTAL_STAKED_BALANCE, BigInteger.class);

    private final VarDB<Address> dividendsScore = Context.newVarDB(DIVIDENDS_SCORE, Address.class);
    private final VarDB<BigInteger> timeOffset = Context.newVarDB(TIME_OFFSET, BigInteger.class);

    // [address][snapshot_id]["ids" || "amount"]
    private final BranchDB<Address, BranchDB<Integer, DictDB<String, BigInteger>>> stakeSnapshots =
            Context.newBranchDB(STAKE_SNAPSHOTS, BigInteger.class);
    // [address] = total_number_of_snapshots_taken
    private final DictDB<Address, Integer> totalSnapshots = Context.newDictDB(TOTAL_SNAPSHOTS, Integer.class);

    // [snapshot_id]["ids" || "amount"]
    private final BranchDB<Integer, DictDB<String, BigInteger>> totalStakedSnapshot =
            Context.newBranchDB(TOTAL_STAKED_SNAPSHOT, BigInteger.class);
    private final VarDB<Integer> totalStakedSnapshotCount = Context.newVarDB(TOTAL_STAKED_SNAPSHOT_COUNT,
            Integer.class);

    private final VarDB<Boolean> enableSnapshots = Context.newVarDB(ENABLE_SNAPSHOTS, Boolean.class);
    private final VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);

    private final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);

    public BalancedTokenImpl(Address _governance) {
        super(TOKEN_NAME, SYMBOL_NAME, null);

        if (governance.get() != null && admin.get() == null) {
            admin.set(_governance);
        }

        if (governance.get() == null) {
            this.governance.set(_governance);
            this.stakingEnabled.set(true);
            this.oracleName.set(DEFAULT_ORACLE_NAME);
            this.lastPrice.set(INITIAL_PRICE_ESTIMATE);
            this.minInterval.set(MIN_UPDATE_TIME);
            this.minimumStake.set(MINIMUM_STAKE_AMOUNT);
            this.unstakingPeriod.set(DEFAULT_UNSTAKING_PERIOD);
            this.enableSnapshots.set(true);
        }

        BalancedAddressManager.setGovernance(governance.get());
        if (this.currentVersion.getOrDefault("").equals(Versions.BALN)) {
            Context.revert("Can't Update same version of code");
        }
        this.currentVersion.set(Versions.BALN);
    }

    @External(readonly = true)
    public String version() {
        return currentVersion.getOrDefault("");
    }

    @EventLog(indexed = 3)
    public void OraclePrice(String market, String oracle_name, Address oracle_address, BigInteger price) {
    }

    @External(readonly = true)
    public String getPeg() {
        return TAG;
    }

    @External
    public void setGovernance(Address _address) {
        onlyOwner();
        this.governance.set(_address);
    }

    @External(readonly = true)
    public Address getGovernance() {
        return this.governance.get();
    }

    @External
    public void setAdmin(Address _address) {
        only(governance);
        admin.set(_address);
    }

    @External(readonly = true)
    public Address getAdmin() {
        return admin.get();
    }

    @External
    public void setBnusd(Address _address) {
        only(governance);
        isContract(_address);
        this.bnusdScore.set(_address);
    }

    @External(readonly = true)
    public Address getBnusd() {
        return this.bnusdScore.get();
    }

    @External
    public void setOracle(Address _address) {
        only(governance);
        isContract(_address);
        this.oracle.set(_address);
    }

    @External(readonly = true)
    public Address getOracle() {
        return this.oracle.get();
    }

    @External
    public void setDex(Address _address) {
        only(governance);
        isContract(_address);
        this.dexScore.set(_address);
    }

    @External(readonly = true)
    public Address getDex() {
        return this.dexScore.get();
    }

    @External
    public void setDividends(Address _address) {
        only(governance);
        isContract(_address);
        this.dividendsScore.set(_address);
    }

    @External(readonly = true)
    public Address getDividends() {
        return this.dividendsScore.get();
    }

    @External
    public void setOracleName(String _name) {
        only(governance);
        this.oracleName.set(_name);
    }

    @External(readonly = true)
    public String getOracleName() {
        return this.oracleName.get();
    }

    @External
    public void setMinInterval(BigInteger _interval) {
        only(governance);
        this.minInterval.set(_interval);
    }

    @External(readonly = true)
    public BigInteger getMinInterval() {
        return this.minInterval.get();
    }

    @External(readonly = true)
    public BigInteger getPriceUpdateTime() {
        return priceUpdateTime.getOrDefault(BigInteger.ZERO);
    }

    /**
     * Returns the price of the asset in loop. Makes a call to the oracle if the last recorded price is not recent
     * enough.
     **/
    @External
    public BigInteger priceInLoop() {
        BigInteger currentTime = BigInteger.valueOf(Context.getBlockTimestamp());
        BigInteger lastPriceUpdateTime = this.priceUpdateTime.getOrDefault(BigInteger.ZERO);
        BigInteger interval = this.minInterval.getOrDefault(BigInteger.ZERO);
        if (currentTime.subtract(lastPriceUpdateTime).compareTo(interval) > 0) {
            this.updateAssetValue();
        }
        return this.lastPrice.get();
    }

    /**
     * Returns the latest price of the asset in loop.
     **/
    @SuppressWarnings("unchecked")
    @External(readonly = true)
    public BigInteger lastPriceInLoop() {
        Address dexScore = this.dexScore.get();
        Address oracleAddress = this.oracle.get();

        BigInteger balnPriceInUsd = Context.call(BigInteger.class, dexScore, "getBalnPrice");
        Map<String, BigInteger> priceData = (Map<String, BigInteger>) Context.call(oracleAddress, "get_reference_data"
                , "USD", "ICX");

        return balnPriceInUsd.multiply(priceData.get("rate")).divide(EXA);
    }

    /**
     * Calls the oracle method for the asset and updates the asset value in loop.
     **/
    private void updateAssetValue() {
        String base = "BALN";
        String quote = "ICX";

        BigInteger newPrice = lastPriceInLoop();

        this.lastPrice.set(newPrice);
        this.priceUpdateTime.set(BigInteger.valueOf(Context.getBlockTimestamp()));
        this.OraclePrice(base + quote, this.oracleName.get(), dexScore.get(), newPrice);
    }

    @External
    public void setMinimumStake(BigInteger _amount) {
        only(governance);
        Context.require(_amount.compareTo(BigInteger.ZERO) >= 0, TAG + ": Amount cannot be less than zero.");

        BigInteger totalAmount = _amount.multiply(pow(BigInteger.TEN, decimals().intValue()));
        this.minimumStake.set(totalAmount);
    }

    @External(readonly = true)
    public BigInteger getMinimumStake() {
        return this.minimumStake.getOrDefault(BigInteger.ZERO);
    }

    @External
    public void setUnstakingPeriod(BigInteger _time) {
        only(governance);
        Context.require(_time.compareTo(BigInteger.ZERO) >= 0, TAG + ": Time cannot be negative.");

        BigInteger totalTime = _time.multiply(MICRO_SECONDS_IN_A_DAY);
        this.unstakingPeriod.set(totalTime);
    }

    @External(readonly = true)
    public BigInteger getUnstakingPeriod() {
        BigInteger timeInMicroseconds = this.unstakingPeriod.getOrDefault(BigInteger.ZERO);
        return timeInMicroseconds.divide(MICRO_SECONDS_IN_A_DAY);
    }

    @External
    public void toggleStakingEnabled() {
        only(governance);
        this.stakingEnabled.set(!this.stakingEnabled.getOrDefault(false));
    }

    @External(readonly = true)
    public boolean getStakingEnabled() {
        return this.stakingEnabled.getOrDefault(false);
    }

    @External
    public void toggleEnableSnapshot() {
        onlyOwner();
        this.enableSnapshots.set(!this.enableSnapshots.getOrDefault(false));
    }

    @External(readonly = true)
    public boolean getSnapshotEnabled() {
        return this.enableSnapshots.getOrDefault(false);
    }

    @External
    public void setTimeOffset() {
        onlyOwner();

        Address dexScore = this.dexScore.get();
        Context.require(dexScore != null, TAG + ": Dex score address must be set first");

        BigInteger deltaTime = Context.call(BigInteger.class, dexScore, "getTimeOffset");
        this.timeOffset.set(deltaTime);
    }

    @External(readonly = true)
    public BigInteger getTimeOffset() {
        return this.timeOffset.getOrDefault(BigInteger.ZERO);
    }

    /**
     * Returns the current day (floored). Used for snapshotting, paying rewards, and paying dividends.
     **/
    @External(readonly = true)
    public BigInteger getDay() {
        return BigInteger.valueOf(Context.getBlockTimestamp()).subtract(this.timeOffset.getOrDefault(BigInteger.ZERO)).divide(MICRO_SECONDS_IN_A_DAY);
    }

    @External(readonly = true)
    public Map<String, BigInteger> detailsBalanceOf(Address _owner) {
        DictDB<Integer, BigInteger> stakingDetail = this.stakedBalances.at(_owner);
        BigInteger unstakingTime = stakingDetail.getOrDefault(Status.UNSTAKING_PERIOD.code, BigInteger.ZERO);
        BigInteger currUnstaked = stakingDetail.getOrDefault(Status.UNSTAKING.code, BigInteger.ZERO);
        BigInteger availableBalance = stakingDetail.getOrDefault(Status.AVAILABLE.code, BigInteger.ZERO);
        BigInteger stakedBalance = stakingDetail.getOrDefault(Status.STAKED.code, BigInteger.ZERO);

        if (unstakingTime.compareTo(BigInteger.valueOf(Context.getBlockTimestamp())) >= 0) {
            currUnstaked = BigInteger.ZERO;
        }

        BigInteger unstakingAmount =
                stakingDetail.getOrDefault(Status.UNSTAKING.code, BigInteger.ZERO).subtract(currUnstaked);

        if (unstakingAmount.equals(BigInteger.ZERO)) {
            unstakingTime = BigInteger.ZERO;
        }

        if (this.firstTime(_owner)) {
            availableBalance = this.balanceOf(_owner);
        }

        return Map.of(
                "Total balance", this.balanceOf(_owner),
                "Available balance", availableBalance.add(currUnstaked),
                "Staked balance", stakedBalance,
                "Unstaking balance", unstakingAmount,
                "Unstaking time (in microseconds)", unstakingTime
        );
    }

    private boolean firstTime(Address from) {
        DictDB<Integer, BigInteger> stakingDetail = this.stakedBalances.at(from);
        return (stakingDetail.getOrDefault(Status.AVAILABLE.code, BigInteger.ZERO).equals(BigInteger.ZERO)
                && stakingDetail.getOrDefault(Status.STAKED.code, BigInteger.ZERO).equals(BigInteger.ZERO)
                && stakingDetail.getOrDefault(Status.UNSTAKING.code, BigInteger.ZERO).equals(BigInteger.ZERO)
                && !this.balanceOf(from).equals(BigInteger.ZERO));
    }

    @External(readonly = true)
    public BigInteger unstakedBalanceOf(Address _owner) {
        Map<String, BigInteger> detailBalance = this.detailsBalanceOf(_owner);
        return detailBalance.get("Unstaking balance");
    }

    @External(readonly = true)
    public BigInteger stakedBalanceOf(Address _owner) {
        return this.stakedBalances.at(_owner).getOrDefault(Status.STAKED.code, BigInteger.ZERO);
    }

    @External(readonly = true)
    public BigInteger availableBalanceOf(Address _owner) {
        Map<String, BigInteger> detailBalance = this.detailsBalanceOf(_owner);
        return detailBalance.get("Available balance");
    }

    @External(readonly = true)
    public BigInteger totalStakedBalance() {
        return this.totalStakedBalance.getOrDefault(BigInteger.ZERO);
    }

    private void checkFirstTime(Address from) {
        // If first time copy the balance to available staked balances
        if (this.firstTime(from)) {
            this.stakedBalances.at(from).set(Status.AVAILABLE.code, this.balanceOf(from));
        }
    }

    private void stakingEnabledOnly() {
        Context.require(stakingEnabled.getOrDefault(false), TAG + ": Staking must first be enabled.");
    }

    private void makeAvailable(Address from) {
        DictDB<Integer, BigInteger> stakingDetail = this.stakedBalances.at(from);

        BigInteger unstakingTime = stakingDetail.getOrDefault(Status.UNSTAKING_PERIOD.code, BigInteger.ZERO);
        if (unstakingTime.compareTo(BigInteger.valueOf(Context.getBlockTimestamp())) <= 0) {
            BigInteger currUnstaked = stakingDetail.getOrDefault(Status.UNSTAKING.code, BigInteger.ZERO);
            stakingDetail.set(Status.UNSTAKING.code, null);
            stakingDetail.set(Status.AVAILABLE.code, stakingDetail.getOrDefault(Status.AVAILABLE.code,
                    BigInteger.ZERO).add(currUnstaked));
        }
    }

    @External
    public void stake(BigInteger _value) {
        checkStatus();
        this.stakingEnabledOnly();
        Address from = Context.getCaller();

        Context.require(_value.compareTo(BigInteger.ZERO) >= 0, TAG + ": Staked BALN value can't be less than zero");
        Context.require(_value.compareTo(this.balanceOf(from)) <= 0, TAG + ": Out of BALN balance.");
        if ((_value.compareTo(this.minimumStake.getOrDefault(BigInteger.ZERO)) < 0) && !(_value.equals(BigInteger.ZERO))) {
            throw new UserRevertedException(TAG + ": Staked BALN must be greater than the minimum stake amount and " +
                    "non zero");
        }

        this.checkFirstTime(from);
        this.makeAvailable(from);

        DictDB<Integer, BigInteger> stakingDetail = this.stakedBalances.at(from);
        BigInteger stakedAmount = stakingDetail.getOrDefault(Status.STAKED.code, BigInteger.ZERO);

        BigInteger oldStake = stakedAmount.add(stakingDetail.getOrDefault(Status.UNSTAKING.code, BigInteger.ZERO));
        BigInteger stakeIncrement = _value.subtract(stakedAmount);
        BigInteger unstakeAmount = BigInteger.ZERO;

        if (_value.compareTo(oldStake) > 0) {
            BigInteger offset = _value.subtract(oldStake);
            stakingDetail.set(Status.AVAILABLE.code, stakingDetail.getOrDefault(Status.AVAILABLE.code,
                    BigInteger.ZERO).subtract(offset));
        } else {
            unstakeAmount = oldStake.subtract(_value);
        }

        stakingDetail.set(Status.STAKED.code, _value);
        stakingDetail.set(Status.UNSTAKING.code, unstakeAmount);
        stakingDetail.set(Status.UNSTAKING_PERIOD.code, BigInteger.valueOf(Context.getBlockTimestamp()).
                add(this.unstakingPeriod.getOrDefault(BigInteger.ZERO)));

        BigInteger newTotal = this.totalStakedBalance.getOrDefault(BigInteger.ZERO).add(stakeIncrement);
        this.totalStakedBalance.set(newTotal);

        if (this.enableSnapshots.getOrDefault(false)) {
            this.updateSnapshotForAddress(Context.getCaller(), _value);
            this.updateTotalStakedSnapshot(newTotal);
        }

        Context.call(dividendsScore.get(), "updateBalnStake", from, stakedAmount, newTotal);
    }

    @Override
    @External
    public void transfer(Address _to, BigInteger _value, @Optional byte[] _data) {
        checkStatus();
        Address from = Context.getCaller();
        this.checkFirstTime(from);
        this.checkFirstTime(_to);
        this.makeAvailable(from);
        this.makeAvailable(_to);

        DictDB<Integer, BigInteger> stakingDetailOfSender = this.stakedBalances.at(from);
        BigInteger availableAmountOfSender = stakingDetailOfSender.getOrDefault(Status.AVAILABLE.code, BigInteger.ZERO);

        Context.require(availableAmountOfSender.compareTo(_value) >= 0, TAG + ": Out of available balance. Please " +
                "check staked and total balance.");

        stakingDetailOfSender.set(Status.AVAILABLE.code, availableAmountOfSender.subtract(_value));
        DictDB<Integer, BigInteger> stakingDetailOfReceiver = this.stakedBalances.at(_to);
        stakingDetailOfReceiver.set(Status.AVAILABLE.code, stakingDetailOfReceiver.getOrDefault(Status.AVAILABLE.code
                , BigInteger.ZERO).add(_value));

        super.transfer(_to, _value, _data);
    }

    @Override
    @External
    public void mint(BigInteger _amount, @Optional byte[] _data) {
        Address to = Context.getCaller();
        this.mintTo(to, _amount, _data);
    }

    @Override
    @External
    public void mintTo(Address _account, BigInteger _amount, @Optional byte[] _data) {
        checkStatus();
        this.checkFirstTime(_account);
        this.makeAvailable(_account);
        DictDB<Integer, BigInteger> stakingDetailOfReceiver = this.stakedBalances.at(_account);
        stakingDetailOfReceiver.set(Status.AVAILABLE.code, stakingDetailOfReceiver.getOrDefault(Status.AVAILABLE.code
                , BigInteger.ZERO).add(_amount));

        super.mintTo(_account, _amount, _data);
    }

    @Override
    @External
    public void burn(BigInteger _amount) {
        Address from = Context.getCaller();
        this.burnFrom(from, _amount);
    }

    @Override
    @External
    public void burnFrom(Address _account, BigInteger _amount) {
        this.checkFirstTime(_account);
        this.makeAvailable(_account);

        DictDB<Integer, BigInteger> stakingDetail = this.stakedBalances.at(_account);
        BigInteger availableBalance = stakingDetail.getOrDefault(Status.AVAILABLE.code, BigInteger.ZERO);

        Context.require(availableBalance.compareTo(_amount) >= 0, TAG + ": Out of available balance. Please check " +
                "staked and total balance.");
        stakingDetail.set(Status.AVAILABLE.code, availableBalance.subtract(_amount));

        super.burnFrom(_account, _amount);
    }

    // ----------------------------------------------------------
    // Snapshots
    // ----------------------------------------------------------

    private void updateSnapshotForAddress(Address account, BigInteger amount) {
        if (this.timeOffset.getOrDefault(BigInteger.ZERO).equals(BigInteger.ZERO)) {
            this.setTimeOffset();
        }

        BigInteger currentId = this.getDay();
        int totalSnapshotsTaken = this.totalSnapshots.getOrDefault(account, 0);

        BranchDB<Integer, DictDB<String, BigInteger>> stakeSnapshots = this.stakeSnapshots.at(account);
        if (totalSnapshotsTaken > 0 && stakeSnapshots.at(totalSnapshotsTaken - 1).getOrDefault(IDS, BigInteger.ZERO)
                .equals(currentId)) {
            stakeSnapshots.at(totalSnapshotsTaken - 1).set(AMOUNT, amount);
        } else {
            stakeSnapshots.at(totalSnapshotsTaken).set(IDS, currentId);
            stakeSnapshots.at(totalSnapshotsTaken).set(AMOUNT, amount);
            this.totalSnapshots.set(account, totalSnapshotsTaken + 1);
        }
    }

    private void updateTotalStakedSnapshot(BigInteger amount) {

        if (this.timeOffset.getOrDefault(BigInteger.ZERO).equals(BigInteger.ZERO)) {
            this.setTimeOffset();
        }

        BigInteger currentId = this.getDay();
        int totalSnapshotsTaken = this.totalStakedSnapshotCount.getOrDefault(0);

        if (totalSnapshotsTaken > 0 && this.totalStakedSnapshot.at(totalSnapshotsTaken - 1).getOrDefault(IDS,
                BigInteger.ZERO).equals(currentId)) {
            this.totalStakedSnapshot.at(totalSnapshotsTaken - 1).set(AMOUNT, amount);
        } else {
            this.totalStakedSnapshot.at(totalSnapshotsTaken).set(IDS, currentId);
            this.totalStakedSnapshot.at(totalSnapshotsTaken).set(AMOUNT, amount);
            this.totalStakedSnapshotCount.set(totalSnapshotsTaken + 1);
        }
    }

    @External(readonly = true)
    public BigInteger stakedBalanceOfAt(Address _account, BigInteger _day) {
        BigInteger currentDay = this.getDay();
        if (_day.compareTo(currentDay) > 0) {
            Context.revert(TAG + ": Asked _day is greater than current day");
        }

        int totalSnapshotsTaken = this.totalSnapshots.getOrDefault(_account, 0);
        if (totalSnapshotsTaken == 0) {
            return BigInteger.ZERO;
        }

        BranchDB<Integer, DictDB<String, BigInteger>> stakeSnapshotDetail = this.stakeSnapshots.at(_account);
        if (stakeSnapshotDetail.at(totalSnapshotsTaken - 1).getOrDefault(IDS, BigInteger.ZERO).compareTo(_day) <= 0) {
            return stakeSnapshotDetail.at(totalSnapshotsTaken - 1).getOrDefault(AMOUNT, BigInteger.ZERO);
        }

        return getSnapshotAmount(_day, totalSnapshotsTaken, stakeSnapshotDetail);
    }

    private BigInteger getSnapshotAmount(BigInteger _day, int totalSnapshotsTaken, BranchDB<Integer, DictDB<String,
            BigInteger>> stakeSnapshotDetail) {
        if (stakeSnapshotDetail.at(0).getOrDefault(IDS, BigInteger.ZERO).compareTo(_day) > 0) {
            return BigInteger.ZERO;
        }

        int low = 0;
        int high = totalSnapshotsTaken - 1;
        while (high > low) {
            int mid = high - (high - low) / 2;
            DictDB<String, BigInteger> midValue = stakeSnapshotDetail.at(mid);
            if (midValue.getOrDefault(IDS, BigInteger.ZERO).equals(_day)) {
                return midValue.getOrDefault(AMOUNT, BigInteger.ZERO);
            } else if (midValue.getOrDefault(IDS, BigInteger.ZERO).compareTo(_day) < 0) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }

        return stakeSnapshotDetail.at(low).getOrDefault(AMOUNT, BigInteger.ZERO);
    }

    @External(readonly = true)
    public BigInteger totalStakedBalanceOfAt(BigInteger _day) {
        BigInteger currentDay = this.getDay();
        if (_day.compareTo(currentDay) > 0) {
            Context.revert(TAG + ": Asked _day is greater than current day");
        }

        int totalSnapshotsTaken = this.totalStakedSnapshotCount.getOrDefault(0);
        if (totalSnapshotsTaken == 0) {
            return BigInteger.ZERO;
        }

        BranchDB<Integer, DictDB<String, BigInteger>> totalStakedSnapshot = this.totalStakedSnapshot;
        BigInteger id = totalStakedSnapshot.at(totalSnapshotsTaken - 1).getOrDefault(IDS, BigInteger.ZERO);
        if (id.compareTo(_day) <= 0) {
            return totalStakedSnapshot.at(totalSnapshotsTaken - 1).getOrDefault(AMOUNT, BigInteger.ZERO);
        }

        return getSnapshotAmount(_day, totalSnapshotsTaken, totalStakedSnapshot);
    }
}
