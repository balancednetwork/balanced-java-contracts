package network.balanced.score.core.governance.interfaces;

import score.Address;

import java.math.BigInteger;

import foundation.icon.score.client.ScoreInterface;

@ScoreInterface
public interface Asset {
    BigInteger balanceOf(Address _owner);
        
    void transfer(Address _to, BigInteger value, byte[] _data);
        
    void setOracleName(String _name);
        
    void setMinInterval(BigInteger _interval);

    BigInteger priceInLoop();
}
