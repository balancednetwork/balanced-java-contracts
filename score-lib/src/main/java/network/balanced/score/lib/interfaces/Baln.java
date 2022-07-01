package network.balanced.score.lib.interfaces;

import score.Address;

import java.math.BigInteger;
import java.util.Map;

import network.balanced.score.lib.interfaces.addresses.*;

import foundation.icon.score.client.ScoreInterface;

@ScoreInterface
public interface Baln extends
        AdminAddress,
        DividendsAddress,
        BnusdAddress,
        DexAddress {

    BigInteger totalSupply();

    void stake(BigInteger _value);
        
    BigInteger totalStakedBalance();
        
    BigInteger balanceOf(Address _owner ); 
        
    BigInteger stakedBalanceOf(Address _owner ); 
        
    BigInteger stakedBalanceOfAt(Address _account, BigInteger _day ); 
        
    BigInteger totalStakedBalanceOfAt(BigInteger _day); 
        
    void transfer(Address _to, BigInteger _value , byte[] _data);
        
    void setOracle(Address _address); 
        
    void setOracleName(String _name); 

    void setTimeOffset();
        
    void toggleStakingEnabled(); 
        
    void toggleEnableSnapshot();

    void setMinimumStake(BigInteger _amount); 
        
    void setUnstakingPeriod(BigInteger _time); 
        
    void setMinInterval(BigInteger _interval);

    BigInteger availableBalanceOf(Address _owner);
    Map<String, BigInteger> detailsBalanceOf(Address _owner);
}
