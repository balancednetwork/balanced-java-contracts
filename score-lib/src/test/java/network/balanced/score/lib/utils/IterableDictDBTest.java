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

import static org.junit.jupiter.api.Assertions.*;

public class IterableDictDBTest extends TestBase {

    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();

    //address
    private final static Address address1 = sm.createAccount().getAddress();
    private final static Address address2 = sm.createAccount().getAddress();
    private final static Address address3 = sm.createAccount().getAddress();
    private final static Address address4 = sm.createAccount().getAddress();
    private final static Address address5 = sm.createAccount().getAddress();

    //values
    private final static BigInteger value1 = BigInteger.ONE;
    private final static BigInteger value2 = BigInteger.TWO;
    private final static BigInteger value3 = BigInteger.valueOf(3);
    private final static BigInteger value4 = BigInteger.valueOf(4);
    private final static BigInteger value5 = BigInteger.valueOf(5);
    private static Score dummyScore;

    public static class DummyScore {

        IterableDictDB<Address, BigInteger> iterabledd = new IterableDictDB<>("iterabledd", BigInteger.class, Address.class, false);

        public DummyScore() {

        }

        public void setAddressesValues(ArrayList<Address> addresses, ArrayList<BigInteger> values) {
            for (Address address : addresses) {
                iterabledd.set(address, values.get(addresses.indexOf(address)));
            }
        }

        public BigInteger valueOf(Address address) {
            return iterabledd.get(address);
        }

        public List<Address> getAddresses() {
            return iterabledd.keys();
        }

        public List<BigInteger> getValues() {
            List<Address> keys = iterabledd.keys();
            List<BigInteger> valueList = new ArrayList<>();
            for (Address key : keys) {
                valueList.add(iterabledd.get(key));
            }
            return valueList;
        }

    }

    @BeforeAll
    public static void setup() throws Exception {
        dummyScore = sm.deploy(owner, DummyScore.class);
    }

    @Test
    public void testSetGetIterableDictDB() {
        List<BigInteger> values = new ArrayList<>();
        values.add(value1);
        values.add(value2);
        values.add(value3);
        values.add(value4);
        values.add(value5);

        List<Address> addresses = new ArrayList<>();
        addresses.add(address1);
        addresses.add(address2);
        addresses.add(address3);
        addresses.add(address4);
        addresses.add(address5);


        dummyScore.invoke(owner, "setAddressesValues", addresses, values);

        List<Address> keys = (List<Address>) dummyScore.call("getAddresses");

        assertEquals(5, keys.size());

        assertTrue(keys.contains(address1));
        assertTrue(keys.contains(address2));
        assertTrue(keys.contains(address3));
        assertTrue(keys.contains(address4));
        assertTrue(keys.contains(address5));

        assertEquals(value3, dummyScore.call("valueOf", address3));


        List<BigInteger> amounts = (List<BigInteger>) dummyScore.call("getValues");

        assertEquals(5, amounts.size());

        assertTrue(amounts.contains(value1));
        assertTrue(amounts.contains(value2));
        assertTrue(amounts.contains(value3));
        assertTrue(amounts.contains(value4));
        assertTrue(amounts.contains(value5));
    }

}
