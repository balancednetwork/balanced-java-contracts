package network.balanced.score.lib.interfaces;

import score.Address;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;
import java.util.Map;

public interface Dex {

    @External
    String name();

    @External
    void setTimeOffset(BigInteger _delta_time);

    @External(readonly = true)
    BigInteger getDay();

    @External(readonly = true)
    BigInteger getTimeOffset();

    @External
    BigInteger getQuotePriceInBase(BigInteger _id);

    @External(readonly = true)
    BigInteger getBasePriceInQuote(BigInteger _id);

    @External(readonly = true)
    BigInteger getPrice(BigInteger _id);

    @External(readonly = true)
    BigInteger getICXBalance(Address _address);

    @External(readonly = true)
    Map<String, Object> getPoolStats(BigInteger _id);

    @External(readonly = true)
    Map<String, BigInteger> getFees();

    @External
    void add(Address _baseToken, Address _quoteToken, BigInteger _baseValue, BigInteger _quoteValue,
             @Optional boolean _withdraw_unused);


    @External(readonly = true)
    BigInteger balanceOf(Address _owner, BigInteger _id);

    @External(readonly = true)
    BigInteger getPoolId(Address _token1Address, Address _token2Address);
}
