package network.balanced.score.core.governance.interfaces;

import java.math.BigInteger;

import foundation.icon.score.client.ScoreInterface;
import score.Address;

@ScoreInterface
public interface Sicx extends Setter{
    BigInteger balanceOf(Address _owner);
        
    void transfer(Address _to, BigInteger value, byte[] _data);
        
    void setOracleName(String _name);
        
    void setMinInterval(BigInteger _interval);

    BigInteger priceInLoop();
}