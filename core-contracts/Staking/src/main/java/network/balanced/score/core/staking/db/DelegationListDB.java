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

package network.balanced.score.core.staking.db;

import foundation.icon.score.data.ScoreDataObject;
import score.Address;
import scorex.util.ArrayList;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@ScoreDataObject
public class DelegationListDB {
    private List<Delegation> delegationList;

    public List<Delegation> getDelegationList() {
        return delegationList;
    }

    public void setDelegationList(List<Delegation> delegationList) {
        this.delegationList = delegationList;
    }

    @Override
    public String toString() {
        return "DelegationListDB{" + "delegationList=" + delegationList + "}";
    }

    public Map<String, BigInteger> toMap() {
        if (delegationList.size() == 0) {
            return Map.of();
        }

        Map<String, BigInteger> delegationMap = new HashMap<>();
        for (Delegation delegation : delegationList) {
            delegationMap.put(delegation.getAddress().toString(), delegation.getDelegationValue());
        }
        return delegationMap;
    }

    public static DelegationListDBSdo fromMap(Map<String, BigInteger> delegationMap) {
        List<Delegation> delegationList = new ArrayList<>();
        if (!delegationMap.isEmpty()) {
            for (Map.Entry<String, BigInteger> delegationEntry : delegationMap.entrySet()) {
                Delegation delegation = new Delegation();
                delegation.setAddress(Address.fromString(delegationEntry.getKey()));
                delegation.setDelegationValue(delegationEntry.getValue());
                delegationList.add(delegation);
            }
        }

        DelegationListDBSdo delegationListDB = new DelegationListDBSdo();
        delegationListDB.setDelegationList(delegationList);
        return delegationListDB;
    }
}
