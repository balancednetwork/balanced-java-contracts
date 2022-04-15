package network.balanced.score.core.dividends;

import network.balanced.score.lib.interfaces.Dividends;

import network.balanced.score.lib.structs.DistPercentDict;
import score.*;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Constants.*;

import static network.balanced.score.core.dividends.Constants.*;
import static network.balanced.score.core.dividends.Helpers.removeFromArrarDb;
import static network.balanced.score.lib.utils.Math.pow;

public class DividendsImpl implements Dividends {

    public static final VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);
    public static final VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);
    public static final VarDB<Address> loanScore = Context.newVarDB(LOANS_SCORE, Address.class);
    public static final VarDB<Address> daoFund = Context.newVarDB(DAOFUND, Address.class);
    public static final VarDB<Address> balnScore = Context.newVarDB(BALN_SCORE, Address.class);
    public static final VarDB<Address> dexScore = Context.newVarDB(DEX_SCORE, Address.class);

    public static final ArrayDB<Address> acceptedTokens = Context.newArrayDB(ACCEPTED_TOKENS, Address.class);

    public static final DictDB<Address, BigInteger> amountToDistribute = Context.newDictDB(AMOUNT_TO_DISTRIBUTE, BigInteger.class);

    public static final DictDB<Address, BigInteger> amountBeingDistributed = Context.newDictDB(AMOUNT_BEING_DISTRIBUTED, BigInteger.class);

    public static final VarDB<String> balnDistIndex = Context.newVarDB(BALN_DIST_INDEX, String.class);
    public static final VarDB<String> stakedDistIndex = Context.newVarDB(STAKED_DIST_INDEX, String.class);

    public static final VarDB<BigInteger> balnInDex = Context.newVarDB(BALN_IN_DEX, BigInteger.class);
    public static final VarDB<BigInteger> totalLpTokens = Context.newVarDB(TOTAL_LP_TOKENS, BigInteger.class);
    public static final VarDB<BigInteger> lpHoldersIndex = Context.newVarDB(LP_HOLDERS_INDEX, BigInteger.class);

    public static final BranchDB<Address, DictDB<Address, BigInteger>> userBalance = Context.newBranchDB(USERS_BALANCE, BigInteger.class);

    public static final VarDB<BigInteger> dividendsDistributionStatus = Context.newVarDB(DIVIDENDS_DISTRIBUTION_STATUS, BigInteger.class);

    public static final VarDB<BigInteger> snapshotId = Context.newVarDB(SNAPSHOT_ID, BigInteger.class);

    public static final VarDB<Boolean> amountReceivedStatus = Context.newVarDB(AMOUNT_RECEIVED_STATUS, Boolean.class);
    public static final BranchDB<BigInteger, DictDB<String, BigInteger>> dailyFees = Context.newBranchDB(DAILY_FEES, BigInteger.class);

    public static final VarDB<BigInteger> maxLoopCount = Context.newVarDB(MAX_LOOP_COUNT, BigInteger.class);
    public static final VarDB<BigInteger> minimumEligibleDebt = Context.newVarDB(MIN_ELIGIBLE_DEBT, BigInteger.class);

    public static final DictDB<String, BigInteger> dividendsPercentage = Context.newDictDB(DIVIDENDS_PERCENTAGE, BigInteger.class);

    public static final BranchDB<String, BranchDB<BigInteger, DictDB<String, BigInteger>>> snapshotDividends = Context.newBranchDB(SNAPSHOT_DIVIDENDS, BigInteger.class);
    public static final DictDB<String, BigInteger> totalSnapshots = Context.newDictDB(TOTAL_SNAPSHOT, BigInteger.class);
    public static final ArrayDB<String> completeDividendsCategories = Context.newArrayDB(COMPLETE_DIVIDENDS_CATEGORIES, String.class);

    public static final VarDB<Boolean> distributionActivate = Context.newVarDB(DISTRIBUTION_ACTIVATE, Boolean.class);
    public static final VarDB<BigInteger> dividendsBatchSize = Context.newVarDB(DIVIDENDS_BATCH_SIZE, BigInteger.class);
    public static final VarDB<BigInteger> timeOffset = Context.newVarDB(TIME_OFFSET, BigInteger.class);
    public static final VarDB<BigInteger> dividends_enabled_to_staked_baln_day = Context.newVarDB(DIVIDENDS_ENABLED_TO_STAKED_BALN_ONLY_DAY, BigInteger.class);

    public DividendsImpl(@Optional Address governance) {
        if (governance != null) {
            Context.require(governance.isContract(), "Dividends: Governance address should be a contract");
            DividendsImpl.governance.set(governance);
            snapshotId.set(BigInteger.ONE);
            maxLoopCount.set(MAX_LOOP);
            minimumEligibleDebt.set(MINIMUM_ELIGIBLE_DEBT);
            distributionActivate.set(false);
            addInitialCategories();
        }
    }

    public void setTimeOffset() {
        BigInteger offsetTime = (BigInteger) Context.call(dexScore.get(), "getTimeOffset");
        timeOffset.set(offsetTime);

    }

    private void addInitialCategories() {
        completeDividendsCategories.add(DAO_FUND);
        completeDividendsCategories.add(BALN_HOLDERS);
        dividendsPercentage.set(DAO_FUND, BigInteger.valueOf(4).multiply(pow(BigInteger.TEN, 17)));
        dividendsPercentage.set(BALN_HOLDERS, BigInteger.valueOf(6).multiply(pow(BigInteger.TEN, 17)));
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
        Context.require(day.compareTo(snapshotId.getOrDefault(BigInteger.ZERO)) < 1, TAG + "Day should be greater than the current snapshot ID.");
        dividends_enabled_to_staked_baln_day.set(day);
    }

    @External(readonly = true)
    public BigInteger getDividendsOnlyToStakedBalnDay() {
        return dividends_enabled_to_staked_baln_day.getOrDefault(BigInteger.ZERO);
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
        for (int i = 0; i < acceptedTokens.size(); i++) {
            String token = String.valueOf(acceptedTokens.get(i));
            fees.put(token, dailyFees.at(_day).get(token));
        }

        return fees;
    }

    @External(readonly = true)
    public List<Address> getAcceptedTokens() {
        List<Address> add = new ArrayList<>();
        for (int i = 0; i < acceptedTokens.size(); i++) {
            Address token = acceptedTokens.get(i);
            add.add(token);
        }

        return add;
    }

    @External
    public void addAcceptedTokens(Address _token) {
        only(governance);
        isContract(_token);

        if (!check(String.valueOf(_token))) {
            acceptedTokens.add(_token);
        }
    }

    private boolean check(String category) {
        int size = acceptedTokens.size();
        for (int i = 0; i < size; i++) {
            if (category.equals(String.valueOf(acceptedTokens.get(i)))) {
                return true;
            }
        }
        return false;
    }

    @External(readonly = true)
    public List<String> getDividendsCategories() {
        List<String> item = new ArrayList<>();
        for (int i = 0; i < completeDividendsCategories.size(); i++) {
            item.add(completeDividendsCategories.get(i));
        }

        return item;
    }

    @External
    public void addDividendsCategory(String _category) {
        only(admin);
        for (int i = 0; i < completeDividendsCategories.size(); i++) {
            if (_category.equals(completeDividendsCategories.get(i))) {
                Context.revert(TAG + ": " + _category + "is already added.");
            }
        }

        completeDividendsCategories.add(_category);
    }

    @External
    public void removeDividendsCategory(String _category) {
        only(admin);
        BigInteger currentDay = getDay();
        for (int i = 0; i < completeDividendsCategories.size(); i++) {
            if (isExists(_category)) {
                Map<String, BigInteger> dividends_dist = dividendsAt(currentDay);
                if (dividends_dist.get(_category).signum() != 0) {
                    Context.revert(TAG + ": Please make the category percentage to 0 before removing.");
                }
            } else {
                Context.revert(TAG + ": " + _category + " not found in the list of dividends categories.");
            }
        }

        removeFromArrarDb(_category, completeDividendsCategories);
    }

    @External(readonly = true)
    public Map<String, BigInteger> getDividendsPercentage() {
        BigInteger currentDay = (BigInteger) Context.call(loanScore.get(), "getDay");
        return dividendsAt(currentDay);
    }

    @External
    public void setDividendsCategoryPercentage(DistPercentDict[] _dist_list) {
        only(admin);
        BigInteger total_percentage = BigInteger.ZERO;
        if (_dist_list.length != completeDividendsCategories.size()) {
            Context.revert(TAG + ": Categories count mismatched!");
        }

        for (DistPercentDict id : _dist_list) {
            String category = id.category;
            BigInteger precent = id.dist_percent;
            if (isExists(category)) {
                updateDividendsSnapshot(category, precent);
                total_percentage = total_percentage.add(precent);
            } else {
                Context.revert(TAG + category + " is not a valid dividends category");
            }
        }

        if (!total_percentage.equals(pow(BigInteger.TEN, 18))) {
            Context.revert(TAG + ": Total percentage doesn't sum up to 100 i.e. 10**18.");
        }
    }

    private boolean isExists(String category) {
        int size = completeDividendsCategories.size();
        for (int i = 0; i < size; i++) {
            if (category.equals(completeDividendsCategories.get(i))) {
                return true;
            }
        }
        return false;
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

    private void checkForNewDay() {
        if (timeOffset.get() == null) {
            setTimeOffset();
        }
        BigInteger current_snapshot_id = getDay();
        if (snapshotId.getOrDefault(BigInteger.ZERO).compareTo(current_snapshot_id) < 0) {
            snapshotId.set(current_snapshot_id);
        }
    }

    @External
    public void transferDaofundDividends(BigInteger _start, BigInteger _end) {
        Address daofund = daoFund.get();
        Context.require(distributionActivate.getOrDefault(false).equals(true), TAG + ": Distribution is not activated. Can't transfer.");

        int[] value = checkStartEnd(_start.intValue(), _end.intValue());
        int start = value[0];
        int end = value[1];
        Map<String, BigInteger> total_dividends = new HashMap<>();
        for (int i = start; i < end; i++) {
            Map<String, BigInteger> dividends = getDividendsForDaoFund(BigInteger.valueOf(i));
            if (dividends.size() != 0) {
                setClaimed(daofund, BigInteger.valueOf(i));
            }
            total_dividends = addDividends(total_dividends, dividends);
        }

        for (int i = 0; i < acceptedTokens.size(); i++) {
            Address token = acceptedTokens.get(i);
            if (total_dividends.get(String.valueOf(token)).signum() > 0) {
                sendToken(daofund, total_dividends.get(String.valueOf(token)), token, "Daofund dividends");
            }
        }

        Claimed(daofund, BigInteger.valueOf(start), BigInteger.valueOf(end), String.valueOf(total_dividends));
    }

    @External
    public void claim(BigInteger _start, BigInteger _end) {
        Context.require(distributionActivate.getOrDefault(false).equals(true), TAG + ": Distribution is not activated. Can't transfer.");

        int[] value = checkStartEnd(_start.intValue(), _end.intValue());
        int start = value[0];
        int end = value[1];
        Address account = Context.getCaller();
        Map<String, BigInteger> total_dividends = new HashMap<>();

        for (int i = start; i < end; i++) {
            Map<String, BigInteger> dividends = getDividendsForDaoFund(BigInteger.valueOf(i));
            if (dividends.size() != 0) {
                setClaimed(account, BigInteger.valueOf(i));
            }
            total_dividends = addDividends(total_dividends, dividends);
        }

        for (int i = 0; i < acceptedTokens.size(); i++) {
            Address token = acceptedTokens.get(i);
            if (total_dividends.get(String.valueOf(token)).signum() > 0) {
                sendToken(account, total_dividends.get(String.valueOf(token)), token, "User dividends");
            }
        }

        Claimed(account, BigInteger.valueOf(start), BigInteger.valueOf(end), String.valueOf(total_dividends));
    }


    private void sendToken(Address _to, BigInteger amount, Address _token, String msg) {
        Context.call(_token, "transfer", _token, amount);
        FundTransfer(_to, amount, msg + amount + " token sent to" + _to);
    }

    @External
    @SuppressWarnings("unchecked")
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        Address sender = Context.getCaller();
        BigInteger snap_id = snapshotId.getOrDefault(BigInteger.ZERO);
        Map<String, ?> available_tokens;

        for (int i = 0; i < acceptedTokens.size(); i++) {
            if (!sender.equals(acceptedTokens.get(i))) {
                available_tokens = (Map<String, ?>) Context.call(loanScore.get(), "getAssetTokens");
                for (String value : available_tokens.keySet()) {
                    if (String.valueOf(sender).equals(value)) {
                        acceptedTokens.add(sender);
                    }
                }
            }
        }
        checkForNewDay();
        BigInteger val = dailyFees.at(snap_id).getOrDefault(sender.toString(), BigInteger.ZERO);
        dailyFees.at(snap_id).set(sender.toString(), val.add(_value));

        DividendsReceivedV2(_value, snap_id, _value + "tokens received as dividends token: " + sender);
        amountReceivedStatus.set(true);
    }

    @External(readonly = true)
    public Map<String, BigInteger> getUserDividends(Address _account, BigInteger _start, BigInteger _end) {
        int[] value = checkStartEnd(_start.intValue(), _end.intValue());
        int start = value[0];
        int end = value[1];

        Map<String, BigInteger> total_dividends = new HashMap<>();
        for (int i = start; i < end; i++) {
            Map<String, BigInteger> dividends = getDividendsForDay(_account, BigInteger.valueOf(i));
            total_dividends = addDividends(total_dividends, dividends);
        }

        return total_dividends;
    }

    @External(readonly = true)
    public Map<String, BigInteger> getDaoFundDividends(BigInteger _start, BigInteger _end) {
        int[] value = checkStartEnd(_start.intValue(), _end.intValue());
        int start = value[0];
        int end = value[1];

        Map<String, BigInteger> total_dividends = new HashMap<>();
        for (int i = start; i < end; i++) {
            Map<String, BigInteger> dividends = getDividendsForDaoFund(BigInteger.valueOf(i));
            total_dividends = addDividends(total_dividends, dividends);
        }

        return total_dividends;
    }

    private int[] checkStartEnd(int _start, int _end) {
        int batch = dividendsBatchSize.getOrDefault(BigInteger.ZERO).intValue();
        int snap = snapshotId.getOrDefault(BigInteger.ZERO).intValue();
        if (_start == 0 && _end == 0) {
            _end = snap;
            _start = Math.max(1, _end - batch);
        } else if (_end == 0) {
            _end = Math.min(snap, _start + batch);
        } else if (_start == 0) {
            _start = Math.max(1, _end - batch);
        }

        if (!(_start >= 1 && _start < snap)) {
            Context.revert("Invalid value of start provided.");
        }
        if (!(_end > 1 && _end <= snap)) {
            Context.revert("Invalid value of end provided.");
        }
        if (_start >= _end) {
            Context.revert("Start must not be greater than or equal to end.");
        }
        if ((_end - _start) > batch) {
            Context.revert("Maximum allowed range is " + batch);
        }

        int[] list = new int[2];
        list[0] = _start;
        list[1] = _end;

        return list;
    }

    private Map<String, BigInteger> getDividendsForDay(Address _account, BigInteger _day) {
        Boolean claim = isClaimed(_account, _day);
        if (claim.equals(true)) {
            return new HashMap<>();
        }

        Address baln = balnScore.get();
        Address dex = dexScore.get();
        BigInteger staked_baln = (BigInteger) Context.call(baln, "stakedBalanceOfAt", _account, _day);
        BigInteger total_staked_baln = (BigInteger) Context.call(baln, "totalStakedBalanceOfAt", _day);

        BigInteger my_baln_from_pools = BigInteger.ZERO;
        BigInteger total_baln_from_pools = BigInteger.ZERO;

        ArrayList<BigInteger> lis = new ArrayList<>();
        lis.add(BALNBNUSD_ID);
        lis.add(BALNSICX_ID);
        BigInteger dividends_switching_day = dividends_enabled_to_staked_baln_day.getOrDefault(BigInteger.ZERO);

        if (dividends_switching_day.equals(BigInteger.ZERO) || (_day.compareTo(dividends_switching_day) < 0)) {
            for (BigInteger pool_id : lis) {
                BigInteger my_lp = (BigInteger) Context.call(dex, "balanceOfAt", _account, pool_id, _day);
                BigInteger total_lp = (BigInteger) Context.call(dex, "totalSupplyAt", pool_id, _day);
                BigInteger total_baln = (BigInteger) Context.call(dex, "totalBalnAt", pool_id, _day);
                BigInteger equivalent_baln = BigInteger.ZERO;

                if (my_lp.compareTo(BigInteger.ZERO) > 0 && total_lp.compareTo(BigInteger.ZERO) > 0 && total_baln.compareTo(BigInteger.ZERO) > 0) {
                    equivalent_baln = my_lp.multiply(total_baln).divide(total_lp);
                }

                my_baln_from_pools = my_baln_from_pools.add(equivalent_baln);
                total_baln_from_pools = total_baln_from_pools.add(total_baln);
            }
        }

        BigInteger my_total_baln_token = staked_baln.add(my_baln_from_pools);
        BigInteger total_baln_token = total_staked_baln.add(total_baln_from_pools);

        Map<String, BigInteger> my_dividends = new HashMap<>();
        if (my_total_baln_token.compareTo(BigInteger.ZERO) > 0 && total_baln_token.compareTo(BigInteger.ZERO) > 0) {
            Map<String, BigInteger> dividends_distribution = dividendsAt(_day);
            for (int i = 0; i < acceptedTokens.size(); i++) {
                Address token = acceptedTokens.get(i);
                BigInteger numerator = my_total_baln_token.multiply(dividends_distribution.get(BALN_HOLDERS)).
                        multiply(dailyFees.at(_day).getOrDefault(String.valueOf(token), BigInteger.ZERO));
                BigInteger denominator = total_baln_token.multiply(pow(BigInteger.TEN, 18));
                my_dividends.put(String.valueOf(token), numerator.divide(denominator));
            }
        }

        return my_dividends;
    }

    private Map<String, BigInteger> getDividendsForDaoFund(BigInteger _day) {
        Address dao = daoFund.get();
        Boolean claim = isClaimed(dao, _day);
        if (claim.equals(true)) {
            return new HashMap<>();
        }

        Map<String, BigInteger> daoFund_dividends = new HashMap<>();
        for (int i = 0; i < acceptedTokens.size(); i++) {
            Address token = acceptedTokens.get(i);
            Map<String, BigInteger> dividends_dist = dividendsAt(_day);
            BigInteger numerator = dividends_dist.get(DAOFUND).multiply(dailyFees.at(_day).getOrDefault(token.toString(), BigInteger.ZERO));
            BigInteger denominator = pow(BigInteger.TEN, 18);
            daoFund_dividends.put(token.toString(), numerator.divide(denominator));
        }

        return daoFund_dividends;
    }

    private Map<String, BigInteger> addDividends(Map<String, BigInteger> a, Map<String, BigInteger> b) {
        if (a.size() > 0 && b.size() > 0) {
            Map<String, BigInteger> response = new HashMap<>();
            for (int i = 0; i < acceptedTokens.size(); i++) {
                Address token = acceptedTokens.get(i);
                BigInteger a_value = a.get(String.valueOf(token));
                BigInteger b_value = b.get(String.valueOf(token));

                response.put(String.valueOf(token), a_value.add(b_value));
            }

            return response;
        } else if (a.size() > 0) {
            return a;
        } else if (b.size() > 0) {
            return b;
        } else
            return new HashMap<>();
    }

    private void setClaimed(Address _account, BigInteger _day) {
        DictDB<String, BigInteger> claimed_bit_map = Context.newDictDB(CLAIMED_BIT_MAP + _account, BigInteger.class);
        String claimed_word_index = String.valueOf(_day.divide(BigInteger.valueOf(256)));
        BigInteger claimed_bit_index = BigInteger.valueOf(_day.intValue() % 256);
        int bitShift = claimed_bit_index.intValue();

        BigInteger value = claimed_bit_map.getOrDefault(claimed_word_index, BigInteger.ZERO).or(BigInteger.valueOf(1L << bitShift));
        claimed_bit_map.set(claimed_word_index, value);
    }

    private boolean isClaimed(Address _account, BigInteger _day) {
        DictDB<String, BigInteger> claimed_bit_map = Context.newDictDB(CLAIMED_BIT_MAP + _account, BigInteger.class);
        String claimed_word_index = String.valueOf(_day.divide(BigInteger.valueOf(256)));
        BigInteger claimed_bit_index = BigInteger.valueOf(_day.intValue() % 256);

        BigInteger claimed_word = claimed_bit_map.getOrDefault(claimed_word_index, BigInteger.ZERO);
        int bitShift = claimed_bit_index.intValue();
        BigInteger mask = BigInteger.valueOf(1L << bitShift);

        return (claimed_word.and(mask).equals(mask));
    }

    private void updateDividendsSnapshot(String _category, BigInteger _percent) {
        BigInteger currentDay = getDay();
        BigInteger total_snapshots_taken = totalSnapshots.getOrDefault(_category, BigInteger.ZERO);
        BigInteger value = snapshotDividends.at(_category).at(total_snapshots_taken.subtract(BigInteger.ONE)).get("ids");

        if (total_snapshots_taken.signum() > 0 && value.equals(currentDay)) {
            snapshotDividends.at(_category).at(total_snapshots_taken.subtract(BigInteger.ONE)).set("amount", _percent);
        } else {
            snapshotDividends.at(_category).at(total_snapshots_taken).set("ids", currentDay);
            snapshotDividends.at(_category).at(total_snapshots_taken).set("amount", _percent);
            totalSnapshots.set(_category, total_snapshots_taken.add(BigInteger.ONE));
        }
    }

    @External(readonly = true)
    @SuppressWarnings("unchecked")
    public Map<String, BigInteger> dividendsAt(BigInteger _day) {
        Context.require(_day.signum() >= 0, TAG + "IRC2Snapshot: day:" + _day + " must be equal to or greater then Zero.");
        Map<String, BigInteger> dividends_dist = new HashMap<>();

        for (int i = 0; i < completeDividendsCategories.size(); i++) {
            String _category = completeDividendsCategories.get(i);
            BigInteger total_snapshots_taken = totalSnapshots.getOrDefault(_category, BigInteger.ZERO);
            if (total_snapshots_taken.signum() == 0) {
                dividends_dist.put(_category, dividendsPercentage.get(_category));
                continue;
            }
            if (snapshotDividends.at(_category).at(total_snapshots_taken.subtract(BigInteger.ONE)).getOrDefault("ids", BigInteger.ZERO).compareTo(_day) < 1) {
                dividends_dist.put(_category, snapshotDividends.at(_category).at(total_snapshots_taken.subtract(BigInteger.ONE)).get("amount"));
                continue;
            }
            if (snapshotDividends.at(_category).at(BigInteger.ZERO).get("ids").compareTo(_day) > 0) {
                dividends_dist.put(_category, dividendsPercentage.get(_category));
                continue;
            }

            BigInteger low = BigInteger.ZERO;
            BigInteger high = total_snapshots_taken.subtract(BigInteger.ONE);
            while (high.signum() > low.signum()) {
                BigInteger mid = high.subtract(high.subtract(low)).divide(BigInteger.TWO);
                Map<String, BigInteger> mid_value = (Map<String, BigInteger>) snapshotDividends.at(_category).at(mid);
                if (mid_value.get("ids").equals(_day)) {
                    dividends_dist.put(_category, mid_value.get("amount"));
                } else if (mid_value.get("ids").compareTo(_day) < 0) {
                    low = mid;
                } else
                    high = mid.subtract(BigInteger.ONE);
            }

            dividends_dist.put(_category, snapshotDividends.at(_category).at(low).get("amount"));
        }

        for (String key : dividends_dist.keySet()) {
            BigInteger value = dividends_dist.get(key);
            if (!value.equals(BigInteger.ZERO))
                dividends_dist.put(key, value);
        }

        return dividends_dist;
    }

//    EVENT LOGS

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
