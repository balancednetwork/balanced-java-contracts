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
import score.Context;
import score.VarDB;

import java.math.BigInteger;

public class NodeDB {

    private static final String NAME = "_NODEDB";

    private final VarDB<BigInteger> value;
    private final VarDB<Address> key;
    private final VarDB<BigInteger> blockHeight;
    private final VarDB<Address> senderAddress;
    private final VarDB<BigInteger> next;
    private final VarDB<BigInteger> prev;

    public NodeDB(String key) {
        String name = key + NAME;
        this.value = Context.newVarDB(name + "_value", BigInteger.class);
        this.key = Context.newVarDB(name + "_key", Address.class);
        this.blockHeight = Context.newVarDB(name + "_block_height", BigInteger.class);
        this.senderAddress = Context.newVarDB(name + "_address", Address.class);
        this.next = Context.newVarDB(name + "_next", BigInteger.class);
        this.prev = Context.newVarDB(name + "_prev", BigInteger.class);
    }

    public void delete() {
        value.set(null);
        key.set(null);
        blockHeight.set(null);
        senderAddress.set(null);
        prev.set(null);
        next.set(null);
    }

    public boolean exists() {
        return value.get() != null;
    }

    public BigInteger getValue() {
        return value.getOrDefault(BigInteger.ZERO);
    }

    public Address getKey() {
        return key.get();
    }

    public BigInteger getBlockHeight() {
        return blockHeight.getOrDefault(BigInteger.ZERO);
    }

    public Address getSenderAddress() {
        return senderAddress.get();
    }

    public void setter(Address key, BigInteger value, BigInteger blockHeight, Address senderAddress) {
        this.value.set(value);
        this.key.set(key);
        this.blockHeight.set(blockHeight);
        this.senderAddress.set(senderAddress);
    }

    public BigInteger getNext() {
        return next.getOrDefault(BigInteger.ZERO);
    }

    public void setNext(BigInteger nextId) {
        next.set(nextId);
    }

    public BigInteger getPrev() {
        return prev.getOrDefault(BigInteger.ZERO);
    }

    public void setPrev(BigInteger prev_id) {
        prev.set(prev_id);
    }

}
