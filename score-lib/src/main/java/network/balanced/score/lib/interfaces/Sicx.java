package network.balanced.score.lib.interfaces;

import network.balanced.score.lib.interfaces.addresses.AdminAddress;
import network.balanced.score.lib.interfaces.addresses.StakingAddress;
import score.annotation.External;

import java.math.BigInteger;

public interface Sicx extends StakingAddress{

    @External(readonly = true)
    String getPeg();

    @External(readonly = true)
    BigInteger priceInLoop();

    @External(readonly = true)
    BigInteger lastPriceInLoop();
}
