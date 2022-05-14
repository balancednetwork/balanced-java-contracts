package network.balanced.score.lib.utils;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;
import scorex.util.ArrayList;

import java.math.BigInteger;
import java.util.List;

import static network.balanced.score.lib.TestHelper.expectErrorMessage;
import static org.junit.jupiter.api.Assertions.*;

public class SetDBTest extends TestBase {

    public static final BigInteger ZERO_ADDRESS = BigInteger.ZERO;
    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private final static BigInteger address1 = BigInteger.ONE;
    private final static BigInteger address2 = BigInteger.TWO;
    private final static BigInteger address3 = BigInteger.valueOf(3);
    private final static BigInteger address4 = BigInteger.valueOf(4);
    private final static BigInteger address5 = BigInteger.valueOf(5);
    private static Score dummyScore;

    public static class DummyScore  {
        public static final BigInteger ZERO_ADDRESS = BigInteger.ZERO;
        SetDB<BigInteger> addressBagDBUnordered = new SetDB<>("address_bag_unordered", BigInteger.class, null);
        SetDB<BigInteger> addressBagDBOrdered = new SetDB<>("address_bag_unordered", BigInteger.class, true);
        public DummyScore() {

        }
        public void   addUnorderedAddressItems(ArrayList<BigInteger > addresses){
            for (BigInteger address: addresses) {
                addressBagDBUnordered.add(address);
            }
        }

        public Boolean itemUnorderedContains(BigInteger item){
            if(item.equals(ZERO_ADDRESS)){
                item = null;
            }
           return addressBagDBUnordered.contains(item);
        }

        public void itemUnorderedRemove(BigInteger item){
            if(item.equals(ZERO_ADDRESS)){
                item = null;
            }
             addressBagDBUnordered.remove(item);
        }

        public List<BigInteger> getUnorderedAddressItems(){
            List<BigInteger> addresses = new ArrayList<>();
            for(int i =0; i<addressBagDBUnordered.size(); i++){
                addresses.add(addressBagDBUnordered.get(i));
            }
            return addresses;
        }

        public void   addOrderedAddressItems(ArrayList<BigInteger> addresses){
            for (BigInteger address: addresses) {
                addressBagDBOrdered.add(address);
            }
        }

        public Boolean itemOrderedContains(BigInteger item){
            if(item.equals(ZERO_ADDRESS)){
                item = null;
            }
            return addressBagDBOrdered.contains(item);
        }

        public void itemOrderedRemove(BigInteger item){
            if(item.equals(ZERO_ADDRESS)){
                item = null;
            }
            addressBagDBOrdered.remove(item);
        }

        public List<BigInteger> getOrderedAddressItems(){
            List<BigInteger> addresses = new ArrayList<>();
            for(int i =0; i<addressBagDBOrdered.size(); i++){
                addresses.add(addressBagDBOrdered.get(i));
            }
            return addresses;
        }

    }


    @BeforeAll
    public static void setup() throws Exception {
        dummyScore = sm.deploy(owner, DummyScore.class);
    }

    @Test
    public void testUnorderedAddressBag(){
        List<BigInteger> addressBagDBUnordered = new ArrayList<>();
        addressBagDBUnordered.add(address1);
        addressBagDBUnordered.add(address2);
        addressBagDBUnordered.add(null);
        addressBagDBUnordered.add(address3);
        addressBagDBUnordered.add(address3);
        addressBagDBUnordered.add(address4);
        dummyScore.invoke(owner, "addUnorderedAddressItems", addressBagDBUnordered);
        List<BigInteger> retAddressBagDBUnordered = (List<BigInteger>) dummyScore.call("getUnorderedAddressItems");
        assertNotEquals(addressBagDBUnordered.size(), retAddressBagDBUnordered.size());
        assertEquals(addressBagDBUnordered.size()-1, retAddressBagDBUnordered.size());
        assertTrue(retAddressBagDBUnordered.contains(address1));
        assertTrue(retAddressBagDBUnordered.contains(null));
        assertFalse(retAddressBagDBUnordered.contains(address5));

        assertTrue((Boolean) dummyScore.call("itemUnorderedContains", address1));
        assertTrue((Boolean) dummyScore.call("itemUnorderedContains", ZERO_ADDRESS));
        assertFalse((Boolean) dummyScore.call("itemUnorderedContains", address5));

        dummyScore.invoke(owner, "itemUnorderedRemove", address1);
        assertFalse((Boolean) dummyScore.call("itemUnorderedContains", address1));
        assertTrue((Boolean) dummyScore.call("itemUnorderedContains", ZERO_ADDRESS));

        dummyScore.invoke(owner, "itemUnorderedRemove", ZERO_ADDRESS);
        assertFalse((Boolean) dummyScore.call("itemUnorderedContains", ZERO_ADDRESS));
        assertTrue((Boolean) dummyScore.call("itemUnorderedContains", address4));

        Executable call = () -> dummyScore.invoke(owner, "itemUnorderedRemove", address5);
        expectErrorMessage(call, "Reverted(0): Item not found " + address5);
    }

    @Test
    public void testOrderedAddressBag(){
        List<BigInteger> addressBagDBOrdered = new ArrayList<>();
        addressBagDBOrdered.add(address1);
        addressBagDBOrdered.add(address2);
        addressBagDBOrdered.add(null);
        addressBagDBOrdered.add(address3);
        addressBagDBOrdered.add(address4);
        dummyScore.invoke(owner, "addOrderedAddressItems", addressBagDBOrdered);
        List<BigInteger> retAddressBagDBUnordered = (List<BigInteger>) dummyScore.call("getOrderedAddressItems");
        assertEquals(addressBagDBOrdered.size(), retAddressBagDBUnordered.size());

        assertTrue(retAddressBagDBUnordered.contains(address1));
        assertTrue(retAddressBagDBUnordered.contains(null));
        assertFalse(retAddressBagDBUnordered.contains(address5));

        assertTrue((Boolean) dummyScore.call("itemOrderedContains", address1));
        assertTrue((Boolean) dummyScore.call("itemOrderedContains", ZERO_ADDRESS));
        assertFalse((Boolean) dummyScore.call("itemOrderedContains", address5));

        dummyScore.invoke(owner, "itemOrderedRemove", address1);
        assertFalse((Boolean) dummyScore.call("itemOrderedContains", address1));
        assertTrue((Boolean) dummyScore.call("itemOrderedContains", ZERO_ADDRESS));

        dummyScore.invoke(owner, "itemOrderedRemove", ZERO_ADDRESS);
        assertFalse((Boolean) dummyScore.call("itemOrderedContains", ZERO_ADDRESS));
        assertTrue((Boolean) dummyScore.call("itemOrderedContains", address4));
        Executable call = () -> dummyScore.invoke(owner, "itemUnorderedRemove", address5);
        expectErrorMessage(call, "Reverted(0): Item not found " + address5);
    }

}
