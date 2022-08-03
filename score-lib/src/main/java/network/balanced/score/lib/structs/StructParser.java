package network.balanced.score.lib.structs;
import com.eclipsesource.json.JsonObject;

import static network.balanced.score.lib.utils.Math.convertToNumber;

import score.Address;

public class StructParser {
    public static DistributionPercentage parseDistributionPercentage(JsonObject value) {
        DistributionPercentage dist = new DistributionPercentage();
        JsonObject jsonDist = value.asObject();
        dist.recipient_name = jsonDist.get("recipient_name").asString();
        dist.dist_percent = convertToNumber(jsonDist.get("dist_percent"));

        return dist;
    }

    public static Disbursement parseDisbursement(JsonObject value) {
        JsonObject jsonDisbursement = value.asObject();
        Disbursement disbursement = new Disbursement();
        disbursement.address = Address.fromString(jsonDisbursement.get("address").asString());
        disbursement.amount = convertToNumber(jsonDisbursement.get("amount"));
        
        return disbursement;
    }

    public static PrepDelegations parsePrepDelegations(JsonObject value) {
        JsonObject jsonDelegation = value.asObject();
        PrepDelegations prepDelegation = new PrepDelegations();
        prepDelegation._address = Address.fromString(jsonDelegation.get("_address").asString());
        prepDelegation._votes_in_per = convertToNumber(jsonDelegation.get("_votes_in_per"));
        
        return prepDelegation;
    }
}
