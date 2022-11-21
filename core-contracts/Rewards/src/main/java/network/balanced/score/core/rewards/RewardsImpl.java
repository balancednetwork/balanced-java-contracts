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

import network.balanced.score.core.rewards.utils.EventLogger;
import network.balanced.score.core.rewards.utils.RewardsConstants;
import network.balanced.score.core.rewards.weight.SourceWeightController;
import network.balanced.score.lib.interfaces.Rewards;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.structs.Point;
import network.balanced.score.lib.structs.RewardsDataEntry;
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.structs.VotedSlope;
import network.balanced.score.lib.utils.IterableDictDB;
import network.balanced.score.lib.utils.SetDB;
import score.*;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import scorex.util.ArrayList;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.rewards.utils.RewardsConstants.*;
import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
import static network.balanced.score.lib.utils.DBHelpers.contains;
import static network.balanced.score.lib.utils.Math.pow;


/***
 * There can be unclaimed rewards if there are no participants in the data source. This can happen in testnet and
 * when we add new source where user haven't participated.
 */
public class RewardsImpl implements Rewards {
    public static final String TAG = "BalancedRewards";

    private static final String GOVERNANCE = "governance";
    private static final String ADMIN = "admin";
    private static final String BALN_ADDRESS = "baln_address";
    private static final String BOOSTED_BALN_ADDRESS = "boosted_baln_address";
    private static final String BWT_ADDRESS = "bwt_address";
    private static final String RESERVE_FUND = "reserve_fund";
    private static final String DAO_FUND = "dao_fund";

    private static final String START_TIMESTAMP = "start_timestamp";
    private static final String BALN_HOLDINGS = "baln_holdings";
    private static final String PLATFORM_DAY = "platform_day";
    private static final String DATA_PROVIDERS = "data_providers";
    private static final String BOOST_WEIGHT = "boost_weight";
    private static final String DAILY_VOTABLE_DISTRIBUTIONS = "daily_votable_distributions";
    private static final String DAILY_FIXED_DISTRIBUTIONS = "daily_fixed_distributions";
    private static final String DISTRIBUTION_PERCENTAGES = "distribution_percentages";
    private static final String FIXED_DISTRIBUTION_PERCENTAGES = "fixed_distribution_percentages";

    private static final VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);
    private static final VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);
    private static final VarDB<Address> balnAddress = Context.newVarDB(BALN_ADDRESS, Address.class);
    public static final VarDB<Address> boostedBaln = Context.newVarDB(BOOSTED_BALN_ADDRESS, Address.class);
    private static final VarDB<Address> bwtAddress = Context.newVarDB(BWT_ADDRESS, Address.class);
    private static final VarDB<Address> reserveFund = Context.newVarDB(RESERVE_FUND, Address.class);
    private static final VarDB<Address> daofund = Context.newVarDB(DAO_FUND, Address.class);
    private static final VarDB<BigInteger> startTimestamp = Context.newVarDB(START_TIMESTAMP, BigInteger.class);
    static final DictDB<String, BigInteger> balnHoldings = Context.newDictDB(BALN_HOLDINGS, BigInteger.class);

    private static final VarDB<BigInteger> platformDay = Context.newVarDB(PLATFORM_DAY, BigInteger.class);
    private final static SetDB<Address> dataProviders = new SetDB<>(DATA_PROVIDERS, Address.class, null);
    public static final VarDB<BigInteger> boostWeight = Context.newVarDB(BOOST_WEIGHT, BigInteger.class);
    public static final DictDB<BigInteger, BigInteger> dailyVotableDistribution =
            Context.newDictDB(DAILY_VOTABLE_DISTRIBUTIONS, BigInteger.class);
    public static final BranchDB<String, DictDB<BigInteger, BigInteger>> dailyFixedDistribution =
            Context.newBranchDB(DAILY_FIXED_DISTRIBUTIONS, BigInteger.class);
    public static final VarDB<BigInteger> weightControllerMigrationDay = Context.newVarDB(
            "weightControllerMigrationDay", BigInteger.class);
    private static final IterableDictDB<String, BigInteger> distributionPercentages =
            new IterableDictDB<>(DISTRIBUTION_PERCENTAGES, BigInteger.class, String.class, false);
    private static final IterableDictDB<String, BigInteger> fixedDistributionPercentages =
            new IterableDictDB<>(FIXED_DISTRIBUTION_PERCENTAGES, BigInteger.class, String.class, false);
    private static final Map<String, VarDB<Address>> platformRecipients = Map.of(WORKER_TOKENS, bwtAddress,
            RewardsConstants.RESERVE_FUND, reserveFund,
            DAOFUND, daofund);

    // deprecated
    private static final String RECIPIENT_SPLIT = "recipient_split";
    private static final String SNAPSHOT_RECIPIENT = "snapshot_recipient";
    private static final String COMPLETE_RECIPIENT = "complete_recipient";
    private static final String TOTAL_SNAPSHOTS = "total_snapshots";
    private static final String RECIPIENTS = "recipients";

    private static final DictDB<String, BigInteger> recipientSplit = Context.newDictDB(RECIPIENT_SPLIT,
            BigInteger.class);
    private static final DictDB<String, BigInteger> totalSnapshots = Context.newDictDB(TOTAL_SNAPSHOTS,
            BigInteger.class);
    private static final BranchDB<String, BranchDB<BigInteger, DictDB<String, BigInteger>>> snapshotRecipient =
            Context.newBranchDB(SNAPSHOT_RECIPIENT, BigInteger.class);
    private static final ArrayDB<String> completeRecipient = Context.newArrayDB(COMPLETE_RECIPIENT, String.class);
    private static final ArrayDB<String> recipients = Context.newArrayDB(RECIPIENTS, String.class);


    public RewardsImpl(@Optional Address _governance) {
        if (governance.get() == null) {
            // On "install" code
            isContract(_governance);
            governance.set(_governance);
            platformDay.set(BigInteger.ONE);
            distributionPercentages.set(WORKER_TOKENS, BigInteger.ZERO);
            distributionPercentages.set(RewardsConstants.RESERVE_FUND, BigInteger.ZERO);
            distributionPercentages.set(DAOFUND, BigInteger.ZERO);

            // deprecated setters
            recipientSplit.set(WORKER_TOKENS, BigInteger.ZERO);
            recipientSplit.set(RewardsConstants.RESERVE_FUND, BigInteger.ZERO);
            recipientSplit.set(DAOFUND, BigInteger.ZERO);

            recipients.add(WORKER_TOKENS);
            recipients.add(RewardsConstants.RESERVE_FUND);
            recipients.add(DAOFUND);
            completeRecipient.add(WORKER_TOKENS);
            completeRecipient.add(RewardsConstants.RESERVE_FUND);
            completeRecipient.add(DAOFUND);
        } else {
            Map<String, BigInteger> recipients = recipientAt(getDay());
            BigInteger bwtPercentage = recipients.get(WORKER_TOKENS);
            BigInteger reservePercentage = recipients.get(RewardsConstants.RESERVE_FUND);
            BigInteger daoFundPercentage = recipients.get(DAOFUND);
            distributionPercentages.set(WORKER_TOKENS, bwtPercentage);
            distributionPercentages.set(RewardsConstants.RESERVE_FUND, reservePercentage);
            distributionPercentages.set(DAOFUND, daoFundPercentage);

            //migrate dex -> stakedLP
            List<String> dataSources = getDataSourceNames();
            Address stakedLPAddress = Context.call(Address.class, governance.get(), "getContractAddress", "stakedLp");
            for (String source : dataSources) {
                if (source.equals("Loans") || source.equals("sICX/ICX")) {
                    continue;
                }

                DataSourceImpl dataSource = DataSourceDB.get(source);
                dataSource.setContractAddress(stakedLPAddress);
            }
        }

        boostWeight.set(WEIGHT);
    }

    @External(readonly = true)
    public String name() {
        return Names.REWARDS;
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
    public Map<String, BigInteger> getBalnHoldings(Address[] _holders) {
        Map<String, BigInteger> holdings = new HashMap<>();
        for (Address address : _holders) {
            holdings.put(address.toString(), balnHoldings.getOrDefault(address.toString(), BigInteger.ZERO));
        }

        return holdings;
    }

    @External(readonly = true)
    public BigInteger getBalnHolding(Address _holder) {
        BigInteger accruedRewards = balnHoldings.getOrDefault(_holder.toString(), BigInteger.ZERO);

        int dataSourcesCount = DataSourceDB.size();
        // TODO If we remove data source, user can't claim rewards for that data source
        for (int i = 0; i < dataSourcesCount; i++) {
            String name = DataSourceDB.names.get(i);
            DataSourceImpl dataSource = DataSourceDB.get(name);
            BigInteger currentTime = getTime();

            BigInteger sourceRewards = dataSource.updateSingleUserData(currentTime, dataSource.getWorkingSupply(true)
                    , _holder, dataSource.getWorkingBalance(_holder, true), true);

            accruedRewards = accruedRewards.add(sourceRewards);
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

    // Split to allow creation of mainnet like scenario
    // In the future use createDataSource
    @External
    public void addNewDataSource(String _name, Address _address) {
        only(governance);
        Context.require(!contains(recipients, _name), TAG + ": Recipient already exists");
        Context.require(_address.isContract(), TAG + " : Data source must be a contract.");

        recipients.add(_name);
        completeRecipient.add(_name);
        DataSourceDB.newSource(_name, _address);
    }

    @External
    public void addDataSource(String name, int sourceType, BigInteger weight) {
        only(governance);
        DataSourceImpl dataSource = DataSourceDB.get(name);
        Context.require(name.equals(dataSource.getName()), "There is no data source with the name " + name);
        SourceWeightController.addSource(name, sourceType, weight);
    }

    @External
    public void createDataSource(String _name, Address _address, int sourceType) {
        only(governance);
        Context.require(!contains(recipients, _name), TAG + ": Recipient already exists");
        Context.require(_address.isContract(), TAG + " : Data source must be a contract.");

        recipients.add(_name);
        completeRecipient.add(_name);
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
        int dataSourcesCount = DataSourceDB.size();
        for (int i = 0; i < dataSourcesCount; i++) {
            Map<String, Object> data = new HashMap<>();
            String name = DataSourceDB.names.get(i);
            data.put("votable", SourceWeightController.isVotable(name));
            data.put("type", SourceWeightController.getSourceType(name));
            data.put("weight", SourceWeightController.getRelativeWeight(name, timestamp));
            dataSources.put(name, data);
        }

        return dataSources;
    }

    @External(readonly = true)
    public Map<String, Object> getSourceData(String _name) {
        return DataSourceDB.get(_name).getData();
    }

    @External(readonly = true)
    public Map<String, BigInteger> getWorkingBalanceAndSupply(String _name, Address _user) {
        DataSourceImpl datasource = DataSourceDB.get(_name);
        return Map.of(
                "workingSupply", datasource.getWorkingSupply(true),
                "workingBalance", datasource.getWorkingBalance(_user, true)
        );
    }

    @External(readonly = true)
    public Map<String, Map<String, BigInteger>> getBoostData(Address user, @Optional String[] sources) {
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
        Context.call(balnAddress.get(), "mint", distribution, new byte[0]);

        BigInteger shares = HUNDRED_PERCENTAGE;
        BigInteger remaining = distribution;
        List<String> recipients = distributionPercentages.keys();
        for (String recipient : recipients) {
            BigInteger split = distributionPercentages.get(recipient);
            BigInteger share = split.multiply(remaining).divide(shares);
            Context.call(balnAddress.get(), "transfer", platformRecipients.get(recipient).get(), share, new byte[0]);
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
        Address user = Context.getCaller();
        BigInteger boostedBalance = fetchBoostedBalance(user);
        BigInteger boostedSupply = fetchBoostedSupply();
        updateAllUserRewards(user, sources, boostedBalance, boostedSupply);
    }

    @External
    public void claimRewards(@Optional String[] sources) {
        if (sources == null) {
            sources = getAllSources();
        }

        Address address = Context.getCaller();
        BigInteger boostedBalance = fetchBoostedBalance(address);
        BigInteger boostedSupply = fetchBoostedSupply();
        updateAllUserRewards(address, sources, boostedBalance, boostedSupply);

        BigInteger userClaimableRewards = balnHoldings.getOrDefault(address.toString(), BigInteger.ZERO);
        if (userClaimableRewards.compareTo(BigInteger.ZERO) > 0) {
            balnHoldings.set(address.toString(), null);
            Context.call(balnAddress.get(), "transfer", address, userClaimableRewards, new byte[0]);
            RewardsClaimed(address, userClaimableRewards);
        }
    }

    @External(readonly = true)
    public BigInteger getAPY(String _name) {
        DataSourceImpl dexDataSource = DataSourceDB.get("sICX/ICX");
        DataSourceImpl dataSource = DataSourceDB.get(_name);
        BigInteger emission = this.getEmission(BigInteger.valueOf(-1));

        BigInteger balnPrice = Context.call(BigInteger.class, dexDataSource.getContractAddress(), "getBalnPrice");
        BigInteger migrationDay = weightControllerMigrationDay.get();
        BigInteger percentage;

        if (migrationDay == null || migrationDay.compareTo(getDay()) > 0) {
            percentage = dataSource.getDistPercent();
        } else {
            BigInteger relativePercentage = SourceWeightController.getRelativeWeight(_name,
                    BigInteger.valueOf(Context.getBlockTimestamp()));
            percentage = relativePercentage.multiply(getVotableDist()).divide(HUNDRED_PERCENTAGE);
        }

        BigInteger sourceValue = dataSource.getValue();
        BigInteger year = BigInteger.valueOf(365);

        BigInteger sourceAmount = year.multiply(emission).multiply(percentage);

        return sourceAmount.multiply(balnPrice).divide(sourceValue.multiply(HUNDRED_PERCENTAGE));
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        Context.require(Context.getCaller().equals(balnAddress.get()),
                TAG + ": The Rewards SCORE can only accept BALN tokens");
    }

    @External
    public void addDataProvider(Address _source) {
        onlyOwner();
        dataProviders.add(_source);
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

    @External
    public void updateRewardsData(String _name, BigInteger _totalSupply, Address _user, BigInteger _balance) {
        Context.require(dataProviders.contains(Context.getCaller()), TAG + ": Only data provider are allowed to " +
                "update rewards data");

        BigInteger currentTime = getTime();
        distribute();
        DataSourceImpl dataSource = DataSourceDB.get(_name);
        BigInteger boostedBalance = fetchBoostedBalance(_user);
        BigInteger boostedSupply = fetchBoostedSupply();
        BigInteger workingBalance = dataSource.getWorkingBalance(_user, _balance, false);
        BigInteger workingSupply = dataSource.getWorkingSupply(_totalSupply, false);
        updateUserAccruedRewards(_name, currentTime, workingSupply, dataSource, _user, workingBalance, boostedBalance
                , boostedSupply);
    }

    @External
    public void updateBatchRewardsData(String _name, BigInteger _totalSupply, RewardsDataEntry[] _data) {
        Context.require(dataProviders.contains(Context.getCaller()), TAG + ": Only data provider are allowed to " +
                "update rewards data");

        BigInteger currentTime = getTime();
        distribute();

        DataSourceImpl dataSource = DataSourceDB.get(_name);
        BigInteger boostedSupply = fetchBoostedSupply();

        for (RewardsDataEntry entry : _data) {
            Address user = entry._user;
            BigInteger boostedBalance = fetchBoostedBalance(user);
            BigInteger previousBalance = entry._balance;
            BigInteger workingBalance = dataSource.getWorkingBalance(user, previousBalance, false);
            BigInteger workingSupply = dataSource.getWorkingSupply(_totalSupply, false);
            updateUserAccruedRewards(_name, currentTime, workingSupply, dataSource, user, workingBalance,
                    boostedBalance, boostedSupply);
        }
    }

    @External
    public void onKick(Address user) {
        only(boostedBaln);
        BigInteger boostedSupply = fetchBoostedSupply();
        updateAllUserRewards(user, getAllSources(), BigInteger.ZERO, boostedSupply);
    }

    @External
    public void kick(Address user, String[] sources) {
        BigInteger boostedBalance = fetchBoostedBalance(user);
        BigInteger boostedSupply = fetchBoostedSupply();
        updateAllUserRewards(user, sources, boostedBalance, boostedSupply);
    }

    @External
    public void onBalanceUpdate(Address user, BigInteger balance) {
        only(boostedBaln);
        BigInteger boostedSupply = fetchBoostedSupply();
        updateAllUserRewards(user, getAllSources(), balance, boostedSupply);
    }

    @External
    public void setBoostWeight(BigInteger weight) {
        only(admin);
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
    public String[] getUserSources(Address user) {
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
        only(governance);
        SourceWeightController.setVotable(name, votable);
    }

    @External
    public void addType(String name) {
        only(governance);
        SourceWeightController.addType(name, BigInteger.ZERO);
    }

    @External
    public void changeTypeWeight(int typeId, BigInteger weight) {
        only(governance);
        SourceWeightController.changeTypeWeight(typeId, weight);
    }

    @External
    public void setMigrateToVotingDay(BigInteger day) {
        onlyOwner();
        Context.require(day.compareTo(platformDay.get()) > 0, "day has to be greater than platformDay");
        if (weightControllerMigrationDay.get() == null) {
            migrateWeightController();
        }

        weightControllerMigrationDay.set(day);
    }

    @External(readonly = true)
    public BigInteger getMigrateToVotingDay() {
        return weightControllerMigrationDay.get();
    }

    @External
    public void setPlatformDistPercentage(String name, BigInteger percentage) {
        only(governance);
        Context.require(platformRecipients.containsKey(name), name + " is not a platform recipient");
        distributionPercentages.set(name, percentage);
        verifyPercentages();
    }

    @External
    public void setFixedSourcePercentage(String name, BigInteger percentage) {
        only(governance);
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
        SourceWeightController.checkpoint();
    }

    @External
    public void checkpointSource(String name) {
        SourceWeightController.checkpointSource(name);
    }

    @External
    public BigInteger updateRelativeSourceWeight(String name, BigInteger time) {
        return SourceWeightController.updateRelativeWeight(name, time);
    }

    @External(readonly = true)
    public BigInteger getRelativeSourceWeight(String name, @Optional BigInteger time) {
        return SourceWeightController.getRelativeWeight(name, time);
    }

    @External
    public void voteForSource(String name, BigInteger userWeight) {
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
        return SourceWeightController.getUserVoteData(user);
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

    private String[] getAllSources() {
        int dataSourcesCount = DataSourceDB.size();
        String[] sources = new String[dataSourcesCount];
        for (int i = 0; i < dataSourcesCount; i++) {
            String name = DataSourceDB.names.get(i);
            sources[i] = name;
        }

        return sources;
    }

    private void updateAllUserRewards(Address user, String[] sources, BigInteger boostedBalance,
                                      BigInteger boostedSupply) {
        distribute();
        BigInteger currentTime = getTime();
        for (String name : sources) {
            DataSourceImpl dataSource = DataSourceDB.get(name);
            BigInteger workingBalance = dataSource.getWorkingBalance(user, false);
            if (workingBalance.equals(BigInteger.ZERO)) {
                continue;
            }

            BigInteger workingSupply = dataSource.getWorkingSupply(false);
            updateUserAccruedRewards(name, currentTime, workingSupply, dataSource, user, workingBalance,
                    boostedBalance, boostedSupply);
        }
    }

    private void updateUserAccruedRewards(String _name, BigInteger currentTime, BigInteger prevWorkingSupply,
                                          DataSourceImpl dataSource, Address user, BigInteger prevWorkingBalance,
                                          BigInteger boostedBalance, BigInteger boostedSupply) {

        BigInteger accruedRewards = dataSource.updateSingleUserData(currentTime, prevWorkingSupply, user,
                prevWorkingBalance, false);
        dataSource.updateWorkingBalanceAndSupply(user, boostedBalance, boostedSupply);

        if (accruedRewards.compareTo(BigInteger.ZERO) > 0) {
            BigInteger newHoldings =
                    balnHoldings.getOrDefault(user.toString(), BigInteger.ZERO).add(accruedRewards);
            balnHoldings.set(user.toString(), newHoldings);
            RewardsAccrued(user, _name, accruedRewards);
        }
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
    public void setBaln(Address _address) {
        only(admin);
        isContract(_address);
        balnAddress.set(_address);
    }

    @External(readonly = true)
    public Address getBaln() {
        return balnAddress.get();
    }

    @External
    public void setBoostedBaln(Address _address) {
        only(admin);
        isContract(_address);
        boostedBaln.set(_address);
    }

    @External(readonly = true)
    public Address getBoostedBaln() {
        return boostedBaln.get();
    }

    @External
    public void setBwt(Address _address) {
        only(admin);
        isContract(_address);
        bwtAddress.set(_address);
    }

    @External(readonly = true)
    public Address getBwt() {
        return bwtAddress.get();
    }

    @External
    public void setReserve(Address _address) {
        only(admin);
        isContract(_address);
        reserveFund.set(_address);
    }

    @External(readonly = true)
    public Address getReserve() {
        return reserveFund.get();
    }

    @External
    public void setDaofund(Address _address) {
        only(admin);
        isContract(_address);
        daofund.set(_address);
    }

    @External(readonly = true)
    public Address getDaofund() {
        return daofund.get();
    }

    @External
    public void setTimeOffset(BigInteger _timestamp) {
        only(admin);
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
        BigInteger migrationDay = weightControllerMigrationDay.get();
        if (migrationDay == null || migrationDay.compareTo(day) > 0) {
            BigInteger dist = dailyDistribution(day);
            Map<String, BigInteger> recipientDistribution = _recipientAt(day);

            BigInteger split = recipientDistribution.get(sourceName);
            if (split == null) {
                return BigInteger.ZERO;
            }

            return split.multiply(dist).divide(HUNDRED_PERCENTAGE);
        }

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

    public static EventLogger getEventLogger() {
        return new EventLogger();
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

    private void migrateWeightController() {
        String coreTypeName = "BALN Core";
        String communityTypeName = "Community";
        SourceWeightController.addType(coreTypeName, EXA);
        SourceWeightController.addType(communityTypeName, EXA);
        int coreType = SourceWeightController.getTypeId(coreTypeName);
        int communityType = SourceWeightController.getTypeId(communityTypeName);
        Map<String, BigInteger> recipients = recipientAt(getDay());

        // core
        if (DataSourceDB.hasSource("Loans")) {
            SourceWeightController.addSource("Loans", coreType, BigInteger.ZERO);
            SourceWeightController.setVotable("Loans", false);
            fixedDistributionPercentages.set("Loans", recipients.get("Loans"));
        }
        if (DataSourceDB.hasSource("sICX/bnUSD")) {
            SourceWeightController.addSource("sICX/bnUSD", coreType, BigInteger.ZERO);
        }
        if (DataSourceDB.hasSource("BALN/bnUSD")) {
            SourceWeightController.addSource("BALN/bnUSD", coreType, BigInteger.ZERO);
        }
        if (DataSourceDB.hasSource("BALN/sICX")) {
            SourceWeightController.addSource("BALN/sICX", coreType, BigInteger.ZERO);
        }

        // Community
        if (DataSourceDB.hasSource("sICX/ICX")) {
            SourceWeightController.addSource("sICX/ICX", communityType, BigInteger.ZERO);
        }
        if (DataSourceDB.hasSource("IUSDC/bnUSD")) {
            SourceWeightController.addSource("IUSDC/bnUSD", communityType, BigInteger.ZERO);
        }
        if (DataSourceDB.hasSource("USDS/bnUSD")) {
            SourceWeightController.addSource("USDS/bnUSD", communityType, BigInteger.ZERO);
        }
        if (DataSourceDB.hasSource("IUSDT/bnUSD")) {
            SourceWeightController.addSource("IUSDT/bnUSD", communityType, BigInteger.ZERO);
        }
    }

    private BigInteger fetchBoostedBalance(Address user) {
        try {
            return (BigInteger) RewardsImpl.call(boostedBaln.get(), "balanceOf", user, BigInteger.ZERO);
        } catch (Exception e) {
            return BigInteger.ZERO;
        }
    }

    private BigInteger fetchBoostedSupply() {
        try {
            return (BigInteger) RewardsImpl.call(boostedBaln.get(), "totalSupply", BigInteger.ZERO);
        } catch (Exception e) {
            return BigInteger.ZERO;
        }
    }

    @EventLog(indexed = 1)
    public void RewardsClaimed(Address _address, BigInteger _amount) {
    }

    @EventLog(indexed = 2)
    public void Report(BigInteger _day, String _name, BigInteger _dist, BigInteger _value) {
    }

    @EventLog(indexed = 2)
    public void RewardsAccrued(Address _user, String _source, BigInteger _value) {
    }

    // deprecated functions
    @External(readonly = true)
    public List<String> getRecipients() {
        List<String> names = new ArrayList<>();
        int recipientsCount = recipients.size();
        for (int i = 0; i < recipientsCount; i++) {
            names.add(recipients.get(i));
        }
        return names;
    }

    @External(readonly = true)
    public Map<String, BigInteger> getRecipientsSplit() {
        return recipientAt(getDay());
    }

    @External(readonly = true)
    public Map<String, Object> distStatus() {
        Map<String, BigInteger> sourceDays = new HashMap<>();
        int dataSourcesCount = DataSourceDB.size();
        for (int i = 0; i < dataSourcesCount; i++) {
            String name = DataSourceDB.names.get(i);
            DataSourceImpl dataSource = DataSourceDB.get(name);

            sourceDays.put(name, dataSource.getDay());
        }

        return Map.of(
                "platform_day", platformDay.get(),
                "source_days", sourceDays
        );
    }


    @External(readonly = true)
    public Map<String, BigInteger> recipientAt(BigInteger _day) {
        return _recipientAt(_day);
    }

    public static Map<String, BigInteger> _recipientAt(BigInteger _day) {
        Context.require(_day.compareTo(BigInteger.ZERO) >= 0, TAG + ": day:" + _day + " must be equal to or greater " +
                "then Zero");

        Map<String, BigInteger> distributions = new HashMap<>();

        int completeRecipientCount = completeRecipient.size();
        for (int i = 0; i < completeRecipientCount; i++) {
            String recipient = completeRecipient.get(i);
            BigInteger totalSnapshotsTaken = totalSnapshots.getOrDefault(recipient, BigInteger.ZERO);
            BranchDB<BigInteger, DictDB<String, BigInteger>> snapshot = snapshotRecipient.at(recipient);
            if (totalSnapshotsTaken.equals(BigInteger.ZERO)) {
                distributions.put(recipient, recipientSplit.getOrDefault(recipient, BigInteger.ZERO));
                continue;
            } else if (snapshot.at(totalSnapshotsTaken.subtract(BigInteger.ONE)).getOrDefault(IDS, BigInteger.ZERO).compareTo(_day) <= 0) {
                distributions.put(recipient,
                        snapshot.at(totalSnapshotsTaken.subtract(BigInteger.ONE)).getOrDefault(AMOUNT,
                                BigInteger.ZERO));
                continue;
            } else if (snapshot.at(BigInteger.ZERO).getOrDefault(IDS, BigInteger.ZERO).compareTo(_day) > 0) {
                distributions.put(recipient, recipientSplit.getOrDefault(recipient, BigInteger.ZERO));
                continue;
            }

            BigInteger low = BigInteger.ZERO;
            BigInteger high = totalSnapshotsTaken.subtract(BigInteger.ONE);
            while (high.compareTo(low) > 0) {
                BigInteger mid = high.subtract(high.subtract(low).divide(BigInteger.TWO));
                DictDB<String, BigInteger> midValue = snapshot.at(mid);
                BigInteger index = midValue.getOrDefault(IDS, BigInteger.ZERO);
                if (index.equals(_day)) {
                    low = mid;
                    break;
                } else if (index.compareTo(_day) < 0) {
                    low = mid;
                } else {
                    high = mid.subtract(BigInteger.ONE);
                }
            }

            distributions.put(recipient, snapshot.at(low).getOrDefault(AMOUNT, BigInteger.ZERO));
        }

        Iterator<Map.Entry<String, BigInteger>> it;
        for (it = distributions.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, BigInteger> entry = it.next();
            if (entry.getValue().equals(BigInteger.ZERO)) {
                it.remove();
            }
        }

        return distributions;
    }

    /**
     * This method provides a means to adjust the allocation of rewards tokens.
     * To maintain consistency a change to these percentages will only be
     * accepted if they sum to 100%, with 100% represented by the value 10**18.
     * This method must only be called when rewards are fully up-to-date.
     * param _recipient_list: List of json containing the allocation spec.
     * type _recipient_list: List[TypedDict]
     **/
    @External
    public void updateBalTokenDistPercentage(DistributionPercentage[] _recipient_list) {
        only(governance);
        Context.require(_recipient_list.length == recipients.size(), TAG + ": Recipient lists lengths mismatched!");
        BigInteger totalPercentage = BigInteger.ZERO;
        BigInteger day = getDay();

        for (DistributionPercentage recipient : _recipient_list) {
            String name = recipient.recipient_name;

            Context.require(contains(recipients, name), TAG + ": Recipient " + name + " does not exist.");

            BigInteger percentage = recipient.dist_percent;
            updateRecipientSnapshot(name, percentage);
            DataSourceImpl dataSource = DataSourceDB.get(name);
            BigInteger dataSourceDay = dataSource.getDay();
            if (dataSource.getTotalDist(dataSourceDay).equals(BigInteger.ZERO)) {
                dataSource.setDay(day);
            }

            if (distributionPercentages.get(name) != null) {
                distributionPercentages.set(name, percentage);
            }

            dataSource.setDistPercent(percentage);
            totalPercentage = totalPercentage.add(percentage);
        }

        Context.require(totalPercentage.equals(HUNDRED_PERCENTAGE), TAG + ": Total percentage does not sum up to 100.");
    }

    @External
    public void removeDataSource(String _name) {
        only(governance);
        if (!contains(recipients, _name)) {
            return;
        }

        BigInteger day = getDay();
        Map<String, BigInteger> recipientDistribution = recipientAt(day);
        Context.require(!recipientDistribution.containsKey(_name), TAG + ": Data source rewards percentage must be " +
                "set to 0 before removing.");
        // TODO this doesn't allow user to claim rewards from previous data source
        String topRecipient = recipients.pop();
        if (!topRecipient.equals(_name)) {
            for (int i = 0; i < recipients.size(); i++) {
                if (recipients.get(i).equals(_name)) {
                    recipients.set(i, topRecipient);
                }
            }
        }

        DataSourceDB.removeSource(_name);
    }

    private void updateRecipientSnapshot(String recipient, BigInteger percentage) {
        BigInteger currentDay = getDay();
        BigInteger totalSnapshotsTaken = totalSnapshots.getOrDefault(recipient, BigInteger.ZERO);
        BranchDB<BigInteger, DictDB<String, BigInteger>> snapshot = snapshotRecipient.at(recipient);

        BigInteger recipientDay = snapshot.at(totalSnapshotsTaken.subtract(BigInteger.ONE)).getOrDefault(IDS,
                BigInteger.ZERO);

        if (totalSnapshotsTaken.compareTo(BigInteger.ZERO) > 0 && recipientDay.equals(currentDay)) {
            snapshot.at(totalSnapshotsTaken.subtract(BigInteger.ONE)).set(AMOUNT, percentage);
        } else {
            snapshot.at(totalSnapshotsTaken).set(IDS, currentDay);
            snapshot.at(totalSnapshotsTaken).set(AMOUNT, percentage);
            totalSnapshots.set(recipient, totalSnapshotsTaken.add(BigInteger.ONE));
        }
    }
}
