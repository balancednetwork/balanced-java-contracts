package network.balanced.score.lib.interfaces;

import network.balanced.score.lib.interfaces.addresses.SicxAddress;
import network.balanced.score.lib.interfaces.addresses.StakingAddress;
import network.balanced.score.lib.interfaces.base.IRC2;
import score.Address;
import score.annotation.External;

import java.math.BigInteger;

public interface Sicx extends SicxAddress, IRC2 {


    @External(readonly = true)
    String getPeg();

    @External(readonly = true)
    BigInteger priceInLoop();

    @External(readonly = true)
    BigInteger lastPriceInLoop();

    @External(readonly = true)
    Address getStakingAddress();

    @External(readonly = true)
    Address getAdmin();

}
