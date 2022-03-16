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

package network.balanced.score.core.staking;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import com.eclipsesource.json.JsonValue;
import network.balanced.score.core.staking.db.LinkedListDB;
import network.balanced.score.core.staking.utils.Constant;
import network.balanced.score.core.staking.utils.PrepDelegations;
import score.Address;
import score.Context;
import score.VarDB;
import score.ArrayDB;
import score.DictDB;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;
import score.annotation.EventLog;
import scorex.util.ArrayList;
import scorex.util.HashMap;
import scorex.util.StringTokenizer;


import static network.balanced.score.core.staking.utils.Checks.*;
import static network.balanced.score.core.staking.utils.Constant.*;

public class Staking {

    private final VarDB<BigInteger> rate = Context.newVarDB(RATE, BigInteger.class);
    private final VarDB<BigInteger> blockHeightWeek = Context.newVarDB(BLOCK_HEIGHT_WEEK, BigInteger.class);
    private final VarDB<BigInteger> blockHeightDay = Context.newVarDB(BLOCK_HEIGHT_DAY, BigInteger.class);
    private final VarDB<Address> sicxAddress = Context.newVarDB(SICX_ADDRESS, Address.class);
    private final VarDB<BigInteger> totalStake = Context.newVarDB(TOTAL_STAKE, BigInteger.class);
    private final VarDB<BigInteger> dailyReward = Context.newVarDB(DAILY_REWARD, BigInteger.class);
    private final VarDB<BigInteger> totalLifetimeReward = Context.newVarDB(TOTAL_LIFETIME_REWARD, BigInteger.class);
    private final VarDB<Boolean> distributing = Context.newVarDB(DISTRIBUTING, Boolean.class);
    private final VarDB<BigInteger> totalUnstakeAmount = Context.newVarDB(TOTAL_UNSTAKE_AMOUNT, BigInteger.class);
    private final ArrayDB<Address> topPreps = Context.newArrayDB(TOP_PREPS, Address.class);
    private final ArrayDB<Address> prepList = Context.newArrayDB(PREP_LIST, Address.class);
    private final VarDB<BigInteger> icxToClaim = Context.newVarDB(ICX_TO_CLAIM, BigInteger.class);
    private final DictDB<String, String> addressDelegations = Context.newDictDB(ADDRESS_DELEGATIONS, String.class);
    private final DictDB<Address, BigInteger> icxPayable = Context.newDictDB(ICX_PAYABLE, BigInteger.class);
    private final DictDB<String, BigInteger> prepDelegations = Context.newDictDB(PREP_DELEGATIONS, BigInteger.class);
    private final VarDB<BigInteger> unstakeBatchLimit = Context.newVarDB(UNSTAKE_BATCH_LIMIT, BigInteger.class);
    public static final VarDB<Boolean> stakingOn = Context.newVarDB(STAKING_ON, Boolean.class);
    private final LinkedListDB unstakeRequestList = new LinkedListDB("unstake_dict");

    public Staking() {

        if (blockHeightDay.get() == null) {
            Map<String, Object> termDetails = (Map<String, Object>) Context.call(SYSTEM_SCORE_ADDRESS, "getIISSInfo");
            BigInteger nextPrepTerm = (BigInteger) termDetails.get("nextPRepTerm");
            blockHeightWeek.set(nextPrepTerm);
            blockHeightDay.set(nextPrepTerm);
            rate.set(ONE_EXA);
            distributing.set(false);
            setTopPreps();
            unstakeBatchLimit.set(DEFAULT_UNSTAKE_BATCH_LIMIT);
            stakingOn.set(false);
        }
    }

    // Event logs
    @EventLog(indexed = 3)
    public void Transfer(Address _from, Address _to, BigInteger _value, byte[] _data) {
    }

    @EventLog(indexed = 2)
    public void FundTransfer(Address destination, BigInteger amount, String note) {
    }

    @EventLog(indexed = 2)
    public void TokenTransfer(Address recipient, BigInteger amount, String note) {
    }

    @EventLog(indexed = 2)
    public void UnstakeRequest(Address sender, BigInteger amount) {
    }

    @EventLog(indexed = 2)
    public void UnstakeAmountTransfer(Address receiver, BigInteger amount) {
    }

    @EventLog(indexed = 2)
    public void IscoreClaimed(BigInteger block_height, BigInteger rewards) {
    }

    // Read Only methods
    @External(readonly = true)
    public String name() {
        return Constant.TAG;
    }

    @External
    public void setBlockHeightWeek(BigInteger _height) {
        onlyOwner();
        blockHeightWeek.set(_height);
    }

    @External(readonly = true)
    public BigInteger getBlockHeightWeek() {
        return blockHeightWeek.getOrDefault(BigInteger.ZERO);
    }

    @External(readonly = true)
    public BigInteger getTodayRate() {
        return rate.getOrDefault(ONE_EXA);
    }

    @External
    public void toggleStakingOn() {
        onlyOwner();
        stakingOn.set(!stakingOn.getOrDefault(false));
    }

    @External(readonly = true)
    public Address getSicxAddress() {
        return sicxAddress.get();
    }

    @External
    public void setUnstakeBatchLimit(BigInteger _limit) {
        onlyOwner();
        unstakeBatchLimit.set(_limit);
    }

    @External(readonly = true)
    public BigInteger getUnstakeBatchLimit() {
        return unstakeBatchLimit.get();
    }

    @External(readonly = true)
    public List<Address> getPrepList() {
        List<Address> prepList = new ArrayList<>();
        for (int i = 0; i < this.prepList.size(); i++) {
            Address prep = this.prepList.get(i);
            prepList.add(prep);
        }
        return prepList;
    }

    @External(readonly = true)
    public BigInteger getUnstakingAmount() {
        return totalUnstakeAmount.getOrDefault(BigInteger.ZERO);
    }

    @External(readonly = true)
    public BigInteger getTotalStake() {
        return totalStake.getOrDefault(BigInteger.ZERO);
    }

    @External(readonly = true)
    public BigInteger getLifetimeReward() {
        return totalLifetimeReward.getOrDefault(BigInteger.ZERO);
    }

    @External(readonly = true)
    public List<Address> getTopPreps() {
        List<Address> topPreps = new ArrayList<>();
        for (int i = 0; i < this.topPreps.size(); i++) {
            Address prep = this.topPreps.get(i);
            topPreps.add(prep);
        }
        return topPreps;
    }

    @External(readonly = true)
    public Map<String, BigInteger> getPrepDelegations() {
        Map<String, BigInteger> prepDelegations = new HashMap<>();
        for (int i = 0; i < this.prepList.size(); i++) {
            Address prep = this.prepList.get(i);
            prepDelegations.put(prep.toString(), this.prepDelegations.getOrDefault(prep.toString(), BigInteger.ZERO));
        }
        return prepDelegations;
    }

    @External
    public void setSicxAddress(Address _address) {
        onlyOwner();
        Context.require(_address.isContract(), Constant.TAG + ": Address provided is an EOA address. A contract " +
                "address is required.");
        sicxAddress.set(_address);
    }

    public BigInteger percentToIcx(BigInteger votingPercentage, BigInteger totalAmount) {
        return votingPercentage.multiply(totalAmount).divide(HUNDRED_PERCENTAGE);
    }

    public void setAddressDelegations(Address to, Address prep, BigInteger votesInPer, BigInteger totalIcxHold) {
        int checkVal = totalIcxHold.compareTo(BigInteger.ZERO);
        if (checkVal != 0) {
            BigInteger value = percentToIcx(votesInPer, totalIcxHold);
            setPrepDelegations(prep.toString(), value);
        }
    }

    public void setPrepDelegations(String prep, BigInteger value) {
        BigInteger prepDelegations = this.prepDelegations.getOrDefault(prep, BigInteger.ZERO);
        this.prepDelegations.set(prep, prepDelegations.add(value));
    }

    @External(readonly = true)
    public BigInteger claimableICX(Address _address) {
        return icxPayable.getOrDefault(_address, BigInteger.ZERO);
    }

    @External(readonly = true)
    public BigInteger totalClaimableIcx() {
        return icxToClaim.getOrDefault(BigInteger.ZERO);
    }

    @Payable
    public void fallback() {
        stakeICX(Context.getCaller(), null);
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        stakingOn();
        if (!Context.getCaller().equals(sicxAddress.get())) {
            Context.revert(Constant.TAG + ": The Staking contract only accepts sICX tokens." + Context.getCaller() +
                    " or " + sicxAddress.get());
        }
        try {
            String unpackedData = new String(_data);
            JsonObject json = Json.parse(unpackedData).asObject();
            String method = json.get("method").asString();
            if (method.equals("unstake")) {
                JsonValue user = json.get("user");
                if (user != null) {
                    unstake(_from, _value, Address.fromString(json.get("user").asString()));
                } else {
                    unstake(_from, _value, null);
                }
            } else {
                Context.revert(Constant.TAG + ": Invalid Parameters.");
            }
        } catch (Exception e) {
            Context.revert(Constant.TAG + ": Invalid data:." + _data);
        }
    }

    @SuppressWarnings("unchecked")
    public void checkForIscore() {
        Map<String, Object> iscoreDetails = (Map<String, Object>) Context.call(SYSTEM_SCORE_ADDRESS, "queryIScore",
                Context.getAddress());
        BigInteger iscoreGenerated = (BigInteger) iscoreDetails.get("estimatedICX");
        if (iscoreGenerated.compareTo(BigInteger.ZERO) > 0) {
            Context.call(SYSTEM_SCORE_ADDRESS, "claimIScore");
            IscoreClaimed(BigInteger.valueOf(Context.getBlockHeight()), iscoreGenerated);
            distributing.set(true);
        }
    }

    // Created only for test
    @External(readonly = true)
    public boolean getDistributing() {
        return distributing.getOrDefault(false);
    }

    @External
    public void claimUnstakedICX(@Optional Address _to) {
        if (_to == null) {
            _to = Context.getCaller();
        }
        BigInteger payableIcx = icxPayable.getOrDefault(_to, BigInteger.ZERO);
        if (payableIcx.compareTo(BigInteger.ZERO) > 0) {
            BigInteger unclaimedIcx = icxToClaim.getOrDefault(BigInteger.ZERO).subtract(payableIcx);
            icxToClaim.set(unclaimedIcx);
            icxPayable.set(_to, null);
            sendIcx(_to, payableIcx, "");
            UnstakeAmountTransfer(_to, payableIcx);
        }
    }

    public void sendIcx(Address to, BigInteger amount, String msg) {
        if (msg == null) {
            msg = "";
        }
        try {
            Context.transfer(to, amount);
            FundTransfer(to, amount, msg + amount + " ICX sent to " + to + ".");
        } catch (Exception e) {
            Context.revert(Constant.TAG + ": " + amount + " ICX not sent to " + to + ".");
        }
    }

    @SuppressWarnings("unchecked")
    public List<Address> setTopPreps() {
        Map<String, Object> prepDict = (Map<String, Object>) Context.call(SYSTEM_SCORE_ADDRESS, "getPReps", 1,
                Constant.TOP_PREP_COUNT);
        List<Map<String, Object>> prepDetails = (List<Map<String, Object>>) prepDict.get("preps");
        List<Address> allPrepAddresses = getPrepList();
        List<Address> topPreps = new ArrayList<>();
        for (Map<String, Object> preps : prepDetails) {
            Address prepAddress = (Address) preps.get("address");
            if (!allPrepAddresses.contains(prepAddress)) {
                prepList.add(prepAddress);
            }
            topPreps.add(prepAddress);
            this.topPreps.add(prepAddress);
        }
        return topPreps;
    }

    @External(readonly = true)
    public Map<String, BigInteger> getAddressDelegations(Address _address) {
        Map<String, BigInteger> delegationIcx = new HashMap<>();
        Map<String, BigInteger> delegationPercent = getDelegationInPercentage(_address);
        BigInteger balance = (BigInteger) Context.call(sicxAddress.get(), "balanceOf", _address);
        BigInteger totalIcxHold = (balance.multiply(getTodayRate())).divide(ONE_EXA);
        for (Map.Entry<String, BigInteger> delegation : delegationPercent.entrySet()) {
            String prepName = delegation.getKey();
            BigInteger votesPercentage = delegation.getValue();
            BigInteger votesIcx = (votesPercentage.multiply(totalIcxHold)).divide(HUNDRED_PERCENTAGE);
            delegationIcx.put(prepName, votesIcx);
        }
        return delegationIcx;
    }

    public void delegateVotes(Address to, PrepDelegations[] userDelegations, BigInteger userIcxHold) {
        BigInteger totalPercentage = BigInteger.ZERO;
        List<Address> similarPrepCheck = new ArrayList<>();
        StringBuilder addressDelegations = new StringBuilder();
        List<Address> allPrepAddresses = getPrepList();
        for (PrepDelegations singlePrep : userDelegations) {
            Address prepAddress = singlePrep._address;
            BigInteger votesInPercentage = singlePrep._votes_in_per;
            if (!allPrepAddresses.contains(prepAddress)) {
                prepList.add(prepAddress);
            }
            if (similarPrepCheck.contains(prepAddress)) {
                Context.revert(Constant.TAG + ": You can not delegate same Prep twice in a transaction.Your " +
                        "delegation preference is" + userDelegations);
            }
            if (votesInPercentage.compareTo(MINIMUM_DELEGATION_PERCENTAGE) < 0) {
                Context.revert(Constant.TAG + ": You should provide delegation percentage more than 0.001. Your " +
                        "delegation preference is " + userDelegations + ".");
            }
            similarPrepCheck.add(prepAddress);
            totalPercentage = totalPercentage.add(votesInPercentage);
            addressDelegations.append(prepAddress).append(":").append(votesInPercentage).append(".");
            setAddressDelegations(to, prepAddress, votesInPercentage, userIcxHold);
        }
        if (!totalPercentage.equals(HUNDRED_PERCENTAGE)) {
            Context.revert(Constant.TAG + ": Total delegations should be 100%.Your delegation preference is ");
        }
        this.addressDelegations.set(to.toString(), addressDelegations.toString());
    }

    public void setEvenAddressDelegationsInPercentage(Address to) {
        int totalPrepsToDelegate = topPreps.size();
        BigInteger topPrepsCount = BigInteger.valueOf(totalPrepsToDelegate);
        BigInteger evenlyDistribution = HUNDRED_PERCENTAGE.divide(topPrepsCount);
        StringBuilder addressDelegation = new StringBuilder();
        BigInteger balance = (BigInteger) Context.call(sicxAddress.get(), "balanceOf", to);
        BigInteger userIcxBalance = balance.multiply(getTodayRate()).divide(ONE_EXA);
        for (int i = 0; i < totalPrepsToDelegate; i++) {
            Address prep = this.topPreps.get(i);
            addressDelegation.append(prep).append(":").append(evenlyDistribution).append(".");
            setAddressDelegations(to, prep, evenlyDistribution, userIcxBalance);
        }
        addressDelegations.set(to.toString(), addressDelegation.toString());
    }

    public void stakeAndDelegateInNetwork() {
        updateTopPreps();
        Context.call(SYSTEM_SCORE_ADDRESS, "setStake", totalStake.getOrDefault(BigInteger.ZERO));
        updateDelegationInNetwork();
    }

    public Map<String, BigInteger> removePreviousDelegations(Address to) {
        String addressStr = to.toString();
        Map<String, BigInteger> previousDelegations = getDelegationInPercentage(to);
        BigInteger balance = (BigInteger) Context.call(sicxAddress.get(), "balanceOf", to);
        BigInteger icxHoldPreviously = balance.multiply(getTodayRate()).divide(ONE_EXA);
        if (!previousDelegations.isEmpty()) {
            addressDelegations.set(addressStr, null);
            for (String prep : previousDelegations.keySet()) {
                BigInteger prepDelegations = this.prepDelegations.getOrDefault(prep, BigInteger.ZERO);
                BigInteger votesPercentage = previousDelegations.get(prep);
                BigInteger votesIcx = votesPercentage.multiply(icxHoldPreviously).divide(HUNDRED_PERCENTAGE);
                this.prepDelegations.set(prep, prepDelegations.subtract(votesIcx));
            }
        }
        return previousDelegations;
    }

    @SuppressWarnings("unchecked")
    public List<Address> updateTopPreps() {
        Map<String, Object> termDetails = (Map<String, Object>) Context.call(SYSTEM_SCORE_ADDRESS, "getIISSInfo");
        BigInteger nextPrepTerm = (BigInteger) termDetails.get("nextPRepTerm");
        BigInteger destinationBlock = blockHeightWeek.getOrDefault(BigInteger.ZERO).add(BigInteger.valueOf(7 * 43200L));
        if (nextPrepTerm.compareTo(destinationBlock) > 0) {
            blockHeightWeek.set(nextPrepTerm);
            for (int i = 0; i < this.topPreps.size(); i++) {
                this.topPreps.pop();
            }
            return setTopPreps();
        } else {
            return List.of();
        }
    }

    @External
    public void delegate(PrepDelegations[] _user_delegations) {
        stakingOn();
        Address to = Context.getCaller();
        performChecksForIscoreAndUnstakedBalance();
        Map<String, BigInteger> previousDelegations = removePreviousDelegations(to);
        BigInteger balance = (BigInteger) Context.call(sicxAddress.get(), "balanceOf", to);
        BigInteger icxHoldPreviously = balance.multiply(getTodayRate()).divide(ONE_EXA);
        delegateVotes(to, _user_delegations, icxHoldPreviously);
        if (balance.compareTo(BigInteger.ZERO) > 0) {
            stakeAndDelegateInNetwork();
        }
    }

    @SuppressWarnings("unchecked")
    public void performChecksForIscoreAndUnstakedBalance() {

        // Calculate ICX available through unstaking
        Map<String, Object> stakeInNetwork = (Map<String, Object>) Context.call(SYSTEM_SCORE_ADDRESS, "getStake",
                Context.getAddress());
        BigInteger totalUnstakeInNetwork = BigInteger.ZERO;
        List<Map<String, Object>> unstakeList = (List<Map<String, Object>>) stakeInNetwork.get("unstakes");
        if (!unstakeList.isEmpty()) {
            for (Map<String, Object> unstakeDetails : unstakeList) {
                BigInteger unstakedIcx = (BigInteger) unstakeDetails.get("unstake");
                totalUnstakeInNetwork = totalUnstakeInNetwork.add(unstakedIcx);
            }
        }
        BigInteger unstakedICX = totalUnstakeAmount.get().subtract(totalUnstakeInNetwork);

        if (distributing.get()) {
            BigInteger dailyReward = Context.getBalance(Context.getAddress()).subtract(unstakedICX)
                    .subtract(Context.getValue())
                    .subtract(icxToClaim.getOrDefault(BigInteger.ZERO));
            if (dailyReward.compareTo(BigInteger.ZERO) <= 0) {
                return;
            }
            totalLifetimeReward.set(getLifetimeReward().add(dailyReward));

            BigInteger totalStake = this.totalStake.getOrDefault(BigInteger.ZERO);
            BigInteger newTotalStake = totalStake.add(dailyReward);
            BigInteger newRate;
            if (newTotalStake.equals(BigInteger.ZERO)) {
                newRate = ONE_EXA;
            } else {
                BigInteger totalSupply = (BigInteger) Context.call(sicxAddress.get(), "totalSupply");
                newRate = newTotalStake.multiply(ONE_EXA).divide(totalSupply);
            }
            rate.set(newRate);
            this.totalStake.set(newTotalStake);

            for (Address prep : getPrepList()) {
                BigInteger valueInIcx = prepDelegations.getOrDefault(prep.toString(), BigInteger.ZERO);
                BigInteger weightagePercentage = valueInIcx.multiply(HUNDRED_PERCENTAGE).divide(totalStake);
                BigInteger prepReward = valueInIcx.multiply(dailyReward).divide(totalStake);
                setPrepDelegations(prep.toString(), prepReward);
            }
            distributing.set(false);
        }
        checkForIscore();
        checkForUnstakedBalance(unstakedICX);
    }

    public BigInteger calculateDelegatedICXOutOfTopPreps(List<Address> topPrepAddresses) {
        BigInteger delegatedAmountOutOfTopPreps = BigInteger.ZERO;
        for (int i = 0; i < this.prepList.size(); i++) {
            Address prep = this.prepList.get(i);
            if (!topPrepAddresses.contains(prep)) {
                BigInteger delegation = this.prepDelegations.getOrDefault(prep.toString(), BigInteger.ZERO);
                delegatedAmountOutOfTopPreps = delegatedAmountOutOfTopPreps.add(delegation);
            }
        }
        return delegatedAmountOutOfTopPreps;
    }

    public void updateDelegationInNetwork() {

        List<Address> topPrepAddresses = getTopPreps();
        int totalPrepsToDelegate = topPrepAddresses.size();
        BigInteger topPrepsCount = BigInteger.valueOf(totalPrepsToDelegate);
        BigInteger amountToDistribute = calculateDelegatedICXOutOfTopPreps(topPrepAddresses);
        BigInteger amountToAddToAllTopPreps = amountToDistribute.divide(topPrepsCount);
        BigInteger amountToAddToOnePrep = amountToDistribute.remainder(topPrepsCount);

        List<Map<String, Object>> delegationList = new ArrayList<>();
        BigInteger delegatedIcxSum = BigInteger.ZERO;
        int lastPrepIndex = totalPrepsToDelegate - 1;
        for (int i = 0; i < lastPrepIndex; i++) {
            Address prep = topPrepAddresses.get(i);
            BigInteger delegatedIcx =
                    prepDelegations.getOrDefault(prep.toString(), BigInteger.ZERO).add(amountToAddToAllTopPreps);
            delegatedIcxSum = delegatedIcxSum.add(delegatedIcx);
            delegationList.add(Map.of("address", prep, "value", delegatedIcx));
        }

        Address lastPrep = topPrepAddresses.get(lastPrepIndex);
        BigInteger amountToDelegateToLastPrep = prepDelegations.getOrDefault(lastPrep.toString(), BigInteger.ZERO).
                add(amountToAddToAllTopPreps).add(amountToAddToOnePrep);
        delegatedIcxSum = delegatedIcxSum.add(amountToDelegateToLastPrep);
        BigInteger freeVotingPower = BigInteger.ZERO;
        BigInteger totalStake = this.totalStake.get();
        if (totalStake != null) {
            freeVotingPower = totalStake.subtract(delegatedIcxSum);
        }
        amountToDelegateToLastPrep = amountToDelegateToLastPrep.add(freeVotingPower);
        delegationList.add(Map.of("address", lastPrep, "value", amountToDelegateToLastPrep));

        Context.call(SYSTEM_SCORE_ADDRESS, "setDelegation", delegationList);
    }

    @External
    @Payable
    public BigInteger stakeICX(@Optional Address _to, @Optional byte[] _data) {
        stakingOn();
        if (_data == null) {
            _data = new byte[0];
        }
        if (_to == null) {
            _to = Context.getCaller();
        }
        BigInteger balance = (BigInteger) Context.call(sicxAddress.get(), "balanceOf", _to);
        BigInteger userOldIcx = (balance.multiply(getTodayRate())).divide(ONE_EXA);
        performChecksForIscoreAndUnstakedBalance();
        BigInteger addedIcx = Context.getValue();
        totalStake.set(totalStake.getOrDefault(BigInteger.ZERO).add(addedIcx));
        BigInteger sicxToMint = (ONE_EXA.multiply(addedIcx)).divide(getTodayRate());
        Map<String, BigInteger> currentDelegation = getDelegationInPercentage(_to);

        Context.call(sicxAddress.get(), "mintTo", _to, sicxToMint, _data);
        TokenTransfer(_to, sicxToMint, sicxToMint + " sICX minted to " + _to);

        BigInteger userNewIcx = userOldIcx.add(addedIcx);
        if (currentDelegation.isEmpty()) {
            setEvenAddressDelegationsInPercentage(_to);
        } else {
            for (String prep : currentDelegation.keySet()) {
                setAddressDelegations(_to, Address.fromString(prep), currentDelegation.get(prep), addedIcx);
            }
        }
        stakeAndDelegateInNetwork();
        return sicxToMint;
    }

    @External
    public void transferUpdateDelegations(Address _from, Address _to, BigInteger _value) {
        stakingOn();
        if (!Context.getCaller().equals(sicxAddress.get())) {
            Context.revert(Constant.TAG + ": Only sicx token contract can call this function.");
        }
        BigInteger sicxToIcx = _value.multiply(getTodayRate()).divide(ONE_EXA);
        Map<String, BigInteger> senderDelegations = getDelegationInPercentage(_from);
        Map<String, BigInteger> receiverDelegations = getDelegationInPercentage(_to);
        for (String prep : senderDelegations.keySet()) {
            BigInteger deductedIcx = percentToIcx(senderDelegations.get(prep), sicxToIcx);
            prepDelegations.set(prep, prepDelegations.getOrDefault(prep, BigInteger.ZERO).subtract(deductedIcx));
        }
        if (!receiverDelegations.isEmpty()) {
            for (String prep : receiverDelegations.keySet()) {
                BigInteger addedIcx = percentToIcx(receiverDelegations.get(prep), sicxToIcx);
                prepDelegations.set(prep, prepDelegations.getOrDefault(prep, BigInteger.ZERO).add(addedIcx));
            }
        } else {
            setEvenAddressDelegationsInPercentage(_to);
            Map<String, BigInteger> newDelegation = getDelegationInPercentage(_to);
            for (String prep : newDelegation.keySet()) {
                BigInteger addedIcx = (newDelegation.get(prep).multiply(sicxToIcx)).divide(HUNDRED_PERCENTAGE);
                setPrepDelegations(prep, addedIcx);
            }
        }
        stakeAndDelegateInNetwork();
    }

    public Map<String, BigInteger> getDelegationInPercentage(Address address) {
        String delegationString = addressDelegations.getOrDefault(address.toString(), "");
        if (delegationString.isEmpty()) {
            return Map.of();
        }

        delegationString = delegationString.substring(0, delegationString.length() - 1);
        StringTokenizer tokenizedDelegationString = new StringTokenizer(delegationString, "\\.");
        Map<String, BigInteger> prepDelegationInPercentage = new HashMap<>();
        while (tokenizedDelegationString.hasMoreTokens()) {
            String prepDelegation = tokenizedDelegationString.nextToken();
            StringTokenizer tokenizedPrepDelegation = new StringTokenizer(prepDelegation, ":");
            String prepName = tokenizedPrepDelegation.nextToken();
            String prepDelegationPercentage = tokenizedPrepDelegation.nextToken();
            prepDelegationInPercentage.put(prepName, new BigInteger(prepDelegationPercentage));
        }
        return prepDelegationInPercentage;
    }

    public void checkForUnstakedBalance(BigInteger unstakedICX) {

        if (unstakedICX.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }

        BigInteger totalUnstakeAmount = this.totalUnstakeAmount.getOrDefault(BigInteger.ZERO);
        BigInteger icxToClaim = this.icxToClaim.getOrDefault(BigInteger.ZERO);
        List<List<Object>> unstakingRequests = getUnstakeInfo();
        for (int i = 0; i < unstakingRequests.size(); i++) {
            if (BigInteger.valueOf(i).compareTo(unstakeBatchLimit.getOrDefault(BigInteger.ZERO)) > 0) {
                return;
            }
            if (unstakedICX.compareTo(BigInteger.ZERO) <= 0) {
                return;
            }
            BigInteger payout;
            List<Object> unstakeInfo = unstakingRequests.get(i);
            BigInteger unstakeAmount = (BigInteger) unstakeInfo.get(1);
            if (unstakeAmount.compareTo(unstakedICX) <= 0) {
                payout = unstakeAmount;
                unstakeRequestList.remove(unstakeRequestList.headId.getOrDefault(BigInteger.ZERO));
            } else {
                payout = unstakedICX;
                unstakeRequestList.updateNode((Address) unstakeInfo.get(2), unstakeAmount.subtract(payout),
                        (BigInteger) unstakeInfo.get(3), (Address) unstakeInfo.get(4),
                        (BigInteger) unstakeInfo.get(0));
            }
            totalUnstakeAmount = totalUnstakeAmount.subtract(payout);
            unstakedICX = unstakedICX.subtract(payout);
            icxToClaim = icxToClaim.add(payout);
            icxPayable.set((Address) unstakeInfo.get(4), icxPayable.getOrDefault((Address) unstakeInfo.get(4),
                    BigInteger.ZERO).add(payout));
        }

        this.totalUnstakeAmount.set(totalUnstakeAmount);
        this.icxToClaim.set(icxToClaim);
    }

    @SuppressWarnings("unchecked")
    public void unstake(Address to, BigInteger value, Address senderAddress) {
        Context.call(sicxAddress.get(), "burn", value);
        BigInteger amountToUnstake = (value.multiply(getTodayRate())).divide(ONE_EXA);
        Map<String, BigInteger> delegationPercent = getDelegationInPercentage(to);
        totalUnstakeAmount.set(totalUnstakeAmount.getOrDefault(BigInteger.ZERO).add(amountToUnstake));
        for (String key : delegationPercent.keySet()) {
            BigInteger prepPercent = delegationPercent.get(key);
            BigInteger prepDelegations = this.prepDelegations.getOrDefault(key, BigInteger.ZERO);
            BigInteger amountToRemove = prepPercent.multiply(amountToUnstake).divide(HUNDRED_PERCENTAGE);
            this.prepDelegations.set(key, prepDelegations.subtract(amountToRemove));
        }
        BigInteger newTotalStake = totalStake.getOrDefault(BigInteger.ZERO).subtract(amountToUnstake);
        totalStake.set(newTotalStake);
        updateDelegationInNetwork();
        Context.call(SYSTEM_SCORE_ADDRESS, "setStake", newTotalStake);

        Address addressToSend = to;
        if (senderAddress != null) {
            addressToSend = senderAddress;
        }

        Map<String, BigInteger> estimatedUnlockPeriod = (Map<String, BigInteger>) Context.call(SYSTEM_SCORE_ADDRESS,
                "estimateUnstakeLockPeriod");
        BigInteger unlockPeriod = estimatedUnlockPeriod.get("unstakeLockPeriod");
        long currentBlockHeight = Context.getBlockHeight();
        BigInteger unstakeHeight = BigInteger.valueOf(currentBlockHeight).add(unlockPeriod);
        unstakeRequestList.append(to, amountToUnstake, unstakeHeight, addressToSend,
                unstakeRequestList.tailId.getOrDefault(BigInteger.ZERO).add(BigInteger.ONE));
        UnstakeRequest(addressToSend, amountToUnstake);
    }


    @External(readonly = true)
    public List<List<Object>> getUnstakeInfo() {
        return unstakeRequestList.iterate();
    }

    @External(readonly = true)
    public List<Map<String, Object>> getUserUnstakeInfo(Address _address) {
        List<List<Object>> linkedListIter = unstakeRequestList.iterate();
        List<Map<String, Object>> response = new ArrayList<>();
        for (List<Object> newList : linkedListIter) {
            if (newList.get(4).equals(_address)) {
                Map<String, Object> unstakeDict = new HashMap<>();
                unstakeDict.put("amount", newList.get(1));
                unstakeDict.put("from", newList.get(2));
                unstakeDict.put("blockHeight", newList.get(3));
                unstakeDict.put("sender", newList.get(4));
                response.add(unstakeDict);
            }
        }
        return response;
    }
}
