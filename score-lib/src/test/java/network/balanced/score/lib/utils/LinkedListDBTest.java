package network.balanced.score.lib.utils;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;
import score.UserRevertException;
import scorex.util.ArrayList;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class LinkedListDBTest extends TestBase {

    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private static final Address user1 = sm.createAccount().getAddress();
    private static final Address user2 = sm.createAccount().getAddress();
    private static final Address user3 = sm.createAccount().getAddress();
    private static final Address user4 = sm.createAccount().getAddress();
    private static final Address user5 = sm.createAccount().getAddress();
    private static final Address user6 = sm.createAccount().getAddress();
    private static final Address user7 = sm.createAccount().getAddress();

    private static Score dummyScore;

    public static class DummyScore  {

        LinkedListDB linkedListDB = new LinkedListDB("linked_list_db");

        public DummyScore() {

        }

        public void appendUsers(ArrayList<Address > addresses){
            int id = 1;
            for (Address address: addresses) {
                linkedListDB.append(BigInteger.valueOf(3), address, BigInteger.valueOf(id++));
            }
        }

        public void appendUserWithId(Address address, BigInteger id){
            linkedListDB.append(BigInteger.valueOf(3), address, id);
        }

        public BigInteger size(){
            return linkedListDB.size();
        }

        public Map<String, Object> headNode(){
            NodeDB node = linkedListDB.getHeadNode();
            return Map.of("user", node.getUser(), "size", node.getSize(), "prev", node.getPrev(), "next", node.getNext());
        }

        public Map<String, Object> tailNode(){
            NodeDB node = linkedListDB.getTailNode();
            return Map.of("user", node.getUser(), "size", node.getSize(), "prev", node.getPrev(), "next", node.getNext());
        }

        public void updateNode(BigInteger nodeId, BigInteger size, Address user){
             linkedListDB.updateNode(nodeId, size, user);
        }

        public Map<String, Object> getNode(BigInteger nodeId){
            NodeDB node = linkedListDB.getNode(nodeId);
            return Map.of("user", node.getUser(), "size", node.getSize(), "prev", node.getPrev(), "next", node.getNext());
        }

        public void removeHead(){
            linkedListDB.removeHead();
        }

        public void removeTail(){
            linkedListDB.removeTail();
        }

        public void remove(BigInteger nodeId){
            linkedListDB.remove(nodeId);
        }

        public void clear(){
            linkedListDB.clear();
        }

    }

    @BeforeAll
    public static void setup() throws Exception {
        dummyScore = sm.deploy(owner, DummyScore.class);
    }

    @Test
    public void testUnorderedAddressBag(){
        List<Address> users = new ArrayList<>();
        users.add(user1);
        users.add(user2);
        users.add(user3);
        users.add(user4);
        users.add(user5);
        dummyScore.invoke(owner, "appendUsers", users);
        assertEquals(BigInteger.valueOf(5), dummyScore.call( "size"));
        dummyScore.invoke(owner, "removeHead");
        assertEquals(BigInteger.valueOf(4), dummyScore.call( "size"));
        dummyScore.invoke(owner, "removeTail");
        assertEquals(BigInteger.valueOf(3), dummyScore.call( "size"));

        dummyScore.invoke(owner, "remove", BigInteger.valueOf(3));
        assertEquals(BigInteger.valueOf(2), dummyScore.call( "size"));
        Map<String, Object> nodeDB = (Map<String, Object>) dummyScore.call( "getNode", BigInteger.TWO);
        assertEquals(nodeDB.get("size"), BigInteger.valueOf(3));
        assertEquals(nodeDB.get("user"), user2);
        assertEquals(nodeDB.get("prev"), BigInteger.ZERO);
        assertEquals(nodeDB.get("next"), BigInteger.valueOf(4));

        dummyScore.invoke(owner, "updateNode", BigInteger.TWO, BigInteger.valueOf(5), user6);
        Map<String, Object> nodeTwo = (Map<String, Object>) dummyScore.call( "getNode", BigInteger.TWO);
        assertEquals(nodeTwo.get("size"), BigInteger.valueOf(5));
        assertEquals(nodeTwo.get("user"), user6);
        assertEquals(nodeDB.get("prev"), BigInteger.ZERO);
        assertEquals(nodeTwo.get("next"), BigInteger.valueOf(4));

        Map<String, Object> headNode = (Map<String, Object>) dummyScore.call( "headNode");
        Map<String, Object> tailNode = (Map<String, Object>) dummyScore.call( "tailNode");
        assertEquals(headNode.get("user"), user6);
        assertEquals(headNode.get("next"), BigInteger.valueOf(4));


        assertEquals(tailNode.get("user"), user4);
        assertEquals(tailNode.get("prev"), BigInteger.valueOf(2));

        AssertionError e = Assertions.assertThrows(AssertionError.class, () -> {
            dummyScore.call( "getNode", BigInteger.valueOf(7));
        });
        assertEquals(e.getMessage(), "Reverted(0): Linked List linked_list_db_LINKED_LISTDB Node not found of nodeId " + "7");

        e = Assertions.assertThrows(AssertionError.class, () -> {
            dummyScore.invoke(owner, "appendUserWithId", user7, BigInteger.TWO);
        });
        assertEquals(e.getMessage(), "Reverted(0): Linked List linked_list_db_LINKED_LISTDB already exists of nodeId." + "2");
    }



}
