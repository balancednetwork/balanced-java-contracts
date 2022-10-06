package network.balanced.score.core.rewards.utils;

import java.math.BigInteger;

import score.Address;
import score.annotation.EventLog;

public class EventLogger {
    @EventLog(indexed = 2)
    public void VoteCast(String vote_name, boolean vote, Address voter, BigInteger stake, BigInteger total_for,
                   BigInteger total_against) {
    }

    @EventLog(indexed = 2)
    public void AddType(String typeName, int typeId) {
    }

    @EventLog(indexed = 1)
    public void NewTypeWeight(int typeId, BigInteger time, BigInteger weight, BigInteger totalWeight) {
    }

    @EventLog(indexed = 2)
    public void VoteForSource(String sourceName, Address user, BigInteger weight, BigInteger time) {
    }


    @EventLog(indexed = 1)
    public void NewSource(String sourceName, int typeId, BigInteger weight) {
    }
}
