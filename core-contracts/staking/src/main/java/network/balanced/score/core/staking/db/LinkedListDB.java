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
import scorex.util.ArrayList;


import java.math.BigInteger;
import java.util.List;

public class LinkedListDB {
    private static final String NAME = "_LINKED_LISTDB";

    public final VarDB<BigInteger> headId;
    public final VarDB<BigInteger> tailId;
    private final VarDB<BigInteger> length;
    private final String name;


    public LinkedListDB(String key) {
        this.name = key + NAME;
        this.headId = Context.newVarDB(this.name + "_head_id", BigInteger.class);
        this.tailId = Context.newVarDB(this.name + "_tail_id", BigInteger.class);
        this.length = Context.newVarDB(this.name + "_length", BigInteger.class);
    }

    public void delete() {
        clear();
        headId.set(null);
        tailId.set(null);
        length.set(null);
    }

    public BigInteger size() {
        return length.getOrDefault(BigInteger.ZERO);
    }

    public NodeDB createNodeInstance(BigInteger nodeId) {
        return new NodeDB(nodeId.toString() + name);
    }

    public void updateNode(Address key, BigInteger value, BigInteger blockHeight, Address senderAddress,
                           BigInteger nodeId) {
        NodeDB node = createNodeInstance(nodeId);
        if (node.exists()) {
            node.setter(key, value, blockHeight, senderAddress);

        } else {
            Context.revert("There is no node of the provided node id.");
        }
    }

    public BigInteger append(Address key, BigInteger value, BigInteger blockHeight
            , Address senderAddress, BigInteger nodeId) {
        NodeDB cur = createNode(key, value, blockHeight, senderAddress, nodeId);
        if (length.getOrDefault(BigInteger.ZERO).equals(BigInteger.ZERO)) {
            headId.set(nodeId);
        } else {
            NodeDB tail = getTailNode();
            tail.setNext(nodeId);
            cur.setPrev(tailId.get());
        }
        tailId.set(nodeId);
        length.set(length.getOrDefault(BigInteger.ZERO).add(BigInteger.ONE));
        return nodeId;
    }

    public NodeDB getNode(BigInteger nodeId) {
        NodeDB node = createNodeInstance(nodeId);
        if (!node.exists()) {
            LinkedNodeNotFound(name, nodeId);
        }
        return node;
    }

    public NodeDB getTailNode() {
        BigInteger tailId = this.tailId.get();
        if (tailId == null) {
            Context.revert("Linked List does not exists");
        }
        return getNode(tailId);
    }

    public NodeDB createNode(Address key, BigInteger value,
                             BigInteger blockHeight, Address senderAddress,
                             BigInteger nodeId) {
        if (nodeId == null) {
            nodeId = new IdFactory(name + "_nodedb").getUid();
        }
        NodeDB node = createNodeInstance(nodeId);
        if (node.exists()) {
            LinkedNodeAlreadyExists(name, nodeId);
        }
        node.setter(key, value, blockHeight, senderAddress);
        return node;
    }


    public void removeHead() {
        if (length.getOrDefault(BigInteger.ZERO).equals(BigInteger.ONE)) {
            clear();
        } else {
            NodeDB oldHead = getNode(headId.getOrDefault(BigInteger.ZERO));
            BigInteger newHead = oldHead.getNext();
            headId.set(newHead);
            getNode(newHead).setPrev(BigInteger.ZERO);
            oldHead.delete();
            length.set(length.getOrDefault(BigInteger.ZERO).subtract(BigInteger.ONE));
        }

    }

    public void removeTail() {
        if (length.getOrDefault(BigInteger.ZERO).equals(BigInteger.ONE)) {
            clear();
        } else {
            NodeDB oldTail = getNode(tailId.get());
            BigInteger newTail = oldTail.getPrev();
            tailId.set(newTail);
            getNode(newTail).setNext(BigInteger.ZERO);
            oldTail.delete();
            length.set(length.getOrDefault(BigInteger.ZERO).subtract(BigInteger.ONE));
        }
    }

    public void remove(BigInteger curId) {
        if (curId.equals(headId.getOrDefault(BigInteger.ZERO))) {
            removeHead();
        } else if (curId.equals(tailId.getOrDefault(BigInteger.ZERO))) {
            removeTail();
        } else {
            NodeDB cur = getNode(curId);
            BigInteger curNextId = cur.getNext();
            NodeDB curnext = getNode(curNextId);
            BigInteger curPrevId = cur.getPrev();
            NodeDB curprev = getNode(curPrevId);
            curnext.setPrev(curPrevId);
            curprev.setNext(curNextId);
            cur.delete();
            length.set(length.getOrDefault(BigInteger.ZERO).subtract(BigInteger.ONE));
        }
    }

    public void clear() {
        BigInteger curId = headId.get();
        if (curId == null) {
            return;
        }
        NodeDB node = getNode(curId);
        BigInteger tailId = this.tailId.getOrDefault(BigInteger.ZERO);
        while (!curId.equals(tailId)) {
            curId = node.getNext();
            node.delete();
            node = getNode(curId);
        }
        node.delete();

        this.tailId.set(null);
        headId.set(null);
        length.set(BigInteger.ZERO);
    }

    public List<List<Object>> iterate() {
        BigInteger curId = headId.getOrDefault(BigInteger.ZERO);
        List<List<Object>> newList = new ArrayList<>();

        if (curId.equals(BigInteger.ZERO)) {
            return newList;
        }
        NodeDB node = getNode(curId);
        newList.add(List.of(curId, node.getValue(), node.getKey(), node.getBlockHeight(), node.getSenderAddress()));

        BigInteger tailId = this.tailId.getOrDefault(BigInteger.ZERO);
        while (!curId.equals(tailId)) {
            curId = node.getNext();
            node = getNode(curId);
            newList.add(List.of(curId, node.getValue(), node.getKey(), node.getBlockHeight(), node.getSenderAddress()));
            tailId = this.tailId.getOrDefault(BigInteger.ZERO);
        }
        return newList;
    }


    private void LinkedNodeAlreadyExists(String name, BigInteger nodeId) {
        Context.revert("Linked List " + name + "already exists of nodeId." + nodeId.toString());
    }

    private void LinkedNodeNotFound(String name, BigInteger nodeId) {
        Context.revert("Linked List  " + name + " Node not found of nodeId " + nodeId.toString());
    }


}