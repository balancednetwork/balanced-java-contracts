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

import static network.balanced.score.core.rewards.utils.RewardsConstants.DEFAULT_BATCH_SIZE;
import static network.balanced.score.lib.utils.Check.isContract;
import static network.balanced.score.lib.utils.Check.only;
import static network.balanced.score.lib.utils.Check.onlyOwner;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.U_SECONDS_DAY;
import static network.balanced.score.lib.utils.DBHelpers.contains;
import static network.balanced.score.lib.utils.Math.pow;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import network.balanced.score.lib.interfaces.DataSourceScoreInterface;
import network.balanced.score.lib.interfaces.Rewards;
import network.balanced.score.lib.interfaces.tokens.MintableScoreInterface;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.structs.RewardsDataEntry;
import score.Address;
import score.ArrayDB;
import score.BranchDB;
import score.Context;
import score.DictDB;
import score.VarDB;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import scorex.util.ArrayList;
import scorex.util.HashMap;

public class RewardsImpl implements Rewards {
    public static final String TAG = "BalancedRewards";

    public static final String GOVERNANCE = "governance";
    public static final String ADMIN = "admin";
    public static final String BALN_ADDRESS = "baln_address";
    public static final String BWT_ADDRESS = "bwt_address";
    public static final String RESERVE_FUND = "reserve_fund";
    public static final String DAO_FUND = "dao_fund";
    public static final String STAKEDLP = "stakedLp";
    public static final String START_TIMESTAMP = "start_timestamp";
    public static final String BATCH_SIZE = "batch_size";
    public static final String BALN_HOLDINGS = "baln_holdings";
    public static final String RECIPIENT_SPLIT = "recipient_split";
    public static final String SNAPSHOT_RECIPIENT = "snapshot_recipient";
    public static final String COMPLETE_RECIPIENT = "complete_recipient";
    public static final String TOTAL_SNAPSHOTS = "total_snapshots";
    public static final String RECIPIENTS = "recipients";
    public static final String TOTAL_DIST = "total_dist";
    public static final String PLATFORM_DAY = "platform_day";
    public static final String CONTINUOUS_REWARDS_DAY = "continuous_rewards_day";
    public static final String MIGRATING_TO_CONTINUOUS = "migrating_to_continuous";

    public static final VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);
    public static final VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);
    public static final VarDB<Address> balnAddress = Context.newVarDB(BALN_ADDRESS, Address.class);
    public static final VarDB<Address> bwtAddress = Context.newVarDB(BWT_ADDRESS, Address.class);
    public static final VarDB<Address> reserveFund = Context.newVarDB(RESERVE_FUND, Address.class);
    public static final VarDB<Address> daofund = Context.newVarDB(DAO_FUND, Address.class);
    public static final VarDB<Address> stakedLp = Context.newVarDB(STAKEDLP, Address.class);
    public static final VarDB<BigInteger> startTimestamp = Context.newVarDB(START_TIMESTAMP, BigInteger.class);
    public static final VarDB<Integer> batchSize = Context.newVarDB(BATCH_SIZE, Integer.class);
    public static final DictDB<String, BigInteger> balnHoldings = Context.newDictDB(BALN_HOLDINGS, BigInteger.class);
    public static final DictDB<String, BigInteger> recipientSplit = Context.newDictDB(RECIPIENT_SPLIT, BigInteger.class);
    public static final BranchDB<String, BranchDB<BigInteger, DictDB<String, BigInteger>>> snapshotRecipient = Context.newBranchDB(SNAPSHOT_RECIPIENT, BigInteger.class);
    public static final ArrayDB<String> completeRecipient = Context.newArrayDB(COMPLETE_RECIPIENT,  String.class);
    public static final DictDB<String, BigInteger> totalSnapshots = Context.newDictDB(TOTAL_SNAPSHOTS, BigInteger.class);
    public static final ArrayDB<String> recipients = Context.newArrayDB(RECIPIENTS, String.class);
    public static final VarDB<BigInteger> totalDist = Context.newVarDB(TOTAL_DIST, BigInteger.class);
    public static final VarDB<BigInteger> platformDay = Context.newVarDB(PLATFORM_DAY, BigInteger.class);
    public static final VarDB<BigInteger> continuousRewardsDay = Context.newVarDB(CONTINUOUS_REWARDS_DAY, BigInteger.class);
    public static final VarDB<Boolean> migratingToContinuous = Context.newVarDB(MIGRATING_TO_CONTINUOUS, boolean.class);

    public static final Map<String, VarDB<Address>> platformRecipients = Map.of("Worker Tokens", bwtAddress,
                            "Reserve Fund", reserveFund,
                            "DAOfund", daofund);
                        
    public RewardsImpl(Address _governance) {
        if (governance.getOrDefault(null) != null) {
            return;
        }

        governance.set(_governance);
        platformDay.set(BigInteger.ONE);
        batchSize.set(DEFAULT_BATCH_SIZE);
        recipientSplit.set("Worker Tokens", BigInteger.ZERO);
        recipientSplit.set("Reserve Fund", BigInteger.ZERO);
        recipientSplit.set("DAOfund", BigInteger.ZERO);

        recipients.add("Worker Tokens");
        recipients.add("Reserve Fund");
        recipients.add("DAOfund");
        completeRecipient.add("Worker Tokens");
        completeRecipient.add("Reserve Fund");
        completeRecipient.add("DAOfund");
        
        continuousRewardsDay.set(BigInteger.ZERO);

        startTimestamp.set(BigInteger.ZERO);
    }

    @External
    public void setDay(BigInteger _day) {
        only(governance);
        for (int i = 0; i < DataSourceDB.size(); i++ ) {
            String name = DataSourceDB.names.get(i);
            DataSourceImpl dataSource = DataSourceDB.get(name);

            BigInteger totatDistribution = dataSource.getTotalDist(_day.subtract(BigInteger.ONE));
            BigInteger totalValue = dataSource.getTotalValue(_day.subtract(BigInteger.ONE));
            Report(_day.subtract(BigInteger.ONE), name, totatDistribution, totalValue);
            dataSource.setDay(_day);
        }
    }
   
    @External(readonly = true)
    public String name() {
        return "Balanced Rewards";
    }
   
    @External(readonly = true)
    public BigInteger getEmission(@Optional BigInteger _day) {
        if (_day == null) {
            _day = BigInteger.valueOf(-1);
        }

        if (_day.compareTo(BigInteger.ONE) < 0) {
            _day = getDay().add(BigInteger.ONE);
        }

        Context.require(_day.compareTo(BigInteger.ZERO) == 1, TAG + ": " + "Invalid day.");
        return dailyDistribution(_day);
    }
   
    @External(readonly = true)
    public Map<Address, BigInteger> getBalnHoldings(Address[] _holders) {
        HashMap<Address, BigInteger> holdings = new HashMap<Address, BigInteger>(_holders.length);
        for(Address address : _holders) {
            holdings.put(address, balnHoldings.getOrDefault(address.toString(), BigInteger.ZERO));
        }

        return holdings;
    }
   
    @External(readonly = true)
    public BigInteger getBalnHolding(Address _holder) {
        BigInteger accruedRewards = balnHoldings.getOrDefault(_holder.toString(), BigInteger.ZERO);

        for (int i = 0; i < DataSourceDB.size(); i++ ) {
            String name = DataSourceDB.names.get(i);
            DataSourceImpl dataSource = DataSourceDB.get(name);

            Map<String, BigInteger> data = dataSource.loadCurrentSupply(_holder);
            BigInteger currentTime = BigInteger.valueOf(Context.getBlockTimestamp());
            BigInteger sourceRewards = dataSource.computeSingelUserData(currentTime,
                                                                        data.get("_totalSupply"),
                                                                        _holder,
                                                                        data.get("_balance"));
            accruedRewards = accruedRewards.add(sourceRewards);
        }

        return accruedRewards;
    }

    @External(readonly = true)
    public Map<String, Object> distStatus() {
        HashMap<String, BigInteger> sourceDays = new HashMap<>(DataSourceDB.size());
        for (int i = 0; i < DataSourceDB.size(); i++ ) {
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
    This method provides a means to adjust the allocation of rewards tokens.
    To maintain consistency a change to these percentages will only be
    accepted if they sum to 100%, with 100% represented by the value 10**18.
    This method must only be called when rewards are fully up to date.

    * param _recipient_list: List of dicts containing the allocation spec.
    * type _recipient_list: List[TypedDict]
    **/
    @External
    public void updateBalTokenDistPercentage(DistributionPercentage[] _recipient_list) {
        only(governance);
        Context.require(_recipient_list.length == recipients.size(), TAG + ": Recipient lists lengths mismatched!");
        BigInteger totalPercentange = BigInteger.ZERO;
        BigInteger day = getDay();

        for (DistributionPercentage recipient : _recipient_list) {
            String name = (String) recipient.recipient_name;

            Context.require(contains(recipients, name), TAG + ": Recipient " + name + " does not exist.");

            BigInteger percentage = (BigInteger) recipient.dist_percent;
            updateRecipientSnapshot(name, percentage);
            DataSourceImpl dataSource = DataSourceDB.get(name);
            BigInteger dataSourceDay = dataSource.getDay();
            if (dataSource.getTotalDist(dataSourceDay).equals(BigInteger.ZERO)) {
                dataSource.setDay(day);
            }

            dataSource.setDistPercent(percentage);
            totalPercentange = totalPercentange.add(percentage);
        }

        Context.require(totalPercentange.equals(EXA), TAG + ": Total percentage does not sum up to 100.");
    }

    @External(readonly = true)
    public List<String> getDataSourceNames() {
        ArrayList<String> names = new ArrayList<>(DataSourceDB.size());
        for (int i = 0; i < DataSourceDB.size(); i++ ) {
            names.add(DataSourceDB.names.get(i));
        }
    
        return names;
    }

    @External(readonly = true)
    public List<String> getRecipients() {
         ArrayList<String> names = new ArrayList<>(recipients.size());
        for (int i = 0; i < recipients.size(); i++ ) {
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
        if (contains(recipients, _name)) {
            return;
        }

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
        Map<String, BigInteger>  recipientDistribution = recipientAt(day);
        Context.require(!recipientDistribution.containsKey(_name), TAG + ": Data source rewards percentage must be set to 0 before removing.");
        String topRecipient = recipients.pop();
        if (topRecipient != _name) {
            for(int i = 0; i < recipients.size(); i++) {
                if (recipients.get(i) == _name) {
                    recipients.set(i, topRecipient);
                }
            }
        }
    
        DataSourceDB.removeSource(_name);
    }

    @External(readonly = true)
    public Map<String, Map<String, Object>> getDataSources() {
         HashMap<String, Map<String, Object>> dataSources = new HashMap<>(DataSourceDB.size());
        for (int i = 0; i < DataSourceDB.size(); i++ ) {
            String name = DataSourceDB.names.get(i);
            DataSourceImpl dataSource = DataSourceDB.get(name);

            dataSources.put(name, dataSource.getData());
        }

        return dataSources;
    }
    
    @External(readonly = true)
    public Map<String, Map<String, Object>> getDataSourcesAt(BigInteger _day) {
        HashMap<String, Map<String, Object>> dataSources = new HashMap<>(DataSourceDB.size());
        for (int i = 0; i < DataSourceDB.size(); i++ ) {
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

    @External
    public boolean distribute() {
        BigInteger platformDay = RewardsImpl.platformDay.get();
        BigInteger day = getDay();
        BigInteger continuousRewardsDay = RewardsImpl.continuousRewardsDay.get();

        boolean distributionRequired = false;
        boolean continuousRewardsActive = day.compareTo(continuousRewardsDay) != -1;
        boolean dayOfContinuousRewards = day.equals(continuousRewardsDay);

        if (platformDay.compareTo(day) < 0 && !continuousRewardsActive) {
            distributionRequired = true;
        } else if (platformDay.compareTo(day.add(BigInteger.ONE)) < 0 && continuousRewardsActive) {
            distributionRequired = true;
        }

        if (distributionRequired) {
            if (totalDist.getOrDefault(BigInteger.ZERO).equals(BigInteger.ZERO) || continuousRewardsActive) {
                MintableScoreInterface baln = new MintableScoreInterface(balnAddress.get());
                
                BigInteger distribution = dailyDistribution(platformDay);
                
                baln.mint(distribution);
                totalDist.set(distribution);

                BigInteger shares = EXA;
                BigInteger remaning = distribution;
                Map<String, BigInteger> recipientDistribution = recipientAt(day);
                for (String name : recipientDistribution.keySet()) {
                    BigInteger split = recipientDistribution.get(name);
                    BigInteger share = remaning.multiply(split).divide(shares);

                    if (contains(DataSourceDB.names, name)) {
                        DataSourceDB.get(name).setTotalDist(platformDay, share);
                    } else {
                        baln.transfer(platformRecipients.get(name).get(), share, new byte[0]);
                    }

                    remaning = remaning.subtract(share);
                    shares = shares.subtract(split);

                    if (shares.equals(BigInteger.ZERO)) {
                        break;
                    }
                }

                totalDist.set(remaning);
                RewardsImpl.platformDay.set(platformDay.add(BigInteger.ONE));
                return false;
            }
        }

        if (dayOfContinuousRewards || !continuousRewardsActive) {
            for (int i = 0; i < DataSourceDB.size(); i++ ) {
                String name = DataSourceDB.names.get(i);
                DataSourceImpl dataSource = DataSourceDB.get(name);
                if (dataSource.getDay().compareTo(day) == -1) {
                    dataSource.distribute(batchSize.get());
                    return false;
                }
        
            }
        }

        return true;
    }

    @External(readonly = true)
    public Map<String, BigInteger> recipientAt(BigInteger _day) {
        Context.require(_day.compareTo(BigInteger.ZERO) != -1, TAG + ": day:" + _day + " must be equal to or greater then Zero");
        
        HashMap<String, BigInteger> distributions = new HashMap<>(completeRecipient.size());
        for (int i = 0; i < completeRecipient.size(); i++) {
            String recipient = completeRecipient.get(i);
            BigInteger totalSnapshotsTaken = totalSnapshots.get(recipient);
            if (totalSnapshotsTaken.equals(BigInteger.ZERO)) {
                distributions.put(recipient, recipientSplit.get(recipient));
                continue;
            } else if (snapshotRecipient.at(recipient).at(totalSnapshotsTaken.subtract(BigInteger.ONE)).get("ids").compareTo(_day) != 1) {
                distributions.put(recipient, snapshotRecipient.at(recipient).at(totalSnapshotsTaken.subtract(BigInteger.ONE)).get("amount"));
                continue;
            } else if (snapshotRecipient.at(recipient).at(BigInteger.ZERO).get("ids").compareTo(_day) == 1) {
                distributions.put(recipient, recipientSplit.getOrDefault(recipient, BigInteger.ZERO));
                continue;
            }

            BigInteger low = BigInteger.ZERO;
            BigInteger high = totalSnapshotsTaken.subtract(BigInteger.ONE);
            BigInteger mid;
            DictDB<String, BigInteger> midValue;
            while (high.compareTo(low) == 1) {
                mid  = high.subtract(high.subtract(low).divide(BigInteger.valueOf(2)));
                midValue = snapshotRecipient.at(recipient).at(mid);
                if (midValue.get("ids").equals(_day)) {
                    distributions.put(recipient, midValue.get("amount"));
                    break;
                } else if (midValue.get("ids").compareTo(_day) == -1) {
                    low = mid;
                } else {
                    high = mid.subtract(BigInteger.ONE);
                }
            }

            distributions.put(recipient, snapshotRecipient.at(recipient).at(low).get("amount"));
        }

        for(Iterator<Map.Entry<String, BigInteger>> it = distributions.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, BigInteger> entry = it.next();
            if(entry.getValue().equals(BigInteger.ZERO)) {
                it.remove();
            }
        }

        return distributions;
    }

    @External
    public void claimRewards() {
        Address address = Context.getCaller();
        BigInteger currentTime = BigInteger.valueOf(Context.getBlockTimestamp());
        distribute();

        for (int i = 0; i < DataSourceDB.size(); i++ ) {
            String name = DataSourceDB.names.get(i);
            DataSourceImpl dataSource = DataSourceDB.get(name);
            Map<String, BigInteger> data = dataSource.loadCurrentSupply(address);
            BigInteger totalSupply = data.get("_totalSupply");
            BigInteger balance = data.get("_balance");

            currentTime = BigInteger.valueOf(Context.getBlockTimestamp());
            BigInteger accuredRewards = dataSource.updateSingleUserData(currentTime, totalSupply, address, balance);
            
            if (accuredRewards.compareTo(BigInteger.ZERO) == 1) {
                BigInteger newHoldings = balnHoldings.getOrDefault(address.toString(), BigInteger.ZERO).add(accuredRewards);
                balnHoldings.set(address.toString(), newHoldings);
                RewardsAccrued(address, name, accuredRewards);
            }
        }

        if (balnHoldings.getOrDefault(address.toString(), BigInteger.ZERO).compareTo(BigInteger.ZERO) == 1) {
            BigInteger accuredRewards = balnHoldings.get(address.toString());
            balnHoldings.set(address.toString(), BigInteger.ZERO);
            
            MintableScoreInterface baln = new MintableScoreInterface(balnAddress.get());
            baln.transfer(address, accuredRewards, new byte[0]);
            RewardsClaimed(address, accuredRewards);
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
        return sourceAmount.multiply(balnPrice).divide(EXA.multiply(sourceValue));
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        Context.require(Context.getCaller().equals(balnAddress.get()),
           TAG + ": The Rewards SCORE can only accept BALN tokens");
    }

    @External
    public void bonusDist(Address[] _addresses,  BigInteger[] _amounts) {
        only(governance);
        Context.require( _addresses.length <= 500, "List length longer than allowed maximum of 500.");
        Context.require( _amounts.length <= 500, "List length longer than allowed maximum of 500.");
        Context.require( _addresses.length == _amounts.length, "Length of addresses and amounts does not match.");
        
        for (int i = 0; i < _amounts.length; i++) {
            balnHoldings.set(_addresses[i].toString(), _amounts[i]);
        }
    }

    @External
    public void updateRewardsData(String _name, BigInteger _totalSupply, Address _user, BigInteger _balance) {
        DataSourceImpl dataSource = DataSourceDB.get(_name);
        Context.require(dataSource.getContractAddress().equals(Context.getCaller()), "Only datasources are allowed to update rewards data");

        BigInteger currentTime = BigInteger.valueOf(Context.getBlockTimestamp());

        distribute();
        BigInteger accuredRewards = dataSource.updateSingleUserData(currentTime, _totalSupply, _user, _balance);

        if (accuredRewards.compareTo(BigInteger.ZERO) == 1) {
            BigInteger newHoldings = balnHoldings.getOrDefault(_user.toString(), BigInteger.ZERO).add(accuredRewards);
            balnHoldings.set(_user.toString(), newHoldings);
            RewardsAccrued(_user, _name, accuredRewards);
        }
    }

    @External
    public void updateBatchRewardsData(String _name, BigInteger _totalSupply, RewardsDataEntry[] _data) {
        DataSourceImpl dataSource = DataSourceDB.get(_name);
        Context.require(dataSource.getContractAddress().equals(Context.getCaller()), "Only datasources are allowed to update rewards data");

        BigInteger currentTime = BigInteger.valueOf(Context.getBlockTimestamp());

        distribute();
        
        for (RewardsDataEntry entry : _data) {
            Address user = (Address) entry._user;
            BigInteger previousBalance = (BigInteger) entry._balance;
            BigInteger accuredRewards = dataSource.updateSingleUserData(currentTime, _totalSupply, user, previousBalance);

            if (accuredRewards.compareTo(BigInteger.ZERO) == 1) {
                BigInteger newHoldings = balnHoldings.getOrDefault(user.toString(), BigInteger.ZERO).add(accuredRewards);
                balnHoldings.set(user.toString(), newHoldings);
                RewardsAccrued(user, _name, accuredRewards);
            }
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
    }

    @External(readonly = true)
    public BigInteger getTimeOffset() {
        return startTimestamp.getOrDefault(BigInteger.ZERO);
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
        BigInteger blockTime = BigInteger.valueOf(Context.getBlockTimestamp()).subtract(startTimestamp.getOrDefault(BigInteger.ZERO));
        return blockTime.divide(U_SECONDS_DAY);
    }

    public BigInteger dailyDistribution(BigInteger day) {
        BigInteger baseDistribution = pow(BigInteger.TEN, 23);
        if (day.compareTo(BigInteger.valueOf(60)) != 1) {
            return baseDistribution;
        } else {
            BigInteger index = day.subtract(BigInteger.valueOf(60));
            BigInteger decay = pow(BigInteger.valueOf(995), index.intValue());
            BigInteger decayOffset = pow(BigInteger.valueOf(1000), index.intValue());
            BigInteger minDistribution = BigInteger.valueOf(1250).multiply(EXA);
            BigInteger distribution = decay.multiply(baseDistribution).divide(decayOffset);
            return minDistribution.max(distribution);
        }
    }

    public void updateRecipientSnapshot(String recipient, BigInteger percentage) {
        BigInteger currentDay = getDay();
        BigInteger totalSnapshotsTaken = totalSnapshots.getOrDefault(recipient, BigInteger.ZERO); 
        BigInteger recipientDay = snapshotRecipient.at(recipient).at(totalSnapshotsTaken.subtract(BigInteger.ONE)).getOrDefault("ids", BigInteger.ZERO);

        if (totalSnapshotsTaken.compareTo(BigInteger.ZERO) == 1 && recipientDay.equals(currentDay)) {
            snapshotRecipient.at(recipient).at(totalSnapshotsTaken.subtract(BigInteger.ONE)).set("amount", percentage);
        } else {
            snapshotRecipient.at(recipient).at(totalSnapshotsTaken).set("ids", currentDay); 
            snapshotRecipient.at(recipient).at(totalSnapshotsTaken).set("amount", percentage);
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
