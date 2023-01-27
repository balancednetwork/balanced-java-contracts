# XCall annotations

## XCall methods
### Usage
Annotate `@XCall` to a interface or a public method in the score.
Note all methods annotated with `@XCall` has the have the first parameter `String from` which represents the usual `Context.getCaller()`.
````java
public interface ExampleScore {
    @XCall
    void methodOne(String from, BigInteger amount, byte[] data);

    @XCall
    void methodTwo(String from, String message);

    void handleCallMessage(String _from, byte[] _data);
}
````

When java compiles, the class ExampleScoreXCall will be generated.
Then in your `handleCallMessage` you can use this class to parse the messages.

````java
import score.Address;
import score.annotation.External;

import java.math.BigInteger;

public class ExampleScoreImpl implements ExampleScore {
    void methodOne(String from, BigInteger amount, byte[] data){
    }

    void methodTwo(String from, String message){
    }

    @External
    void handleCallMessage(String _from, byte[] _data) {
        // Verify caller is XCall contract or other allowed contract.
        ExampleScoreXCall.process(this, _from, _data);
    }
}
````

## XCall messages
When annotating methods with XCall a message factory is also generated for testing purposes.
Using the above interface (ExampleScore). The class ExampleScoreMessages is generated under the package network.balanced.score.xcall.messages.ExampleScoreMessages. This class can then be used in both unit and integration tests to generate correct byte messages.


ExampleScoreImplTest class in src/test/java/...
````java
import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import network.balanced.score.xcall.messages.ExampleScoreMessages;
import network.balanced.score.xcall.util.XCallMessage;

public class ExampleScoreImplTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private static final Account xCallScore = Account.newScoreAccount(1);

    private static Score score;

    @BeforeEach
    public void setup() throws Exception {
        score = sm.deploy(owner, ExampleScoreImpl.class);
    }

    @Test
    public void testMethodTwo() {
        String from = "<BTPAddress>";
        XCallMessage msg = ExampleScoreMessages.methodTwo("example message");
        score.invoke(xCallScore, "handleCallMessage", from, msg.toBytes());
    }
}
````
