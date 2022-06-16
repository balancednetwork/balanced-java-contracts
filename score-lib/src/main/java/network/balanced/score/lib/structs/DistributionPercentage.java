package network.balanced.score.lib.structs;

import java.math.BigInteger;

import score.annotation.Keep;

public class DistributionPercentage {
    @Keep
    public String recipient_name;

    @Keep
    public BigInteger dist_percent;
}
