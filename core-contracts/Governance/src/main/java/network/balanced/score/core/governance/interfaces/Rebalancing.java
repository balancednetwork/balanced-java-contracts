package network.balanced.score.core.governance.interfaces;

import java.math.BigInteger;

import foundation.icon.score.client.ScoreInterface;

@ScoreInterface
public interface Rebalancing extends Setter {
    void setPriceDiffThreshold(BigInteger _value);
    
}
