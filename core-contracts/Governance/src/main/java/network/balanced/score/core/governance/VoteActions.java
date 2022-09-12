/*
 * Copyright (c) 2022 Balanced.network.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package network.balanced.score.core.governance;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import network.balanced.score.lib.structs.Disbursement;
import network.balanced.score.lib.structs.DistributionPercentage;
import score.Address;
import score.Context;
import scorex.util.ArrayList;

import java.util.List;

import static network.balanced.score.lib.utils.Math.convertToNumber;

public class VoteActions {
    public static void execute(GovernanceImpl gov, String method, JsonObject params) {
        switch (method) {
            case "enableDividends":
                gov.enableDividends();
                break;
            case "addNewDataSource":
                gov._addNewDataSource(params.get("_data_source_name").asString(),
                        params.get("_contract_address").asString());
                break;
            case "updateBalTokenDistPercentage":
                gov._updateBalTokenDistPercentage(parseDistPercentage(params.get("_recipient_list").asArray()));
                break;
            case "setLockingRatio":
                gov._setLockingRatio(params.get("_symbol").asString(), convertToNumber(params.get("_value")));
                break;
            case "setOriginationFee":
                gov.setOriginationFee(convertToNumber(params.get("_fee")));
                break;
            case "setLiquidationRatio":
                gov._setLiquidationRatio(params.get("_symbol").asString(), convertToNumber(params.get("_ratio")));
                break;
            case "setRetirementBonus":
                gov.setRetirementBonus(convertToNumber(params.get("_points")));
                break;
            case "setLiquidationReward":
                gov.setLiquidationReward(convertToNumber(params.get("_points")));
                break;
            case "setMaxRetirePercent":
                gov._setMaxRetirePercent(convertToNumber(params.get("_value")));
                break;
            case "setRebalancingThreshold":
                gov._setRebalancingThreshold(convertToNumber(params.get("_value")));
                break;
            case "setVoteDurationLimits":
                gov._setVoteDurationLimits(convertToNumber(params.get("_min")), convertToNumber(params.get("_max")));
                break;
            case "setQuorum":
                gov._setQuorum(convertToNumber(params.get("quorum")));
                break;
            case "setVoteDefinitionFee":
                gov._setVoteDefinitionFee(convertToNumber(params.get("fee")));
                break;
            case "setBalnVoteDefinitionCriterion":
                gov._setBalnVoteDefinitionCriterion(convertToNumber(params.get("percentage")));
                break;
            case "setDividendsCategoryPercentage":
                gov.setDividendsCategoryPercentage(parseDistPercentage(params.get("_dist_list").asArray()));
                break;
            case "daoDisburse":
                gov.daoDisburse(params.get("_recipient").asString(),
                        parseDisbursement(params.get("_amounts").asArray()));
                break;
            case "addAcceptedTokens":
                gov._addAcceptedTokens(params.get("_token").asString());
                break;
            case "addCollateral":
                gov._addCollateral(Address.fromString(params.get("_token_address").asString()),
                        params.get("_active").asBoolean(),
                        params.get("_peg").asString(),
                        convertToNumber(params.get("_lockingRatio")),
                        convertToNumber(params.get("_liquidationRatio")),
                        convertToNumber(params.get("_debtCeiling")));
                break;
            case "addDexPricedCollateral":
                gov._addDexPricedCollateral(Address.fromString(params.get("_token_address").asString()),
                        params.get("_active").asBoolean(),
                        convertToNumber(params.get("_lockingRatio")),
                        convertToNumber(params.get("_liquidationRatio")),
                        convertToNumber(params.get("_debtCeiling")));
                break;
            case "call":
                Address address = Address.fromString(params.get("contract_address").asString());
                GovernanceImpl.call(address, params.get("method").asString(),
                        parseVarArgs(params.get("parameters").asArray()));
                break;

            default:
                Context.require(false, "Method " + method + " does not exist.");
        }
    }

    private static Object[] parseVarArgs(JsonArray parameters) {
        List<Object> varArgs = new ArrayList<>(parameters.size());
        for (int i = 0; i < parameters.size(); i++) {
            JsonObject jsonParameter = parameters.get(i).asObject();
            String type = jsonParameter.get("type").asString();
            JsonValue value = jsonParameter.get("value");
            switch (type) {
                case "String":
                    varArgs.add(value.asString());
                    break;
                case "Address":
                    varArgs.add(Address.fromString(value.asString()));
                    break;
                case "Number":
                    varArgs.add(convertToNumber(value));
                    break;
                case "Boolean":
                    varArgs.add(value.asBoolean());
                    break;
            }
        }

        return varArgs.toArray();
    }

    private static DistributionPercentage[] parseDistPercentage(JsonArray jsonDistributions) {
        DistributionPercentage[] distPercentages = new DistributionPercentage[jsonDistributions.size()];
        for (int i = 0; i < jsonDistributions.size(); i++) {
            JsonObject jsonDist = jsonDistributions.get(i).asObject();
            DistributionPercentage dist = new DistributionPercentage();
            dist.recipient_name = jsonDist.get("recipient_name").asString();
            dist.dist_percent = convertToNumber(jsonDist.get("dist_percent"));
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
            disb.amount = convertToNumber(jsonDisb.get("amount"));
            disbursements[i] = disb;
        }

        return disbursements;
    }
}
