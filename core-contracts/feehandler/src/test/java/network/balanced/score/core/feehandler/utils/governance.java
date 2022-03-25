package network.balanced.score.core.feehandler.utils;

import com.iconloop.score.test.Account;
import score.Address;

public class governance {
    public static final Account baln = Account.newScoreAccount(2);
    public static final Account router = Account.newScoreAccount(22);
    public static final Account dividends = Account.newScoreAccount(23);
    public static final Account dex = Account.newScoreAccount(24);

    public governance(){
    }
    public Address getContractAddress(String contract){
        switch (contract) {
            case "baln":
                return baln.getAddress();
            case "router":
                return router.getAddress();
            case "dividends":
                return dividends.getAddress();
            case "dex":
                return dex.getAddress();
            default:
                return Address.fromString("");
        }
    }
}
