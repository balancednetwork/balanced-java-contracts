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

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import score.Address;
import scorex.util.ArrayList;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BagDBTest extends TestBase {

    public static final Address ZERO_ADDRESS = new Address(new byte[Address.LENGTH]);
    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private final static Address address1 = sm.createAccount().getAddress();
    private final static Address address2 = sm.createAccount().getAddress();
    private final static Address address3 = sm.createAccount().getAddress();
    private final static Address address4 = sm.createAccount().getAddress();
    private final static Address address5 = sm.createAccount().getAddress();
    private static Score dummyScore;

    public static class DummyScore  {

        public static final Address ZERO_ADDRESS = new Address(new byte[Address.LENGTH]);
        BagDB<Address> addressBagDBUnordered = new BagDB<>("address_bag_unordered", Address.class, null);
        BagDB<Address> addressBagDBOrdered = new BagDB<>("address_bag_ordered", Address.class, true);

        public DummyScore() {

        }

        public void addUnorderedAddressItems(ArrayList<Address> addresses) {
            for (Address address : addresses) {
                addressBagDBUnordered.add(address);
            }
        }

        public Boolean itemUnorderedContains(Address item){
            if(item.equals(ZERO_ADDRESS)){
                item = null;
            }
           return addressBagDBUnordered.contains(item);
        }

        public void itemUnorderedRemove(Address item){
            if(item.equals(ZERO_ADDRESS)){
                item = null;
            }
             addressBagDBUnordered.remove(item);
        }

        public List<Address> getUnorderedAddressItems(){
            List<Address> addresses = new ArrayList<>();
            for(int i =0; i<addressBagDBUnordered.size(); i++){
                addresses.add(addressBagDBUnordered.get(i));
            }
            return addresses;
        }

        public void   addOrderedAddressItems(ArrayList<Address> addresses){
            for (Address address: addresses) {
                addressBagDBOrdered.add(address);
            }
        }

        public Boolean itemOrderedContains(Address item){
            if(item.equals(ZERO_ADDRESS)){
                item = null;
            }
            return addressBagDBOrdered.contains(item);
        }

        public void itemOrderedRemove(Address item){
            if(item.equals(ZERO_ADDRESS)){
                item = null;
            }
            addressBagDBOrdered.remove(item);
        }

        public List<Address> getOrderedAddressItems(){
            List<Address> addresses = new ArrayList<>();
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

    @SuppressWarnings("unchecked")
    @Test
    public void testUnorderedAddressBag(){
        List<Address> addressBagDBUnordered = new ArrayList<>();
        addressBagDBUnordered.add(address1);
        addressBagDBUnordered.add(address2);
        addressBagDBUnordered.add(address2);
        addressBagDBUnordered.add(null);
        addressBagDBUnordered.add(address3);
        addressBagDBUnordered.add(address3);
        addressBagDBUnordered.add(address4);
        dummyScore.invoke(owner, "addUnorderedAddressItems", addressBagDBUnordered);
        List<Address> retAddressBagDBUnordered = (List<Address>) dummyScore.call("getUnorderedAddressItems");

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
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testOrderedAddressBag(){
        List<Address> addressBagDBOrdered = new ArrayList<>();
        addressBagDBOrdered.add(address1);
        addressBagDBOrdered.add(address2);
        addressBagDBOrdered.add(null);
        addressBagDBOrdered.add(address3);
        dummyScore.invoke(owner, "addOrderedAddressItems", addressBagDBOrdered);
        List<Address> retAddressBagDBUnordered = (List<Address>) dummyScore.call("getOrderedAddressItems");

        assertTrue(retAddressBagDBUnordered.contains(address1));
        assertTrue(retAddressBagDBUnordered.contains(null));
        assertFalse(retAddressBagDBUnordered.contains(address4));

        assertTrue((Boolean) dummyScore.call("itemOrderedContains", address1));
        assertTrue((Boolean) dummyScore.call("itemOrderedContains", ZERO_ADDRESS));
        assertFalse((Boolean) dummyScore.call("itemOrderedContains", address5));

        dummyScore.invoke(owner, "itemOrderedRemove", address1);
        assertFalse((Boolean) dummyScore.call("itemOrderedContains", address1));
        assertTrue((Boolean) dummyScore.call("itemOrderedContains", ZERO_ADDRESS));

        dummyScore.invoke(owner, "itemOrderedRemove", ZERO_ADDRESS);
        assertFalse((Boolean) dummyScore.call("itemOrderedContains", ZERO_ADDRESS));
        assertFalse((Boolean) dummyScore.call("itemOrderedContains", address4));
    }

}
