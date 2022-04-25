package network.balanced.score.lib.interfaces;
import network.balanced.score.lib.interfaces.addresses.StakingAddress;
import network.balanced.score.lib.interfaces.base.IRC2BurnableInterface;
import score.annotation.External;

import java.math.BigInteger;

public interface Sicx extends StakingAddress, IRC2BurnableInterface {

    @External(readonly = true)
    String getPeg();

    @External(readonly = true)
    BigInteger priceInLoop();

    @External(readonly = true)
    BigInteger lastPriceInLoop();
}
