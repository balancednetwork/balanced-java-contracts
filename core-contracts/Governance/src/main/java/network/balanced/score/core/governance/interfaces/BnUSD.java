package network.balanced.score.core.governance.interfaces;

import score.Address;

import java.math.BigInteger;

import foundation.icon.score.client.ScoreInterface;

@ScoreInterface
public interface BnUSD extends Asset, Setter {
    void govTransfer(Address _from, Address _to, BigInteger _value, byte[] _data);
}