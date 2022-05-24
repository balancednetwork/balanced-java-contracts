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

package network.balanced.score.core.loans.linkedlist;

import score.Context;
import score.VarDB;

import java.math.BigInteger;

public class LinkedListDB {
    private final static String _NAME = "_LINKED_LISTDB";
    private final String name;
    private final String dbKey;
    private final VarDB<String> metadata;
    private String dataString;
    private int headId;
    private int tailId;
    private int size;

    public LinkedListDB(String dbName) {
        name = dbName + _NAME;
        dbKey = "";
        metadata = Context.newVarDB(name + "_metadata", String.class);
        initialize();
    }

    @SuppressWarnings("unchecked")
    public LinkedListDB(String dbName, String key) {
        name = dbName + _NAME;
        dbKey = key;
        metadata = (VarDB<String>) Context.newBranchDB(name + "_metadata", String.class).at(dbKey);
        initialize();
    }

    private void initialize() {
        dataString = metadata.get();
        if (dataString == null || dataString.isEmpty()) {
            dataString = "";
            headId = 0;
            tailId = 0;
            size = 0;
            return;
        }

        int delimiter1 = dataString.indexOf("|");
        int delimiter2 = dataString.lastIndexOf("|");

        headId = Integer.parseInt(dataString.substring(0, delimiter1));
        tailId = Integer.parseInt(dataString.substring(delimiter1 + 1, delimiter2));
        size = Integer.parseInt(dataString.substring(delimiter2 + 1));
    }

    public void serialize() {
        String newData = headId + "|" + tailId + "|" + size;
        if (!newData.equals(dataString)) {
            dataString = newData;
            metadata.set(newData);
        }
    }

    public int size() {
        return size;
    }

    private Node getNode(int nodeId) {
        if (!dbKey.equals("")) {
            return new Node(nodeId + name, dbKey);
        }
        return new Node(nodeId + name);
    }

    public BigInteger nodeValue(int id) {
        return getNode(id).getValue();
    }

    public boolean contains(int id) {
        return getNode(id).exists();
    }

    public int getHeadId() {
        return headId;
    }

    public int getTailId() {
        return tailId;
    }

    public int getNextId(int id) {
        Node node = getNode(id);
        int nextId = node.getNext();
        Context.require(nextId != 0, name + ": End of list reached");
        return nextId;
    }

    public void set(int id, BigInteger value) {
        Node node = getNode(id);
        node.setValue(value);
        node.repack();
    }

    public void append(BigInteger value, int id) {
        Node node = getNode(id);
        Context.require(!node.exists(), name + ": node with id " + id + " already exists");

        node.setValue(value);
        if (size == 0) {
            headId = id;
        } else {
            Node tail = getNode(tailId);
            tail.setNext(id);
            tail.repack();

            node.setPrev(tailId);
        }
        node.repack();
        tailId = id;
        size = size + 1;
        serialize();
    }

    public void headToTail() {
        Context.require(size != 0, name + ": No data in the list");

        if (size == 1) {
            return;
        }

        Node head = getNode(headId);
        Node tail = getNode(tailId);
        int nextId = head.getNext();
        Node headNext = getNode(nextId);

        // Update node next to head
        headNext.setPrev(0);
        headNext.repack();

        // Update tail node
        tail.setNext(headId);
        tail.repack();

        // Update previous head as new tail
        head.setPrev(tailId);
        head.setNext(0);
        head.repack();

        tailId = headId;
        headId = nextId;
        serialize();
    }

    private void removeHead() {
        Node oldHead = getNode(headId);

        int newHeadId = oldHead.getNext();
        Node newHead = getNode(newHeadId);
        headId = newHeadId;

        newHead.setPrev(0);
        newHead.repack();

        oldHead.delete();
        size = size - 1;
    }

    private void removeTail() {
        Node oldTail = getNode(tailId);

        int newTailId = oldTail.getPrev();
        Node newTail = getNode(newTailId);
        tailId = newTailId;

        newTail.setNext(0);
        newTail.repack();

        oldTail.delete();
        size = size - 1;
    }

    private void removeMiddleNode(int id) {
        Node node = getNode(id);
        int nextId = node.getNext();
        Node nextNode = getNode(nextId);

        int prevId = node.getPrev();
        Node prevNode = getNode(prevId);

        nextNode.setPrev(prevId);
        nextNode.repack();

        prevNode.setNext(nextId);
        prevNode.repack();

        node.delete();
        size = size - 1;
    }

    public void remove(int id) {
        if (size == 1) {
            Node singleNode = getNode(id);
            tailId = 0;
            headId = 0;
            size = 0;
            singleNode.delete();
            dataString = "";
            metadata.set(null);
            return;
        } else if (id == headId) {
            removeHead();
        } else if (id == tailId) {
            removeTail();
        } else {
            removeMiddleNode(id);
        }
        serialize();
    }

}
