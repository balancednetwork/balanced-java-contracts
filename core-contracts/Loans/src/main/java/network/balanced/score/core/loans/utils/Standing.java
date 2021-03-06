package network.balanced.score.core.loans.utils;

import java.math.BigInteger;
import java.util.Map;
import scorex.util.HashMap;

import static network.balanced.score.core.loans.utils.LoansConstants.StandingsMap;
import static network.balanced.score.core.loans.utils.LoansConstants.Standings;

public class Standing {
    public BigInteger totalDebt;
    public BigInteger collateral;
    public BigInteger ratio;
    public Standings standing;

    public Map<String, Object> toMap() {
        Map<String, Object> standingData = new HashMap<>();
        standingData.put("collateral", collateral);
        standingData.put("debt", totalDebt);
        standingData.put("ratio", ratio);
        standingData.put("standing", StandingsMap.get(standing));

        return standingData;
    }
}