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

import score.Address;
import score.ObjectReader;
import score.ObjectWriter;

import java.math.BigInteger;

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

    public static Delegation readObject(ObjectReader reader) {
        Delegation obj = new Delegation();
        reader.beginList();
        obj.setAddress(reader.readAddress());
        obj.setDelegationValue(reader.readBigInteger());
        reader.end();
        return obj;
    }

    public void writeObject(ObjectWriter writer) {
        writer.beginList(2);
        writer.write(this.getAddress());
        writer.write(this.getDelegationValue());
        writer.end();
    }

    public static void writeObject(ObjectWriter writer, Delegation obj) {
        obj.writeObject(writer);
    }
}
