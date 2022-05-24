package network.balanced.score.lib.structs;

import java.math.BigInteger;

import score.Address;

public class UnstakeDetails {
    public BigInteger nodeId;
    public BigInteger unstakeAmount;
    public Address key;
    public BigInteger unstakeBlockHeight;
    public Address receiverAddress;
}
