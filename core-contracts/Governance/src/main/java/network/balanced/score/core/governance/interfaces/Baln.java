package network.balanced.score.core.governance.interfaces;

import score.Address;

import java.math.BigInteger;

import foundation.icon.score.client.ScoreInterface;

@ScoreInterface
public interface Baln extends Setter{
    BigInteger totalSupply();
        
    BigInteger totalStakedBalance();
        
    BigInteger balanceOf(Address _owner ); 
        
    BigInteger stakedBalanceOf(Address _owner ); 
        
    BigInteger stakedBalanceOfAt(Address _account, BigInteger _day ); 
        
    BigInteger totalStakedBalanceOfAt(BigInteger _day); 
        
    void transfer(Address _to, BigInteger _value , byte[] _data);
        
    void setOracle(Address _address); 
        
    void setOracleName(String _name); 
        
    void toggleStakingEnabled(); 
        
    void setMinimumStake(BigInteger _amount); 
        
    void setUnstakingPeriod(BigInteger _time); 
        
    void setMinInterval(BigInteger _interval); 
                
}
