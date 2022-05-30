package network.balanced.score.core.dividends;

import network.balanced.score.lib.interfaces.Dividends;
import network.balanced.score.lib.structs.DistributionPercentage;
import score.*;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import scorex.util.HashMap;
import scorex.util.ArrayList;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.dividends.Constants.*;
import static network.balanced.score.lib.utils.ArrayDBUtils.*;
import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Math.pow;

public class DividendsImpl implements Dividends {
    private static final VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);
    private static final VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);
    private static final VarDB<Address> loanScore = Context.newVarDB(LOANS_SCORE, Address.class);
    private static final VarDB<Address> daoFund = Context.newVarDB(DAOFUND, Address.class);
    private static final VarDB<Address> balnScore = Context.newVarDB(BALN_SCORE, Address.class);
    private static final VarDB<Address> dexScore = Context.newVarDB(DEX_SCORE, Address.class);

    private static final ArrayDB<Address> acceptedTokens = Context.newArrayDB(ACCEPTED_TOKENS, Address.class);

    private static final VarDB<BigInteger> snapshotId = Context.newVarDB(SNAPSHOT_ID, BigInteger.class);

    private static final BranchDB<BigInteger, DictDB<String, BigInteger>> dailyFees = Context.newBranchDB(DAILY_FEES, BigInteger.class);

    private static final DictDB<String, BigInteger> dividendsPercentage = Context.newDictDB(DIVIDENDS_PERCENTAGE, BigInteger.class);

    private static final BranchDB<String, BranchDB<BigInteger, DictDB<String, BigInteger>>> snapshotDividends = Context.newBranchDB(SNAPSHOT_DIVIDENDS, BigInteger.class);
    private static final DictDB<String, BigInteger> totalSnapshots = Context.newDictDB(TOTAL_SNAPSHOT, BigInteger.class);
    private static final ArrayDB<String> completeDividendsCategories = Context.newArrayDB(COMPLETE_DIVIDENDS_CATEGORIES, String.class);

    private static final VarDB<Boolean> distributionActivate = Context.newVarDB(DISTRIBUTION_ACTIVATE, Boolean.class);
    private static final VarDB<BigInteger> dividendsBatchSize = Context.newVarDB(DIVIDENDS_BATCH_SIZE, BigInteger.class);
    private static final VarDB<BigInteger> timeOffset = Context.newVarDB(TIME_OFFSET, BigInteger.class);
    private static final VarDB<BigInteger> dividendsEnabledToStakedBalnDay = Context.newVarDB(DIVIDENDS_ENABLED_TO_STAKED_BALN_ONLY_DAY, BigInteger.class);

    public DividendsImpl(@Optional Address _governance) {
        if (_governance != null) {
            Context.require(_governance.isContract(), "Dividends: Governance address should be a contract");
            DividendsImpl.governance.set(_governance);
            snapshotId.set(BigInteger.ZERO);
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
        only(governance);
        distributionActivate.set(_status);
    }
            
    @External
    public void setTimeOffset(BigInteger deltaTime) {
        only(governance);
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
        Context.require(day.compareTo(snapshotId.getOrDefault(BigInteger.ZERO)) > 0, TAG + ": Day should be greater than the current snapshot ID.");
        dividendsEnabledToStakedBalnDay.set(day);
    }

    @External(readonly = true)
    public BigInteger getDividendsOnlyToStakedBalnDay() {
        return dividendsEnabledToStakedBalnDay.getOrDefault(BigInteger.ZERO);
    }

    @External(readonly = true)
    @SuppressWarnings("unchecked")
    public Map<String, BigInteger> getBalances() {
        Address address = Context.getAddress();
        Map<String, String> assets = (Map<String, String>) Context.call(loanScore.get(), "getAssetTokens");
        Map<String, BigInteger> balances = new HashMap<>();
        for (String symbol : assets.keySet()) {
            BigInteger balance = (BigInteger) Context.call(Address.fromString(assets.get(symbol)), "balanceOf",
                    address);
            if (balance.compareTo(BigInteger.ZERO) > 0) {
                balances.put(symbol, balance);
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

        for (int i = 0; i < numberOfAcceptedTokens; i++) {
            String token = acceptedTokens.get(i).toString();
            fees.put(token, dailyFees.at(_day).get(token));
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
        only(governance);
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
        Context.require(!arrayDbContains(completeDividendsCategories, _category), TAG + ": " + _category + "is already added.");
        completeDividendsCategories.add(_category);
    }

    @External
    public void removeDividendsCategory(String _category) {
        only(admin);
        BigInteger currentDay = getDay();
        Context.require(arrayDbContains(completeDividendsCategories, _category), TAG + ": " + _category + " not found in the list of dividends categories.");

        Map<String, BigInteger> dividendsDist = dividendsAt(currentDay);
        Context.require(dividendsDist.get(_category).equals(BigInteger.ZERO), TAG + ": Please make the category percentage to 0 before removing.");

        removeFromArraydb(_category, completeDividendsCategories);
    }

    @External(readonly = true)
    public Map<String, BigInteger> getDividendsPercentage() {
        BigInteger currentDay = (BigInteger) Context.call(loanScore.get(), "getDay");
        return dividendsAt(currentDay);
    }

    @External
    public void setDividendsCategoryPercentage(DistributionPercentage[] _dist_list) {
        only(admin);
        BigInteger totalPercentage = BigInteger.ZERO;
        if (_dist_list.length != completeDividendsCategories.size()) {
            Context.revert(TAG + ": Categories count mismatched!");
        }

        for (DistributionPercentage id : _dist_list) {
            String category = id.recipient_name;
            BigInteger percent = id.dist_percent;
            Context.require(arrayDbContains(completeDividendsCategories, category),
                            TAG + category + " is not a valid dividends category");
            updateDividendsSnapshot(category, percent);
            totalPercentage = totalPercentage.add(percent);
        }

        if (!totalPercentage.equals(pow(BigInteger.TEN, 18))) {
            Context.revert(TAG + ": Total percentage doesn't sum up to 100 i.e. 10**18.");
        }
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
        return time.subtract(offset).divide(U_SECONDS_DAY);
    }

    @External(readonly = true)
    public BigInteger getTimeOffset() {
        return timeOffset.getOrDefault(BigInteger.ZERO);
    }

    @External
    public boolean distribute() {
        checkForNewDay();
        return true;
    }

    @External
    public void transferDaofundDividends(@Optional int _start, @Optional int _end) {
        Address daofund = daoFund.get();
        Context.require(distributionActivate.getOrDefault(false), TAG + ": Distribution is not activated. Can't transfer.");

        int[] value = checkStartEnd(_start, _end);
        int start = value[0];
        int end = value[1];
        Map<String, BigInteger> totalDividends = new HashMap<>();
        for (int i = start; i < end; i++) {
            Map<String, BigInteger> dividends = getDividendsForDaoFund(BigInteger.valueOf(i));
            if (dividends.size() != 0) {
                setClaimed(daofund, BigInteger.valueOf(i));
            }

            totalDividends = addDividends(totalDividends, dividends);
        }

        int numberOfAcceptedTokens = acceptedTokens.size();
        for (int i = 0; i < numberOfAcceptedTokens; i++) {
            Address token = acceptedTokens.get(i);

            if (totalDividends.containsKey(token.toString()) && totalDividends.get(token.toString()).signum() > 0) {
                sendToken(daofund, totalDividends.get(token.toString()), token, "Daofund dividends");
            }
        }

        Claimed(daofund, BigInteger.valueOf(start), BigInteger.valueOf(end), totalDividends.toString());
    }

    @External
    public void claim(@Optional int _start, @Optional int _end) {
        Context.require(distributionActivate.getOrDefault(false), TAG + ": Distribution is not activated. Can't transfer.");

        int[] value = checkStartEnd(_start, _end);
        int start = value[0];
        int end = value[1];
        Address account = Context.getCaller();
        Map<String, BigInteger> totalDividends = new HashMap<>();

        for (int i = start; i < end; i++) {
            Map<String, BigInteger> dividends = getDividendsForDay(account, BigInteger.valueOf(i));
            if (dividends.size() != 0) {
                setClaimed(account, BigInteger.valueOf(i));
            }

            totalDividends = addDividends(totalDividends, dividends);
        }

        int numberOfAcceptedTokens = acceptedTokens.size();
        for (int i = 0; i < numberOfAcceptedTokens; i++) {
            Address token = acceptedTokens.get(i);
            if (totalDividends.containsKey(token.toString()) && totalDividends.get(token.toString()).signum() > 0) {
                sendToken(account, totalDividends.get(token.toString()), token, "User dividends");
            }
        }

        Claimed(account, BigInteger.valueOf(start), BigInteger.valueOf(end), totalDividends.toString());
    }

    @External
    @SuppressWarnings("unchecked")
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        Address sender = Context.getCaller();
        BigInteger snapId = snapshotId.getOrDefault(BigInteger.ZERO);
        Map<String, ?> availableTokens;

        for (int i = 0; i < acceptedTokens.size(); i++) {
            if (!sender.equals(acceptedTokens.get(i))) {
                availableTokens = (Map<String, ?>) Context.call(loanScore.get(), "getAssetTokens");
                for (String value : availableTokens.keySet()) {
                    if (sender.toString().equals(value)) {
                        acceptedTokens.add(sender);
                    }
                }
            }
        }
        checkForNewDay();
        BigInteger val = dailyFees.at(snapId).getOrDefault(sender.toString(), BigInteger.ZERO);
        dailyFees.at(snapId).set(sender.toString(), val.add(_value));

        DividendsReceivedV2(_value, snapId, _value + "tokens received as dividends token: " + sender);
    }

    @External(readonly = true)
    public Map<String, BigInteger> getUserDividends(Address _account, int _start, int _end) {
        int[] value = checkStartEnd(_start, _end);
        int start = value[0];
        int end = value[1];

        Map<String, BigInteger> totalDividends = new HashMap<>();
        for (int i = start; i < end; i++) {
            Map<String, BigInteger> dividends = getDividendsForDay(_account, BigInteger.valueOf(i));
            totalDividends = addDividends(totalDividends, dividends);
        }

        return totalDividends;
    }

    @External(readonly = true)
    public Map<String, BigInteger> getDaoFundDividends(int _start, int _end) {
        int[] value = checkStartEnd(_start, _end);
        int start = value[0];
        int end = value[1];

        Map<String, BigInteger> totalDividends = new HashMap<>();
        for (int i = start; i < end; i++) {
            Map<String, BigInteger> dividends = getDividendsForDaoFund(BigInteger.valueOf(i));
            totalDividends = addDividends(totalDividends, dividends);
        }

        return totalDividends;
    }

    @External(readonly = true)
    @SuppressWarnings("unchecked")
    public Map<String, BigInteger> dividendsAt(BigInteger _day) {
        Context.require(_day.signum() >= 0, TAG + "IRC2Snapshot: day:" + _day + " must be equal to or greater then Zero.");
        Map<String, BigInteger> dividendsDist = new HashMap<>();
        int numberOfCategories = completeDividendsCategories.size();
        for (int i = 0; i < numberOfCategories; i++) {
            String category = completeDividendsCategories.get(i);
            BigInteger totalSnapshotsTaken = totalSnapshots.getOrDefault(category, BigInteger.ZERO);
            if (totalSnapshotsTaken.signum() == 0) {
                dividendsDist.put(category, dividendsPercentage.get(category));
                continue;
            } else if (snapshotDividends.at(category).at(totalSnapshotsTaken.subtract(BigInteger.ONE)).getOrDefault("ids", BigInteger.ZERO).compareTo(_day) < 1) {
                dividendsDist.put(category, snapshotDividends.at(category).at(totalSnapshotsTaken.subtract(BigInteger.ONE)).get("amount"));
                continue;
            } else if (snapshotDividends.at(category).at(BigInteger.ZERO).get("ids").compareTo(_day) > 0) {
                dividendsDist.put(category, dividendsPercentage.get(category));
                continue;
            }

            BigInteger low = BigInteger.ZERO;
            BigInteger high = totalSnapshotsTaken.subtract(BigInteger.ONE);
            while (high.compareTo(low) > 0) {
                BigInteger mid = high.subtract(high.subtract(low).divide(BigInteger.TWO));
                DictDB<String,BigInteger> midValue = snapshotDividends.at(category).at(mid);
                BigInteger index = midValue.getOrDefault("ids", BigInteger.ZERO);
                if (index.equals(_day)) {
                    low = mid;
                    break;
                } else if (index.compareTo(_day) < 0) {
                    low = mid;
                } else
                    high = mid.subtract(BigInteger.ONE);
            }

            dividendsDist.put(category, snapshotDividends.at(category).at(low).get("amount"));
        }

        for (String key : dividendsDist.keySet()) {
            BigInteger value = dividendsDist.get(key);
            if (!value.equals(BigInteger.ZERO))
                dividendsDist.put(key, value);
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

        if (!(start >= 1 && start < snap)) {
            Context.revert("Invalid value of start provided.");
        }
        if (!(end > 1 && end <= snap)) {
            Context.revert("Invalid value of end provided.");
        }
        if (start >= end) {
            Context.revert("Start must not be greater than or equal to end.");
        }
        if ((end - start) > batch) {
            Context.revert("Maximum allowed range is " + batch);
        }

        int[] list = new int[2];
        list[0] = start;
        list[1] = end;

        return list;
    }

    private Map<String, BigInteger> getDividendsForDay(Address account, BigInteger day) {
        boolean claim = isClaimed(account, day);
        if (claim) {
            return new HashMap<>();
        }

        Address baln = balnScore.get();
        Address dex = dexScore.get();
        BigInteger stakedBaln = (BigInteger) Context.call(baln, "stakedBalanceOfAt", account, day);
        BigInteger totalStakedBaln = (BigInteger) Context.call(baln, "totalStakedBalanceOfAt", day);

        BigInteger myBalnFromPools = BigInteger.ZERO;
        BigInteger totalBalnFromPools = BigInteger.ZERO;

        ArrayList<BigInteger> poolList = new ArrayList<>();
        poolList.add(BALNBNUSD_ID);
        poolList.add(BALNSICX_ID);
        BigInteger dividendsSwitchingDay = dividendsEnabledToStakedBalnDay.getOrDefault(BigInteger.ZERO);

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
            for (int i = 0; i < acceptedTokens.size(); i++) {
                Address token = acceptedTokens.get(i);
                BigInteger numerator = myTotalBalnToken.multiply(dividendsDistribution.get(BALN_HOLDERS)).
                        multiply(dailyFees.at(day).getOrDefault(token.toString(), BigInteger.ZERO));
                BigInteger denominator = totalBalnToken.multiply(pow(BigInteger.TEN, 18));
                myDividends.put(token.toString(), numerator.divide(denominator));
            }
        }

        return myDividends;
    }

    private Map<String, BigInteger> getDividendsForDaoFund(BigInteger day) {
        Address dao = daoFund.get();
        Boolean claim = isClaimed(dao, day);
        if (claim.equals(true)) {
            return new HashMap<>();
        }

        Map<String, BigInteger> daoFundDividends = new HashMap<>();
        for (int i = 0; i < acceptedTokens.size(); i++) {
            Address token = acceptedTokens.get(i);
            Map<String, BigInteger> dividendsDist = dividendsAt(day);
            BigInteger numerator = dividendsDist.get(DAOFUND).multiply(dailyFees.at(day).getOrDefault(token.toString(), BigInteger.ZERO));
            BigInteger denominator = pow(BigInteger.TEN, 18);
            daoFundDividends.put(token.toString(), numerator.divide(denominator));
        }

        return daoFundDividends;
    }

    private Map<String, BigInteger> addDividends(Map<String, BigInteger> totalDividends, Map<String, BigInteger> currentDividends) {
        if (totalDividends.size() > 0 && currentDividends.size() > 0) {
            Map<String, BigInteger> response = new HashMap<>();
            for (int i = 0; i < acceptedTokens.size(); i++) {
                Address token = acceptedTokens.get(i);
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
            return new HashMap<>();
    }

    private void setClaimed(Address account, BigInteger day) {
        DictDB<BigInteger, BigInteger> claimedBitMap = Context.newDictDB(CLAIMED_BIT_MAP + account, BigInteger.class);
        BigInteger claimedWordIndex = day.divide(TWO_FIFTY_SIX);
        BigInteger claimedBitIndex = day.remainder(TWO_FIFTY_SIX);
        int bitShift = claimedBitIndex.intValue();

        claimedBitMap.set(claimedWordIndex, claimedBitMap.getOrDefault(claimedWordIndex, BigInteger.ZERO).setBit(bitShift));
    }

    private boolean isClaimed(Address account, BigInteger day) {
        DictDB<BigInteger, BigInteger> claimedBitMap = Context.newDictDB(CLAIMED_BIT_MAP + account, BigInteger.class);
        BigInteger claimedWordIndex = day.divide(TWO_FIFTY_SIX);
        BigInteger claimedBitIndex = day.remainder(TWO_FIFTY_SIX);

        BigInteger claimedWord = claimedBitMap.getOrDefault(claimedWordIndex, BigInteger.ZERO);
        int bitShift = claimedBitIndex.intValue();
        BigInteger mask = BigInteger.ONE.shiftLeft(bitShift);

        return (claimedWord.and(mask).equals(mask));
    }

    private void updateDividendsSnapshot(String category, BigInteger percent) {
        BigInteger currentDay = getDay();
        BigInteger totalSnapshotsTaken = totalSnapshots.getOrDefault(category, BigInteger.ZERO);
        BigInteger value = snapshotDividends.at(category).at(totalSnapshotsTaken.subtract(BigInteger.ONE)).get("ids");

        if (totalSnapshotsTaken.signum() > 0 && value.equals(currentDay)) {
            snapshotDividends.at(category).at(totalSnapshotsTaken.subtract(BigInteger.ONE)).set("amount", percent);
        } else {
            snapshotDividends.at(category).at(totalSnapshotsTaken).set("ids", currentDay);
            snapshotDividends.at(category).at(totalSnapshotsTaken).set("amount", percent);
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
