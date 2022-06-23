package network.balanced.score.lib.interfaces;

import java.math.BigInteger;

import foundation.icon.score.client.ScoreInterface;
import score.Address;
import score.annotation.External;

@ScoreInterface
public interface FeeHandler {
    @External
    void setAcceptedDividendTokens(Address[] _tokens);
   
    @External
    void setRoute(Address _fromToken, Address _toToken, Address[] _path);
 
    @External
    void deleteRoute(Address _fromToken, Address _toToken);
        
    @External
    void setFeeProcessingInterval(BigInteger _interval);
    
    @External
    void enable();

    @External
    void disable();

    @External
    void setFeeProcessingInterval();

    @External(readonly = true)
    int getFeeProcessingInterval();
}
