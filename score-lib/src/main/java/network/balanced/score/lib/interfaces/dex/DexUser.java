package network.balanced.score.lib.interfaces.dex;

import score.Address;
import score.annotation.External;

import java.math.BigInteger;

public interface DexUser {

    @External
    void addQuoteCoin(Address _address);

    @score.annotation.Payable
    void fallback();

    @External
    void cancelSicxicxOrder();

    @External
    void tokenFallback(Address _from, BigInteger _value, byte[] _data);

    @External
    void transfer(Address _to, BigInteger _value, BigInteger _id, @score.annotation.Optional byte[] _data);


    @External
    void onIRC31Received(Address _operator, Address _from,
                         BigInteger _id, BigInteger _value,
                         byte[] _data);

    @External
    void withdraw(Address _token, BigInteger _value) ;

    @External
    void remove(BigInteger _id, BigInteger _value, boolean _withdraw);

    @External
    void add(Address _baseToken, Address _quoteToken, BigInteger _baseValue, BigInteger _quoteValue,
             @score.annotation.Optional boolean _withdraw_unused);

    @External
    void withdrawSicxEarnings(@score.annotation.Optional BigInteger _value);

}
