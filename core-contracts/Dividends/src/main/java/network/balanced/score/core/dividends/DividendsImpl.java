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

package network.balanced.score.core.dividends;

import foundation.icon.xcall.NetworkAddress;
import network.balanced.score.lib.interfaces.Dividends;
import network.balanced.score.lib.interfaces.DividendsXCall;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.structs.PrepDelegations;
import network.balanced.score.lib.utils.*;
import score.*;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import scorex.util.ArrayList;
import scorex.util.HashMap;

import java.lang.Math;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.dividends.Constants.*;
import static network.balanced.score.core.dividends.DividendsTracker.getBoostedTotalSupply;
import static network.balanced.score.core.dividends.DividendsTracker.userBalance;
import static network.balanced.score.lib.utils.ArrayDBUtils.arrayDbContains;
import static network.balanced.score.lib.utils.ArrayDBUtils.removeFromArraydb;
import static network.balanced.score.lib.utils.BalancedAddressManager.*;
import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
import static network.balanced.score.lib.utils.Math.pow;

public class DividendsImpl implements Dividends {

    private static final VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);

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

    private static final VarDB<BigInteger> dividendsBatchSize = Context.newVarDB(DIVIDENDS_BATCH_SIZE,
            BigInteger.class);
    private static final VarDB<BigInteger> timeOffset = Context.newVarDB(TIME_OFFSET, BigInteger.class);
    private static final VarDB<BigInteger> dividendsEnabledToStakedBalnDay =
            Context.newVarDB(DIVIDENDS_ENABLED_TO_STAKED_BALN_ONLY_DAY, BigInteger.class);

    private static final NetworkAddressBranchDictDB<Address, BigInteger> accruedDividends =
            new NetworkAddressBranchDictDB<>(ACCRUED_DIVIDENDS, BigInteger.class);

    private final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);
    public static String NATIVE_NID;

    public DividendsImpl(@Optional Address _governance) {
        if (governance.get() == null) {
            isContract(_governance);
            DividendsImpl.governance.set(_governance);
            snapshotId.set(BigInteger.ONE);
            dividendsBatchSize.set(BigInteger.valueOf(50));
            addInitialCategories();
        }


        if (governance.get() != null) {
            setGovernance(governance.get());
        }

        if (currentVersion.getOrDefault("").equals(Versions.DIVIDENDS)) {
            Context.revert("Can't Update same version of code");
        }
        currentVersion.set(Versions.DIVIDENDS);
        NATIVE_NID = (String) Context.call(getXCall(), "getNetworkId");
    }

    @External(readonly = true)
    public String name() {
        return Names.DIVIDENDS;
    }

    @External(readonly = true)
    public String version() {
        return currentVersion.getOrDefault("");
    }

    @External
    public void updateAddress(String name) {
        resetAddress(name);
    }

    @External(readonly = true)
    public Address getAddress(String name) {
        return getAddressByName(name);
    }

    @External
    public void setTimeOffset(BigInteger deltaTime) {
        onlyGovernance();
        timeOffset.set(deltaTime);
    }

    protected String getStringNetworkAddress(Address address){
        return new NetworkAddress(NATIVE_NID, address).toString();
    }

    @External
    public void setDividendsOnlyToStakedBalnDay(BigInteger day) {
        onlyGovernance();
        Context.require(day.compareTo(snapshotId.getOrDefault(BigInteger.ZERO)) > 0,
                TAG + ": Day should be greater than the current snapshot ID.");
        dividendsEnabledToStakedBalnDay.set(day);
    }

    @External(readonly = true)
    public BigInteger getDividendsOnlyToStakedBalnDay() {
        return dividendsEnabledToStakedBalnDay.getOrDefault(BigInteger.ZERO);
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
        onlyGovernance();
        isContract(_token);

        if (!arrayDbContains(acceptedTokens, _token)) {
            acceptedTokens.add(_token);
        }
    }

    @External
    public void removeAcceptedTokens(Address _token) {
        onlyGovernance();
        removeFromArraydb(_token, acceptedTokens);
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
        onlyGovernance();
        Context.require(!arrayDbContains(completeDividendsCategories, _category),
                TAG + ": " + _category + " is already added.");
        completeDividendsCategories.add(_category);
    }

    @External
    public void removeDividendsCategory(String _category) {
        onlyGovernance();
        Context.require(arrayDbContains(completeDividendsCategories, _category),
                TAG + ": " + _category + " not found in the list of dividends categories.");
        Context.require(dividendsPercentage.get(_category).equals(BigInteger.ZERO),
                TAG + ": Please make the category percentage to 0 before removing.");


        removeFromArraydb(_category, completeDividendsCategories);
    }

    @External(readonly = true)
    public Map<String, BigInteger> getDividendsPercentage() {
        Map<String, BigInteger> dividendsDist = new HashMap<>();
        int numberOfCategories = completeDividendsCategories.size();
        for (int i = 0; i < numberOfCategories; i++) {
            String category = completeDividendsCategories.get(i);
            dividendsDist.put(category, dividendsPercentage.getOrDefault(category, BigInteger.ZERO));
        }

        return dividendsDist;
    }

    @External
    public void setDividendsCategoryPercentage(DistributionPercentage[] _dist_list) {
        onlyGovernance();
        BigInteger totalPercentage = BigInteger.ZERO;
        Context.require(_dist_list.length == completeDividendsCategories.size(),
                TAG + ": Categories count mismatched!");

        for (DistributionPercentage id : _dist_list) {
            String category = id.recipient_name;
            BigInteger percent = id.dist_percent;
            Context.require(arrayDbContains(completeDividendsCategories, category),
                    TAG + ": " + category + " is not a valid dividends category");

            dividendsPercentage.set(category, percent);
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
        onlyGovernance();
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
        onlyGovernance();
        Context.call(getStaking(), "delegate", (Object) prepDelegations);
    }

    @External
    public boolean distribute() {
        checkStatus();
        return true;
    }

    @External
    public void transferDaofundDividends(@Optional int _start, @Optional int _end) {
        checkStatus();
        Address daofund = getDaofund();

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
                setClaimed(daofund.toString(), BigInteger.valueOf(i));
            }

            totalDividends = addDividends(totalDividends, dividends, acceptedTokensList);
        }

        Map<String, BigInteger> nonZeroTokens = new HashMap<>();
        for (int i = 0; i < size; i++) {
            Address token = acceptedTokensList.get(i);

            if (totalDividends.containsKey(token.toString()) && totalDividends.get(token.toString()).signum() > 0) {
                nonZeroTokens.put(token.toString(), totalDividends.get(token.toString()));
                sendToken(daofund.toString(), totalDividends.get(token.toString()), token, "Daofund dividends");
            }
        }
        if (nonZeroTokens.size() > 0) {
            Claimed(daofund, BigInteger.valueOf(start), BigInteger.valueOf(end), dividendsMapToJson(nonZeroTokens));
        }
    }

    protected BigInteger calculateAccruedDividends(Address token, String user, boolean readonly) {
        BigInteger accruedDividends = BigInteger.ZERO;
        BigInteger balance = getBalnBalance(user);
        BigInteger bbalnBalance = userBalance.getOrDefault(user, BigInteger.ZERO);
        if (!DividendsTracker.balnRewardsClaimed(user, token)) {
            accruedDividends = DividendsTracker.updateUserData
                    (token, user, balance, readonly);
        }

        accruedDividends = accruedDividends.add(DividendsTracker.updateBoostedUserData(token,
                user, bbalnBalance, readonly));

        return accruedDividends;
    }


    @External(readonly = true)
    public Map<String, BigInteger> getUnclaimedDividends(Address user) {
        Map<String, BigInteger> totalDividends = new HashMap<>();

        int size = acceptedTokens.size();
        for (int i = 0; i < size; i++) {
            Address token = acceptedTokens.get(i);
            BigInteger calcAccruedDividends = calculateAccruedDividends(token, new NetworkAddress(NATIVE_NID, user).toString(), true);
            BigInteger prevAccruedDividends = accruedDividends.getOrDefault(new NetworkAddress(NATIVE_NID, user), token, BigInteger.ZERO);
            BigInteger totalDivs = calcAccruedDividends.add(prevAccruedDividends);
            totalDividends.put(token.toString(), totalDivs);
        }

        return totalDividends;
    }

    @External
    public void handleCallMessage(String _from, byte[] _data, @Optional String[] _protocols) {
        Check.checkStatus();
        only(getXCall());
        XCallUtils.verifyXCallProtocols(_from, _protocols);
        DividendsXCall.process(this, _from, _data);
    }

    public void xClaimDividends(String from){
        claimDividendsInternal(from);
    }

    @External
    public void claimDividends() {
        Address user = Context.getCaller();
        claimDividendsInternal(getStringNetworkAddress(user));
    }

    private void claimDividendsInternal(String user){
        checkStatus();
        routeFees();
        int size = acceptedTokens.size();
        Map<String, BigInteger> nonZeroTokens = new HashMap<>();
        for (int i = 0; i < size; i++) {
            Address token = acceptedTokens.get(i);
            BigInteger calcAccruedDividends = calculateAccruedDividends(token, user, false);
            BigInteger prevAccruedDividends = accruedDividends.getOrDefault(NetworkAddress.valueOf(user), token, BigInteger.ZERO);
            BigInteger totalDivs = calcAccruedDividends.add(prevAccruedDividends);
            if (totalDivs.signum() > 0) {
                nonZeroTokens.put(token.toString(), totalDivs);
                accruedDividends.set(NetworkAddress.valueOf(user), token, null);
                sendToken(user, totalDivs, token, "User dividends");
            }
        }
        if (nonZeroTokens.size() > 0) {
            ClaimedV2(user, BigInteger.ZERO, BigInteger.ZERO, dividendsMapToJson(nonZeroTokens));
        }

        BigInteger bbalnBalance = getBBalnBalance(user);
        BigInteger prevBalance = userBalance.getOrDefault(user, BigInteger.ZERO);
        userBalance.set(user, bbalnBalance);
        DividendsTracker.setBBalnTotalSupply(getBoostedTotalSupply().add(bbalnBalance).subtract(prevBalance));
    }

    public void xClaim(String from, @Optional Integer _start, @Optional Integer _end){
        claimInternal(from, _start, _end);
    }

    @External
    public void claim(@Optional int _start, @Optional int _end) {
        Address account = Context.getCaller();
        claimInternal(getStringNetworkAddress(account), _start, _end);
    }

    private void claimInternal(String account, @Optional int _start, @Optional int _end) {
        checkStatus();

        int[] value = checkStartEnd(_start, _end);
        int start = value[0];
        int end = value[1];
        Map<String, BigInteger> totalDividends = new HashMap<>();

        Address baln = getBaln();
        Address dex = getDex();
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
            ClaimedV2(account, BigInteger.valueOf(start), BigInteger.valueOf(end), dividendsMapToJson(nonZeroTokens));
        }
    }

    public void xAccumulateDividends(String from, @Optional Integer _start, @Optional Integer _end){
        accumulateDividendsInternal(from, _start, _end);
    }

    @External
    public void accumulateDividends(Address user, @Optional int _start, @Optional int _end) {
        accumulateDividendsInternal(getStringNetworkAddress(user), _start, _end);
    }

    private void accumulateDividendsInternal(String user, @Optional int _start, @Optional int _end) {
        checkStatus();

        int[] value = checkStartEnd(_start, _end);
        int start = value[0];
        int end = value[1];
        Map<String, BigInteger> totalDividends = new HashMap<>();

        Address baln = getBaln();
        Address dex = getDex();
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

        for (Address token : acceptedTokensList) {
            if (totalDividends.containsKey(token.toString()) && totalDividends.get(token.toString()).signum() > 0) {
                BigInteger calcAccruedDividends = totalDividends.get(token.toString());
                BigInteger prevAccruedDividends = accruedDividends.getOrDefault(NetworkAddress.valueOf(user), token, BigInteger.ZERO);
                accruedDividends.set(NetworkAddress.valueOf(user), token, calcAccruedDividends.add(prevAccruedDividends));
            }
        }
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        checkStatus();
        Address token = Context.getCaller();
        Context.require(arrayDbContains(acceptedTokens, token), "Dividends contract does not accept " + token);
        String daoFundNetworkAddress = new NetworkAddress(NATIVE_NID, getDaofund()).toString();
        if (getBoostedTotalSupply().equals(BigInteger.ZERO)) {
            sendToken( daoFundNetworkAddress, _value, token, "Daofund dividends");
            return;
        }

        BigInteger dividendsToDaofund = _value.multiply(dividendsPercentage.get(DAOFUND)).divide(EXA);

        // update boosted total weight if the bbaln day is started
        DividendsTracker.updateBoostedTotalWeight(token, _value.subtract(dividendsToDaofund));
        DividendsReceivedV2(_value, getDay(), _value + " tokens received as dividends token: " + token);
        sendToken( daoFundNetworkAddress, dividendsToDaofund, token, "Daofund dividends");
    }

//    @External
//    public void updateBalnStake(Address user, BigInteger prevStakedBalance, BigInteger currentTotalSupply) {
//        checkStatus();
//        only(getBaln());
//        int size = acceptedTokens.size();
//        DictDB<Address, BigInteger> userAccruedDividends = accruedDividends.at(user);
//        for (int i = 0; i < size; i++) {
//            Address token = acceptedTokens.get(i);
//            if (DividendsTracker.balnRewardsClaimed(user, token)) {
//                return;
//            }
//
//            BigInteger accruedDividends = DividendsTracker.updateUserData(token, user, prevStakedBalance, false);
//            BigInteger prevAccruedDividends = userAccruedDividends.getOrDefault(token, BigInteger.ZERO);
//            userAccruedDividends.set(token, prevAccruedDividends.add(accruedDividends));
//        }
//
//        DividendsTracker.setTotalSupply(currentTotalSupply);
//    }

    @External(readonly = true)
    public Map<String, BigInteger> getUserDividends(Address _account, @Optional int _start, @Optional int _end) {
        int[] value = checkStartEnd(_start, _end);
        int start = value[0];
        int end = value[1];

        Address baln = getBaln();
        Address dex = getDex();
        BigInteger dividendsSwitchingDay = dividendsEnabledToStakedBalnDay.getOrDefault(BigInteger.ZERO);
        int size = acceptedTokens.size();
        List<Address> acceptedTokensList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            acceptedTokensList.add(acceptedTokens.get(i));
        }

        Map<String, BigInteger> totalDividends = new HashMap<>();

        for (int i = start; i < end; i++) {
            Map<String, BigInteger> dividends = getDividendsForDay(new NetworkAddress(NATIVE_NID, _account).toString(), BigInteger.valueOf(i), baln, dex,
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

        Address dao = getDaofund();
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

    @External
    public void onKick(String user) {
        checkStatus();
        only(getBoostedBaln());
        BigInteger userPrevBalance = userBalance.getOrDefault(user, BigInteger.ZERO);
        Context.require(!userPrevBalance.equals(BigInteger.ZERO), TAG + " " + user + " User with no balance can not " +
                "be kicked.");
        updateUserDividends(user, userPrevBalance);
        userBalance.set(user, null);
        DividendsTracker.setBBalnTotalSupply(getBoostedTotalSupply().subtract(userPrevBalance));
        UserKickedV2(user, "user kicked".getBytes());
    }

    @External
    public void onBalanceUpdate(String user, BigInteger bBalnBalance) {
        checkStatus();
        only(getBoostedBaln());
        BigInteger prevBalance = userBalance.getOrDefault(user, BigInteger.ZERO);
        updateUserDividends(user, prevBalance);
        userBalance.set(user, bBalnBalance);
        DividendsTracker.setBBalnTotalSupply(getBoostedTotalSupply().add(bBalnBalance).subtract(prevBalance));
    }

    private void updateUserDividends(String user, BigInteger prevBalance) {
        int size = acceptedTokens.size();
        for (int i = 0; i < size; i++) {
            Address token = acceptedTokens.get(i);
            BigInteger calcAccruedDividends = DividendsTracker.updateBoostedUserData(token, user, prevBalance, false);
            BigInteger prevAccruedDividends = accruedDividends.getOrDefault(NetworkAddress.valueOf(user), token, BigInteger.ZERO);
            accruedDividends.set(NetworkAddress.valueOf(user), token, prevAccruedDividends.add(calcAccruedDividends));
        }
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

    private Map<String, BigInteger> getDividendsForDay(String account, BigInteger day, Address baln, Address dex,
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
        boolean claim = isClaimed(new NetworkAddress(NATIVE_NID, dao).toString(), day);
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

    private void setClaimed(String account, BigInteger day) {
        DictDB<BigInteger, BigInteger> claimedBitMap = Context.newDictDB(CLAIMED_BIT_MAP + account,
                BigInteger.class);
        BigInteger claimedWordIndex = day.divide(TWO_FIFTY_SIX);
        BigInteger claimedBitIndex = day.remainder(TWO_FIFTY_SIX);
        int bitShift = claimedBitIndex.intValue();

        claimedBitMap.set(claimedWordIndex,
                claimedBitMap.getOrDefault(claimedWordIndex, BigInteger.ZERO).setBit(bitShift));
    }

    private boolean isClaimed(String account, BigInteger day) {
        DictDB<BigInteger, BigInteger> claimedBitMap = Context.newDictDB(CLAIMED_BIT_MAP + account,
                BigInteger.class);
        BigInteger claimedWordIndex = day.divide(TWO_FIFTY_SIX);
        BigInteger claimedBitIndex = day.remainder(TWO_FIFTY_SIX);

        BigInteger claimedWord = claimedBitMap.getOrDefault(claimedWordIndex, BigInteger.ZERO);
        int bitShift = claimedBitIndex.intValue();
        BigInteger mask = BigInteger.ONE.shiftLeft(bitShift);

        return claimedWord.and(mask).equals(mask);
    }

    private void sendToken(String to, BigInteger amount, Address token, String msg) {
        TokenTransfer.transfer(token, to, amount);
        FundTransferV2(to, amount, msg + amount + " token sent to" + to);
    }


    private BigInteger getBalnBalance(String user) {
        return BigInteger.ZERO;
    }

    private BigInteger getBBalnBalance(String user) {
        return Context.call(BigInteger.class, getBoostedBaln(), "xBalanceOf", user, BigInteger.ZERO);
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

    private void routeFees() {
        try {
            Context.call(getFeehandler(), "routeFees");
        } catch (Exception e) {
        }
    }

    @EventLog(indexed = 3)
    public void FundTransfer(Address destination, BigInteger amount, String note) {

    }

    @EventLog(indexed = 3)
    public void FundTransferV2(String destination, BigInteger amount, String note) {

    }

    @EventLog(indexed = 2)
    public void DividendsReceivedV2(BigInteger _amount, BigInteger _day, String _data) {

    }

    @EventLog(indexed = 1)
    public void Claimed(Address _address, BigInteger _start, BigInteger _end, String _dividends) {

    }

    @EventLog(indexed = 1)
    public void ClaimedV2(String _address, BigInteger _start, BigInteger _end, String _dividends) {

    }

    @EventLog(indexed = 1)
    public void UserKicked(Address user, byte[] _data) {

    }

    @EventLog(indexed = 1)
    public void UserKickedV2(String user, byte[] _data) {

    }
}