/*
 * Copyright (c) 2022-2022 Balanced.network.
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

package network.balanced.score.core.multicall;

import score.Address;
import score.Context;
import score.VarDB;
import score.annotation.External;

import java.math.BigInteger;
import java.util.Map;

import scorex.util.HashMap;

public class Multicall {

    private final Address defaultAddress = new Address(new byte[Address.LENGTH]);
    public final VarDB<Address> dexAddress = Context.newVarDB("dexAddress", Address.class);
    public static final String TAG = "Multicall";

    public Multicall() {
    }

    private void onlyOwner() {
        Address caller = Context.getCaller();
        Address owner = Context.getOwner();
        Context.require(caller.equals(owner), TAG + ": Caller is not the owner");
    }

    @External(readonly = true)
    public String name() {
        return "Balanced Multicall";
    }

    @External(readonly = true)
    public Address getDexAddress() {
        return dexAddress.get();
    }

    @External
    public void setDexAddress(Address dex) {
        onlyOwner();
        Context.require(dex.isContract(), TAG + ": Dex parameter is not contract address");
        this.dexAddress.set(dex);
    }

    @External(readonly = true)
    public Map<String, Object> getPoolStatsForPair(Address _base, Address _quote) {
        BigInteger poolId = (BigInteger) Context.call(dexAddress.getOrDefault(defaultAddress), "getPoolId", _base,
                _quote);

        @SuppressWarnings("unchecked")
        Map<String, Object> poolStats = (Map<String, Object>) Context.call(dexAddress.getOrDefault(defaultAddress),
                "getPoolStats", poolId);

        Map<String, Object> poolStatsWithId = new HashMap<>();
        poolStatsWithId.put("id", poolId);
        poolStatsWithId.putAll(poolStats);

        return poolStatsWithId;
    }

}
