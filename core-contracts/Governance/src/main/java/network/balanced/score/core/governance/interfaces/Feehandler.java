package network.balanced.score.core.governance.interfaces;

import score.Address;

import java.math.BigInteger;

import foundation.icon.score.client.ScoreInterface;

@ScoreInterface
public interface Feehandler extends Setter{
    void setAcceptedDividendTokens(Address[] _tokens);
        
    void setRoute(Address _fromToken, Address _toToken, Address[] _path);
        
    void deleteRoute(Address _fromToken, Address _toToken);
        
    void setFeeProcessingInterval(BigInteger _interval);
                
    void enable();

    void disable();
}
