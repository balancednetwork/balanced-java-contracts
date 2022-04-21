package network.balanced.score.core.governance;

import java.math.BigInteger;

public class VoteActions {
    public static void execute(GovernanceImpl gov, String method, Object... params) {
        switch (method) {
            case "enableDividends":
                 gov.enableDividends();
            case "addNewDataSource":
                 gov.addNewDataSource((String) params[0], (String)params[1]);
            // case "updateBalTokenDistPercentage":
            //      gov.updateBalTokenDistPercentage((BigInteger) params[0]);
            case "setMiningRatio":
                 gov.setMiningRatio((BigInteger) params[0]);
            case "setLockingRatio":
                 gov.setLockingRatio((BigInteger) params[0]);
            case "setOriginationFee":
                 gov.setOriginationFee((BigInteger) params[0]);
            case "setLiquidationRatio":
                 gov.setLiquidationRatio((BigInteger) params[0]);
            case "setRetirementBonus":
                 gov.setRetirementBonus((BigInteger) params[0]);
            case "setLiquidationReward":
                 gov.setLiquidationReward((BigInteger) params[0]);
            case "setMaxRetirePercent":
                 gov.setMaxRetirePercent((BigInteger) params[0]);
            case "setRebalancingThreshold":
                 gov.setRebalancingThreshold((BigInteger) params[0]);
            case "setVoteDuration":
                 gov.setVoteDuration((BigInteger) params[0]);
            case "setQuorum":
                 gov.setQuorum((BigInteger) params[0]);
            case "setVoteDefinitionFee":
                 gov.setVoteDefinitionFee((BigInteger) params[0]);
            case "setBalnVoteDefinitionCriterion":
                 gov.setBalnVoteDefinitionCriterion((BigInteger) params[0]);
            // case "setDividendsCategoryPercentage":
            //      gov.setDividendsCategoryPercentage((DistributionPercentage[]) params[0]);
            // case "daoDisburse":
            //      gov.daoDisburse((BigInteger) params[0]);
            case "addAcceptedTokens":
                 gov.addAcceptedTokens((String) params[0]);
        }
    }
}
