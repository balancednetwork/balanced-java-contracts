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

import java.math.BigInteger;

@ScoreDataObject
public class Delegation {

    private Address address;
    private BigInteger delegationValue;

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public BigInteger getDelegationValue() {
        return delegationValue;
    }

    public void setDelegationValue(BigInteger delegationValue) {
        this.delegationValue = delegationValue;
    }

    @Override
    public String toString() {
        return "Delegation{" + "address='" + address + '\'' +
                "delegationValue='" + delegationValue + '\'' +
                '}';
    }
}
