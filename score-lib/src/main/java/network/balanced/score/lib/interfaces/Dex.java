package network.balanced.score.lib.interfaces;

import foundation.icon.score.client.ScoreInterface;
import network.balanced.score.lib.interfaces.addresses.*;
import network.balanced.score.lib.interfaces.base.Name;
import score.annotation.External;

import java.math.BigInteger;

@ScoreInterface
public interface Dex extends AdminAddress, BnusdAddress, DexAddress, GovernanceAddress, LoansAddress,
        Name, SicxAddress {

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
}

