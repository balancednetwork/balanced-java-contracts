package network.balanced.score.core.loans.linkedlist;

import score.Context;
import score.VarDB;
import scorex.util.HashMap;

import java.math.BigInteger;

import network.balanced.score.core.loans.LoansImpl;

public class LinkedListDB {
    private final static String _NAME = "_LINKED_LISTDB";
    private String name;
    private String dbKey;
    private HashMap<Integer, Node> cacheDB;
    private VarDB<String> metadata;
    private String dataString;
    private int headId;
    private int tailId;
    private int size;

    public LinkedListDB(String dbName, String key) {
        name = dbName + _NAME;
        dbKey = key;
        metadata = (VarDB<String>) Context.newBranchDB(name + "_metadata", String.class).at(dbKey);
        setup();
    }

    public LinkedListDB(String dbName) {
        name = dbName + _NAME;
        dbKey = "";
        metadata = Context.newVarDB(name + "_metadata", String.class);
        setup();
    }

    private void setup() {
        cacheDB = new HashMap<Integer, Node>(LoansImpl.redeemBatch.get());
        dataString = "";
        headId = 0;
        tailId = 0;
        size = 0;
        deserialize();
    }

    public void deserialize() {
        dataString = metadata.getOrDefault("");
        if (dataString.equals("")) {
            return;
        }

        int delimiter1 = dataString.indexOf("|");
        int delimiter2 = dataString.lastIndexOf("|");

        headId = Integer.parseInt(dataString.substring(0, delimiter1));
        tailId = Integer.parseInt(dataString.substring(delimiter1+1, delimiter2));
        size = Integer.parseInt(dataString.substring(delimiter2+1));
    }

    public void serialize() {
        String newData = headId + "|" + tailId +  "|" + size;
        if (!newData.equals(dataString)) {
            metadata.set(newData);
        }
    }

    private Node _Node(int nodeId) {
        if (dbKey != "") {
            return new Node(nodeId+name, dbKey);
        }

        return new Node(nodeId+name);
    }

    private Node getNode(int nodeId) {
        Node node;
        if (!cacheDB.containsKey(nodeId)) {
            node = _Node(nodeId);
            cacheDB.put(nodeId, node);
        }
        
        node = cacheDB.get(nodeId);
        if (!node.exists()){
            _append(nodeId);
        }
     
        return node;
    }

    private Node createNode(BigInteger value, int id) {
        Node node = _Node(id);
        Context.require(!node.exists(), name + ": node with id " + id + " already exists");

        node.setValue(value);
        node.repack();
        return node;
    }

    private Node getTailNode() {
        Context.require(tailId != 0, name + ": List is empty");
        return _Node(tailId);
    }

    public int size() {
        return size;
    }

    public boolean contains(int id)  {
        return _Node(id).exists();
    }

    public void set(int id, BigInteger value) {
        Node node = getNode(id);
        node.setValue(value);
        node.repack();
    }

    public BigInteger nodeValue(int id) {
        return getNode(id).getValue();
    }

    public int getHeadId() {
        return headId;
    }

    public int getTailId() {
        return tailId;
    }
    
    public int next(int id) {
        getNode(id);
        int nextId = getNode(id).getNext();
        Context.require(nextId != 0, name + ": End of list reached");

        return nextId;
    }
    
    public void remove(int id) {
        if (id == headId) {
            _removeHead();
        } else if (id == tailId) {
            _removeTail();
        } else {
            _remove(id);
        }

        serialize();
    }

    private void _remove(int id) {
        Node node = getNode(id);
        int nextId = node.getNext();
        Node next = getNode(nextId);
        int prevId = node.getPrev();
        Node prev = getNode(prevId);
        next.setPrev(prevId);
        prev.setNext(nextId);
        node.delete();
        size = size- 1;
        next.repack();
        prev.repack();
    }

    private void _removeHead() {
        Node oldHead = getNode(headId);
        if (size == 1) {
            tailId = 0;
            headId = 0;
            size = 0;
            oldHead.delete();
            return;
        }

        int newHeadId = oldHead.getNext();
        Node newHead = getNode(newHeadId);
        headId = newHeadId;

        newHead.setPrev(0);
        oldHead.delete();
        size = size -1;
        newHead.repack();
    }

    private void _removeTail() {
        Node oldTail = getNode(tailId);
        if (size == 1) {
            tailId = 0;
            headId = 0;
            size = 0;
            oldTail.delete();
            return;
        }

        int newTailId = oldTail.getPrev();
        Node newTail = getNode(newTailId);
        tailId = newTailId;
        newTail.setNext(0);
        oldTail.delete();
        size = size -1;
        newTail.repack();
    }

    public void headToTail() {
        Node head = getNode(headId);
        Node tail = getNode(tailId);
  
        int nextId = head.getNext();
        Node headNext = getNode(nextId);
        headNext.setPrev(0);
        headNext.repack();

        tail.setNext(headId);
        tail.repack();

        head.setPrev(tailId);
        head.setNext(0);
        head.repack();

        tailId = headId;
        headId = nextId;
    }

    public int append(BigInteger value, int id)  {
        Node node = createNode(value, id);
        cacheDB.put(id, node);
        return _append(id);
    }

    private int _append(int id) {
        Node node = cacheDB.get(id);

        if (size == 0) {
            headId = id;
            tailId = id;
        } else {
            Node tail = getTailNode();
            tail.setNext(id);
            tail.repack();

            node.setPrev(tailId);
            node.repack();

            tailId = id;
        }
    
        size = size + 1;
        serialize();
        return id;
    }



}