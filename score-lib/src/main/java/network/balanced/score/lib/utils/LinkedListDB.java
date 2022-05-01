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


public class LinkedListDB {

    private static final String NAME = "_LINKED_LISTDB";
    public static final BigInteger DEFAULT_NODE_ID = BigInteger.ZERO;

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

    public BigInteger getTailId() {
        return tailId.getOrDefault(BigInteger.ZERO);
    }

    public BigInteger size() {
        return length.getOrDefault(BigInteger.ZERO);
    }

    public NodeDB createNodeInstance(BigInteger nodeId) {
        return new NodeDB(nodeId.toString() + name);
    }

    public void updateNode(BigInteger size, Address user, BigInteger nodeId) {
        NodeDB node = createNodeInstance(nodeId);
        if (node.exists()) {
            node.setValues(size, user);
        } else {
            Context.revert("There is no node of the provided node id.");
        }
    }

    public BigInteger append(BigInteger size, Address user, BigInteger nodeId) {
        NodeDB nodeToAppend = createNode(size, user, nodeId);
        if (length.getOrDefault(BigInteger.ZERO).equals(BigInteger.ZERO)) {
            headId.set(nodeId);
            tailId.set(nodeId);
        } else {
            BigInteger tailId = this.tailId.get();
            NodeDB tail = getNode(tailId);
            tail.setNext(nodeId);
            nodeToAppend.setPrev(tailId);
        }
        tailId.set(nodeId);
        length.set(length.getOrDefault(BigInteger.ZERO).add(BigInteger.ONE));
        return nodeId;
    }

    public NodeDB getNode(BigInteger nodeId) {
        if (nodeId == null) {
            Context.revert("Invalid Node Id");
        }
        NodeDB node = createNodeInstance(nodeId);
        if (!node.exists()) {
            LinkedNodeNotFound(name, nodeId);
        }
        return node;
    }

    public NodeDB getTailNode() {
        BigInteger tailId = this.tailId.get();
        if (tailId == null) {
            Context.revert("Empty Linked list");
        }
        return getNode(tailId);
    }

    public NodeDB getHeadNode() {
        BigInteger headId = this.headId.get();
        if (headId == null) {
            Context.revert("Empty Linked list");
        }
        return getNode(headId);
    }


    public NodeDB createNode(BigInteger size, Address user,
                             BigInteger nodeId) {
        NodeDB node = createNodeInstance(nodeId);
        if (node.exists()) {
            LinkedNodeAlreadyExists(name, nodeId);
        }
        node.setValues(size, user);
        return node;
    }


    public void removeHead() {
        BigInteger size = length.getOrDefault(BigInteger.ZERO);
        if (size.equals(BigInteger.ZERO)) {
            return;
        }
        if (size.equals(BigInteger.ONE)) {
            clear();
        } else {
            NodeDB oldHead = getNode(headId.get());
            BigInteger newHead = oldHead.getNext();
            headId.set(newHead);
            getNode(newHead).setPrev(DEFAULT_NODE_ID);
            oldHead.delete();
            length.set(size.subtract(BigInteger.ONE));
        }
    }

    public void removeTail() {
        BigInteger size = length.getOrDefault(BigInteger.ZERO);
        if (size.equals(BigInteger.ZERO)) {
            return;
        }
        if (size.equals(BigInteger.ONE)) {
            clear();
        } else {
            NodeDB oldTail = getNode(tailId.get());
            BigInteger newTail = oldTail.getPrev();
            tailId.set(newTail);
            getNode(newTail).setNext(DEFAULT_NODE_ID);
            oldTail.delete();
            length.set(size.subtract(BigInteger.ONE));
        }
    }

    public void remove(BigInteger curId) {
        BigInteger size = length.getOrDefault(BigInteger.ZERO);
        if (size.equals(BigInteger.ZERO)) {
            return;
        }
        if (curId.equals(headId.getOrDefault(DEFAULT_NODE_ID))) {
            removeHead();
        } else if (curId.equals(tailId.getOrDefault(DEFAULT_NODE_ID))) {
            removeTail();
        } else {
            NodeDB nodeToRemove = getNode(curId);
            BigInteger nextNodeId = nodeToRemove.getNext();
            NodeDB nextNode = getNode(nextNodeId);
            BigInteger previousNodeId = nodeToRemove.getPrev();
            NodeDB previousNode = getNode(previousNodeId);
            nextNode.setPrev(previousNodeId);
            previousNode.setNext(nextNodeId);
            nodeToRemove.delete();
            length.set(size.subtract(BigInteger.ONE));
        }
    }

    public void clear() {
        BigInteger currentId = headId.get();
        if (currentId == null) {
            return;
        }
        NodeDB nodeToRemove = getNode(currentId);
        BigInteger tailId = this.tailId.getOrDefault(DEFAULT_NODE_ID);
        while (!currentId.equals(tailId)) {
            currentId = nodeToRemove.getNext();
            nodeToRemove.delete();
            nodeToRemove = getNode(currentId);
        }
        nodeToRemove.delete();

        this.tailId.set(null);
        headId.set(null);
        length.set(null);
    }

    private void LinkedNodeAlreadyExists(String name, BigInteger nodeId) {
        Context.revert("Linked List " + name + "already exists of nodeId." + nodeId.toString());
    }

    private void LinkedNodeNotFound(String name, BigInteger nodeId) {
        Context.revert("Linked List  " + name + " Node not found of nodeId " + nodeId.toString());
    }

}

