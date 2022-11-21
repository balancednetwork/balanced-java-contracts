package network.balanced.score.core.governance.utils;

import java.math.BigInteger;

import score.Address;
import score.annotation.EventLog;

public class EventLogger {
    @EventLog(indexed = 2)
    public void VoteCast(String vote_name, boolean vote, Address voter, BigInteger stake, BigInteger total_for,
                   BigInteger total_against) {
    }
}
