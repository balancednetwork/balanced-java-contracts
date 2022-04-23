package network.balanced.score.core.governance;

import java.math.BigInteger;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import network.balanced.score.lib.structs.Disbursement;
import network.balanced.score.lib.structs.DistributionPercentage;
import score.Address;

public class VoteActions {
     public static void execute(GovernanceImpl gov, String method, JsonObject params) {
          switch (method) {
               case "enableDividends":
                    gov.enableDividends();
               case "addNewDataSource":
                    gov.addNewDataSource(params.get("_data_source_name").asString(), params.get("_contract_address").asString());
               case "updateBalTokenDistPercentage":
                    gov.updateBalTokenDistPercentage(parseDistPercentage(params.get("_recipient_list").asArray()));
               case "setMiningRatio":
                    gov.setMiningRatio(BigInteger.valueOf(params.get("_value").asInt()));
               case "setLockingRatio":
                    gov.setLockingRatio(BigInteger.valueOf(params.get("_value").asInt()));
               case "setOriginationFee":
                    gov.setOriginationFee(BigInteger.valueOf(params.get("_fee").asInt()));
               case "setLiquidationRatio":
                    gov.setLiquidationRatio(BigInteger.valueOf(params.get("_ratio").asInt()));
               case "setRetirementBonus":
                    gov.setRetirementBonus(BigInteger.valueOf(params.get("_points").asInt()));
               case "setLiquidationReward":
                    gov.setLiquidationReward(BigInteger.valueOf(params.get("_points").asInt()));
               case "setMaxRetirePercent":
                    gov.setMaxRetirePercent(BigInteger.valueOf(params.get("_value").asInt()));
               case "setRebalancingThreshold":
                    gov.setRebalancingThreshold(BigInteger.valueOf(params.get("_value").asInt()));
               case "setVoteDuration":
                    gov.setVoteDuration(BigInteger.valueOf(params.get("_duration").asInt()));
               case "setQuorum":
                    gov.setQuorum(BigInteger.valueOf(params.get("quorum").asInt()));
               case "setVoteDefinitionFee":
                    gov.setVoteDefinitionFee(BigInteger.valueOf(params.get("fee").asInt()));
               case "setBalnVoteDefinitionCriterion":
                    gov.setBalnVoteDefinitionCriterion(BigInteger.valueOf(params.get("percentage").asInt()));
               case "setDividendsCategoryPercentage":
                    gov.setDividendsCategoryPercentage(parseDistPercentage(params.get("_dist_list").asArray()));
               case "daoDisburse":
                    gov.daoDisburse(params.get("_recipient").asString(), parseDisbursement(params.get("_amounts").asArray()));
               case "addAcceptedTokens":
                    gov.addAcceptedTokens(params.get("_token").asString());
        }
    }

    private static DistributionPercentage[] parseDistPercentage(JsonArray jsonDistributions) {
          DistributionPercentage[] distPercentages = new DistributionPercentage[jsonDistributions.size()];  
          for (int i = 0; i < jsonDistributions.size(); i++) {
               JsonObject jsonDist = jsonDistributions.get(i).asObject();
               DistributionPercentage dist = new DistributionPercentage();
               dist.recipient_name = jsonDist.get("recipient_name").asString();
               dist.dist_percent = new BigInteger(jsonDist.get("dist_percent").asString(), 10);
               distPercentages[i] = dist;
          }

          return distPercentages;
    }

    private static Disbursement[] parseDisbursement(JsonArray jsonDisbursement) {
          Disbursement[] disbursements = new Disbursement[jsonDisbursement.size()];  
          for (int i = 0; i < jsonDisbursement.size(); i++) {
               JsonObject jsonDisb = jsonDisbursement.get(i).asObject();
               Disbursement disb = new Disbursement();
               disb.address = Address.fromString(jsonDisb.get("address").asString());
               disb.amount = BigInteger.valueOf(jsonDisb.get("amount").asInt());
               disbursements[i] = disb;
          }

          return disbursements;
}
}
