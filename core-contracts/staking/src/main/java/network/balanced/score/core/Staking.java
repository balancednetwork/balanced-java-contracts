package network.balanced.score.core;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import java.math.BigInteger;
import java.sql.Struct;
import java.util.List;
import java.util.Map;

import network.balanced.score.core.db.Delegations;
import network.balanced.score.core.db.LinkedListDB;
import network.balanced.score.core.db.userDelegations;
import network.balanced.score.core.utils.Constant;
import network.balanced.score.core.utils.PrepDelegations;
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

import static network.balanced.score.core.utils.Checks.*;

public class Staking {

    public Staking() {
        Map<String, Object> termDetails = (Map<String, Object>) Context.call(Address.fromString(Constant.SYSTEM_SCORE_ADDRESS), "getIISSInfo");
        BigInteger nextPrepTerm = (BigInteger) termDetails.get("nextPRepTerm");
        blockHeightWeek.set(nextPrepTerm);
        blockHeightDay.set(nextPrepTerm);
        rate.set(Constant.DENOMINATOR);
        distributing.set(false);
        setTopPreps();
        unstakeBatchLimit.set(Constant.DEFAULT_UNSTAKE_BATCH_LIMIT);
        stakingOn.set(false);
        dustPrep.set(getTopPreps().get(35));
    }



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

    private static final String SICX_SUPPLY = "sICX_supply";
    private static final String RATE = "_rate";
    private static final String SICX_ADDRESS = "sICX_address";
    private static final String BLOCK_HEIGHT_WEEK = "_block_height_week";
    private static final String BLOCK_HEIGHT_DAY = "_block_height_day";
    private static final String TOTAL_STAKE = "_total_stake";
    private static final String DAILY_REWARD = "_daily_reward";
    private static final String TOTAL_LIFETIME_REWARD = "_total_lifetime_reward";
    private static final String DISTRIBUTING = "_distributing";
    private static final String TOP_PREPS = "_top_preps";
    private static final String PREP_LIST = "_prep_list";
    private static final String ADDRESS_DELEGATIONS = "_address_delegations";
    private static final String PREP_DELEGATIONS = "_prep_delegations";
    private static final String TOTAL_UNSTAKE_AMOUNT = "_total_unstake_amount";
    private static final String UNSTAKE_BATCH_LIMIT = "_unstake_batch_limit";
    private static final String STAKING_ON = "staking_on";
    private static final String ICX_PAYABLE = "icx_payable";
    private static final String ICX_TO_CLAIM = "icx_to_claim";

    private final VarDB<BigInteger> sicxSupply = Context.newVarDB(SICX_SUPPLY, BigInteger.class);
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
    private final DictDB<String, Boolean> userMigration = Context.newDictDB("userMigration", Boolean.class);
    private final VarDB<BigInteger> unstakeBatchLimit = Context.newVarDB(UNSTAKE_BATCH_LIMIT, BigInteger.class);
    public static final VarDB<Boolean> stakingOn = Context.newVarDB(STAKING_ON, Boolean.class);
    private final LinkedListDB linkedListDb = new LinkedListDB("unstake_dict");
    private final VarDB<Address> dustPrep = Context.newVarDB("dustPrep", Address.class);

    @External(readonly = true)
    public String name() {
        return Constant.TAG;
    }

    @External
    public void setBlockHeightWeek(BigInteger _height) {
        onlyOwner();
        blockHeightWeek.set(_height);
    }

    @External
    public void setDustPrep(Address _address) {
        onlyOwner();
        dustPrep.set(_address);
    }

    @External(readonly = true)
    public Address getDustPrep() {
        return dustPrep.get();
    }

    @External
    public void fixDelegation(ContractDelegation[] delegationDict ) {
        onlyOwner();
        for (ContractDelegation singlePrep : delegationDict) {
            Address prepAddress = singlePrep.address;
            BigInteger delegation = singlePrep.votesInIcx;
            prepDelegations.set(prepAddress.toString(), delegation);
        }
    }

    @External(readonly = true)
    public BigInteger getBlockHeightWeek() {
        return blockHeightWeek.getOrDefault(BigInteger.ZERO);
    }

    @External(readonly = true)
    public BigInteger getTodayRate() {
        return rate.getOrDefault(BigInteger.ZERO);
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
        return unstakeBatchLimit.getOrDefault(BigInteger.ZERO);
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
        Context.require(_address.isContract(), Constant.TAG + ": Address provided is an EOA address. A contract address is " +
                "required.");
        sicxAddress.set(_address);
    }

    public BigInteger percentToIcx(BigInteger votingPercentage, BigInteger totalAmount) {
        BigInteger numerator = votingPercentage.multiply(totalAmount);
        return numerator.divide(Constant.DENOMINATOR.multiply(Constant.HUNDRED));
    }

    public BigInteger setAddressDelegations(Address to, Address prep, BigInteger votesInPer, BigInteger totalIcxHold) {
        int checkVal = totalIcxHold.compareTo(BigInteger.ZERO);
        BigInteger value = BigInteger.ZERO;
        if (checkVal != 0) {
            value = percentToIcx(votesInPer, totalIcxHold);
            setPrepDelegations(prep, value);
        }
        return value;
    }

    public void setPrepDelegations(Address prep, BigInteger value) {
        BigInteger prepDelegations = this.prepDelegations.getOrDefault(prep.toString(), BigInteger.ZERO);
        this.prepDelegations.set(prep.toString(), prepDelegations.add(value));
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
    public void fallback() throws Exception {
        stakeICX(Context.getCaller(), null);
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        stakingOn();
        if ( ! Context.getCaller().equals(sicxAddress.get())) {
            Context.revert(Constant.TAG + ": The Staking contract only accepts sICX tokens."+ Context.getCaller() + " ori"+sicxAddress.get());
        }
        try {
            String unpackedData = new String(_data);
            JsonObject json = Json.parse(unpackedData).asObject();
            String method = json.get("method").asString();
            if ((json.contains("method")) && method.equals("unstake")) {
                if (json.contains("user")) {
                    unstake(_from, _value, Address.fromString(json.get("user").asString()));
                }
                else {
                    unstake(_from, _value, null);
                }
            }
            else {
                Context.revert(Constant.TAG + ": Invalid Parameters.");
            }
        }
        catch (Exception e) {
            Context.revert(Constant.TAG + ": Invalid data:." + _data);

        }
    }

    public void checkForIscore() {
        if (!distributing.get()) {
            claimIscore();
        }
    }

    // Created only for test
    @External(readonly = true)
    public boolean getDistributing(){
        return distributing.getOrDefault(false);
    }

    @SuppressWarnings("unchecked")
    public void claimIscore() {
        Map<String, Object> iscoreDetails = (Map<String, Object>) Context.call(Address.fromString(Constant.SYSTEM_SCORE_ADDRESS), "queryIScore", Context.getAddress());
        BigInteger iscoreGenerated = (BigInteger) iscoreDetails.get("estimatedICX");
        if (iscoreGenerated.compareTo(BigInteger.ZERO) > 0) {
            Context.call(Address.fromString(Constant.SYSTEM_SCORE_ADDRESS), "claimIScore");
            IscoreClaimed(BigInteger.valueOf(Context.getBlockHeight()), iscoreGenerated);
            distributing.set(true);
        }
    }

    public void stake(BigInteger stakeValue) {
        Context.call(Address.fromString(Constant.SYSTEM_SCORE_ADDRESS), "setStake", stakeValue);
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
            icxPayable.set(_to, BigInteger.ZERO);
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
    public void setTopPreps() {
        Map<String, Object> prepDict = (Map<String, Object>) Context.call(Address.fromString(Constant.SYSTEM_SCORE_ADDRESS), "getPReps", 1,Constant.TOP_PREP_COUNT);
        List<Map<String, Object>> prepDetails = (List<Map<String, Object>>)prepDict.get("preps");
        List<Address> addresses = getPrepList();
        for (Map<String, Object> preps : prepDetails) {
            Address prepAddress =(Address) preps.get("address");
            if (! contains(prepAddress, addresses)) {
                addresses.add(prepAddress);
                prepList.add(prepAddress);
            }
            topPreps.add(prepAddress);
        }
    }

    @External(readonly= true)
    public Map<String, BigInteger> getAddressDelegations(Address _address)
    {
        Map<String, BigInteger> delegationIcx = new HashMap<>();
        Map<String, BigInteger> delegationPercent = new HashMap<>();
        delegationPercent = delegationInPer(_address);
        BigInteger balance = (BigInteger) Context.call(sicxAddress.get(), "balanceOf",_address );
        BigInteger totalIcxHold = (balance.multiply(rate.getOrDefault(BigInteger.ZERO))).divide(Constant.DENOMINATOR);
        for (String name : delegationPercent.keySet())
        {
            BigInteger votesPer = delegationPercent.get(name);
            BigInteger votesIcx = (votesPer.multiply(totalIcxHold)).divide(Constant.DENOMINATOR.multiply(Constant.HUNDRED));
            delegationIcx.put(name, votesIcx);
        }
        return delegationIcx;
    }

    public BigInteger getRate()
    {
        BigInteger totalStake = this.totalStake.getOrDefault(BigInteger.ZERO);
        BigInteger totalSupply = (BigInteger) Context.call(sicxAddress.get(), "totalSupply" );
        BigInteger rate = BigInteger.ZERO;
        if (totalStake.compareTo(BigInteger.ZERO) == 0)
        {
            rate = Constant.DENOMINATOR;
        }
        else
        {
            rate = (totalStake.add(dailyReward.getOrDefault(BigInteger.ZERO)).multiply(Constant.DENOMINATOR)).divide(totalSupply);
        }
        return rate;
    }

    public boolean contains(Address target, List<Address> addresses) {
        for(Address address : addresses) {
            if (address.equals(target)){
                return true;
            }
        }
        return false;
    }

    public BigInteger delegateVotes(Address to, PrepDelegations[] userDelegations, BigInteger userIcxHold)
    {
        BigInteger amountToStake = BigInteger.ZERO;
        List<Address> similarPrepCheck = new ArrayList<>();
        StringBuilder addressDelegations = new StringBuilder();
        List<Address> addresses = getPrepList();
        BigInteger tempIcxBalance = BigInteger.ZERO;
        for (PrepDelegations singlePrep : userDelegations)
        {
            Address prepAddress = (Address) singlePrep._address;
            BigInteger votesInPer = (BigInteger) singlePrep._votes_in_per;
            if (! contains(prepAddress, addresses)) {
                addresses.add(prepAddress);
                prepList.add(prepAddress);
            }
            if (similarPrepCheck.toString().contains(prepAddress.toString()))
            {
                Context.revert(Constant.TAG+": You can not delegate same Prep twice in a transaction.Your delegation preference is"+ userDelegations);
            }
            if (votesInPer.compareTo(BigInteger.valueOf(1000000000000000L)) < 0)
            {
                Context.revert(Constant.TAG+": You should provide delegation percentage more than 0.001. Your delegation preference is "+userDelegations+".");
            }
            similarPrepCheck.add(prepAddress);
            amountToStake = amountToStake.add(votesInPer);
            addressDelegations.append(prepAddress.toString()).append(":").append(votesInPer.toString()).append(".");
            BigInteger balance = setAddressDelegations(to, prepAddress, votesInPer, userIcxHold);
            tempIcxBalance = tempIcxBalance.add(balance);

        }
        BigInteger dustBalance = userIcxHold.subtract(tempIcxBalance);
        if (dustBalance.compareTo(BigInteger.ZERO) > 0){
            setPrepDelegations(dustPrep.get(), dustBalance);
        }
        this.addressDelegations.set(to.toString(), addressDelegations.toString());
        return amountToStake;

    }

    public BigInteger distributeEvenly(BigInteger amountToDistribute)
    {
        BigInteger evenlyDistribution = (Constant.DENOMINATOR.multiply(amountToDistribute)).divide(Constant.TOP_PREP_COUNT);
        return evenlyDistribution.divide(Constant.DENOMINATOR);
    }

    public void stakeAndDelegate(BigInteger evenlyDistributeValue) {
        stake(getTotalStake());
        delegations(evenlyDistributeValue);
    }

    public BigInteger removeDelegations(String address, BigInteger value) {
        BigInteger balance = prepDelegations.get(address);
        if (balance.compareTo(value)< 0){
            prepDelegations.set(address, BigInteger.ZERO);
            return balance;
        }
        else{
            prepDelegations.set(address, prepDelegations.getOrDefault(address, BigInteger.ZERO).subtract(value));
            return value;
        }
    }

    public BigInteger resetTopPreps(){
        BigInteger toDistribute = BigInteger.ZERO;
        BigInteger prepDelegationSum = BigInteger.ZERO;
        List<Address> addresses = getTopPreps();
        for (int i = 0; i < this.prepList.size(); i++) {
            Address prep = this.prepList.get(i);
            BigInteger prepDelegations = this.prepDelegations.getOrDefault(prep.toString(), BigInteger.ZERO);
            prepDelegationSum = prepDelegationSum.add(prepDelegations);
            if (! contains(prep, addresses)) {
                toDistribute = toDistribute.add(prepDelegations);
            }
        }
        toDistribute = toDistribute.add(getTotalStake().subtract(prepDelegationSum));
        return distributeEvenly(toDistribute);
    }

    public Map<String, BigInteger> removePreviousDelegations(Address to){
        String addressStr = to.toString();
        Map<String, BigInteger> previousDelegations = delegationInPer(to);
        BigInteger balance = (BigInteger) Context.call(sicxAddress.get(), "balanceOf",to );
        BigInteger icxHoldPreviously = (balance.multiply(rate.getOrDefault(BigInteger.ZERO))).divide(Constant.DENOMINATOR);
        BigInteger tempIcxBalance = BigInteger.ZERO;
        if (!previousDelegations.isEmpty()){
            addressDelegations.set(addressStr, "");
            for (String prep : previousDelegations.keySet()){
                BigInteger votesPer = previousDelegations.get(prep);
                BigInteger votesIcx = (votesPer.multiply(icxHoldPreviously)).divide(Constant.DENOMINATOR.multiply(Constant.HUNDRED));
                BigInteger deductedIcx = removeDelegations(prep, votesIcx);
                tempIcxBalance = tempIcxBalance.add(deductedIcx);
            }
            BigInteger dustBalance = icxHoldPreviously.subtract(tempIcxBalance);
            if (dustBalance.compareTo(BigInteger.ZERO) > 0){
                this.prepDelegations.set(dustPrep.get().toString(), prepDelegations.get(dustPrep.get().toString()).subtract(dustBalance));

            }
            else if (dustBalance.compareTo(BigInteger.ZERO) < 0){
                this.prepDelegations.set(dustPrep.get().toString(), prepDelegations.get(dustPrep.get().toString()).add(dustBalance));
            }
        }
        return previousDelegations;
    }
    @SuppressWarnings("unchecked")
    public BigInteger checkForWeek(){
        Map<String, Object> termDetails = (Map<String, Object>) Context.call(Address.fromString(Constant.SYSTEM_SCORE_ADDRESS), "getIISSInfo");
        BigInteger nextPrepTerm = (BigInteger) termDetails.get("nextPRepTerm");
        if (nextPrepTerm.compareTo(blockHeightWeek.getOrDefault(BigInteger.ZERO).add(BigInteger.valueOf(302400L))) > 0){
            blockHeightWeek.set(nextPrepTerm);
            for (int i = 0; i <= this.topPreps.size(); i++) {
                this.topPreps.pop();
            }
            setTopPreps();
        }
        return resetTopPreps();
    }

    @External
    public void delegate(PrepDelegations[] _user_delegations) throws Exception {
        stakingOn();
        Address to = Context.getCaller();
        String addressString = to.toString();
        migrateUserDelegations(addressString);
        performChecks();
        Map<String, BigInteger> previousDelegations = removePreviousDelegations(to);
        BigInteger balance = (BigInteger) Context.call(sicxAddress.get(), "balanceOf",to );
        BigInteger icxHoldPreviously = (balance.multiply(rate.getOrDefault(BigInteger.ZERO))).divide(Constant.DENOMINATOR);
        BigInteger totalPer = delegateVotes(to, _user_delegations, icxHoldPreviously);
        if (totalPer.compareTo(Constant.HUNDRED.multiply(Constant.DENOMINATOR)) != 0){
            Context.revert(Constant.TAG+": Total delegations should be 100%.Your delegation preference is ");
        }
        if (! balance.equals(BigInteger.ZERO)){
            stakeAndDelegate(checkForWeek());
        }
    }

    @SuppressWarnings("unchecked")
    public void performChecks() throws Exception {
        if (distributing.get()){
            Map<String, Object> stakeInNetwork = (Map<String, Object>) Context.call(Address.fromString(Constant.SYSTEM_SCORE_ADDRESS), "getStake", Context.getAddress());
            BigInteger totalUnstakeInNetwork = BigInteger.ZERO;
            List<Map<String,Object>> result = (List<Map<String, Object>>) stakeInNetwork.get("unstakes");
            if (!result.isEmpty()){
                for (Map<String, Object> unstakeDetails : result){
                    BigInteger unstakedIcx = (BigInteger) unstakeDetails.get("unstake");
                    totalUnstakeInNetwork = totalUnstakeInNetwork.add(unstakedIcx);
                }}
            BigInteger dailyReward = (totalUnstakeInNetwork.add(Context.getBalance(Context.getAddress())))
                    .subtract(totalUnstakeAmount.getOrDefault(BigInteger.ZERO).add(Context.getValue().add(icxToClaim.getOrDefault(BigInteger.ZERO))));
            this.dailyReward.set(dailyReward);
            totalLifetimeReward.set(getLifetimeReward().add(dailyReward));
            rate.set(getRate());
            this.totalStake.set(getTotalStake().add(dailyReward));
            BigInteger tempRewards = BigInteger.ZERO;
            BigInteger prepDelegationSum = BigInteger.ZERO;
            for (BigInteger delegation : getPrepDelegations().values()) {
                prepDelegationSum = prepDelegationSum.add(delegation);
            }
            for (Address prep : getPrepList()){
                BigInteger valueInIcx = prepDelegations.getOrDefault(prep.toString(), BigInteger.ZERO);
                BigInteger weightagePer = ((valueInIcx.multiply(Constant.DENOMINATOR).multiply(Constant.HUNDRED)).divide(prepDelegationSum));
                BigInteger prepReward = (weightagePer.multiply(dailyReward)).divide(Constant.HUNDRED.multiply(Constant.DENOMINATOR));
                tempRewards = tempRewards.add(prepReward);
                setPrepDelegations(prep, prepReward);
            }
            BigInteger dustBalance = dailyReward.subtract(tempRewards);
            if (dustBalance.compareTo(BigInteger.ZERO) > 0){
                prepDelegations.set(dustPrep.get().toString(), prepDelegations.get(dustPrep.get().toString()).add(dustBalance));
            }
            this.dailyReward.set(BigInteger.ZERO);
            distributing.set(false);
        }
        checkForIscore();
        checkForBalance();
    }

    public void delegations(BigInteger evenlyDistributeValue){
        List<Map<String, Object>> delegationList = new ArrayList<>();
        int topPrepCount = topPreps.size();
        BigInteger votingPowerCheck = BigInteger.ZERO;
        for (int i = 0; i < this.topPreps.size(); i++) {
            Map<String, Object> delegateDict = new HashMap<>();
            Address prep = this.topPreps.get(i);
            BigInteger valueInIcx = prepDelegations.getOrDefault(prep.toString(), BigInteger.ZERO).add(evenlyDistributeValue);
            votingPowerCheck = votingPowerCheck.add(valueInIcx);
            if ( i== (topPrepCount - 1)){
                BigInteger dust = getTotalStake().subtract(votingPowerCheck);
                valueInIcx = valueInIcx.add(dust);
            }
            delegateDict.put("address", prep);
            delegateDict.put("value", valueInIcx);
            delegationList.add(delegateDict);
        }
        Context.call(Address.fromString(Constant.SYSTEM_SCORE_ADDRESS), "setDelegation", delegationList);
    }

    @External
    @Payable
    public BigInteger stakeICX(@Optional Address _to, @Optional byte[]  _data) throws Exception {
        stakingOn();
        if (_data == null){
            _data = new byte[0];
        }
        if (_to == null){
            _to = Context.getCaller();
        }
        // set user delegation empty here and check the migration db if ite empty or not
        String addressString = _to.toString();
        migrateUserDelegations(addressString);
        performChecks();
        totalStake.set(totalStake.getOrDefault(BigInteger.ZERO).add(Context.getValue()));
        BigInteger amount = (Constant.DENOMINATOR.multiply(Context.getValue())).divide(rate.getOrDefault(BigInteger.ZERO));
        Map<String, BigInteger> previousDelegations = delegationInPer(_to);
        Context.call(sicxAddress.get(), "mintTo",_to, amount, _data );
        if (! previousDelegations.isEmpty()){
            BigInteger deltaIcx = Context.getValue();
            BigInteger tempIcxBalance = BigInteger.ZERO;
            for (String prep : previousDelegations.keySet()){
                BigInteger balance = setAddressDelegations(_to, Address.fromString(prep), previousDelegations.get(prep), deltaIcx);
                tempIcxBalance = tempIcxBalance.add(balance);

            }
            BigInteger dustBalance = deltaIcx.subtract(tempIcxBalance);
            if (dustBalance.compareTo(BigInteger.ZERO) > 0){
                setPrepDelegations(dustPrep.get(), dustBalance);
            }
        }
        stakeAndDelegate(checkForWeek());
        sicxSupply.set(sicxSupply.getOrDefault(BigInteger.ZERO).add(amount));
        TokenTransfer(_to, amount, amount.divide(Constant.DENOMINATOR) + " sICX minted to "+_to );
        return amount;

    }

    public void migrateUserDelegations(String user){
        if (! userMigration.getOrDefault(user, false)) {
            addressDelegations.set(user, "");
            userMigration.set(user, true);
        }
    }

    @External
    public void transferUpdateDelegations(Address _from,Address _to,BigInteger _value){
        stakingOn();
        if (! Context.getCaller().equals(sicxAddress.get())){
            Context.revert(Constant.TAG+": Only sicx token contract can call this function.");
        }
        String senderAddressStr = _from.toString();
        String receiverAddressStr = _to.toString();
        migrateUserDelegations(senderAddressStr);
        migrateUserDelegations(receiverAddressStr);
        BigInteger dustBalance = BigInteger.ZERO;
        BigInteger sicxToIcx = (_value.multiply(rate.getOrDefault(BigInteger.ZERO))).divide(Constant.DENOMINATOR);
        Map<String, BigInteger> senderDelegations = delegationInPer(_from);
        Map<String, BigInteger> receiverDelegations = delegationInPer(_to);
        Address dustprep = dustPrep.get();
        BigInteger tempIcxBalance = BigInteger.ZERO;
        if (!senderDelegations.isEmpty()){
        for (String prep : senderDelegations.keySet()){
            BigInteger amountToRemove = percentToIcx(senderDelegations.get(prep), sicxToIcx);
            BigInteger deductedIcx = removeDelegations(prep, amountToRemove);
            tempIcxBalance = tempIcxBalance.add(deductedIcx);
        }
        dustBalance = sicxToIcx.subtract(tempIcxBalance);
        if (dustBalance.compareTo(BigInteger.ZERO) >0){
            prepDelegations.set(dustprep.toString() , prepDelegations.get(dustprep.toString()).subtract(dustBalance));

        }
        }
        if (!receiverDelegations.isEmpty()){
            BigInteger tempVal = BigInteger.ZERO;
            for (String prep : receiverDelegations.keySet()){
                BigInteger addedIcx = percentToIcx(receiverDelegations.get(prep), sicxToIcx);
                prepDelegations.set(prep, prepDelegations.getOrDefault(prep,BigInteger.ZERO).add(addedIcx));
                tempVal = tempVal.add(addedIcx);
            }
            dustBalance = sicxToIcx.subtract(tempVal);
            if (dustBalance.compareTo(BigInteger.ZERO)> 0){
                prepDelegations.set(dustprep.toString(), prepDelegations.get(dustprep.toString()).add(dustBalance));
            }
        }
        stakeAndDelegate(checkForWeek());
    }

    private List<String> splitResult(StringTokenizer st){
        List<String> splittedList = new ArrayList<>();
        while (st.hasMoreTokens()) {
            splittedList.add(st.nextToken());
        }
        return splittedList;
    }

    public Map<String, BigInteger> delegationInPer(Address address){
        String delegationString = addressDelegations.getOrDefault(address.toString(), "");
        if (! delegationString.isEmpty()){
            delegationString = delegationString.substring(0, delegationString.length() - 1);
            StringTokenizer st = new StringTokenizer(delegationString, "\\.");
            Map<String, BigInteger>delegationPercent = new HashMap<>();
            List<String> splittedList = new ArrayList<>();
            splittedList = splitResult(st);
            for (String item: splittedList){
                st = new StringTokenizer(item, ":");
                splittedList = splitResult(st);
                if (delegationPercent.get(splittedList.get(0)) == null){
                    delegationPercent.put(splittedList.get(0), new BigInteger(splittedList.get(1)));
                }
                else{
                    if ((splittedList.get(1)!= "0")){
                        BigInteger value = delegationPercent.get(splittedList.get(0));
                        delegationPercent.put(splittedList.get(0),  new BigInteger(splittedList.get(1)).add(value));
                    }
                    else{
                        delegationPercent.put(splittedList.get(0), BigInteger.ZERO);
                    }
                }

            }
            return delegationPercent;

        }
        else{
            return new HashMap<>();
        }

    }

    public void checkForBalance() throws Exception {
        BigInteger balance = Context.getBalance(Context.getAddress()).subtract(dailyReward.getOrDefault(BigInteger.ZERO))
                .subtract(icxToClaim.getOrDefault(BigInteger.ZERO));
        if (balance.compareTo(BigInteger.ZERO) <= 0){
            return ;
        }
        List<List<Object>> unstakingRequests = getUnstakeInfo();
        for (int i =0; i< unstakingRequests.size(); i++){
            if (BigInteger.valueOf(i).compareTo(unstakeBatchLimit.getOrDefault(BigInteger.ZERO))>0){
                return;
            }
            if (balance.compareTo(BigInteger.ZERO)<=0){
                return;
            }
            BigInteger payout = BigInteger.ZERO;
            List<Object> unstakeInfo = unstakingRequests.get(i);
            BigInteger unstakeAmount = (BigInteger) unstakeInfo.get(1);
            if (unstakeAmount.compareTo(balance)<= 0){
                payout = unstakeAmount;
                linkedListDb.remove(linkedListDb.headId.getOrDefault(BigInteger.ZERO));
            }
            else{
                payout = balance;

                linkedListDb.updateNode((Address) unstakeInfo.get(2), unstakeAmount.subtract(payout),
                        (BigInteger) unstakeInfo.get(3), (Address) unstakeInfo.get(4),
                        (BigInteger) unstakeInfo.get(0));
            }
            totalUnstakeAmount.set(totalUnstakeAmount.getOrDefault(BigInteger.ZERO).subtract(payout));
            balance = balance.subtract(payout);
            icxToClaim.set(icxToClaim.getOrDefault(BigInteger.ZERO).add(payout));
            icxPayable.set((Address) unstakeInfo.get(4), icxPayable.getOrDefault((Address) unstakeInfo.get(4),BigInteger.ZERO).add(payout));

        }

    }

    @SuppressWarnings("unchecked")
    public void unstake(Address to, BigInteger value, Address senderAddress) throws Exception {
        Context.call(sicxAddress.get(), "burn",value);
        migrateUserDelegations(to.toString());
        BigInteger amountToUnstake = (value.multiply(rate.getOrDefault(BigInteger.ZERO))).divide(Constant.DENOMINATOR);
        Map<String, BigInteger> delegationPercent = delegationInPer(to);
        BigInteger tempIcxBalance = BigInteger.ZERO;
        totalUnstakeAmount.set(totalUnstakeAmount.getOrDefault(BigInteger.ZERO).add(amountToUnstake));
        if (! delegationPercent.isEmpty()){
        for (String key: delegationPercent.keySet()){
            BigInteger prepPercent = delegationPercent.get(key);
            BigInteger amountToRemove = (prepPercent.multiply(amountToUnstake)).divide(Constant.DENOMINATOR.multiply(Constant.HUNDRED));
            BigInteger deductedIcx = removeDelegations(key, amountToRemove);
            tempIcxBalance = tempIcxBalance.add(deductedIcx);
        }
        BigInteger dustBalance = amountToUnstake.subtract(tempIcxBalance);
        if (dustBalance.compareTo(BigInteger.ZERO) > 0){
            prepDelegations.set(dustPrep.get().toString(), prepDelegations.get(dustPrep.get().toString()).subtract(dustBalance));
        }
        }
        totalStake.set(totalStake.getOrDefault(BigInteger.ZERO).subtract(amountToUnstake));
        delegations(resetTopPreps());
        stake(totalStake.getOrDefault(BigInteger.ZERO));
        Map<String, Object> stakeInNetwork = (Map<String, Object>) Context.call(Address.fromString(Constant.SYSTEM_SCORE_ADDRESS), "getStake", Context.getAddress());
        Address addressToSend = to;
        if (senderAddress != null){
            addressToSend = senderAddress;
        }
        List<Map<String,Object>> result = (List<Map<String, Object>>) stakeInNetwork.get("unstakes");
        Map<String,Object> recentUnstakeInfo = result.get(result.size() - 1);
        linkedListDb.append(to, amountToUnstake,
                (BigInteger) recentUnstakeInfo.get("unstakeBlockHeight"),
                addressToSend,
                linkedListDb.tailId.getOrDefault(BigInteger.ZERO).add(BigInteger.ONE));
        sicxSupply.set(sicxSupply.getOrDefault(BigInteger.ZERO).subtract(value));
        UnstakeRequest(addressToSend, amountToUnstake);
    }


    @External(readonly= true)
    public List<List<Object>> getUnstakeInfo() throws Exception {
        List<List<Object>>linked_list_iter =  linkedListDb.iterate();
        List<List<Object>> unstakeList = new ArrayList<>();
        unstakeList.addAll(linked_list_iter);
        return unstakeList;

    }

    @External(readonly= true)
    public List<Map<String, Object>> getUserUnstakeInfo(Address _address) throws Exception {
        List<List<Object>>linkedListIter =  linkedListDb.iterate();
        List<Map<String, Object>> response = new ArrayList<>();
        for (List<Object> newList : linkedListIter) {
            if (newList.get(4).equals(_address)) {
                Map<String, Object>unstakeDict = new HashMap<>();
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
