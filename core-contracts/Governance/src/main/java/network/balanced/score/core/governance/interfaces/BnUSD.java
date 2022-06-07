package network.balanced.score.core.governance.interfaces;

import score.Address;

import java.math.BigInteger;

import foundation.icon.score.client.ScoreInterface;

@ScoreInterface
public interface BnUSD {
    void govTransfer(Address _from, Address _to, BigInteger _value, byte[] _data);
    
    BigInteger balanceOf(Address _owner);
        
    void transfer(Address _to, BigInteger value, byte[] _data);
        
    void setOracleName(String _name);
    
    void setOracle(Address _address);
        
    void setMinInterval(BigInteger _interval);

    BigInteger priceInLoop();
}