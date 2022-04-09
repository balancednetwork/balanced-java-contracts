package network.balanced.score.lib.test.mock;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.ServiceManager;
import score.Address;

import com.iconloop.score.test.Score;
import org.mockito.Mockito;

public class MockContract<T> {
    final private static Address EOA_ZERO = Address.fromString("cx" + "0".repeat(40));
    public final Account account;
    public final T mock;

    public MockContract(Class<T> classToMock, ServiceManager sm, Account admin) throws Exception {
        mock = Mockito.mock(classToMock);
        Score score = sm.deploy(admin, classToMock, EOA_ZERO);
        score.setInstance(mock);
        account = score.getAccount();
    }

    public Address getAddress() {
        return account.getAddress();
    }
}