package network.balanced.score.lib.interfaces;

import score.Address;
import score.annotation.Optional;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

public interface BoostedBaln {

    void setMinimumLockingAmount(BigInteger value);

    BigInteger getMinimumLockingAmount();

    void setPenaltyAddress(Address penaltyAddress);

    void commitTransferOwnership(Address address);

    void applyTransferOwnership();

    Map<String, BigInteger> getLocked(Address _owner);

    BigInteger getTotalLocked();

    List<Address> getUsers(int start, int end);


    BigInteger getLastUserSlope(Address address);


    BigInteger userPointHistoryTimestamp(Address address, BigInteger index);


    BigInteger lockedEnd(Address address);


    void checkpoint();


    void tokenFallback(Address _from, BigInteger _value, byte[] _data);


    void increaseUnlockTime(BigInteger unlockTime);


    void withdraw();


    BigInteger balanceOf(Address address, @Optional BigInteger timestamp);


    BigInteger balanceOfAt(Address address, BigInteger block);


    BigInteger totalSupply(@Optional BigInteger time);


    BigInteger totalSupplyAt(BigInteger block);


    Address admin();


    Address futureAdmin();


    String name();


    String symbol();


    BigInteger userPointEpoch(Address address);


    int decimals();
}
