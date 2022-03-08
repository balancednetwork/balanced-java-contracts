package network.balanced.score.core.db;

import score.Address;
import score.Context;
import score.VarDB;

import java.math.BigInteger;

public class NodeDB {

    private static final String NAME = "_NODEDB";
    private static final BigInteger UNINITIALIZED = BigInteger.ZERO;
    private static final BigInteger INITIALIZED = BigInteger.ONE;

    private final VarDB<BigInteger> value;
    private final VarDB<BigInteger> init;
    private final VarDB<BigInteger> blockHeight;
    private final VarDB<BigInteger> next;
    private final VarDB<BigInteger> prev;
    private final VarDB<Address> key;
    private final VarDB<Address> senderAddress;

    public NodeDB(String key) {
        String name = key + NAME;
        this.value = Context.newVarDB(name + "_value", BigInteger.class);
        this.blockHeight = Context.newVarDB(name + "_block_height", BigInteger.class);
        this.init = Context.newVarDB(name + "_init", BigInteger.class);
        this.next = Context.newVarDB(name + "_next", BigInteger.class);
        this.prev = Context.newVarDB(name + "_prev", BigInteger.class);
        this.key = Context.newVarDB(name + "_key", Address.class);
        this.senderAddress = Context.newVarDB(name + "_sender_address", Address.class);
    }

    public void delete(){
        value.set(null);
        key.set(null);
        blockHeight.set(null);
        senderAddress.set(null);
        prev.set(null);
        next.set(null);
        init.set(null);
    }

    public boolean exists(){
        return ( ! init.getOrDefault(BigInteger.ZERO).equals(NodeDB.UNINITIALIZED));
    }

    public BigInteger getValue(){
        return value.getOrDefault(BigInteger.ZERO);
    }

    public Address getKey(){
        return key.get();
    }

    public BigInteger getBlockHeight(){
        return blockHeight.getOrDefault(BigInteger.ZERO);
    }

    public Address getSenderAddress(){
        return senderAddress.get();
    }

    public void setValue(BigInteger value){
        init.set(NodeDB.INITIALIZED);
        this.value.set(value);
    }

    public void setKey(Address key){
        init.set(NodeDB.INITIALIZED);
        this.key.set(key);
    }

    public void setBlockHeight(BigInteger blockHeight){
        init.set(NodeDB.INITIALIZED);
        this.blockHeight.set(blockHeight);
    }

    public void setSenderAddress(Address senderAddress){
        init.set(NodeDB.INITIALIZED);
        this.senderAddress.set(senderAddress);
    }

    public BigInteger getNext(){
        return next.getOrDefault(BigInteger.ZERO);
    }

    public void setNext(BigInteger nextId){
        next.set(nextId);
    }

    public BigInteger getPrev(){
        return prev.getOrDefault(BigInteger.ZERO);
    }

    public void setPrev(BigInteger prev_id){
        prev.set(prev_id);
    }

}
