package network.balanced.score.core.governance.utils;

import java.math.BigInteger;

import score.Address;
import score.Context;

public class EventLogger {
    public static void VoteCast(String vote_name, boolean vote, Address voter, BigInteger stake, BigInteger total_for,
                   BigInteger total_against) {
        Context.logEvent(
            new Object[]{"VoteCast(String,boolean,Address,int,int,int)", vote_name, vote}, 
            new Object[]{voter, stake, total_for, total_against}
        );
    }
}