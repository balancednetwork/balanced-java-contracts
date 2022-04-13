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

package network.balanced.score.tokens.workertoken;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import network.balanced.score.lib.test.VarargAnyMatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.List;

import static network.balanced.score.lib.test.UnitTest.*;
import static network.balanced.score.tokens.workertoken.WorkerTokenImpl.TAG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.internal.verification.VerificationModeFactory.times;

class WorkerTokenImplTest extends TestBase {

    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();

    private static Score workerToken;
    private static final Account admin = sm.createAccount();
    private static final Account governanceScore = Account.newScoreAccount(scoreCount++);
    private static final Account balnScore = Account.newScoreAccount(scoreCount++);

    private final MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS);

    @BeforeEach
    void setup() throws Exception {
        workerToken = sm.deploy(owner, WorkerTokenImpl.class, governanceScore.getAddress());
    }

    @Test
    void totalSupply() {
        BigInteger totalSupply = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(6));
        assertEquals(totalSupply, workerToken.call("totalSupply"));
    }

    @Test
    void setAndGetGovernance() {
        testGovernance(workerToken, governanceScore, owner);
    }

    @Test
    void setAndGetAdmin() {
        testAdmin(workerToken, governanceScore, admin);
    }

    @Test
    void setAndGetBaln() {
        testContractSettersAndGetters(workerToken, governanceScore, admin, "setBaln", balnScore.getAddress(),
                "getBaln");
    }

    @Test
    void adminTransfer() {
        workerToken.invoke(governanceScore, "setAdmin", admin.getAddress());

        Account nonAdmin = sm.createAccount();
        Account receiver = sm.createAccount();
        BigInteger transferValue = BigInteger.TEN.pow(6);

        Executable nonAdminTransfer = () -> workerToken.invoke(nonAdmin, "adminTransfer", owner.getAddress(),
                receiver.getAddress(), transferValue, new byte[0]);
        String expectedErrorMessage =
                "Authorization Check: Authorization failed. Caller: " + nonAdmin.getAddress() + " Authorized Caller: "
                        + admin.getAddress();
        expectErrorMessage(nonAdminTransfer, expectedErrorMessage);

        BigInteger ownerBalance = (BigInteger) workerToken.call("balanceOf", owner.getAddress());
        workerToken.invoke(admin, "adminTransfer", owner.getAddress(), receiver.getAddress(), transferValue,
                new byte[0]);
        assertEquals(ownerBalance.subtract(transferValue), workerToken.call("balanceOf", owner.getAddress()));
        assertEquals(transferValue, workerToken.call("balanceOf", receiver.getAddress()));
    }

    @Test
    void transfer() {
        int maxHolderCount = 400;
        Account[] receivers = new Account[maxHolderCount];

        for (int i = 0; i < maxHolderCount; i++) {
            receivers[i] = sm.createAccount();
        }

        BigInteger transferValue = BigInteger.TEN;
        for (int i = 0; i < maxHolderCount - 1; i++) {
            workerToken.invoke(owner, "transfer", receivers[i].getAddress(), transferValue, new byte[0]);
        }

        //working between existing owners
        workerToken.invoke(owner, "transfer", receivers[1].getAddress(), transferValue, new byte[0]);

        // Fails for new user
        // Even this fails, the address is already added in the db. This is due to limitation in unit test framework
        Executable maxHoldersReached = () -> workerToken.invoke(owner, "transfer",
                receivers[maxHolderCount - 1].getAddress(), transferValue, new byte[0]);
        String expectedErrorMessage = TAG + ": The maximum holder count of " + maxHolderCount + " has been reached. " +
                "Only transfers of whole balances or moves between current holders is allowed until the total holder " +
                "count is reduced.";
        expectErrorMessage(maxHoldersReached, expectedErrorMessage);

        //Reduce the holders count by sending the received amount back to owner
        workerToken.invoke(receivers[0], "transfer", owner.getAddress(), transferValue, new byte[0]);
        workerToken.invoke(owner, "transfer", receivers[maxHolderCount - 1].getAddress(), transferValue, new byte[0]);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void distribute() {
        workerToken.invoke(governanceScore, "setAdmin", admin.getAddress());
        workerToken.invoke(admin, "setBaln", balnScore.getAddress());

        int holdersCount = 13;
        Account[] holders = new Account[holdersCount];
        holders[0] = owner;

        for (int i = 1; i < holdersCount; i++) {
            holders[i] = sm.createAccount();
        }
        BigInteger transferValue = BigInteger.valueOf(997L);
        for (int i = 1; i < holdersCount; i++) {
            BigInteger transferAmount = transferValue.multiply(BigInteger.valueOf(i));
            workerToken.invoke(owner, "transfer", holders[i].getAddress(), transferAmount, new byte[0]);
            holders[i].addBalance("BALW", transferAmount);
        }
        holders[0].addBalance("BALW", (BigInteger) workerToken.call("balanceOf", owner.getAddress()));

        contextMock.reset();
        BigInteger totalBalnToDistribute = BigInteger.valueOf(6833L).multiply(ICX);
        contextMock.when(() -> Context.call(balnScore.getAddress(), "balanceOf", workerToken.getAddress())).thenReturn(totalBalnToDistribute);
        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("transfer"),
                ArgumentMatchers.argThat(new VarargAnyMatcher()))).thenReturn(null);

        ArgumentCaptor<Address> receiver = ArgumentCaptor.forClass(Address.class);
        ArgumentCaptor<BigInteger> receivableAmount = ArgumentCaptor.forClass(BigInteger.class);

        workerToken.invoke(owner, "distribute");
        contextMock.verify(() -> Context.call(balnScore.getAddress(), "balanceOf", workerToken.getAddress()));
        contextMock.verify(() -> Context.call(eq(balnScore.getAddress()), eq("transfer"), receiver.capture(),
                receivableAmount.capture(), any(byte[].class)), times(holdersCount));

        List<BigInteger> amountsReceived = receivableAmount.getAllValues();
        List<Address> distributionReceivers = receiver.getAllValues();

        BigInteger totalDistributedAmount = BigInteger.ZERO;
        for (BigInteger amount : amountsReceived) {
            totalDistributedAmount = totalDistributedAmount.add(amount);
        }
        assertEquals(totalBalnToDistribute, totalDistributedAmount);

        BigInteger totalTokenBalance = (BigInteger) workerToken.call("totalSupply");
        for (int i = 0; i < amountsReceived.size(); i++) {
            BigInteger amount = amountsReceived.get(i);
            Address distributionReceiver = distributionReceivers.get(i);

            BigInteger myTokenBalance = Account.getAccount(distributionReceiver).getBalance("BALW");
            BigInteger expectedAmountToReceive =
                    myTokenBalance.multiply(totalBalnToDistribute).divide(totalTokenBalance);
            assertEquals(expectedAmountToReceive, amount);
            totalTokenBalance = totalTokenBalance.subtract(myTokenBalance);
            totalBalnToDistribute = totalBalnToDistribute.subtract(expectedAmountToReceive);
        }
    }

    @Test
    void tokenFallback() {
        workerToken.invoke(governanceScore, "setAdmin", admin.getAddress());
        workerToken.invoke(admin, "setBaln", balnScore.getAddress());

        BigInteger totalBalnToDistribute = BigInteger.valueOf(6833L).multiply(ICX);
        Account nonBalnScore = Account.newScoreAccount(scoreCount++);
        Executable nonBalnTransfer = () -> workerToken.invoke(nonBalnScore, "tokenFallback", owner.getAddress(),
                totalBalnToDistribute, new byte[0]);
        String expectedErrorMessage = TAG + ": The Worker Token contract can only accept BALN tokens. Deposit not " +
                "accepted from" + nonBalnScore.getAddress() + "Only accepted from BALN = " + balnScore.getAddress();
        expectErrorMessage(nonBalnTransfer, expectedErrorMessage);
    }

    @AfterEach
    void closeMock() {
        contextMock.close();
    }
}
