package network.balanced.score.core.governance.interfaces;

import score.Address;

import java.math.BigInteger;

import foundation.icon.score.client.ScoreInterface;

@ScoreInterface
public interface Dex extends Setter{
    void add(Address _baseToken, Address _quoteToken, BigInteger _baseValue, BigInteger _quoteValue ,boolean _withdraw_unused);
        
    void setPoolLpFee(BigInteger _value);
        
    void setPoolBalnFee(BigInteger _value);
        
    void setIcxBalnFee(BigInteger _value);
        
    void setIcxConversionFee(BigInteger _value);
        
    void setTimeOffset(BigInteger _time_delta);
        
    void turnDexOn();
        
    void permit(BigInteger id, boolean _permission);
        
    void setMarketName(BigInteger _id , String _name);
        
    BigInteger getPoolId(Address _token1Address , Address _token2Address ); 
        
    void addQuoteCoin(Address _address);
        
    void addLpAddresses(BigInteger _poolId , Address[] _addresses);
        
    BigInteger balanceOfAt(Address _account , BigInteger _id ,BigInteger _snapshot_id , boolean _twa); 
        
    BigInteger totalSupplyAt(BigInteger _id , BigInteger _snapshot_id ,  boolean _twa); 
        
    BigInteger totalBalnAt(BigInteger _id, BigInteger _snapshot_id , boolean _twa); 
        
    void setContinuousRewardsDay(BigInteger _day );
}
