package network.balanced.score.core.governance.interfaces;

import java.math.BigInteger;

import foundation.icon.score.client.ScoreInterface;

@ScoreInterface
public interface StakedLp extends Setter {    
    void addPool(BigInteger _id);
}