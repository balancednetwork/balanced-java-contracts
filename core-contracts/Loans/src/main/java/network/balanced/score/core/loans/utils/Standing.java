package network.balanced.score.core.loans.utils;

import java.math.BigInteger;
import java.util.Map;

public class Standing {
    public BigInteger totalDebt;
    public BigInteger collateral;
    public BigInteger ratio;
    public Constants.Standings standing;

    public Map<String, Object> toMap() {
        return Map.of(
            "collateral", collateral,
            "debt", totalDebt,
            "ratio", ratio,
            "standing", Constants.StandingsMap.get(standing)
        );
    }
}