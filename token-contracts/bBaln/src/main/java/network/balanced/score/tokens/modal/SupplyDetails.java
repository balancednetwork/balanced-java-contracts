package network.balanced.score.tokens.modal;

import score.annotation.Keep;

import java.math.BigInteger;

public class SupplyDetails {

    @Keep
    public BigInteger decimals;
    @Keep
    public BigInteger principalUserBalance;
    @Keep
    public BigInteger principalTotalSupply;

}
