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

package network.balanced.score.lib.utils;

import score.Address;
import score.Context;
import score.VarDB;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.LinkedListDB.DEFAULT_NODE_ID;

public class NodeDB {

    private static final String NAME = "_NODEDB";

    private final VarDB<BigInteger> size;
    private final VarDB<Address> user;
    private final VarDB<BigInteger> next;
    private final VarDB<BigInteger> prev;

    public NodeDB(String key) {
        String name = key + NAME;
        this.size = Context.newVarDB(name + "_value1", BigInteger.class);
        this.user = Context.newVarDB(name + "_value2", Address.class);
        this.next = Context.newVarDB(name + "_next", BigInteger.class);
        this.prev = Context.newVarDB(name + "_prev", BigInteger.class);
    }

    public void delete() {
        size.set(null);
        user.set(null);
        prev.set(null);
        next.set(null);
    }

    public boolean exists() {
        return (size.get() != null) && (user.get() != null);
    }

    public BigInteger getSize() {
        return size.getOrDefault(BigInteger.ZERO);
    }

    public void setSize(BigInteger value) {
        size.set(value);
    }

    public Address getUser() {
        return user.get();
    }

    public void setUser(Address user) {
         this.user.set(user);
    }

    public void setValues(BigInteger size, Address user) {
        this.size.set(size);
        this.user.set(user);
    }

    public BigInteger getNext() {
        return next.getOrDefault(DEFAULT_NODE_ID);
    }

    public void setNext(BigInteger nextId) {
        next.set(nextId);
    }

    public BigInteger getPrev() {
        return prev.getOrDefault(DEFAULT_NODE_ID);
    }

    public void setPrev(BigInteger prev_id) {
        prev.set(prev_id);
    }

}
