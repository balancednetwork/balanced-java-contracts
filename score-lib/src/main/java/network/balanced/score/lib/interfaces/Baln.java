package network.balanced.score.lib.interfaces;

import score.Address;
import score.annotation.External;

import java.math.BigInteger;

import foundation.icon.score.client.ScoreInterface;

@ScoreInterface
public interface Baln {
    @External
    BigInteger totalSupply();

    @External
    void stake(BigInteger _value);
        
    @External(readonly = true)
    BigInteger totalStakedBalance();
        
    @External(readonly = true)
    BigInteger balanceOf(Address _owner ); 
        
    @External(readonly = true)
    BigInteger stakedBalanceOf(Address _owner ); 
        
    @External(readonly = true)
    BigInteger stakedBalanceOfAt(Address _account, BigInteger _day ); 
        
    @External(readonly = true)
    BigInteger totalStakedBalanceOfAt(BigInteger _day); 
        
    @External
    void transfer(Address _to, BigInteger _value , byte[] _data);
        
    @External
    void setOracle(Address _address); 
        
    @External
    void setOracleName(String _name); 
        
    @External
    void toggleStakingEnabled(); 
        
    @External
    void setMinimumStake(BigInteger _amount); 
        
    @External
    void setUnstakingPeriod(BigInteger _time); 
        
    @External
    void setMinInterval(BigInteger _interval); 
                
}
