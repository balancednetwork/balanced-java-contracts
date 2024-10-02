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

package network.balanced.score.core.rewards;

import foundation.icon.xcall.NetworkAddress;
import network.balanced.score.core.rewards.utils.BalanceData;
import network.balanced.score.core.rewards.weight.SourceWeightController;
import network.balanced.score.lib.interfaces.Rewards;
import network.balanced.score.lib.interfaces.RewardsXCall;
import network.balanced.score.lib.structs.Point;
import network.balanced.score.lib.structs.RewardsDataEntry;
import network.balanced.score.lib.structs.RewardsDataEntryOld;
import network.balanced.score.lib.structs.VotedSlope;
import network.balanced.score.lib.utils.*;
import score.*;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import scorex.util.ArrayList;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonValue;

import static network.balanced.score.core.rewards.utils.RewardsConstants.*;
import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
import static network.balanced.score.lib.utils.Constants.WEEK_IN_MICRO_SECONDS;
import static network.balanced.score.lib.utils.Math.convertToNumber;
import static network.balanced.score.lib.utils.Math.pow;
import static network.balanced.score.lib.utils.BalancedAddressManager.getBaln;
import static network.balanced.score.lib.utils.BalancedAddressManager.getBoostedBaln;


/***
 * There can be unclaimed rewards if there are no participants in the data source. This can happen in testnet and
 * when we add new source where user haven't participated.
 */
public class RewardsImpl implements Rewards {
    public static final String TAG = "BalancedRewards";
    private static final String START_TIMESTAMP = "start_timestamp";
    private static final String BALN_HOLDINGS = "baln_holdings";
    private static final String EXTERNAL_HOLDINGS = "external_holdings";
    private static final String EXTERNAL_REWARD_PROVIDERS = "external_reward_providers";
    private static final String EXTERNAL_REWARD_TOKENS = "external_reward_tokens";
    private static final String PLATFORM_DAY = "platform_day";
    private static final String DATA_PROVIDERS = "data_providers";
    private static final String BOOST_WEIGHT = "boost_weight";
    private static final String DAILY_VOTABLE_DISTRIBUTIONS = "daily_votable_distributions";
    private static final String DAILY_FIXED_DISTRIBUTIONS = "daily_fixed_distributions";
    private static final String DISTRIBUTION_PERCENTAGES = "distribution_percentages";
    private static final String FIXED_DISTRIBUTION_PERCENTAGES = "fixed_distribution_percentages";
    private static final String VERSION = "version";

    private static final VarDB<BigInteger> startTimestamp = Context.newVarDB(START_TIMESTAMP, BigInteger.class);
    static final DictDB<String, BigInteger> balnHoldings = Context.newDictDB(BALN_HOLDINGS, BigInteger.class);
    static final BranchDB<String, DictDB<Address, BigInteger>> externalHoldings = Context.newBranchDB(EXTERNAL_HOLDINGS, BigInteger.class);
    static final DictDB<Address, Boolean> externalRewardProviders = Context.newDictDB(EXTERNAL_REWARD_PROVIDERS, Boolean.class);
    static final IterableDictDB<Address, Boolean> externalRewardTokens = new IterableDictDB<>(EXTERNAL_REWARD_TOKENS, Boolean.class, Address.class, false);

    private static final VarDB<BigInteger> platformDay = Context.newVarDB(PLATFORM_DAY, BigInteger.class);
    private final static SetDB<Address> dataProviders = new SetDB<>(DATA_PROVIDERS, Address.class, null);
    public static final VarDB<BigInteger> boostWeight = Context.newVarDB(BOOST_WEIGHT, BigInteger.class);
    public static final DictDB<BigInteger, BigInteger> dailyVotableDistribution =
            Context.newDictDB(DAILY_VOTABLE_DISTRIBUTIONS, BigInteger.class);
    public static final BranchDB<String, DictDB<BigInteger, BigInteger>> dailyFixedDistribution =
            Context.newBranchDB(DAILY_FIXED_DISTRIBUTIONS, BigInteger.class);
    private static final IterableDictDB<String, BigInteger> distributionPercentages =
            new IterableDictDB<>(DISTRIBUTION_PERCENTAGES, BigInteger.class, String.class, false);
    private static final IterableDictDB<String, BigInteger> fixedDistributionPercentages =
            new IterableDictDB<>(FIXED_DISTRIBUTION_PERCENTAGES, BigInteger.class, String.class, false);

    private final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);
    private static String NATIVE_NID;

    public RewardsImpl(@Optional Address _governance) {
        SourceWeightController.rewards = this;

        if (BalancedAddressManager.getAddressByName(Names.GOVERNANCE) == null) {
            // On "install" code
            BalancedAddressManager.setGovernance(_governance);
            platformDay.set(BigInteger.ONE);
            distributionPercentages.set(Names.WORKERTOKEN, BigInteger.ZERO);
            distributionPercentages.set(Names.RESERVE, BigInteger.ZERO);
            distributionPercentages.set(Names.DAOFUND, BigInteger.ZERO);

            SourceWeightController.addType(coreTypeName, EXA);
            SourceWeightController.addType(communityTypeName, EXA);
            boostWeight.set(WEIGHT);
        } else if (distributionPercentages.get(WORKER_TOKENS) != null) {
            distributionPercentages.set(Names.WORKERTOKEN, distributionPercentages.get(WORKER_TOKENS));
            distributionPercentages.set(Names.RESERVE, distributionPercentages.get(RESERVE_FUND));
            distributionPercentages.set(Names.DAOFUND, distributionPercentages.get(DAOFUND));
            distributionPercentages.remove(WORKER_TOKENS);
            distributionPercentages.remove(RESERVE_FUND);
            distributionPercentages.remove(DAOFUND);
        }

        if (currentVersion.getOrDefault("").equals(Versions.REWARDS)) {
            Context.revert("Can't Update same version of code");
        }
        currentVersion.set(Versions.REWARDS);
        NATIVE_NID = (String) Context.call(BalancedAddressManager.getXCall(), "getNetworkId");
    }

    @External(readonly = true)
    public String name() {
        return Names.REWARDS;
    }

    @External(readonly = true)
    public String version() {
        return currentVersion.getOrDefault("");
    }

    @External
    public void updateAddress(String name) {
        BalancedAddressManager.resetAddress(name);
    }

    @External(readonly = true)
    public Address getAddress(String name) {
        return BalancedAddressManager.getAddressByName(name);
    }

    @External(readonly = true)
    public BigInteger getEmission(@Optional BigInteger _day) {
        if (_day == null || _day.equals(BigInteger.ZERO)) {
            _day = BigInteger.valueOf(-1);
        }

        if (_day.compareTo(BigInteger.ONE) < 0) {
            _day = _day.add(getDay()).add(BigInteger.ONE);
        }

        Context.require(_day.compareTo(BigInteger.ZERO) > 0, TAG + ": " + "Invalid day.");
        return dailyDistribution(_day);
    }

    @External(readonly = true)
    public Map<String, BigInteger> getBalnHoldings(String[] _holders) {
        Map<String, BigInteger> holdings = new HashMap<>();
        for (String address : _holders) {
            holdings.put(address, balnHoldings.getOrDefault(address, BigInteger.ZERO));
        }

        return holdings;
    }


    @External(readonly = true)
    public Map<String, BigInteger> getHoldings(String _holder) {
        List<Address> tokens = externalRewardTokens.keys();
        DictDB<Address, BigInteger> holdingsDB = externalHoldings.at(_holder);
        Map<String, BigInteger> holdings = new HashMap<>();

        for (Address token : tokens) {
            holdings.put(token.toString(), holdingsDB.getOrDefault(token, BigInteger.ZERO));
        }

        holdings.put(getBaln().toString(), balnHoldings.getOrDefault(_holder, BigInteger.ZERO));

        return holdings;
    }

    @External(readonly = true)
    public BigInteger getBalnHolding(String _holder) {
        Address baln = getBaln();
        return getRewards(_holder).get(baln.toString());
    }

    @External(readonly = true)
    public Map<String, BigInteger> getRewards(String _holder) {
        Map<String, BigInteger> accruedRewards = getHoldings(_holder);
        int dataSourcesCount = DataSourceDB.size();
        for (int i = 0; i < dataSourcesCount; i++) {
            String name = DataSourceDB.names.get(i);
            DataSourceImpl dataSource = DataSourceDB.get(name);
            BigInteger currentTime = getTime();
            BalanceData balances = new BalanceData();
            balances.prevBalance = dataSource.getBalance(_holder);
            balances.prevSupply = dataSource.getTotalSupply();
            balances.prevWorkingBalance = dataSource.getWorkingBalance(_holder, true);
            balances.prevWorkingSupply = dataSource.getWorkingSupply(true);
            Map<Address, BigInteger> sourceRewards = dataSource.updateSingleUserData(currentTime, balances, _holder, true);
            for (Map.Entry<Address, BigInteger> entry : sourceRewards.entrySet()) {
                accruedRewards.put(entry.getKey().toString(), accruedRewards.get(entry.getKey().toString()).add(entry.getValue()));
            }
        }

        return accruedRewards;
    }

    @External(readonly = true)
    public List<String> getDataSourceNames() {
        List<String> names = new ArrayList<>();
        int dataSourcesCount = DataSourceDB.size();
        for (int i = 0; i < dataSourcesCount; i++) {
            names.add(DataSourceDB.names.get(i));
        }

        return names;
    }

    @External
    public void createDataSource(String _name, Address _address, int sourceType) {
        onlyOwner();
        Context.require(_address.isContract(), TAG + " : Data source must be a contract.");

        DataSourceDB.newSource(_name, _address);
        SourceWeightController.addSource(_name, sourceType, BigInteger.ZERO);
    }

    @External(readonly = true)
    public Map<String, Map<String, Object>> getDataSources() {
        Map<String, Map<String, Object>> dataSources = new HashMap<>();
        int dataSourcesCount = DataSourceDB.size();
        for (int i = 0; i < dataSourcesCount; i++) {
            String name = DataSourceDB.names.get(i);
            DataSourceImpl dataSource = DataSourceDB.get(name);

            dataSources.put(name, dataSource.getData());
        }

        return dataSources;
    }

    @External(readonly = true)
    public Map<String, Map<String, Object>> getUserSourceData(String user) {
        Map<String, Map<String, Object>> dataSources = new HashMap<>();
        int dataSourcesCount = DataSourceDB.size();
        for (int i = 0; i < dataSourcesCount; i++) {
            String name = DataSourceDB.names.get(i);
            DataSourceImpl dataSource = DataSourceDB.get(name);
            dataSources.put(name, dataSource.getUserData(user));
        }

        return dataSources;
    }

    @External(readonly = true)
    public Map<String, Map<String, Object>> getDataSourcesAt(BigInteger _day) {
        Map<String, Map<String, Object>> dataSources = new HashMap<>();
        int dataSourcesCount = DataSourceDB.size();
        for (int i = 0; i < dataSourcesCount; i++) {
            String name = DataSourceDB.names.get(i);
            DataSourceImpl dataSource = DataSourceDB.get(name);
            dataSources.put(name, dataSource.getDataAt(_day));
        }

        return dataSources;
    }

    @External(readonly = true)
    public Map<String, Map<String, Object>> getSourceVoteData() {
        Map<String, Map<String, Object>> dataSources = new HashMap<>();
        BigInteger timestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        BigInteger nextWeekTimestamp = timestamp.add(WEEK_IN_MICRO_SECONDS);
        int dataSourcesCount = DataSourceDB.size();
        for (int i = 0; i < dataSourcesCount; i++) {
            Map<String, Object> data = new HashMap<>();
            String name = DataSourceDB.names.get(i);
            data.put("votable", SourceWeightController.isVotable(name));
            data.put("type", SourceWeightController.getSourceType(name));
            data.put("weight", SourceWeightController.getRelativeWeight(name, timestamp));
            data.put("currentWeight", SourceWeightController.getRelativeWeight(name, nextWeekTimestamp));
            Point sourceWeightPoint = SourceWeightController.getSourcePointsWeight(name);
            data.put("currentBias", sourceWeightPoint.bias);
            data.put("currentSlope", sourceWeightPoint.slope);
            dataSources.put(name, data);
        }

        return dataSources;
    }

    @External(readonly = true)
    public Map<String, Object> getSourceData(String _name) {
        return DataSourceDB.get(_name).getData();
    }

    @External(readonly = true)
    public Map<String, BigInteger> getWorkingBalanceAndSupply(String _name, String _user) {
        DataSourceImpl datasource = DataSourceDB.get(_name);
        return Map.of(
                "workingSupply", datasource.getWorkingSupply(true),
                "workingBalance", datasource.getWorkingBalance(_user, true)
        );
    }

    @External(readonly = true)
    public Map<String, Map<String, BigInteger>> getBoostData(String user, @Optional String[] sources) {
        if (sources == null) {
            sources = getAllSources();
        }

        Map<String, Map<String, BigInteger>> boostData = new HashMap<>();
        for (String name : sources) {
            Map<String, BigInteger> sourceData = new HashMap<>();
            DataSourceImpl datasource = DataSourceDB.get(name);
            Map<String, BigInteger> balanceAndSupply = datasource.loadCurrentSupply(user);

            sourceData.put("workingBalance", datasource.getWorkingBalance(user, true));
            sourceData.put("workingSupply", datasource.getWorkingSupply(true));
            sourceData.put("balance", balanceAndSupply.get(BALANCE));
            sourceData.put("supply", balanceAndSupply.get(TOTAL_SUPPLY));

            boostData.put(name, sourceData);
        }

        return boostData;
    }

    /**
     * This method should be called once after the balanced has been launched, so that if there are baln tokens to
     * mint it will be minted. In continuous rewards it will be minted while in non-continuous it will not  be minted
     */
    @External
    public boolean distribute() {
        checkStatus();
        BigInteger platformDay = RewardsImpl.platformDay.get();
        BigInteger day = getDay();

        while (platformDay.compareTo(day) <= 0) {
            mintAndAllocateBalnReward(platformDay);
            platformDay = RewardsImpl.platformDay.get();
        }

        return true;
    }

    private boolean mintAndAllocateBalnReward(BigInteger platformDay) {
        BigInteger distribution = dailyDistribution(platformDay);
        Context.call(getBaln(), "mint", distribution, new byte[0]);

        BigInteger shares = HUNDRED_PERCENTAGE;
        BigInteger remaining = distribution;
        List<String> recipients = distributionPercentages.keys();
        for (String recipient : recipients) {
            BigInteger split = distributionPercentages.get(recipient);
            BigInteger share = split.multiply(remaining).divide(shares);
            Context.call(getBaln(), "transfer", BalancedAddressManager.getAddress(recipient) , share, new byte[0]);
            remaining = remaining.subtract(share);
            shares = shares.subtract(split);
        }

        List<String> fixedPercentageSources = fixedDistributionPercentages.keys();
        for (String recipient : fixedPercentageSources) {
            BigInteger split = fixedDistributionPercentages.get(recipient);
            BigInteger share = split.multiply(remaining).divide(shares);
            dailyFixedDistribution.at(recipient).set(platformDay, share);
            remaining = remaining.subtract(share);
            shares = shares.subtract(split);
        }

        dailyVotableDistribution.set(platformDay, remaining);

        RewardsImpl.platformDay.set(platformDay.add(BigInteger.ONE));

        return false;
    }

    @External
    public void boost(String[] sources) {
        checkStatus();
        Address user = Context.getCaller();
        BigInteger boostedBalance = fetchBoostedBalance(user);
        BigInteger boostedSupply = fetchBoostedSupply();
        updateAllUserRewards(user.toString(), sources, boostedBalance, boostedSupply);
    }

    @External
    public void handleCallMessage(String _from, byte[] _data, @Optional String[] _protocols) {
        checkStatus();
        only(BalancedAddressManager.getXCall());
        XCallUtils.verifyXCallProtocols(_from, _protocols);
        RewardsXCall.process(this, _from, _data);
    }

    public void xClaimRewards(String from, @Optional String to, @Optional String[] sources) {
        if (to.isEmpty()){
            to = from;
        }
        _claimRewards(to, sources);
    }

    @External
    public void claimRewards(@Optional String[] sources) {
        checkStatus();
        if (sources == null || sources.length==0) {
            sources = getAllSources();
        }
        _claimRewards(Context.getCaller().toString(), sources);
    }

    private void _claimRewards(String address, String[] sources){
        NetworkAddress networkAddress = NetworkAddress.valueOf(address, NATIVE_NID);
        BigInteger boostedBalance = fetchBoostedBalance(address);
        BigInteger boostedSupply = fetchBoostedSupply();
        updateAllUserRewards(address, sources, boostedBalance, boostedSupply);

        List<Address> tokens = externalRewardTokens.keys();
        DictDB<Address, BigInteger> holdingsDB = externalHoldings.at(address);

        for (Address token : tokens) {
            BigInteger amount = holdingsDB.getOrDefault(token, BigInteger.ZERO);
            if (amount.compareTo(BigInteger.ZERO) > 0) {
                if(networkAddress.net().equals(NATIVE_NID)) {
                    Context.call(token, "transfer", Address.fromString(networkAddress.account()), amount, new byte[0]);
                }else{
                    try {
                        Context.call(token, "xTransfer", address, amount, new byte[0]);
                    }catch (Exception ignored){}
                }
                holdingsDB.set(token, null);
                RewardsClaimedV2(token, address, amount);
            }
        }

        BigInteger userClaimableRewards = balnHoldings.getOrDefault(address, BigInteger.ZERO);
        if (userClaimableRewards.compareTo(BigInteger.ZERO) > 0) {
            balnHoldings.set(address, null);
            Address baln = getBaln();
            if(networkAddress.net().equals(NATIVE_NID)) {
                Address nativeAddress = Address.fromString(networkAddress.account());
                Context.call(baln, "transfer", nativeAddress, userClaimableRewards, new byte[0]);
                RewardsClaimed(nativeAddress, userClaimableRewards);
            }else{
                try {
                    Context.call(baln, "xTransfer", address, userClaimableRewards, new byte[0]);
                }catch (Exception ignored){}
            }

            RewardsClaimedV2(baln, address, userClaimableRewards);
        }

    }
    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        checkStatus();
        Address token = Context.getCaller();
        if (token.equals(getBaln())) {
            return;
        }

        Context.require(externalRewardTokens.getOrDefault(token, false), "Only whitelisted tokens is allowed as rewards tokens");
        Context.require(externalRewardProviders.getOrDefault(_from, false), "Only whitelisted addresses is allowed to provider external rewards");
        String unpackedData = new String(_data);
        Context.require(!unpackedData.isEmpty(), "Token Fallback: Data can't be empty");

        JsonArray amounts = Json.parse(unpackedData).asArray();

        BigInteger rewardDay = getDay().add(BigInteger.ONE);
        BigInteger total = BigInteger.ZERO;
        for (JsonValue jsonValue : amounts) {
            BigInteger amount = convertToNumber(jsonValue.asObject().get("amount"));
            String source = jsonValue.asObject().get("source").asString();

            total = total.add(amount);

            DataSourceImpl dataSource = DataSourceDB.get(source);
            if (dataSource.getTotalExternalWeight(token).equals(BigInteger.ZERO)) {
                if (!ArrayDBUtils.arrayDbContains(dataSource.getRewardTokensDB(), token)) {
                    dataSource.addRewardToken(token);
                }
            }

            BigInteger prevAmount = dataSource.getTotalDist(rewardDay);
            dataSource.setTotalExternalDist(rewardDay, token, amount.add(prevAmount));
        }

        Context.require(total.equals(_value), "Total allocations do not match amount deposited");
    }

    @External
    public void addDataProvider(Address _source) {
        onlyOwner();
        dataProviders.add(_source);
    }

    @External
    public void configureExternalReward(Address token, boolean allow) {
        onlyOwner();
        externalRewardTokens.set(token, allow);
    }

    @External
    public void configureExternalRewardProvider(Address provider, boolean allow) {
        onlyOwner();
        externalRewardProviders.set(provider, allow);
    }

    @External(readonly = true)
    public List<Address> getDataProviders() {
        int dataProvidersSize = dataProviders.size();
        List<Address> dataProvidersList = new ArrayList<>();
        for (int i = 0; i < dataProvidersSize; i++) {
            dataProvidersList.add(dataProviders.get(i));
        }

        return dataProvidersList;
    }

    @External
    public void removeDataProvider(Address _source) {
        onlyOwner();
        dataProviders.remove(_source);
    }

    // old versions only used by balanced contracts
    @External
    public void updateRewardsData(String _name, BigInteger _totalSupply, Address _user, BigInteger _balance) {
        checkStatus();
        DataSourceImpl dataSource = DataSourceDB.get(_name);
        Context.require(dataSource.getContractAddress().equals(Context.getCaller()), TAG + ": Only data provider are " +
                "allowed to update rewards data");

        BigInteger currentTime = getTime();
        distribute();
        String user = _user.toString();
        BalanceData balances = new BalanceData();
        balances.boostedBalance = fetchBoostedBalance(_user);
        balances.boostedSupply = fetchBoostedSupply();
        Map<String, BigInteger> balanceAndSupply = dataSource.loadCurrentSupply(user);
        balances.balance = balanceAndSupply.get(BALANCE);
        balances.supply = balanceAndSupply.get(TOTAL_SUPPLY);
        balances.prevBalance = dataSource.getBalance(user);
        balances.prevSupply = dataSource.getTotalSupply();
        balances.prevWorkingBalance = dataSource.getWorkingBalance(user, _balance, false);
        balances.prevWorkingSupply = dataSource.getWorkingSupply(_totalSupply, false);

        updateUserAccruedRewards(_name, currentTime, dataSource, user, balances);
    }

    // old versions only used by balanced contracts
    @External
    public void updateBatchRewardsData(String _name, BigInteger _totalSupply, RewardsDataEntryOld[] _data) {
        checkStatus();
        DataSourceImpl dataSource = DataSourceDB.get(_name);
        Context.require(dataSource.getContractAddress().equals(Context.getCaller()), TAG + ": Only data provider are " +
                "allowed to update rewards data");

        BigInteger currentTime = getTime();
        distribute();

        BigInteger boostedSupply = fetchBoostedSupply();

        for (RewardsDataEntryOld entry : _data) {
            BalanceData balances = new BalanceData();
            balances.boostedSupply = boostedSupply;
            balances.boostedBalance = fetchBoostedBalance(entry._user);
            String user = entry._user.toString();
            Map<String, BigInteger> balanceAndSupply = dataSource.loadCurrentSupply(user);
            balances.balance = balanceAndSupply.get(BALANCE);
            balances.supply = balanceAndSupply.get(TOTAL_SUPPLY);
            balances.prevBalance = dataSource.getBalance(user);
            balances.prevSupply = dataSource.getTotalSupply();
            balances.prevWorkingBalance = dataSource.getWorkingBalance(user, entry._balance, false);
            balances.prevWorkingSupply = dataSource.getWorkingSupply(_totalSupply, false);

            updateUserAccruedRewards(_name, currentTime, dataSource, user, balances);
        }
    }

    @External
    public void updateBalanceAndSupply(String _name, BigInteger _totalSupply, String _user, BigInteger _balance) {
        checkStatus();
        DataSourceImpl dataSource = DataSourceDB.get(_name);
        Context.require(dataSource.getContractAddress().equals(Context.getCaller()), TAG + ": Only data provider are " +
                "allowed to update rewards data");

        BigInteger currentTime = getTime();
        distribute();

        BalanceData balances = new BalanceData();
        balances.boostedBalance = fetchBoostedBalance(_user);
        balances.boostedSupply = fetchBoostedSupply();
        balances.balance = _balance;
        balances.supply = _totalSupply;
        balances.prevBalance = dataSource.getBalance(_user);
        balances.prevSupply = dataSource.getTotalSupply();
        balances.prevWorkingBalance = dataSource.getWorkingBalance(_user);
        balances.prevWorkingSupply = dataSource.getWorkingSupply();

        updateUserAccruedRewards(_name, currentTime, dataSource, _user, balances);
    }

    @External
    public void updateBalanceAndSupplyBatch(String _name, BigInteger _totalSupply, RewardsDataEntry[] _data) {
        checkStatus();
        DataSourceImpl dataSource = DataSourceDB.get(_name);
        Context.require(dataSource.getContractAddress().equals(Context.getCaller()), TAG + ": Only data provider are " +
                "allowed to update rewards data");

        BigInteger currentTime = getTime();
        distribute();

        BigInteger boostedSupply = fetchBoostedSupply();

        for (RewardsDataEntry entry : _data) {
            BalanceData balances = new BalanceData();
            balances.boostedBalance = fetchBoostedBalance(entry._user);
            balances.boostedSupply = boostedSupply;
            balances.balance = entry._balance;
            balances.supply = _totalSupply;
            balances.prevBalance = dataSource.getBalance(entry._user);
            balances.prevSupply = dataSource.getTotalSupply();
            balances.prevWorkingBalance = dataSource.getWorkingBalance(entry._user);
            balances.prevWorkingSupply = dataSource.getWorkingSupply();

            updateUserAccruedRewards(_name, currentTime, dataSource, entry._user, balances);
        }
    }

    @External
    public void onKick(Address user) {
        checkStatus();
        only(getBoostedBaln());
        BigInteger boostedSupply = fetchBoostedSupply();
        updateAllUserRewards(user.toString(), getAllSources(), BigInteger.ZERO, boostedSupply);
    }

    @External
    public void kick(Address user, String[] sources) {
        checkStatus();
        BigInteger boostedBalance = fetchBoostedBalance(user);
        BigInteger boostedSupply = fetchBoostedSupply();
        updateAllUserRewards(user.toString(), sources, boostedBalance, boostedSupply);
    }

    @External
    public void onBalanceUpdate(Address user, BigInteger balance) {
        checkStatus();
        only(getBoostedBaln());
        BigInteger boostedSupply = fetchBoostedSupply();
        updateAllUserRewards(user.toString(), getAllSources(), balance, boostedSupply);
    }

    @External
    public void setBoostWeight(BigInteger weight) {
        onlyOwner();
        Context.require(weight.compareTo(HUNDRED_PERCENTAGE.divide(BigInteger.valueOf(100))) >= 0, "Boost weight has " +
                "to be above 1%");
        Context.require(weight.compareTo(HUNDRED_PERCENTAGE) <= 0, "Boost weight has to be below 100%");
        boostWeight.set(weight);
    }

    @External(readonly = true)
    public BigInteger getBoostWeight() {
        return boostWeight.get();
    }

    @External(readonly = true)
    public String[] getUserSources(String user) {
        int dataSourcesCount = DataSourceDB.size();
        List<String> sources = new ArrayList<>();
        for (int i = 0; i < dataSourcesCount; i++) {
            String name = DataSourceDB.names.get(i);
            DataSourceImpl dataSource = DataSourceDB.get(name);
            BigInteger workingBalance = dataSource.getWorkingBalance(user, true);
            if (workingBalance.compareTo(BigInteger.ZERO) > 0) {
                sources.add(name);
            }
        }

        int userSourcesCount = sources.size();
        String[] arrSources = new String[userSourcesCount];
        for (int i = 0; i < userSourcesCount; i++) {
            arrSources[i] = sources.get(i);
        }

        return arrSources;
    }

    @External
    public void setVotable(String name, boolean votable) {
        onlyOwner();
        SourceWeightController.setVotable(name, votable);
    }

    @External
    public void addType(String name) {
        onlyOwner();
        SourceWeightController.addType(name, BigInteger.ZERO);
    }

    @External
    public void changeTypeWeight(int typeId, BigInteger weight) {
        onlyOwner();
        SourceWeightController.changeTypeWeight(typeId, weight);
    }

    @External
    public void setPlatformDistPercentage(String name, BigInteger percentage) {
        onlyOwner();
        distributionPercentages.set(name, percentage);
        verifyPercentages();
    }

    @External
    public void setFixedSourcePercentage(String name, BigInteger percentage) {
        onlyOwner();
        DataSourceImpl dataSource = DataSourceDB.get(name);
        Context.require(dataSource.getName().equals(name), "There is no data source with the name " + name);
        if (percentage.equals(BigInteger.ZERO)) {
            fixedDistributionPercentages.remove(name);
        } else {
            fixedDistributionPercentages.set(name, percentage);
        }

        verifyPercentages();
    }

    @External(readonly = true)
    public Map<String, Map<String, BigInteger>> getDistributionPercentages() {
        Map<String, Map<String, BigInteger>> allPercentages = new HashMap<>();
        Map<String, BigInteger> basePercentages = new HashMap<>();
        Map<String, BigInteger> fixedPercentages = new HashMap<>();
        Map<String, BigInteger> votePercentages = new HashMap<>();

        BigInteger total = HUNDRED_PERCENTAGE;
        List<String> recipients = distributionPercentages.keys();
        for (String recipient : recipients) {
            BigInteger percentage = distributionPercentages.get(recipient);
            basePercentages.put(recipient, percentage);
            total = total.subtract(percentage);
        }

        List<String> fixedPercentageSources = fixedDistributionPercentages.keys();
        for (String recipient : fixedPercentageSources) {
            BigInteger percentage = fixedDistributionPercentages.get(recipient);
            fixedPercentages.put(recipient, percentage);
            total = total.subtract(percentage);
        }

        String[] sources = getAllSources();
        BigInteger votingPercentage = total;
        BigInteger time = BigInteger.valueOf(Context.getBlockTimestamp());
        for (String source : sources) {
            BigInteger percent =
                    SourceWeightController.getRelativeWeight(source, time).multiply(votingPercentage).divide(HUNDRED_PERCENTAGE);
            votePercentages.put(source, percent);
        }

        allPercentages.put("Base", basePercentages);
        allPercentages.put("Fixed", fixedPercentages);
        allPercentages.put("Voting", votePercentages);

        return allPercentages;
    }

    @External
    public void checkpoint() {
        checkStatus();
        SourceWeightController.checkpoint();
    }

    @External
    public void checkpointSource(String name) {
        checkStatus();
        SourceWeightController.checkpointSource(name);
    }

    @External
    public BigInteger updateRelativeSourceWeight(String name, BigInteger time) {
        checkStatus();
        return SourceWeightController.updateRelativeWeight(name, time);
    }

    @External(readonly = true)
    public BigInteger getRelativeSourceWeight(String name, @Optional BigInteger time) {
        return SourceWeightController.getRelativeWeight(name, time);
    }

    @External
    public void voteForSource(String name, BigInteger userWeight) {
        checkStatus();
        SourceWeightController.voteForSourceWeights(name, userWeight);
    }

    @External(readonly = true)
    public boolean isVotable(String name) {
        return SourceWeightController.isVotable(name);
    }

    @External(readonly = true)
    public Point getSourceWeight(String sourceName, @Optional BigInteger time) {
        if (time.equals(BigInteger.ZERO)) {
            return SourceWeightController.getSourcePointsWeight(sourceName);
        }

        return SourceWeightController.getSourcePointsWeightAt(sourceName, time);
    }

    @External(readonly = true)
    public BigInteger getCurrentTypeWeight(int typeId) {
        return SourceWeightController.getCurrentTypeWeight(typeId);
    }

    @External(readonly = true)
    public BigInteger getTotalWeight() {
        return SourceWeightController.getTotalWeight();
    }

    @External(readonly = true)
    public Point getWeightsSumPerType(int typeId) {
        return SourceWeightController.getPointsSumPerType(typeId);
    }

    @External(readonly = true)
    public VotedSlope getUserSlope(Address user, String source) {
        return SourceWeightController.getUserSlope(user, source);
    }

    @External(readonly = true)
    public BigInteger getLastUserVote(Address user, String source) {
        return SourceWeightController.getLastUserVote(user, source);
    }

    @External(readonly = true)
    public int getTypeId(String name) {
        return SourceWeightController.getTypeId(name);
    }

    @External(readonly = true)
    public String getTypeName(int typeId) {
        return SourceWeightController.getTypeName(typeId);
    }

    @External(readonly = true)
    public int getSourceType(String sourceName) {
        return SourceWeightController.getSourceType(sourceName);
    }

    @External(readonly = true)
    public Map<String, Map<String, BigInteger>> getUserVoteData(Address user) {
        Map<String, Map<String, BigInteger>> data = new HashMap<>();
        BigInteger currentTime = BigInteger.valueOf(Context.getBlockTimestamp());

        int dataSourcesCount = DataSourceDB.size();
        for (int i = 0; i < dataSourcesCount; i++) {
            String source = DataSourceDB.names.get(i);
            VotedSlope votedSlope = getUserSlope(user, source);
            BigInteger lastVote = SourceWeightController.getLastUserVote(user, source);
            BigInteger timeSinceLastVote = currentTime.subtract(lastVote);
            if (timeSinceLastVote.compareTo(SourceWeightController.WEIGHT_VOTE_DELAY) > 0 && votedSlope.power.equals(BigInteger.ZERO)) {
                continue;
            }

            Map<String, BigInteger> sourceData = new HashMap<>();
            sourceData.put("slope", votedSlope.slope);
            sourceData.put("power", votedSlope.power);
            sourceData.put("end", votedSlope.end);
            sourceData.put("lastVote", lastVote);
            data.put(source, sourceData);
        }

        return data;
    }

    private void verifyPercentages() {
        BigInteger total = BigInteger.ZERO;
        List<String> recipients = distributionPercentages.keys();
        for (String recipient : recipients) {
            BigInteger split = distributionPercentages.get(recipient);
            total = total.add(split);
        }

        List<String> fixedPercentageSources = fixedDistributionPercentages.keys();
        for (String recipient : fixedPercentageSources) {
            BigInteger split = fixedDistributionPercentages.get(recipient);
            total = total.add(split);
        }

        Context.require(total.compareTo(HUNDRED_PERCENTAGE) <= 0, "Sum of distributions exceeds 100%");
    }

    private BigInteger getVotableDist() {
        BigInteger total = HUNDRED_PERCENTAGE;
        List<String> recipients = distributionPercentages.keys();
        for (String recipient : recipients) {
            BigInteger split = distributionPercentages.get(recipient);
            total = total.subtract(split);
        }

        List<String> fixedPercentageSources = fixedDistributionPercentages.keys();
        for (String recipient : fixedPercentageSources) {
            BigInteger split = fixedDistributionPercentages.get(recipient);
            total = total.subtract(split);
        }

        return total;
    }

    public static String[] getAllSources() {
        int dataSourcesCount = DataSourceDB.size();
        String[] sources = new String[dataSourcesCount];
        for (int i = 0; i < dataSourcesCount; i++) {
            String name = DataSourceDB.names.get(i);
            sources[i] = name;
        }

        return sources;
    }

    private void updateAllUserRewards(String user, String[] sources, BigInteger boostedBalance,
                                      BigInteger boostedSupply) {
        distribute();
        BigInteger currentTime = getTime();
        for (String name : sources) {
            DataSourceImpl dataSource = DataSourceDB.get(name);
            BigInteger workingBalance = dataSource.getWorkingBalance(user, false);
            if (workingBalance.equals(BigInteger.ZERO)) {
                continue;
            }

            BalanceData balances = new BalanceData();
            balances.boostedBalance = fetchBoostedBalance(user);
            balances.boostedSupply = boostedSupply;
            Map<String, BigInteger> balanceAndSupply = dataSource.loadCurrentSupply(user);
            balances.balance = balanceAndSupply.get(BALANCE);
            balances.supply = balanceAndSupply.get(TOTAL_SUPPLY);
            balances.prevBalance = dataSource.getBalance(user);
            balances.prevSupply = dataSource.getTotalSupply();
            balances.prevWorkingBalance = workingBalance;
            balances.prevWorkingSupply = dataSource.getWorkingSupply(false);

            updateUserAccruedRewards(name, currentTime, dataSource, user, balances);
        }
    }

    private void updateUserAccruedRewards(String _name, BigInteger currentTime, DataSourceImpl dataSource,
                                          String user, BalanceData balances) {

        Map<Address, BigInteger> accruedRewards = dataSource.updateSingleUserData(currentTime, balances, user, false);
        dataSource.updateWorkingBalanceAndSupply(user, balances);

        Address baln = getBaln();
        if (accruedRewards.get(baln).compareTo(BigInteger.ZERO) > 0) {
            BigInteger newHoldings =
                    balnHoldings.getOrDefault(user, BigInteger.ZERO).add(accruedRewards.get(baln));
            balnHoldings.set(user, newHoldings);
            RewardsAccrued(user, _name, accruedRewards.get(baln));
            accruedRewards.remove(baln);

        }
        DictDB<Address, BigInteger> externalHoldingsDB = externalHoldings.at(user);
        for (Map.Entry<Address, BigInteger> entry : accruedRewards.entrySet()) {
            BigInteger prevRewards = externalHoldingsDB.getOrDefault(entry.getKey(), BigInteger.ZERO);
            externalHoldingsDB.set(entry.getKey(), prevRewards.add(entry.getValue()));
        }

    }

    @External
    public void setTimeOffset(BigInteger _timestamp) {
        onlyOwner();
        startTimestamp.set(_timestamp);
        Context.require(getDay().compareTo(BigInteger.ZERO) > 0,
                TAG + ": Day should begin from 1. Please set earlier time offset");
    }

    @External(readonly = true)
    public BigInteger getTimeOffset() {
        return startTimestamp.get();
    }

    public static BigInteger getDay() {
        BigInteger blockTime = BigInteger.valueOf(Context.getBlockTimestamp()).subtract(startTimestamp.get());
        return blockTime.divide(MICRO_SECONDS_IN_A_DAY);
    }

    private static BigInteger getTime() {
        return BigInteger.valueOf(Context.getBlockTimestamp()).subtract(startTimestamp.get());
    }

    static Object call(Address targetAddress, String method, Object... params) {
        return Context.call(targetAddress, method, params);
    }

    public static BigInteger getTotalDist(String sourceName, BigInteger day, boolean readonly) {
        BigInteger dist = dailyVotableDistribution.getOrDefault(day, BigInteger.ZERO);
        BigInteger time = day.multiply(MICRO_SECONDS_IN_A_DAY).add(startTimestamp.get());
        BigInteger weight;
        if (readonly) {
            weight = SourceWeightController.getRelativeWeight(sourceName, time);
        } else {
            weight = SourceWeightController.updateRelativeWeight(sourceName, time);
        }

        BigInteger fixedDist = dailyFixedDistribution.at(sourceName).getOrDefault(day, BigInteger.ZERO);

        return weight.multiply(dist).divide(HUNDRED_PERCENTAGE).add(fixedDist);
    }

    private static BigInteger dailyDistribution(BigInteger day) {
        BigInteger baseDistribution = pow(BigInteger.TEN, 23);
        int offset = 5;
        if (day.compareTo(BigInteger.valueOf(60)) <= 0) {
            return baseDistribution;
        } else if (day.compareTo(BigInteger.valueOf(66)) <= 0) {
            BigInteger index = day.subtract(BigInteger.valueOf(60));
            BigInteger decay = pow(BigInteger.valueOf(995), index.intValue());
            BigInteger decayOffset = pow(BigInteger.valueOf(1000), index.intValue());
            BigInteger minDistribution = BigInteger.valueOf(1250).multiply(EXA);
            BigInteger distribution = decay.multiply(baseDistribution).divide(decayOffset);

            return minDistribution.max(distribution);
        } else {
            int index = day.subtract(BigInteger.valueOf(60)).intValue();
            BigInteger distribution = baseDistribution;

            for (int i = 0; i < offset; i++) {
                distribution = distribution.multiply(BigInteger.valueOf(995));
            }

            int exponent = index - offset;
            for (int i = 0; i < exponent; i++) {
                distribution = distribution.multiply(BigInteger.valueOf(995)).divide(BigInteger.valueOf(1000));
            }

            for (int i = 0; i < offset; i++) {
                distribution = distribution.divide(BigInteger.valueOf(1000));
            }

            BigInteger minDistribution = BigInteger.valueOf(1250).multiply(EXA);

            return minDistribution.max(distribution);
        }
    }

    private BigInteger fetchBoostedBalance(String user) {
        if (user.contains("/")) {
            return BigInteger.ZERO;
        }

        return fetchBoostedBalance(Address.fromString(user));
    }

    private BigInteger fetchBoostedBalance(Address user) {
        try {
            return (BigInteger) RewardsImpl.call(getBoostedBaln(), "balanceOf", user, BigInteger.ZERO);
        } catch (Exception e) {
            return BigInteger.ZERO;
        }
    }

    private BigInteger fetchBoostedSupply() {
        try {
            return (BigInteger) RewardsImpl.call(getBoostedBaln(), "totalSupply", BigInteger.ZERO);
        } catch (Exception e) {
            return BigInteger.ZERO;
        }
    }

    @EventLog(indexed = 1)
    public void RewardsClaimed(Address _address, BigInteger _amount) {
    }

    @EventLog(indexed = 2)
    public void RewardsClaimedV2(Address _token, String _address, BigInteger _amount) {
    }


    @EventLog(indexed = 2)
    public void Report(BigInteger _day, String _name, BigInteger _dist, BigInteger _value) {
    }

    @EventLog(indexed = 2)
    public void RewardsAccrued(String _user, String _source, BigInteger _value) {
    }

    @EventLog(indexed = 2)
    public void VoteCast(String vote_name, boolean vote, Address voter, BigInteger stake, BigInteger total_for,
                         BigInteger total_against) {
    }

    @EventLog(indexed = 2)
    public void AddType(String typeName, int typeId) {
    }

    @EventLog(indexed = 1)
    public void NewTypeWeight(int typeId, BigInteger time, BigInteger weight, BigInteger totalWeight) {
    }

    @EventLog(indexed = 2)
    public void VoteForSource(String sourceName, Address user, BigInteger weight, BigInteger time) {
    }

    @EventLog(indexed = 1)
    public void NewSource(String sourceName, int typeId, BigInteger weight) {
    }

}
