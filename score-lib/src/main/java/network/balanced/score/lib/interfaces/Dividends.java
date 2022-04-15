package network.balanced.score.lib.interfaces;

import foundation.icon.score.client.ScoreInterface;
import network.balanced.score.lib.interfaces.addresses.*;
import network.balanced.score.lib.interfaces.base.Name;
import network.balanced.score.lib.interfaces.base.TokenFallback;
import network.balanced.score.lib.structs.DistPercentDict;
import score.Address;
import score.annotation.External;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@ScoreInterface
public interface Dividends extends AdminAddress, GovernanceAddress, LoansAddress, DaoFundAddress, BalnAddress,
        Name, DexAddress, TokenFallback {

    @External(readonly = true)
    boolean getDistributionActivationStatus();

    @External
    void setDistributionActivationStatus(boolean _status);

    @External
    void setDividendsOnlyToStakedBalnDay(BigInteger day);

    @External(readonly = true)
    BigInteger getDividendsOnlyToStakedBalnDay();

    @External(readonly = true)
    Map<String, BigInteger> getBalances();

    @External(readonly = true)
    Map<String, BigInteger> getDailyFees(BigInteger _day);

    @External(readonly = true)
    List<Address> getAcceptedTokens();

    @External
    void addAcceptedTokens(Address _token);

    @External(readonly = true)
    List<String> getDividendsCategories();

    @External
    void addDividendsCategory(String _category);

    @External
    void removeDividendsCategory(String _category);

    @External(readonly = true)
    Map<String, BigInteger> getDividendsPercentage();

    @External
    void setDividendsCategoryPercentage(DistPercentDict[] _dist_list);

    @External(readonly = true)
    BigInteger getDividendsBatchSize();

    @External
    void setDividendsBatchSize(BigInteger _size);

    @External(readonly = true)
    BigInteger getSnapshotId();

    @External(readonly = true)
    BigInteger getDay();

    @External(readonly = true)
    BigInteger getTimeOffset();

    @External
    boolean distribute();

    @External
    void transferDaofundDividends(BigInteger _start, BigInteger _end);

    @External
    void claim(BigInteger _start, BigInteger _end);

    @External(readonly = true)
    Map<String, BigInteger> getUserDividends(Address _account, BigInteger _start, BigInteger _end);

    @External(readonly = true)
    Map<String, BigInteger> getDaoFundDividends(BigInteger _start, BigInteger _end);

    @External(readonly = true)
    Map<String, BigInteger> dividendsAt(BigInteger _day);

}
