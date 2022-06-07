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

import network.balanced.score.core.rewards.utils.RewardsConstants;
import network.balanced.score.lib.interfaces.DataSourceScoreInterface;
import network.balanced.score.lib.interfaces.Rewards;
import network.balanced.score.lib.interfaces.tokens.IRC2MintableScoreInterface;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.structs.RewardsDataEntry;
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

import static network.balanced.score.core.rewards.utils.Check.continuousRewardsActive;
import static network.balanced.score.core.rewards.utils.RewardsConstants.*;
import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.U_SECONDS_DAY;
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
    private static final String BWT_ADDRESS = "bwt_address";
    private static final String RESERVE_FUND = "reserve_fund";
    private static final String DAO_FUND = "dao_fund";
    private static final String STAKED_LP = "stakedLp";
    private static final String START_TIMESTAMP = "start_timestamp";
    private static final String BATCH_SIZE = "batch_size";
    private static final String BALN_HOLDINGS = "baln_holdings";
    private static final String RECIPIENT_SPLIT = "recipient_split";
    private static final String SNAPSHOT_RECIPIENT = "snapshot_recipient";
    private static final String COMPLETE_RECIPIENT = "complete_recipient";
    private static final String TOTAL_SNAPSHOTS = "total_snapshots";
    private static final String RECIPIENTS = "recipients";
    private static final String TOTAL_DIST = "total_dist";
    private static final String PLATFORM_DAY = "platform_day";
    private static final String CONTINUOUS_REWARDS_DAY = "continuous_rewards_day";
    private static final String DATA_PROVIDERS = "data_providers";
    private static final String NON_CONTINUOUS_REWARDS_DAY_COUNT = "non_continuous_rewards_day_count";

    private static final VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);
    private static final VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);
    private static final VarDB<Address> balnAddress = Context.newVarDB(BALN_ADDRESS, Address.class);
    private static final VarDB<Address> bwtAddress = Context.newVarDB(BWT_ADDRESS, Address.class);
    private static final VarDB<Address> reserveFund = Context.newVarDB(RESERVE_FUND, Address.class);
    private static final VarDB<Address> daofund = Context.newVarDB(DAO_FUND, Address.class);
    private static final VarDB<Address> stakedLp = Context.newVarDB(STAKED_LP, Address.class);
    private static final VarDB<BigInteger> startTimestamp = Context.newVarDB(START_TIMESTAMP, BigInteger.class);
    private static final VarDB<Integer> batchSize = Context.newVarDB(BATCH_SIZE, Integer.class);
    static final DictDB<String, BigInteger> balnHoldings = Context.newDictDB(BALN_HOLDINGS, BigInteger.class);
    private static final DictDB<String, BigInteger> recipientSplit = Context.newDictDB(RECIPIENT_SPLIT,
            BigInteger.class);
    private static final BranchDB<String, BranchDB<BigInteger, DictDB<String, BigInteger>>> snapshotRecipient =
            Context.newBranchDB(SNAPSHOT_RECIPIENT, BigInteger.class);
    private static final ArrayDB<String> completeRecipient = Context.newArrayDB(COMPLETE_RECIPIENT, String.class);
    private static final DictDB<String, BigInteger> totalSnapshots = Context.newDictDB(TOTAL_SNAPSHOTS,
            BigInteger.class);
    private static final ArrayDB<String> recipients = Context.newArrayDB(RECIPIENTS, String.class);
    public static final VarDB<BigInteger> totalDist = Context.newVarDB(TOTAL_DIST, BigInteger.class);
    private static final VarDB<BigInteger> platformDay = Context.newVarDB(PLATFORM_DAY, BigInteger.class);
    public static final VarDB<BigInteger> continuousRewardsDay = Context.newVarDB(CONTINUOUS_REWARDS_DAY,
            BigInteger.class);
    private final static SetDB<Address> dataProviders = new SetDB<>(DATA_PROVIDERS, Address.class, null);
    private static final VarDB<BigInteger> nonContinuousRewardsDayCount =
            Context.newVarDB(NON_CONTINUOUS_REWARDS_DAY_COUNT, BigInteger.class);

    private static final Map<String, VarDB<Address>> platformRecipients = Map.of(WORKER_TOKENS, bwtAddress,
            RewardsConstants.RESERVE_FUND, reserveFund,
            DAOFUND, daofund);

    public RewardsImpl(@Optional Address _governance) {
        if (governance.get() == null) {
            // On "install" code
            isContract(_governance);
            governance.set(_governance);
            platformDay.set(BigInteger.ONE);
            batchSize.set(DEFAULT_BATCH_SIZE);
            recipientSplit.set(WORKER_TOKENS, BigInteger.ZERO);
            recipientSplit.set(RewardsConstants.RESERVE_FUND, BigInteger.ZERO);
            recipientSplit.set(DAOFUND, BigInteger.ZERO);
            recipients.add(WORKER_TOKENS);
            recipients.add(RewardsConstants.RESERVE_FUND);
            recipients.add(DAOFUND);
            completeRecipient.add(WORKER_TOKENS);
            completeRecipient.add(RewardsConstants.RESERVE_FUND);
            completeRecipient.add(DAOFUND);
        }
    }

    @External(readonly = true)
    public String name() {
        return "Balanced Rewards";
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
    public Map<Address, BigInteger> getBalnHoldings(Address[] _holders) {
        Map<Address, BigInteger> holdings = new HashMap<>();
        for(Address address : _holders) {
            holdings.put(address, balnHoldings.getOrDefault(address.toString(), BigInteger.ZERO));
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

            Map<String, BigInteger> data = dataSource.loadCurrentSupply(_holder);

            BigInteger currentTime = getTime();
            BigInteger sourceRewards = dataSource.updateSingleUserData(currentTime, data.get(TOTAL_SUPPLY), _holder
                    , data.get(BALANCE), true);
            accruedRewards = accruedRewards.add(sourceRewards);
        }

        return accruedRewards;
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

            dataSource.setDistPercent(percentage);
            totalPercentage = totalPercentage.add(percentage);
        }

        Context.require(totalPercentage.equals(HUNDRED_PERCENTAGE), TAG + ": Total percentage does not sum up to 100.");
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
    public Map<String, Object> getSourceData(String _name) {
        return DataSourceDB.get(_name).getData();
    }

    /**
     * This method should be called once after the balanced has been launched, so that if there are baln tokens to
     * mint it will be minted. In continuous rewards it will be minted while in non-continuous it will not  be minted
     */
    @External
    public boolean distribute() {
        BigInteger platformDay = RewardsImpl.platformDay.get();
        BigInteger day = getDay();
        boolean continuousRewardsIsActive = continuousRewardsActive();

        boolean distributionRequired =
                platformDay.compareTo(day) < 0 || (platformDay.equals(day) && continuousRewardsIsActive);

        BigInteger continuousRewardsDay = getContinuousRewardsDay();
        if (platformDay.compareTo(day) < 0 && (continuousRewardsDay == null || platformDay.compareTo(continuousRewardsDay) <= 0)) {
            BigInteger previousCount = nonContinuousRewardsDayCount.getOrDefault(BigInteger.ZERO);
            nonContinuousRewardsDayCount.set(previousCount.add(BigInteger.ONE));
        }

        if (distributionRequired) {
            return mintAndAllocateBalnReward(platformDay);
        }

        BigInteger nonContinuousDistributionCount = nonContinuousRewardsDayCount.getOrDefault(BigInteger.ZERO);
        if (nonContinuousDistributionCount.compareTo(BigInteger.ZERO) > 0) {
            for (int i = 0; i < DataSourceDB.size(); i++) {
                String name = DataSourceDB.names.get(i);
                DataSourceImpl dataSource = DataSourceDB.get(name);
                BigInteger sourceDay = dataSource.getDay();

                if (sourceDay.compareTo(day) < 0) {
                    dataSource.distribute(batchSize.get());
                    BigInteger remaining = dataSource.getTotalDist(sourceDay);
                    BigInteger shares = dataSource.getTotalValue(sourceDay);
                    Report(day, name, remaining, shares);
                    return false;
                }
            }
            nonContinuousRewardsDayCount.set(nonContinuousDistributionCount.subtract(BigInteger.ONE));
        }
        return true;
    }

    private boolean mintAndAllocateBalnReward(BigInteger platformDay) {
        BigInteger distribution = dailyDistribution(platformDay);

        IRC2MintableScoreInterface baln = new IRC2MintableScoreInterface(balnAddress.get());
        baln.mint(distribution, new byte[0]);

        BigInteger shares = HUNDRED_PERCENTAGE;
        BigInteger remaining = distribution;
        Map<String, BigInteger> recipientDistribution = recipientAt(platformDay);

        for (String name : recipientDistribution.keySet()) {
            BigInteger split = recipientDistribution.get(name);
            BigInteger share = split.multiply(remaining).divide(shares);

            if (contains(DataSourceDB.names, name)) {
                DataSourceDB.get(name).setTotalDist(platformDay, share);
            } else {
                baln.transfer(platformRecipients.get(name).get(), share, new byte[0]);
            }

            remaining = remaining.subtract(share);
            shares = shares.subtract(split);

            if (shares.equals(BigInteger.ZERO) || remaining.equals(BigInteger.ZERO)) {
                break;
            }
        }

        RewardsImpl.platformDay.set(platformDay.add(BigInteger.ONE));
        return false;
    }

    @External(readonly = true)
    public Map<String, BigInteger> recipientAt(BigInteger _day) {
        Context.require(_day.compareTo(BigInteger.ZERO) >= 0, TAG + ": day:" + _day + " must be equal to or greater " +
                "then Zero");

        Map<String, BigInteger> distributions = new HashMap<>();

        int completeRecipientCount = completeRecipient.size();
        for (int i = 0; i < completeRecipientCount; i++) {
            String recipient = completeRecipient.get(i);
            BigInteger totalSnapshotsTaken = totalSnapshots.getOrDefault(recipient, BigInteger.ZERO);
            BranchDB<BigInteger, DictDB<String, BigInteger>> snapshot = snapshotRecipient.at(recipient);
            if (totalSnapshotsTaken.equals(BigInteger.ZERO)) {
                distributions.put(recipient, recipientSplit.get(recipient));
                continue;
            } else if (snapshot.at(totalSnapshotsTaken.subtract(BigInteger.ONE)).getOrDefault(IDS, BigInteger.ZERO).compareTo(_day) <= 0) {
                distributions.put(recipient, snapshot.at(totalSnapshotsTaken.subtract(BigInteger.ONE)).get(AMOUNT));
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

            distributions.put(recipient, snapshot.at(low).get(AMOUNT));
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

    @External
    public void claimRewards() {
        Address address = Context.getCaller();
        BigInteger currentTime;
        distribute();

        int dataSourcesCount = DataSourceDB.size();
        for (int i = 0; i < dataSourcesCount; i++) {
            String name = DataSourceDB.names.get(i);
            DataSourceImpl dataSource = DataSourceDB.get(name);
            Map<String, BigInteger> data = dataSource.loadCurrentSupply(address);

            BigInteger totalSupply = data.get(TOTAL_SUPPLY);
            BigInteger balance = data.get(BALANCE);

            currentTime = getTime();
            updateUserAccruedRewards(name, totalSupply, currentTime, dataSource, address, balance);
        }

        BigInteger userClaimableRewards = balnHoldings.getOrDefault(address.toString(), BigInteger.ZERO);
        if (userClaimableRewards.compareTo(BigInteger.ZERO) > 0) {
            balnHoldings.set(address.toString(), null);
            IRC2MintableScoreInterface baln = new IRC2MintableScoreInterface(balnAddress.get());
            baln.transfer(address, userClaimableRewards, new byte[0]);
            RewardsClaimed(address, userClaimableRewards);
        }
    }

    @External(readonly = true)
    public BigInteger getAPY(String _name) {
        DataSourceImpl dexDataSource = DataSourceDB.get("sICX/ICX");
        DataSourceImpl dataSource = DataSourceDB.get(_name);
        BigInteger emission = this.getEmission(BigInteger.valueOf(-1));

        DataSourceScoreInterface dex = new DataSourceScoreInterface(dexDataSource.getContractAddress());

        BigInteger balnPrice = dex.getBalnPrice();
        BigInteger percentage = dataSource.getDistPercent();
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
        Context.require(dataProviders.contains(Context.getCaller()), TAG + ": Only data sources are allowed to update" +
                " rewards data");

        BigInteger currentTime = getTime();
        distribute();
        DataSourceImpl dataSource = DataSourceDB.get(_name);
        updateUserAccruedRewards(_name, _totalSupply, currentTime, dataSource, _user, _balance);
    }

    @External
    public void updateBatchRewardsData(String _name, BigInteger _totalSupply, RewardsDataEntry[] _data) {
        Context.require(dataProviders.contains(Context.getCaller()), TAG + ": Only data sources are allowed to update" +
                " rewards data");

        BigInteger currentTime = getTime();
        distribute();

        DataSourceImpl dataSource = DataSourceDB.get(_name);
        for (RewardsDataEntry entry : _data) {
            Address user = entry._user;
            BigInteger previousBalance = entry._balance;
            updateUserAccruedRewards(_name, _totalSupply, currentTime, dataSource, user, previousBalance);
        }
    }

    private void updateUserAccruedRewards(String _name, BigInteger _totalSupply, BigInteger currentTime,
                                          DataSourceImpl dataSource, Address user, BigInteger previousBalance) {
        BigInteger accruedRewards = dataSource.updateSingleUserData(currentTime, _totalSupply, user,
                previousBalance, false);

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
    public void setStakedLp(Address _address) {
        only(admin);
        isContract(_address);
        stakedLp.set(_address);
    }

    @External(readonly = true)
    public Address getStakedLp() {
        return stakedLp.get();
    }

    @External
    public void setBatchSize(int _batch_size) {
        only(admin);
        batchSize.set(_batch_size);
    }

    @External(readonly = true)
    public int getBatchSize() {
        return batchSize.get();
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

    @External
    public void setContinuousRewardsDay(BigInteger _continuous_rewards_day) {
        only(admin);
        continuousRewardsDay.set(_continuous_rewards_day);
    }

    @External(readonly = true)
    public BigInteger getContinuousRewardsDay() {
        return continuousRewardsDay.get();
    }

    public static BigInteger getDay() {
        BigInteger blockTime = BigInteger.valueOf(Context.getBlockTimestamp()).subtract(startTimestamp.get());
        return blockTime.divide(U_SECONDS_DAY);
    }

    private static BigInteger getTime() {
        return BigInteger.valueOf(Context.getBlockTimestamp()).subtract(startTimestamp.get());
    }

    static Object call(Address targetAddress, String method, Object... params) {
        return Context.call(targetAddress, method, params);
    }

    private BigInteger dailyDistribution(BigInteger day) {
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
            Integer index = day.subtract(BigInteger.valueOf(60)).intValue();
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

    @EventLog(indexed=1)
    public void RewardsClaimed(Address _address, BigInteger _amount){}

    @EventLog(indexed=2)
    public void Report(BigInteger _day, String _name, BigInteger _dist, BigInteger _value) {}

    @EventLog(indexed=2)
    public void  RewardsAccrued(Address _user, String _source, BigInteger _value) {}
}
