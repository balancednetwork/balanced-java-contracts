package network.balanced.score.core.loans.utils;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.loans.utils.LoansConstants.StandingsMap;
import static network.balanced.score.core.loans.utils.LoansConstants.Standings;

public class Standing {
    public BigInteger totalDebt;
    public BigInteger collateral;
    public BigInteger ratio;
    public Standings standing;

    public Map<String, Object> toMap() {
        return Map.of(
            "collateral", collateral,
            "debt", totalDebt,
            "ratio", ratio,
            "standing", StandingsMap.get(standing)
        );
    }
}