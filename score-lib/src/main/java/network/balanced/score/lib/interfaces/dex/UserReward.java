package network.balanced.score.lib.interfaces.dex;

import score.annotation.External;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

public interface UserReward {

    @External
    void distribute();

    @External(readonly = true)
    List<String> getDataSourceNames();

    @External(readonly = true)
    BigInteger getBalnHolding(String _holder);

    @External(readonly = true)
    Map<Object, Object> recipientAt(BigInteger _day);



}
