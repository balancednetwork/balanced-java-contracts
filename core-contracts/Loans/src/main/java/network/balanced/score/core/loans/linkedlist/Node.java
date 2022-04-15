package network.balanced.score.core.loans.linkedlist;

import score.Context;
import score.VarDB;

import java.math.BigInteger;

public class Node {
    
    public final String _NAME = "_Node";
    public String name;
    public VarDB<String> nodeData;
    public String dataString;
    public BigInteger value;
    public int next;
    public int prev;

    public Node(String dbName) {
        name = dbName + _NAME;
        nodeData = Context.newVarDB(name + "_node_data", String.class);
        setup();
    }

    public Node(String dbName, String key) {
        name = dbName + _NAME;
        nodeData = (VarDB<String>) Context.newBranchDB(name + "_node_data", String.class).at(key);
        setup();
    }

    private void setup() {
        dataString = "";
        value = BigInteger.ZERO;
        next = 0;
        prev = 0;
        unpack();
    }

    public void unpack() {
        dataString = nodeData.getOrDefault("");
        if (dataString.equals("")) {
            return;
        }

        int delimiter1 = dataString.indexOf("|");
        int delimiter2 = dataString.lastIndexOf("|");
        value = new BigInteger(dataString.substring(0, delimiter1));
        next = Integer.parseInt(dataString.substring(delimiter1+1, delimiter2));
        prev = Integer.parseInt(dataString.substring(delimiter2+1, dataString.length()));
    }

    public void repack() {
        String data = value.toString() + "|" + next +  "|" + prev;
        nodeData.set(data);
    }

    public void delete() {
        nodeData.set("");   
    }

    public boolean exists() {
        return !dataString.equals("");
    }

    public BigInteger getValue() {
        return value;
    }

    public void setValue(BigInteger value){
        this.value = value;
    }

    public int getNext(){
        return next;
    }

    public void setNext(int nextId){
        next = nextId;
    }

    public int getPrev(){
        return prev;
    }

    public void setPrev(int prevId){
        prev = prevId;
    }
}