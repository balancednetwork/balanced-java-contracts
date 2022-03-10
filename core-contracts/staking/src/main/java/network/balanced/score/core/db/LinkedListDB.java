package network.balanced.score.core.db;

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

    public void delete() throws Exception {
        clear();
        headId.set(null);
        tailId.set(null);
        length.set(null);
    }

    public BigInteger size(){
        return length.getOrDefault(BigInteger.ZERO);
    }

    public NodeDB createNodeInstance(BigInteger nodeId){
        return new NodeDB(nodeId.toString() + name);
    }

    public void updateNode(Address key, BigInteger value, BigInteger blockHeight, Address senderAddress, BigInteger nodeId){
        NodeDB node = createNodeInstance(nodeId);
        if (node.exists()){
            node.setter(key, value, blockHeight, senderAddress);

        }
        else{
            Context.revert("There is no node of the provided node id.");
        }
    }

    public BigInteger append(Address key, BigInteger value, BigInteger blockHeight
            , Address senderAddress, BigInteger nodeId) throws Exception {
        NodeDB cur = createNode(key, value, blockHeight, senderAddress, nodeId);
        if (length.getOrDefault(BigInteger.ZERO).equals(BigInteger.ZERO)){
            headId.set(nodeId);
            tailId.set(nodeId);
        }
        else{
            NodeDB tail = getTailNode();
            tail.setNext(nodeId);
            cur.setPrev(tailId.get());
            tailId.set(nodeId);
        }
        length.set(length.getOrDefault(BigInteger.ZERO).add(BigInteger.ONE));
        return nodeId;
    }

    public NodeDB getNode(BigInteger nodeId) throws Exception {
        NodeDB node = createNodeInstance(nodeId);
        if (! node.exists()){
            LinkedNodeNotFound(name, nodeId);
        }
        return node;
    }

    public NodeDB getTailNode() throws Exception {
        BigInteger tailId = this.tailId.get();
        if (tailId== null){
            Context.revert("Linked List does not exists");
        }
        return getNode(tailId);
    }

    public NodeDB createNode(Address key, BigInteger value,
                               BigInteger blockHeight, Address senderAddress,
                               BigInteger nodeId) throws Exception {
        if (nodeId == null) {
            nodeId = new IdFactory(name + "_nodedb").getUid();
        }
        NodeDB node = createNodeInstance(nodeId);
        if (node.exists()){
            LinkedNodeAlreadyExists(name, nodeId);
        }
        node.setter(key, value, blockHeight, senderAddress);
        return node;
    }


    public void removeHead() throws Exception {
        if (length.getOrDefault(BigInteger.ZERO).equals(BigInteger.ONE)){
            clear();
        }else{
            NodeDB oldHead = getNode(headId.getOrDefault(BigInteger.ZERO));
            BigInteger newHead = oldHead.getNext();
            headId.set(newHead);
            getNode(newHead).setPrev(BigInteger.ZERO);
            oldHead.delete();
            length.set(length.getOrDefault(BigInteger.ZERO).subtract(BigInteger.ONE));
        }

    }

    public void removeTail() throws Exception {
        if (length.getOrDefault(BigInteger.ZERO).equals(BigInteger.ONE)){
            clear();
        }else{
            NodeDB oldTail = getNode(tailId.get());
            BigInteger newTail = oldTail.getPrev();
            tailId.set(newTail);
            getNode(newTail).setNext(BigInteger.ZERO);
            oldTail.delete();
            length.set(length.getOrDefault(BigInteger.ZERO).subtract(BigInteger.ONE));
        }
    }

    public void remove(BigInteger curId) throws Exception {
        if (curId.equals(headId.getOrDefault(BigInteger.ZERO))){
            removeHead();
        }
        else if (curId.equals(tailId.getOrDefault(BigInteger.ZERO))){
            removeTail();
        }
        else{
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

    public void clear() throws Exception {
        BigInteger curId = headId.get();
        if (curId == null){
          return;
        }
        NodeDB node = getNode(curId);
        BigInteger tailId = this.tailId.getOrDefault(BigInteger.ZERO);
        while (! curId.equals(tailId)){
            curId = node.getNext();
            node.delete();
            node = getNode(curId);
        }
        node.delete();

        this.tailId.set(null);
        headId.set(null);
        length.set(BigInteger.ZERO);
    }

    public List<List<Object>> iterate() throws Exception {
        BigInteger curId = headId.getOrDefault(BigInteger.ZERO);
        List<List<Object>> newList = new ArrayList<>();

        if (curId.equals(BigInteger.ZERO)){
            return newList;
        }
        NodeDB node = getNode(curId);
        newList.add(List.of(curId, node.getValue(), node.getKey(), node.getBlockHeight(), node.getSenderAddress()));

        BigInteger tailId = this.tailId.getOrDefault(BigInteger.ZERO);
        while (! curId.equals(tailId)){
            curId = node.getNext();
            node = getNode(curId);
            newList.add(List.of(curId, node.getValue(), node.getKey(), node.getBlockHeight(), node.getSenderAddress()));
            tailId = this.tailId.getOrDefault(BigInteger.ZERO);
        }
        return newList;
    }


    private void LinkedNodeAlreadyExists(String name, BigInteger nodeId) {
        Context.revert("Linked List "+name+"already exists of nodeId."+ nodeId.toString());
    }

    private void LinkedNodeNotFound(String name, BigInteger nodeId) {
        Context.revert("Linked List  "+name+" Node not found of nodeId "+ nodeId.toString());
    }



}