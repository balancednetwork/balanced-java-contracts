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
import network.balanced.score.lib.interfaces.BoostedBaln;
import network.balanced.score.lib.interfaces.DataSourceScoreInterface;
import network.balanced.score.lib.interfaces.Rewards;
import network.balanced.score.lib.interfaces.StakedLP;
import network.balanced.score.lib.interfaces.addresses.LoansAddress;
import network.balanced.score.lib.interfaces.tokens.IRC2MintableScoreInterface;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.structs.RewardsDataEntry;
import network.balanced.score.lib.utils.EnumerableSetDB;
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
    private static final String RECIPIENT_SPLIT = "recipient_split";
    private static final String SNAPSHOT_RECIPIENT = "snapshot_recipient";
    private static final String COMPLETE_RECIPIENT = "complete_recipient";
    private static final String TOTAL_SNAPSHOTS = "total_snapshots";
    private static final String RECIPIENTS = "recipients";
    private static final String TOTAL_DIST = "total_dist";
    private static final String PLATFORM_DAY = "platform_day";
    private static final String LAST_UPDATE_DAY = "last_update_day";
    private static final String DATA_PROVIDERS = "data_providers";
    private static final String BOOSTS = "user_boosts_map";
    private static final String BOOST_WEIGHT = "user_boosts_map";
    private static final String USER_SOURCES = "user_sources";

    private static final VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);
    private static final VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);
    private static final VarDB<Address> balnAddress = Context.newVarDB(BALN_ADDRESS, Address.class);
    public static final VarDB<Address> boostedBaln = Context.newVarDB(BOOSTED_BALN_ADDRESS, Address.class);
    private static final VarDB<Address> bwtAddress = Context.newVarDB(BWT_ADDRESS, Address.class);
    private static final VarDB<Address> reserveFund = Context.newVarDB(RESERVE_FUND, Address.class);
    private static final VarDB<Address> daofund = Context.newVarDB(DAO_FUND, Address.class);
    private static final VarDB<BigInteger> startTimestamp = Context.newVarDB(START_TIMESTAMP, BigInteger.class);
    static final DictDB<String, BigInteger> balnHoldings = Context.newDictDB(BALN_HOLDINGS, BigInteger.class);
    private static final DictDB<String, BigInteger> recipientSplit = Context.newDictDB(RECIPIENT_SPLIT,
            BigInteger.class);
    private static final DictDB<String, BigInteger> internalDistributions = Context.newDictDB("tmp",
            BigInteger.class);
    private static final BranchDB<String, BranchDB<BigInteger, DictDB<String, BigInteger>>> snapshotRecipient =
            Context.newBranchDB(SNAPSHOT_RECIPIENT, BigInteger.class);
    private static final ArrayDB<String> completeRecipient = Context.newArrayDB(COMPLETE_RECIPIENT, String.class);
    private static final DictDB<String, BigInteger> totalSnapshots = Context.newDictDB(TOTAL_SNAPSHOTS,
            BigInteger.class);
    private static final ArrayDB<String> recipients = Context.newArrayDB(RECIPIENTS, String.class);
    public static final VarDB<BigInteger> totalDist = Context.newVarDB(TOTAL_DIST, BigInteger.class);
    private static final VarDB<BigInteger> platformDay = Context.newVarDB(PLATFORM_DAY, BigInteger.class);
    private static final VarDB<BigInteger> lastUpdateDay = Context.newVarDB(LAST_UPDATE_DAY, BigInteger.class);
    private final static SetDB<Address> dataProviders = new SetDB<>(DATA_PROVIDERS, Address.class, null);
    private static final DictDB<Address, String> boosts = Context.newDictDB(BOOSTS, String.class);
    public static final VarDB<BigInteger> boostWeight = Context.newVarDB(BOOST_WEIGHT, BigInteger.class);


    public RewardsImpl(@Optional Address _governance) {
        if (governance.get() == null) {
            // On "install" code
            isContract(_governance);
            governance.set(_governance);
            platformDay.set(BigInteger.ONE);
            internalDistributions.set(WORKER_TOKENS, BigInteger.ZERO);
            internalDistributions.set(RewardsConstants.RESERVE_FUND, BigInteger.ZERO);
            internalDistributions.set(DAOFUND, HUNDRED_PERCENTAGE);
            boostWeight.set(WEIGHT);
        }

        if (boostWeight.get() == null) {
            bBalnUpdateMigration();
        }    
    }

    private void bBalnUpdateMigration() {
        BigInteger day = getDay();
        Map<String, BigInteger> dists = recipientAt(getDay());
        internalDistributions.set(WORKER_TOKENS, dists.get(WORKER_TOKENS));
        internalDistributions.set(RewardsConstants.RESERVE_FUND, dists.get(RewardsConstants.RESERVE_FUND));
        internalDistributions.set(DAOFUND, dists.get(DAOFUND));
        boostWeight.set(WEIGHT);
        Address stakedLp = Context.call(Address.class, governance.get(), "getContractAddress", "stakedLp");

        int dataSourcesCount = DataSourceDB.size();

        int dataProvidersSize = dataProviders.size();
        List<Address> dataProvidersList = new ArrayList<>();
        for (int i = 0; i < dataProvidersSize; i++) {
            dataProvidersList.add(dataProviders.get(i));
        }

        for (Address dataProvider : dataProvidersList) {
            dataProviders.remove(dataProvider);
        }

        for (int i = 0; i < dataSourcesCount; i++) {
            String name = DataSourceDB.names.get(i);
            DataSourceImpl dataSource = DataSourceDB.get(name);
            if (name.equals("Loans")) {
                dataSource.setDataProvider(dataSource.getContractAddress());
            } else if (name.equals("sICX/ICX")) {
                dataSource.setDataProvider(dataSource.getContractAddress());
            } else {
                dataSource.setDataProvider(stakedLp);
            }

            dataSource.setDistPercent(day, dists.get(name));
        }

        lastUpdateDay.set(day);
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
    public List<String> getUserDataSources(Address _user) {
        EnumerableSetDB<String> userSources = getUserSources(_user);
        return userSources.range(BigInteger.ZERO, BigInteger.valueOf(userSources.length()));
    }

    @External
    public void addUserDataSource(String source) {
        _addUserDataSource(Context.getCaller(), source);
    }

    @External
    public void removeUserDataSource(String source) {
        _removeUserDataSource(Context.getCaller(), source);
    }

    @External(readonly = true)
    public Map<String, BigInteger> getBalnHoldings(Address[] _holders) {
        Map<String, BigInteger> holdings = new HashMap<>();
        for(Address address : _holders) {
            holdings.put(address.toString(), balnHoldings.getOrDefault(address.toString(), BigInteger.ZERO));
        }

        return holdings;
    }

    @External(readonly = true)
    public BigInteger getBalnHolding(Address _holder, @Optional String[] sourcesToUpdate) {
            BigInteger accruedRewards = balnHoldings.getOrDefault(_holder.toString(), BigInteger.ZERO);

        for (String name : sourcesToUpdate) {
            DataSourceImpl dataSource = DataSourceDB.get(name);
            BigInteger currentTime = getTime();
            Map<String, BigInteger> data = dataSource.loadCurrentSupply(_holder);
            Map<String, BigInteger> workingBalanceAndSupply = 
                dataSource.getWorkingBalanceAndSupply( _holder, 
                                                      data.get(BALANCE),
                                                      data.get(TOTAL_SUPPLY), 
                                                      currentTime, 
                                                      hasBoost(_holder, name));

            BigInteger sourceRewards = dataSource.updateSingleUserData(currentTime,
                                                                       workingBalanceAndSupply.get("workingSupply"),
                                                                       _holder, 
                                                                       workingBalanceAndSupply.get("workingBalance"), 
                                                                       true);
            accruedRewards = accruedRewards.add(sourceRewards);
        }

        return accruedRewards;
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
        distribute();
        BigInteger totalPercentage = HUNDRED_PERCENTAGE;
        BigInteger day = platformDay.get();
        BigInteger lastDay = lastUpdateDay.getOrDefault(day);

        for (DistributionPercentage recipient : _recipient_list) {
            String name = recipient.recipient_name;
            BigInteger percentage = recipient.dist_percent;
            BigInteger oldPercentage = internalDistributions.get(name);
            if (oldPercentage != null) {
                internalDistributions.set(name, percentage);
            } else {
                DataSourceImpl dataSource = DataSourceDB.get(name);
                oldPercentage = dataSource.getDistPercent(lastDay, false);
                dataSource.setDistPercent(day, percentage);
            }

            BigInteger diff = percentage.subtract(oldPercentage);
            totalPercentage = totalPercentage.add(diff);
        }

        lastUpdateDay.set(day);

        Context.require(totalPercentage.equals(HUNDRED_PERCENTAGE), TAG + ": Total percentage does not sum up to 100.");
    }

    @External
    public void addNewDataSource(String _name, Address _dataProvider, @Optional Address _contractAddress) {
        _contractAddress = optionalDefault(_contractAddress, _dataProvider);
        Context.require(_dataProvider.isContract(), TAG + " : Data source must be a contract.");

        recipients.add(_name);
        completeRecipient.add(_name);
        DataSourceDB.newSource(_name, _contractAddress, _dataProvider);
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

        if (platformDay.compareTo(day) <= 0) {
            return mintAndAllocateBalnReward(platformDay);
        }

        return true;
    }

    private boolean mintAndAllocateBalnReward(BigInteger platformDay) {
        BigInteger distribution = dailyDistribution(platformDay);

        IRC2MintableScoreInterface baln = new IRC2MintableScoreInterface(balnAddress.get());
        baln.mint(distribution, new byte[0]);

        BigInteger bwtDist = internalDistributions.get(WORKER_TOKENS).multiply(distribution).divide(HUNDRED_PERCENTAGE);
        BigInteger reserveDist = internalDistributions.get(RewardsConstants.RESERVE_FUND).multiply(distribution).divide(HUNDRED_PERCENTAGE);
        BigInteger daoFundDist =  internalDistributions.get(DAOFUND).multiply(distribution).divide(HUNDRED_PERCENTAGE);

        baln.transfer(bwtAddress.get(), bwtDist, new byte[0]);
        baln.transfer(reserveFund.get(), reserveDist, new byte[0]);
        baln.transfer(daofund.get(), daoFundDist, new byte[0]);

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
    public void boost(String _name) {
        Address user = Context.getCaller();
        String previousBoost = boosts.get(user);
        boosts.set(user, _name);
        if (previousBoost != null) {
            updateCurrentUserAccruedRewards(previousBoost, user);
        }

        updateCurrentUserAccruedRewards(_name, user);
    }

    @External(readonly = true)
    public String getBoost(Address user) {
        return boosts.get(user);
    }

    @External
    public void claimRewards(@Optional String[] sourcesToUpdate) {
        Address address = Context.getCaller();
        BigInteger currentTime;
        distribute();

        for (String name : sourcesToUpdate) {
            updateCurrentUserAccruedRewards(name, address);
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
        // DataSourceImpl dexDataSource = DataSourceDB.get("sICX/ICX");
        // DataSourceImpl dataSource = DataSourceDB.get(_name);
        // BigInteger emission = this.getEmission(BigInteger.valueOf(-1));

        // DataSourceScoreInterface dex = new DataSourceScoreInterface(dexDataSource.getContractAddress());

        // BigInteger balnPrice = dex.getBalnPrice();
        // BigInteger percentage = dataSource.getDistPercent();
        // BigInteger sourceValue = dataSource.getValue();
        // BigInteger year = BigInteger.valueOf(365);

        // BigInteger sourceAmount = year.multiply(emission).multiply(percentage);
        return BigInteger.ZERO;//sourceAmount.multiply(balnPrice).divide(sourceValue.multiply(HUNDRED_PERCENTAGE));
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        Context.require(Context.getCaller().equals(balnAddress.get()),
                TAG + ": The Rewards SCORE can only accept BALN tokens");
    }

    @Deprecated
    @External
    public void addDataProvider(Address _source) {
    }

    @Deprecated
    @External(readonly = true)
    public List<Address> getDataProviders() {
        return null;
    }

    @Deprecated
    @External
    public void removeDataProvider(Address _source) {
    }

    @External
    public void updateRewardsData(String _name, BigInteger _totalSupply, Address _user, BigInteger _balance) {
        DataSourceImpl dataSource = DataSourceDB.get(_name);
        Context.require(dataSource.getDataProvider().equals(Context.getCaller()), TAG + ": Only data sources are allowed to update" +
                " rewards data");

        BigInteger currentTime = getTime();
        distribute();

        _addUserDataSource(_user, _name);

        updateUserAccruedRewards(_name, _totalSupply, currentTime, dataSource, _user, _balance);
    }

    @External
    public void updateBatchRewardsData(String _name, BigInteger _totalSupply, RewardsDataEntry[] _data) {
        DataSourceImpl dataSource = DataSourceDB.get(_name);
        Context.require(dataSource.getDataProvider().equals(Context.getCaller()), TAG + ": Only data sources are allowed to update" +
                " rewards data");

        BigInteger currentTime = getTime();
        distribute();

        for (RewardsDataEntry entry : _data) {
            Address user = entry._user;
            BigInteger previousBalance = entry._balance;
            _addUserDataSource(user, _name);
            updateUserAccruedRewards(_name, _totalSupply, currentTime, dataSource, user, previousBalance);
        }
    }
    @External
    public void onKick(Address user, BigInteger bBalnUserBalance, @Optional byte[] data) {
        only(boostedBaln);

        String boostedSource = boosts.get(user);
        if (boostedSource == null) {
            // TODO: check if we do anythig here
            return;
        }

        updateCurrentUserAccruedRewards(boostedSource, user);
    }

    @External
    public void onBalanceUpdate(Address user) {
        only(boostedBaln);
        String boostedSource = boosts.get(user);
        if (boostedSource == null) {
            // TODO: check if we do anythig here
            return;
        }

        updateCurrentUserAccruedRewards(boostedSource, user);
    }

    @External
    public void setBoostWeight(BigInteger weight) {
        only(admin);
        Context.require(weight.compareTo(HUNDRED_PERCENTAGE.divide(BigInteger.valueOf(100))) >= 0, "Boost weight has to be above 1%");
        Context.require(weight.compareTo(HUNDRED_PERCENTAGE) <= 0, "Boost weight has to be below 100%");
        boostWeight.set(weight);
    }

    @External(readonly = true)
    public BigInteger getBoostWeight() {
        return boostWeight.get();
    }

    private void updateCurrentUserAccruedRewards(String name, Address user) {
        DataSourceImpl dataSource = DataSourceDB.get(name);
        Map<String, BigInteger> data = dataSource.loadCurrentSupply(user);

        BigInteger totalSupply = data.get(TOTAL_SUPPLY);
        BigInteger balance = data.get(BALANCE);

        if (balance.equals(BigInteger.ZERO)) {
            _removeUserDataSource(user, name);
        }

        BigInteger currentTime = getTime();
        updateUserAccruedRewards(name, totalSupply, currentTime, dataSource, user, balance);
    }

    private void updateUserAccruedRewards(String _name, BigInteger _totalSupply, BigInteger currentTime,
                                          DataSourceImpl dataSource, Address user, BigInteger previousBalance) {

        Map<String, BigInteger> workingBalanceAndSupply = 
            dataSource.updateWorkingBalanceAndSupply(user, 
                                                     previousBalance,
                                                     _totalSupply, 
                                                     currentTime, 
                                                     hasBoost(user, _name));

        BigInteger accruedRewards = 
            dataSource.updateSingleUserData(currentTime, 
                                            workingBalanceAndSupply.get("workingSupply"), 
                                            user,
                                            workingBalanceAndSupply.get("workingBalance"),  
                                            false);

        if (accruedRewards.compareTo(BigInteger.ZERO) > 0) {
            BigInteger newHoldings =
                    balnHoldings.getOrDefault(user.toString(), BigInteger.ZERO).add(accruedRewards);
            balnHoldings.set(user.toString(), newHoldings);
            RewardsAccrued(user, _name, accruedRewards);
        }
    }

    private boolean hasBoost(Address user, String name) {
        return name.equals(boosts.get(user));
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

    @Deprecated
    @External
    public void setContinuousRewardsDay(BigInteger _continuous_rewards_day) {
    }

    @Deprecated
    @External(readonly = true)
    public BigInteger getContinuousRewardsDay() {
        return BigInteger.ZERO;
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

    static BigInteger dailyDistribution(BigInteger day) {
        //TODO consider adding cache DiutDB day, dist
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

    private EnumerableSetDB<String> getUserSources(Address user) {
        return new EnumerableSetDB<String>(user.toString() + "|" + USER_SOURCES, String.class);
    }

    private void _addUserDataSource(Address user, String source) {
        EnumerableSetDB<String> userSources = getUserSources(user);
        userSources.add(source);
    }

    private void _removeUserDataSource(Address user, String source) {
        EnumerableSetDB<String> userSources = getUserSources(user);
        userSources.remove(source);
    }

    @EventLog(indexed=1)
    public void RewardsClaimed(Address _address, BigInteger _amount){}

    @EventLog(indexed=2)
    public void Report(BigInteger _day, String _name, BigInteger _dist, BigInteger _value) {}

    @EventLog(indexed=2)
    public void  RewardsAccrued(Address _user, String _source, BigInteger _value) {}
}
