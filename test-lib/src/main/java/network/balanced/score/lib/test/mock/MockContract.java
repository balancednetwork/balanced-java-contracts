package network.balanced.score.lib.test.mock;


import java.util.Random;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.ServiceManager;
import score.Address;

import com.iconloop.score.test.Score;
import org.mockito.Mockito;

public class MockContract<T> {
    public final Account account;
    public final T mock;
    private static final  Random generator = new Random();

    public MockContract(Class<T> classToMock, ServiceManager sm, Account admin) throws Exception {
        Account asd = Account.newScoreAccount(generator.nextInt());
        mock = Mockito.mock(classToMock);
        Score score = sm.deploy(admin, classToMock, asd.getAddress());
        score.setInstance(mock);
        account = score.getAccount();
    }

    public Address getAddress() {
        return account.getAddress();
    }
}