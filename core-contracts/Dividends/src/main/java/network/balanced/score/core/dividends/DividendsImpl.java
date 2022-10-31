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

package network.balanced.score.core.dividends;

import network.balanced.score.lib.interfaces.Dividends;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.structs.PrepDelegations;
import score.*;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import scorex.util.ArrayList;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.dividends.Check.continuousDividendsActive;
import static network.balanced.score.core.dividends.Constants.*;
import static network.balanced.score.lib.utils.ArrayDBUtils.arrayDbContains;
import static network.balanced.score.lib.utils.ArrayDBUtils.removeFromArraydb;
import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
import static network.balanced.score.lib.utils.Math.pow;

public class DividendsImpl implements Dividends {

    private static final VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);
    private static final VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);
    private static final VarDB<Address> loanScore = Context.newVarDB(LOANS_SCORE, Address.class);
    private static final VarDB<Address> daoFund = Context.newVarDB(DAOFUND, Address.class);
    public static final VarDB<Address> balnScore = Context.newVarDB(BALN_SCORE, Address.class);
    private static final VarDB<Address> dexScore = Context.newVarDB(DEX_SCORE, Address.class);

    private static final ArrayDB<Address> acceptedTokens = Context.newArrayDB(ACCEPTED_TOKENS, Address.class);
    public static final VarDB<BigInteger> snapshotId = Context.newVarDB(SNAPSHOT_ID, BigInteger.class);
    private static final BranchDB<BigInteger, DictDB<String, BigInteger>> dailyFees = Context.newBranchDB(DAILY_FEES,
            BigInteger.class);

    private static final DictDB<String, BigInteger> dividendsPercentage = Context.newDictDB(DIVIDENDS_PERCENTAGE,
            BigInteger.class);
    private static final BranchDB<String, BranchDB<BigInteger, DictDB<String, BigInteger>>> snapshotDividends =
            Context.newBranchDB(SNAPSHOT_DIVIDENDS, BigInteger.class);
    private static final DictDB<String, BigInteger> totalSnapshots = Context.newDictDB(TOTAL_SNAPSHOT,
            BigInteger.class);
    private static final ArrayDB<String> completeDividendsCategories =
            Context.newArrayDB(COMPLETE_DIVIDENDS_CATEGORIES, String.class);

    private static final VarDB<Boolean> distributionActivate = Context.newVarDB(DISTRIBUTION_ACTIVATE, Boolean.class);
    private static final VarDB<BigInteger> dividendsBatchSize = Context.newVarDB(DIVIDENDS_BATCH_SIZE,
            BigInteger.class);
    private static final VarDB<BigInteger> timeOffset = Context.newVarDB(TIME_OFFSET, BigInteger.class);
    private static final VarDB<BigInteger> dividendsEnabledToStakedBalnDay =
            Context.newVarDB(DIVIDENDS_ENABLED_TO_STAKED_BALN_ONLY_DAY, BigInteger.class);
    public static final VarDB<BigInteger> continuousDividendsDay =
            Context.newVarDB(CONTINUOUS_DIVIDENDS_DAY, BigInteger.class);

    private static final BranchDB<Address, DictDB<Address, BigInteger>> accruedDividends =
            Context.newBranchDB(ACCRUED_DIVIDENDS, BigInteger.class);

    public DividendsImpl(@Optional Address _governance) {
        if (governance.get() == null) {
            isContract(_governance);
            DividendsImpl.governance.set(_governance);
            snapshotId.set(BigInteger.ONE);
            dividendsBatchSize.set(BigInteger.valueOf(50));
            distributionActivate.set(false);
            addInitialCategories();
        }
    }

    @External(readonly = true)
    public String name() {
        return "Balanced Dividends";
    }

    @External(readonly = true)
    public boolean getDistributionActivationStatus() {
        return distributionActivate.getOrDefault(false);
    }

    @External
    public void setDistributionActivationStatus(boolean _status) {
        only(admin);
        distributionActivate.set(_status);
    }

    @External
    public void setTimeOffset(BigInteger deltaTime) {
        only(admin);
        timeOffset.set(deltaTime);
    }

    @External
    public void setGovernance(Address _address) {
        onlyOwner();
        isContract(_address);
        governance.set(_address);
    }

    @External(readonly = true)
    public Address getGovernance() {
        return governance.get();
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
    public void setLoans(Address _address) {
        only(admin);
        isContract(_address);
        loanScore.set(_address);
    }

    @External(readonly = true)
    public Address getLoans() {
        return loanScore.get();
    }

    @External
    public void setDaofund(Address _address) {
        only(admin);
        isContract(_address);
        daoFund.set(_address);
    }

    @External(readonly = true)
    public Address getDaofund() {
        return daoFund.get();
    }

    @External
    public void setBaln(Address _address) {
        only(admin);
        isContract(_address);
        balnScore.set(_address);
    }

    @External(readonly = true)
    public Address getBaln() {
        return balnScore.get();
    }

    @External
    public void setDex(Address _address) {
        only(admin);
        isContract(_address);
        dexScore.set(_address);
    }

    @External(readonly = true)
    public Address getDex() {
        return dexScore.get();
    }

    @External
    public void setDividendsOnlyToStakedBalnDay(BigInteger day) {
        only(admin);
        Context.require(day.compareTo(snapshotId.getOrDefault(BigInteger.ZERO)) > 0,
                TAG + ": Day should be greater than the current snapshot ID.");
        dividendsEnabledToStakedBalnDay.set(day);
    }

    @External(readonly = true)
    public BigInteger getDividendsOnlyToStakedBalnDay() {
        return dividendsEnabledToStakedBalnDay.getOrDefault(BigInteger.ZERO);
    }

    @External
    public void setContinuousDividendsDay(BigInteger day) {
        onlyOwner();
        Context.require(day.compareTo(snapshotId.getOrDefault(BigInteger.ZERO)) > 0,
                TAG + ": Day should be greater than the current snapshot ID.");
        continuousDividendsDay.set(day);
    }

    @External(readonly = true)
    public BigInteger getContinuousDividendsDay() {
        return continuousDividendsDay.getOrDefault(BigInteger.ZERO);
    }

    @External(readonly = true)
    public Map<String, BigInteger> getBalances() {
        Address address = Context.getAddress();
        Map<String, BigInteger> balances = new HashMap<>();
        int numberOfAcceptedTokens = acceptedTokens.size();
        for (int i = 0; i < numberOfAcceptedTokens; i++) {
            Address tokenAddress = acceptedTokens.get(i);
            BigInteger balance = (BigInteger) Context.call(tokenAddress, "balanceOf", address);
            if (balance.compareTo(BigInteger.ZERO) > 0) {
                balances.put(Context.call(String.class, tokenAddress, "symbol"), balance);
            }
        }

        BigInteger balance = Context.getBalance(address);
        balances.put("ICX", balance);
        return balances;
    }

    @External(readonly = true)
    public Map<String, BigInteger> getDailyFees(BigInteger _day) {
        Map<String, BigInteger> fees = new HashMap<>();

        int numberOfAcceptedTokens = acceptedTokens.size();
        DictDB<String, BigInteger> dailyFeesInfoForDay = dailyFees.at(_day);
        for (int i = 0; i < numberOfAcceptedTokens; i++) {
            String token = acceptedTokens.get(i).toString();
            fees.put(token, dailyFeesInfoForDay.getOrDefault(token, BigInteger.ZERO));
        }
        return fees;
    }

    @External(readonly = true)
    public List<Address> getAcceptedTokens() {
        List<Address> acceptedTokenList = new ArrayList<>();
        int numberOfAcceptedTokens = acceptedTokens.size();

        for (int i = 0; i < numberOfAcceptedTokens; i++) {
            Address token = acceptedTokens.get(i);
            acceptedTokenList.add(token);
        }
        return acceptedTokenList;
    }

    @External
    public void addAcceptedTokens(Address _token) {
        only(admin);
        isContract(_token);

        if (!arrayDbContains(acceptedTokens, _token)) {
            acceptedTokens.add(_token);
        }
    }

    @External(readonly = true)
    public List<String> getDividendsCategories() {
        List<String> item = new ArrayList<>();
        int numberOfCategories = completeDividendsCategories.size();

        for (int i = 0; i < numberOfCategories; i++) {
            item.add(completeDividendsCategories.get(i));
        }
        return item;
    }

    @External
    public void addDividendsCategory(String _category) {
        only(admin);
        Context.require(!arrayDbContains(completeDividendsCategories, _category),
                TAG + ": " + _category + " is already added.");
        completeDividendsCategories.add(_category);
    }

    @External
    public void removeDividendsCategory(String _category) {
        only(admin);
        BigInteger currentDay = getDay();
        Context.require(arrayDbContains(completeDividendsCategories, _category),
                TAG + ": " + _category + " not found in the list of dividends categories.");

        if (!continuousDividendsActive()) {
            Map<String, BigInteger> dividendsDist = dividendsAt(currentDay);
            Context.require(dividendsDist.get(_category).equals(BigInteger.ZERO),
                    TAG + ": Please make the category percentage to 0 before removing.");
        } else {
            Context.require(dividendsPercentage.get(_category).equals(BigInteger.ZERO),
                    TAG + ": Please make the category percentage to 0 before removing.");
        }

        removeFromArraydb(_category, completeDividendsCategories);
    }

    @External(readonly = true)
    public Map<String, BigInteger> getDividendsPercentage() {
        if (continuousDividendsActive()) {
            Map<String, BigInteger> dividendsDist = new HashMap<>();
            int numberOfCategories = completeDividendsCategories.size();
            for (int i = 0; i < numberOfCategories; i++) {
                String category = completeDividendsCategories.get(i);
                dividendsDist.put(category, dividendsPercentage.get(category));
            }

            return dividendsDist;
        }

        BigInteger currentDay = getDay();
        return dividendsAt(currentDay);
    }

    @External
    public void setDividendsCategoryPercentage(DistributionPercentage[] _dist_list) {
        only(admin);
        BigInteger totalPercentage = BigInteger.ZERO;
        Context.require(_dist_list.length == completeDividendsCategories.size(),
                TAG + ": Categories count mismatched!");

        for (DistributionPercentage id : _dist_list) {
            String category = id.recipient_name;
            BigInteger percent = id.dist_percent;
            Context.require(arrayDbContains(completeDividendsCategories, category),
                    TAG + ": " + category + " is not a valid dividends category");
            if (!continuousDividendsActive()) {
                updateDividendsSnapshot(category, percent);
            } else {
                dividendsPercentage.set(category, percent);
            }

            totalPercentage = totalPercentage.add(percent);
        }

        Context.require(totalPercentage.equals(EXA), TAG + ": Total percentage doesn't sum up to 100 i.e. 10**18.");
    }

    @External(readonly = true)
    public BigInteger getDividendsBatchSize() {
        return dividendsBatchSize.getOrDefault(BigInteger.ZERO);
    }

    @External
    public void setDividendsBatchSize(BigInteger _size) {
        only(admin);
        Context.require(_size.signum() > 0, TAG + ": Size can't be negative or zero.");
        dividendsBatchSize.set(_size);
    }

    @External(readonly = true)
    public BigInteger getSnapshotId() {
        return snapshotId.getOrDefault(BigInteger.ZERO);
    }

    @External(readonly = true)
    public BigInteger getDay() {
        BigInteger time = BigInteger.valueOf(Context.getBlockTimestamp());
        BigInteger offset = timeOffset.getOrDefault(BigInteger.ZERO);
        return time.subtract(offset).divide(MICRO_SECONDS_IN_A_DAY);
    }

    @External(readonly = true)
    public BigInteger getTimeOffset() {
        return timeOffset.getOrDefault(BigInteger.ZERO);
    }

    @External
    public void delegate(PrepDelegations[] prepDelegations) {
        only(governance);
        Address staking = (Address) Context.call(governance.get(), "getContractAddress", "staking");
        Context.call(staking, "delegate", (Object) prepDelegations);
    }

    @External
    public boolean distribute() {
        if (!continuousDividendsActive()) {
            checkForNewDay();
        }

        return true;
    }

    @External
    public void transferDaofundDividends(@Optional int _start, @Optional int _end) {
        Address daofund = daoFund.get();
        Context.require(distributionActivate.getOrDefault(false),
                TAG + ": Distribution is not activated. Can't transfer.");

        int[] value = checkStartEnd(_start, _end);
        int start = value[0];
        int end = value[1];
        Map<String, BigInteger> totalDividends = new HashMap<>();
        int size = acceptedTokens.size();
        List<Address> acceptedTokensList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            acceptedTokensList.add(acceptedTokens.get(i));
        }
        for (int i = start; i < end; i++) {
            Map<String, BigInteger> dividends = getDividendsForDaoFund(BigInteger.valueOf(i), acceptedTokensList,
                    daofund);
            if (dividends.size() != 0) {
                setClaimed(daofund, BigInteger.valueOf(i));
            }

            totalDividends = addDividends(totalDividends, dividends, acceptedTokensList);
        }

        Map<String, BigInteger> nonZeroTokens = new HashMap<>();
        for (int i = 0; i < size; i++) {
            Address token = acceptedTokensList.get(i);

            if (totalDividends.containsKey(token.toString()) && totalDividends.get(token.toString()).signum() > 0) {
                nonZeroTokens.put(token.toString(), totalDividends.get(token.toString()));
                sendToken(daofund, totalDividends.get(token.toString()), token, "Daofund dividends");
            }
        }
        if (nonZeroTokens.size() > 0) {
            Claimed(daofund, BigInteger.valueOf(start), BigInteger.valueOf(end), dividendsMapToJson(nonZeroTokens));
        }
    }

    @External(readonly = true)
    public Map<String, BigInteger> getUnclaimedDividends(Address user) {
        BigInteger stakedBalance = getBalnBalance(user);
        Map<String, BigInteger> totalDividends = new HashMap<>();

        int size = acceptedTokens.size();
        DictDB<Address, BigInteger> userAccruedDividends = accruedDividends.at(user);
        for (int i = 0; i < size; i++) {
            Address token = acceptedTokens.get(i);
            BigInteger accruedDividends = DividendsTracker.updateUserData(token, user, stakedBalance, true);
            BigInteger prevAccruedDividends = userAccruedDividends.getOrDefault(token, BigInteger.ZERO);
            BigInteger totalDivs = accruedDividends.add(prevAccruedDividends);
            totalDividends.put(token.toString(), totalDivs);
        }

        return totalDividends;
    }

    @External
    public void claimDividends() {
        Address user = Context.getCaller();
        BigInteger stakedBalance = getBalnBalance(user);
        int size = acceptedTokens.size();
        DictDB<Address, BigInteger> userAccruedDividends = accruedDividends.at(user);
        for (int i = 0; i < size; i++) {
            Address token = acceptedTokens.get(i);
            BigInteger accruedDividends = DividendsTracker.updateUserData(token, user, stakedBalance, false);
            BigInteger prevAccruedDividends = userAccruedDividends.getOrDefault(token, BigInteger.ZERO);
            BigInteger totalDivs = accruedDividends.add(prevAccruedDividends);
            if (totalDivs.signum() > 0) {
                userAccruedDividends.set(token, BigInteger.ZERO);
                sendToken(user, totalDivs, token, "User dividends");
            }
        }
    }

    @External
    public void claim(@Optional int _start, @Optional int _end) {
        Context.require(distributionActivate.getOrDefault(false),
                TAG + ": Distribution is not activated. Can't transfer.");

        int[] value = checkStartEnd(_start, _end);
        int start = value[0];
        int end = value[1];
        Address account = Context.getCaller();
        Map<String, BigInteger> totalDividends = new HashMap<>();

        Address baln = balnScore.get();
        Address dex = dexScore.get();
        BigInteger dividendsSwitchingDay = dividendsEnabledToStakedBalnDay.getOrDefault(BigInteger.ZERO);
        int size = acceptedTokens.size();
        List<Address> acceptedTokensList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            acceptedTokensList.add(acceptedTokens.get(i));
        }

        for (int i = start; i < end; i++) {
            Map<String, BigInteger> dividends = getDividendsForDay(account, BigInteger.valueOf(i), baln, dex,
                    dividendsSwitchingDay, acceptedTokensList);
            if (dividends.size() != 0) {
                setClaimed(account, BigInteger.valueOf(i));
            }

            totalDividends = addDividends(totalDividends, dividends, acceptedTokensList);
        }

        Map<String, BigInteger> nonZeroTokens = new HashMap<>();
        for (Address token : acceptedTokensList) {
            if (totalDividends.containsKey(token.toString()) && totalDividends.get(token.toString()).signum() > 0) {
                nonZeroTokens.put(token.toString(), totalDividends.get(token.toString()));
                sendToken(account, totalDividends.get(token.toString()), token, "User dividends");
            }
        }
        if (nonZeroTokens.size() > 0) {
            Claimed(account, BigInteger.valueOf(start), BigInteger.valueOf(end), dividendsMapToJson(nonZeroTokens));
        }
    }

    @External
    public void accumulateDividends(Address user, @Optional int _start, @Optional int _end) {
        Context.require(distributionActivate.getOrDefault(false),
                TAG + ": Distribution is not activated. Can't transfer.");

        int[] value = checkStartEnd(_start, _end);
        int start = value[0];
        int end = value[1];
        Map<String, BigInteger> totalDividends = new HashMap<>();

        Address baln = balnScore.get();
        Address dex = dexScore.get();
        BigInteger dividendsSwitchingDay = dividendsEnabledToStakedBalnDay.getOrDefault(BigInteger.ZERO);
        int size = acceptedTokens.size();
        List<Address> acceptedTokensList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            acceptedTokensList.add(acceptedTokens.get(i));
        }

        for (int i = start; i < end; i++) {
            Map<String, BigInteger> dividends = getDividendsForDay(user, BigInteger.valueOf(i), baln, dex,
                    dividendsSwitchingDay, acceptedTokensList);
            if (dividends.size() != 0) {
                setClaimed(user, BigInteger.valueOf(i));
            }

            totalDividends = addDividends(totalDividends, dividends, acceptedTokensList);
        }

        DictDB<Address, BigInteger> userAccruedDividends = accruedDividends.at(user);
        for (Address token : acceptedTokensList) {
            if (totalDividends.containsKey(token.toString()) && totalDividends.get(token.toString()).signum() > 0) {
                BigInteger accruedDividends = totalDividends.get(token.toString());
                BigInteger prevAccruedDividends = userAccruedDividends.getOrDefault(token, BigInteger.ZERO);
                userAccruedDividends.set(token, accruedDividends.add(prevAccruedDividends));
            }
        }
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        Address token = Context.getCaller();
        Context.require(arrayDbContains(acceptedTokens, token), "Dividends contract does not accept " + token);
        if (continuousDividendsActive()) {
            BigInteger dividendsToDaofund = _value.multiply(dividendsPercentage.get(DAOFUND)).divide(EXA);
            DividendsTracker.updateTotalWeight(token, _value.subtract(dividendsToDaofund));
            DividendsReceivedV2(_value, getDay(), _value + " tokens received as dividends token: " + token);
            sendToken(daoFund.get(), dividendsToDaofund, token, "Daofund dividends");
        } else {
            checkForNewDay();
            BigInteger snapId = snapshotId.getOrDefault(BigInteger.ZERO);
            DictDB<String, BigInteger> feesOnSnapId = dailyFees.at(snapId);
            BigInteger previousFees = feesOnSnapId.getOrDefault(token.toString(), BigInteger.ZERO);
            feesOnSnapId.set(token.toString(), previousFees.add(_value));
            DividendsReceivedV2(_value, getDay(), _value + " tokens received as dividends token: " + token);
        }
    }

    @External
    public void updateBalnStake(Address user, BigInteger prevStakedBalance, BigInteger currentTotalSupply) {
        only(balnScore);
        int size = acceptedTokens.size();
        DictDB<Address, BigInteger> userAccruedDividends = accruedDividends.at(user);
        for (int i = 0; i < size; i++) {
            Address token = acceptedTokens.get(i);
            BigInteger accruedDividends = DividendsTracker.updateUserData(token, user, prevStakedBalance, false);
            BigInteger prevAccruedDividends = userAccruedDividends.getOrDefault(token, BigInteger.ZERO);
            userAccruedDividends.set(token, prevAccruedDividends.add(accruedDividends));
        }

        DividendsTracker.setTotalSupply(currentTotalSupply);
    }

    @External(readonly = true)
    public Map<String, BigInteger> getUserDividends(Address _account, @Optional int _start, @Optional int _end) {
        int[] value = checkStartEnd(_start, _end);
        int start = value[0];
        int end = value[1];

        Address baln = balnScore.get();
        Address dex = dexScore.get();
        BigInteger dividendsSwitchingDay = dividendsEnabledToStakedBalnDay.getOrDefault(BigInteger.ZERO);
        int size = acceptedTokens.size();
        List<Address> acceptedTokensList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            acceptedTokensList.add(acceptedTokens.get(i));
        }

        Map<String, BigInteger> totalDividends = new HashMap<>();

        for (int i = start; i < end; i++) {
            Map<String, BigInteger> dividends = getDividendsForDay(_account, BigInteger.valueOf(i), baln, dex,
                    dividendsSwitchingDay, acceptedTokensList);
            totalDividends = addDividends(totalDividends, dividends, acceptedTokensList);
        }

        return totalDividends;
    }

    @External(readonly = true)
    public Map<String, BigInteger> getDaoFundDividends(@Optional int _start, @Optional int _end) {
        int[] value = checkStartEnd(_start, _end);
        int start = value[0];
        int end = value[1];

        Map<String, BigInteger> totalDividends = new HashMap<>();
        int size = acceptedTokens.size();
        List<Address> acceptedTokensList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            acceptedTokensList.add(acceptedTokens.get(i));
        }
        Address dao = daoFund.get();
        for (int i = start; i < end; i++) {
            Map<String, BigInteger> dividends = getDividendsForDaoFund(BigInteger.valueOf(i), acceptedTokensList, dao);
            totalDividends = addDividends(totalDividends, dividends, acceptedTokensList);
        }

        return totalDividends;
    }

    @External(readonly = true)
    public Map<String, BigInteger> dividendsAt(BigInteger _day) {
        Context.require(_day.signum() >= 0,
                TAG + "IRC2Snapshot: day:" + _day + " must be equal to or greater then Zero.");
        Map<String, BigInteger> dividendsDist = new HashMap<>();

        int numberOfCategories = completeDividendsCategories.size();
        for (int i = 0; i < numberOfCategories; i++) {
            String category = completeDividendsCategories.get(i);
            BigInteger totalSnapshotsTaken = totalSnapshots.getOrDefault(category, BigInteger.ZERO);
            BranchDB<BigInteger, DictDB<String, BigInteger>> snapshot = snapshotDividends.at(category);
            if (totalSnapshotsTaken.equals(BigInteger.ZERO)) {
                dividendsDist.put(category, dividendsPercentage.get(category));
                continue;
            } else if (snapshot.at(totalSnapshotsTaken.subtract(BigInteger.ONE)).getOrDefault(IDS, BigInteger.ZERO).compareTo(_day) <= 0) {
                dividendsDist.put(category, snapshot.at(totalSnapshotsTaken.subtract(BigInteger.ONE)).get(AMOUNT));
                continue;
            } else if (snapshot.at(BigInteger.ZERO).get(IDS).compareTo(_day) > 0) {
                dividendsDist.put(category, dividendsPercentage.get(category));
                continue;
            }

            BigInteger low = BigInteger.ZERO;
            BigInteger high = totalSnapshotsTaken.subtract(BigInteger.ONE);
            while (high.compareTo(low) > 0) {
                BigInteger mid = high.subtract((high.subtract(low)).divide(BigInteger.TWO));
                DictDB<String, BigInteger> midValue = snapshot.at(mid);
                BigInteger index = midValue.getOrDefault(IDS, BigInteger.ZERO);
                if (index.equals(_day)) {
                    low = mid;
                    break;
                } else if (index.compareTo(_day) < 0) {
                    low = mid;
                } else
                    high = mid.subtract(BigInteger.ONE);
            }

            dividendsDist.put(category, snapshot.at(low).get(AMOUNT));
        }

        return dividendsDist;
    }

    private int[] checkStartEnd(int start, int end) {
        int batch = dividendsBatchSize.getOrDefault(BigInteger.ZERO).intValue();
        int snap = snapshotId.getOrDefault(BigInteger.ZERO).intValue();
        if (start == 0 && end == 0) {
            end = snap;
            start = Math.max(1, end - batch);
        } else if (end == 0) {
            end = Math.min(snap, start + batch);
        } else if (start == 0) {
            start = Math.max(1, end - batch);
        }

        Context.require(start >= 1 && start < snap, TAG + ": " + "Invalid value of start provided.");
        Context.require(end > 1 && end <= snap, TAG + ": " + "Invalid value of end provided.");
        Context.require(start < end, TAG + ": " + "Start must not be greater than or equal to end.");
        Context.require((end - start) <= batch, TAG + ": " + "Maximum allowed range is " + batch);

        return new int[]{start, end};
    }

    private Map<String, BigInteger> getDividendsForDay(Address account, BigInteger day, Address baln, Address dex,
                                                       BigInteger dividendsSwitchingDay,
                                                       List<Address> acceptedTokensList) {
        boolean claim = isClaimed(account, day);
        if (claim) {
            return Map.of();
        }

        BigInteger stakedBaln = (BigInteger) Context.call(baln, "stakedBalanceOfAt", account, day);
        BigInteger totalStakedBaln = (BigInteger) Context.call(baln, "totalStakedBalanceOfAt", day);

        BigInteger myBalnFromPools = BigInteger.ZERO;
        BigInteger totalBalnFromPools = BigInteger.ZERO;

        List<BigInteger> poolList = new ArrayList<>();
        poolList.add(BALNBNUSD_ID);
        poolList.add(BALNSICX_ID);

        if (dividendsSwitchingDay.equals(BigInteger.ZERO) || (day.compareTo(dividendsSwitchingDay) < 0)) {
            for (BigInteger poolId : poolList) {
                BigInteger myLp = (BigInteger) Context.call(dex, "balanceOfAt", account, poolId, day);
                BigInteger totalLp = (BigInteger) Context.call(dex, "totalSupplyAt", poolId, day);
                BigInteger totalBaln = (BigInteger) Context.call(dex, "totalBalnAt", poolId, day);
                BigInteger equivalentBaln = BigInteger.ZERO;

                if (myLp.compareTo(BigInteger.ZERO) > 0 && totalLp.compareTo(BigInteger.ZERO) > 0 && totalBaln.compareTo(BigInteger.ZERO) > 0) {
                    equivalentBaln = myLp.multiply(totalBaln).divide(totalLp);
                }

                myBalnFromPools = myBalnFromPools.add(equivalentBaln);
                totalBalnFromPools = totalBalnFromPools.add(totalBaln);
            }
        }

        BigInteger myTotalBalnToken = stakedBaln.add(myBalnFromPools);
        BigInteger totalBalnToken = totalStakedBaln.add(totalBalnFromPools);

        Map<String, BigInteger> myDividends = new HashMap<>();
        if (myTotalBalnToken.compareTo(BigInteger.ZERO) > 0 && totalBalnToken.compareTo(BigInteger.ZERO) > 0) {
            Map<String, BigInteger> dividendsDistribution = dividendsAt(day);

            for (Address token : acceptedTokensList) {
                BigInteger numerator = myTotalBalnToken.multiply(dividendsDistribution.get(BALN_HOLDERS)).
                        multiply(dailyFees.at(day).getOrDefault(token.toString(), BigInteger.ZERO));
                BigInteger denominator = totalBalnToken.multiply(EXA);
                myDividends.put(token.toString(), numerator.divide(denominator));
            }
        }

        return myDividends;
    }

    private Map<String, BigInteger> getDividendsForDaoFund(BigInteger day, List<Address> acceptedTokensList,
                                                           Address dao) {
        boolean claim = isClaimed(dao, day);
        if (claim) {
            return Map.of();
        }

        Map<String, BigInteger> daoFundDividends = new HashMap<>();
        for (Address token : acceptedTokensList) {
            Map<String, BigInteger> dividendsDist = dividendsAt(day);
            BigInteger numerator =
                    dividendsDist.get(DAOFUND).multiply(dailyFees.at(day).getOrDefault(token.toString(),
                            BigInteger.ZERO));
            daoFundDividends.put(token.toString(), numerator.divide(EXA));
        }

        return daoFundDividends;
    }

    private Map<String, BigInteger> addDividends(Map<String, BigInteger> totalDividends,
                                                 Map<String, BigInteger> currentDividends,
                                                 List<Address> acceptedTokensList) {
        if (totalDividends.size() > 0 && currentDividends.size() > 0) {
            Map<String, BigInteger> response = new HashMap<>();
            for (Address token : acceptedTokensList) {
                BigInteger totalDividendsValue = totalDividends.get(token.toString());
                BigInteger currentDividendsValue = currentDividends.get(token.toString());

                response.put(token.toString(), totalDividendsValue.add(currentDividendsValue));
            }

            return response;
        } else if (totalDividends.size() > 0) {
            return totalDividends;
        } else if (currentDividends.size() > 0) {
            return currentDividends;
        } else
            return Map.of();
    }

    private void setClaimed(Address account, BigInteger day) {
        DictDB<BigInteger, BigInteger> claimedBitMap = Context.newDictDB(CLAIMED_BIT_MAP + account.toString(),
                BigInteger.class);
        BigInteger claimedWordIndex = day.divide(TWO_FIFTY_SIX);
        BigInteger claimedBitIndex = day.remainder(TWO_FIFTY_SIX);
        int bitShift = claimedBitIndex.intValue();

        claimedBitMap.set(claimedWordIndex,
                claimedBitMap.getOrDefault(claimedWordIndex, BigInteger.ZERO).setBit(bitShift));
    }

    private boolean isClaimed(Address account, BigInteger day) {
        DictDB<BigInteger, BigInteger> claimedBitMap = Context.newDictDB(CLAIMED_BIT_MAP + account.toString(),
                BigInteger.class);
        BigInteger claimedWordIndex = day.divide(TWO_FIFTY_SIX);
        BigInteger claimedBitIndex = day.remainder(TWO_FIFTY_SIX);

        BigInteger claimedWord = claimedBitMap.getOrDefault(claimedWordIndex, BigInteger.ZERO);
        int bitShift = claimedBitIndex.intValue();
        BigInteger mask = BigInteger.ONE.shiftLeft(bitShift);

        return claimedWord.and(mask).equals(mask);
    }

    private void updateDividendsSnapshot(String category, BigInteger percent) {
        BigInteger currentDay = getDay();
        BigInteger totalSnapshotsTaken = totalSnapshots.getOrDefault(category, BigInteger.ZERO);
        BranchDB<BigInteger, DictDB<String, BigInteger>> snapshot = snapshotDividends.at(category);
        BigInteger value = snapshot.at(totalSnapshotsTaken.subtract(BigInteger.ONE)).get(IDS);

        if (totalSnapshotsTaken.compareTo(BigInteger.ZERO) > 0 && value.equals(currentDay)) {
            snapshot.at(totalSnapshotsTaken.subtract(BigInteger.ONE)).set(AMOUNT, percent);
        } else {
            snapshot.at(totalSnapshotsTaken).set(IDS, currentDay);
            snapshot.at(totalSnapshotsTaken).set(AMOUNT, percent);
            totalSnapshots.set(category, totalSnapshotsTaken.add(BigInteger.ONE));
        }
    }

    private void checkForNewDay() {
        if (timeOffset.getOrDefault(BigInteger.ZERO).equals(BigInteger.ZERO)) {
            setTimeOffset();
        }
        BigInteger currentSnapshotId = getDay();
        if (snapshotId.getOrDefault(BigInteger.ZERO).compareTo(currentSnapshotId) < 0) {
            snapshotId.set(currentSnapshotId);
        }
    }

    private void sendToken(Address to, BigInteger amount, Address token, String msg) {
        Context.call(token, "transfer", to, amount);
        FundTransfer(to, amount, msg + amount + " token sent to" + to);
    }

    private BigInteger getBalnBalance(Address user) {
        return Context.call(BigInteger.class, balnScore.get(), "stakedBalanceOf", user);
    }

    private void setTimeOffset() {
        BigInteger offsetTime = (BigInteger) Context.call(dexScore.get(), "getTimeOffset");
        timeOffset.set(offsetTime);
    }

    private void addInitialCategories() {
        completeDividendsCategories.add(DAO_FUND);
        completeDividendsCategories.add(BALN_HOLDERS);
        dividendsPercentage.set(DAO_FUND, BigInteger.valueOf(4).multiply(pow(BigInteger.TEN, 17)));
        dividendsPercentage.set(BALN_HOLDERS, BigInteger.valueOf(6).multiply(pow(BigInteger.TEN, 17)));
    }

    private String dividendsMapToJson(Map<String, BigInteger> map) {
        StringBuilder mapAsJson = new StringBuilder("{");
        for (String key : map.keySet()) {
            mapAsJson.append("'").append(key).append("': ").append(map.get(key)).append(", ");
        }
        mapAsJson.delete(mapAsJson.length() - 2, mapAsJson.length()).append("}");
        return mapAsJson.toString();
    }

    @EventLog(indexed = 3)
    public void FundTransfer(Address destination, BigInteger amount, String note) {

    }

    @EventLog(indexed = 2)
    public void DividendsReceivedV2(BigInteger _amount, BigInteger _day, String _data) {

    }

    @EventLog(indexed = 1)
    public void Claimed(Address _address, BigInteger _start, BigInteger _end, String _dividends) {

    }
}