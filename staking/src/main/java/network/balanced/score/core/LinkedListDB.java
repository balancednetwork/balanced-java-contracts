package network.balanced.score.core;

import score.Address;
import score.Context;
import score.VarDB;
import scorex.util.ArrayList;


import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

public class LinkedListDB {
    private static final String NAME = "_LINKED_LISTDB";

    public final VarDB<BigInteger> headId;
    final VarDB<BigInteger> tailId;
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

    public BigInteger __len__(){
        return length.getOrDefault(BigInteger.ZERO);
    }

    public _NodeDB node(BigInteger nodeId){
        return new _NodeDB(nodeId.toString() + name);
    }

    public void updateNode(Address key, BigInteger value, BigInteger blockHeight, Address senderAddress, BigInteger nodeId){
        _NodeDB node = node(nodeId);
        if (node.exists()){
            node.setValue(value);
            node.setKey(key);
            node.setBlockHeight(blockHeight);
            node.setSenderAddress(senderAddress);
        }
        else{
            Context.revert("There is no node of the provided node id.");
        }
    }

    public BigInteger append(Address key, BigInteger value, BigInteger blockHeight
            , Address senderAddress, BigInteger nodeId) throws Exception {
        Object[] nodeDetails = createNode(key, value, blockHeight, senderAddress, nodeId);
        BigInteger curId = (BigInteger) nodeDetails[0];
        _NodeDB cur = (_NodeDB) nodeDetails[1];
        if (length.getOrDefault(BigInteger.ZERO).equals(BigInteger.ZERO)){
            headId.set(curId);
            tailId.set(curId);
        }
        else{
            _NodeDB tail = getTailNode();
            tail.setNext(curId);
            cur.setPrev(tailId.get());
            tailId.set(curId);
        }
        length.set(length.getOrDefault(BigInteger.ZERO).add(BigInteger.ONE));
        return curId;
    }

    public _NodeDB getNode(BigInteger nodeId) throws Exception {
        _NodeDB node = node(nodeId);
        if (! node.exists()){
            throw Objects.requireNonNull(LinkedNodeNotFound(name, nodeId));
        }
        return node;
    }

    public _NodeDB getTailNode() throws Exception {
        BigInteger tailId = this.tailId.getOrDefault(BigInteger.ZERO);
        if (tailId== null){
            Context.revert("Linked List does not exists");
        }
        return getNode(tailId);
    }

    public Object[] createNode(Address key, BigInteger value,
                               BigInteger blockHeight, Address senderAddres,
                               BigInteger nodeId) throws Exception {
        if (nodeId == null) {
            nodeId = new IdFactory(name + "_nodedb").getUid();
        }
        _NodeDB node = node(nodeId);
        if (node.exists()){
            throw Objects.requireNonNull(LinkedNodeAlreadyExists(name, nodeId));
        }
        node.setValue(value);
        node.setKey(key);
        node.setBlockHeight(blockHeight);
        node.setSenderAddress(senderAddres);
        return new Object[] {nodeId, node};
    }


    public void removeHead() throws Exception {
        if (length.getOrDefault(BigInteger.ZERO).equals(BigInteger.ONE)){
            clear();
        }else{
            _NodeDB oldHead = getNode(headId.getOrDefault(BigInteger.ZERO));
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
            _NodeDB oldTail = getNode(tailId.get());
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
            _NodeDB cur = getNode(curId);
            BigInteger curNextId = cur.getNext();
            _NodeDB curnext = getNode(curNextId);
            BigInteger curPrevId = cur.getPrev();
            _NodeDB curprev = getNode(curPrevId);
            curnext.setPrev(curPrevId);
            curprev.setNext(curNextId);
            cur.delete();
            length.set(length.getOrDefault(BigInteger.ZERO).subtract(BigInteger.ONE));
        }
    }

    public void clear() throws Exception {
        BigInteger curId = headId.getOrDefault(BigInteger.ZERO);
        if (curId == null){
          return;
        }
        _NodeDB node = getNode(curId);
        BigInteger tailId = this.tailId.getOrDefault(BigInteger.ZERO);
        while (!Objects.equals(curId, tailId)){
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
        if (curId==null){
            return newList;
        }
        _NodeDB node = getNode(curId);

        newList.add(List.of(curId, node.getValue(), node.getKey(), node.getBlockHeight(), node.getSenderAddress()));

        BigInteger tailId = this.tailId.getOrDefault(BigInteger.ZERO);
        while (!Objects.equals(curId, tailId)){
            curId = node.getNext();
            node = getNode(curId);
            newList.add(List.of(curId, node.getValue(), node.getKey(), node.getBlockHeight(), node.getSenderAddress()));
            tailId = this.tailId.getOrDefault(BigInteger.ZERO);
        }
        return newList;
    }


    private Exception LinkedNodeAlreadyExists(String name, BigInteger nodeId) {
        Context.revert("Linked List "+name+"already exists of nodeId."+ nodeId.toString());
        return null;
    }

    private Exception LinkedNodeNotFound(String name, BigInteger nodeId) {
        Context.revert("Linked List  "+name+" Node not found of nodeId "+ nodeId.toString());
        return null;
    }



}