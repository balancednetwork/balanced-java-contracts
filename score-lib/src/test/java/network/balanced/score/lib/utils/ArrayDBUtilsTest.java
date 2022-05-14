package network.balanced.score.lib.utils;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import score.ArrayDB;
import scorex.util.ArrayList;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static score.Context.newArrayDB;

public class ArrayDBUtilsTest extends TestBase {

    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private static Score dummyScore;

    public static class DummyScore {
        ArrayDB<String> arrayDB = newArrayDB("array_db", String.class);

        public DummyScore() {
        }

        public void addArrayElements(ArrayList<String> elements){
            for(String ele: elements){
                arrayDB.add(ele);
            }
        }

        public boolean containsInArrayDB(String ele){
            return ArrayDBUtils.arrayDBContains (arrayDB, ele);
        }

        public  void removeFromArrayDB(String ele){
             ArrayDBUtils.removeFromArrayDB(ele, arrayDB);
        }

        public int arrayDBSize(){
            return arrayDB.size();
        }
    }

    @BeforeAll
    public static void setup() throws Exception {
        dummyScore = sm.deploy(owner, DummyScore.class);
        List<String> list = new ArrayList<>();
        list.add("apple");
        list.add("banana");
        list.add("papaya");
        list.add("grapes");
        list.add("mango");
        dummyScore.invoke(owner, "addArrayElements", list);
    }

    @Test
    public void testArrayDbContains(){
        assertEquals(5, dummyScore.call("arrayDBSize"));
        assertTrue((boolean) dummyScore.call("containsInArrayDB", "papaya"));
        assertTrue((boolean) dummyScore.call("containsInArrayDB", "apple"));
        assertFalse((boolean) dummyScore.call("containsInArrayDB", "orange"));
    }

    @Test
    public void testRemoveFromArrayDb(){
        assertEquals(5, dummyScore.call("arrayDBSize"));
        assertTrue((boolean) dummyScore.call("containsInArrayDB", "papaya"));
        dummyScore.invoke(owner,"removeFromArrayDB", "papaya");
        assertFalse((boolean) dummyScore.call("containsInArrayDB", "papaya"));
        assertEquals(4, dummyScore.call("arrayDBSize"));
    }

}
