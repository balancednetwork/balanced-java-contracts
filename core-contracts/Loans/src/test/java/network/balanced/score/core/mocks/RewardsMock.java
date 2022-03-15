package network.balanced.score.core;

import score.Context;
import score.VarDB;
import score.DictDB;
import score.Address;
import score.annotation.External;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import java.math.BigInteger;

import java.util.Map;

import score.Context;
import scorex.util.ArrayList;

import java.math.BigInteger;

public class RewardsMock {
    private Address loans;
    public RewardsMock(Address loansAddress) {
       loans = loansAddress;
    }

    @External
    public boolean distribute() {
        return true;
    }

    @External
    public void updateRewardsData(String name, BigInteger supply, Address from, BigInteger borrowed) {
    }
    @External
    public void updateBatchRewardsData(String name, BigInteger supply, ArrayList<Map<String, Object>> batch) {
    }

    @External
    public void precompute(int _snapshot_id, int batch_size) {
        Context.call(loans, "precompute", _snapshot_id, batch_size);
    }
}
