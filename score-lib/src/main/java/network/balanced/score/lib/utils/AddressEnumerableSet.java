package network.balanced.score.lib.utils;

import com.iconloop.score.util.EnumerableSet;

import foundation.icon.xcall.NetworkAddress;
import score.Address;

public class AddressEnumerableSet {

    protected final EnumerableSet<Address> legacyEnumerableSet;
    protected final EnumerableSet<String> enumerableSet;

    public AddressEnumerableSet(String id){
        legacyEnumerableSet = new EnumerableSet<>(id, Address.class);
        enumerableSet = new EnumerableSet<>(id+"_migrated", String.class);
    }

    public int length() {
        return legacyEnumerableSet.length()+enumerableSet.length();
    }

    public String at(int index) {
        if(index< legacyEnumerableSet.length()){
            return legacyEnumerableSet.at(index).toString();
        }else{
            index = index - enumerableSet.length();
            return enumerableSet.at(index);
        }
    }

    public boolean contains(String value) {
        return enumerableSet.contains(value) || legacyEnumerableSet.contains(Address.fromString(NetworkAddress.valueOf(value).account()));
    }

    //remove if not needed
    public Integer indexOf(String value) {
        Integer index = enumerableSet.indexOf(value);
        Integer legacyIndex = legacyEnumerableSet.indexOf(Address.fromString(value)); //todo: check if throws error
        if(index!=null){
            return index + legacyEnumerableSet.length();
        }else return legacyIndex;
    }

    public void add(String value) {
        if (!contains(value)) {
            // add new value
            enumerableSet.add(value);
        }
    }

    public void remove(String value) {
        Integer index = enumerableSet.indexOf(value);
        if(index!=null){
            enumerableSet.remove(value);
        }else{
            Integer legacyIndex = legacyEnumerableSet.indexOf(Address.fromString(NetworkAddress.valueOf(value).account())); //todo: check if throws error
            if(legacyIndex!=null){
                legacyEnumerableSet.remove(Address.fromString(value));
            }
        }
    }

}
