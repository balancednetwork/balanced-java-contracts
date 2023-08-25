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

package network.balanced.score.lib.tokens;

import static java.math.BigInteger.TEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.math.BigInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;

import network.balanced.score.lib.test.mock.MockBalanced;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.lib.interfaces.tokens.SpokeTokenMessages;
import score.Context;
import score.DictDB;
import score.annotation.External;
import xcall.score.lib.interfaces.XCall;
import xcall.score.lib.interfaces.XTokenReceiver;
import xcall.score.lib.interfaces.XTokenReceiverScoreInterface;
import xcall.score.lib.util.NetworkAddress;

class SpokeTokenTest extends TestBase {
    private static final String name = "MyIRC2Token";
    private static final String symbol = "MIT";
    private static final BigInteger decimals = BigInteger.valueOf(18);

    private static final BigInteger totalSupply = BigInteger.valueOf(100000).multiply(TEN.pow(decimals.intValue()));
    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();

    private static Score tokenScore;

    private static MockContract<XTokenReceiver> receiverContract;
    private static MockBalanced mockBalanced;
    private static MockContract<XCall> xCall;
    private static SpokeTokenTester tokenSpy;
    private static String ICON_NID = "01.ICON";

    public static class SpokeTokenTester extends SpokeTokenImpl {
        public SpokeTokenTester(String _nid, String _tokenName, String _symbolName, BigInteger _decimals, BigInteger _initialSupply) {
            super(_nid, _tokenName, _symbolName, _decimals);

            // mint the initial token supply here
            mint(new NetworkAddress(_nid, Context.getCaller()), _initialSupply);
        }
        private DictDB<score.Address, BigInteger> legacyAddressDB = Context.newDictDB(BALANCES, BigInteger.class);

        @External
        public void addOldBalance(score.Address address, BigInteger balance) {
            legacyAddressDB.set(address, balance);
        }
    }

    @BeforeEach
    public void setup() throws Exception {
        tokenScore = sm.deploy(owner, SpokeTokenTester.class,
            ICON_NID, name, symbol, decimals, totalSupply);

        mockBalanced = new MockBalanced(sm, owner);
        xCall = mockBalanced.xCall;
        tokenSpy = (SpokeTokenTester) spy(tokenScore.getInstance());
        tokenScore.setInstance(tokenSpy);
        receiverContract = new MockContract<>(XTokenReceiverScoreInterface.class, sm, owner);
    }

    @Test
    void transfer_ICONUserToICONUser() {
        // Arrange
        Account alice = sm.createAccount();
        Account bob = sm.createAccount();
        BigInteger amount = BigInteger.TWO.pow(18);
        addBalance(alice, amount);

        // Act
        tokenScore.invoke(alice, "transfer", bob.getAddress(), amount, new byte[0]);

        // Assert
        assertEquals(BigInteger.ZERO, balanceOf(alice));
        assertEquals(amount, balanceOf(bob));
        verify(tokenSpy).Transfer(alice.getAddress(), bob.getAddress(), amount, new byte[0]);
    }

    @Test
    void hubTransfer_ICONUserToICONUser() {
         // Arrange
        Account alice = sm.createAccount();
        Account bob = sm.createAccount();
        BigInteger amount = BigInteger.TWO.pow(18);
        addBalance(alice, amount);

        // Act
        tokenScore.invoke(alice, "hubTransfer", bob.getAddress().toString(), amount, new byte[0]);

        // Assert
        assertEquals(BigInteger.ZERO, balanceOf(alice));
        assertEquals(amount, balanceOf(bob));
        verify(tokenSpy).Transfer(alice.getAddress(), bob.getAddress(), amount, new byte[0]);
    }

    @Test
    void transfer_ICONUserToICONContract() {
        // Arrange
        Account alice = sm.createAccount();
        BigInteger amount = BigInteger.TWO.pow(18);
        addBalance(alice, amount);

        // Act
        tokenScore.invoke(alice, "transfer", receiverContract.getAddress(), amount, new byte[0]);

        // Assert
        assertEquals(BigInteger.ZERO, balanceOf(alice));
        assertEquals(amount, balanceOf(receiverContract.account));
        verify(tokenSpy).Transfer(alice.getAddress(), receiverContract.getAddress(), amount, new byte[0]);
        verify(receiverContract.mock).tokenFallback(alice.getAddress(), amount, new byte[0]);
    }

    @Test
    void hubTransfer_ICONUserToICONContract() {
        // Arrange
        Account alice = sm.createAccount();
        BigInteger amount = BigInteger.TWO.pow(18);
        addBalance(alice, amount);

        // Act
        tokenScore.invoke(alice, "hubTransfer", receiverContract.getAddress().toString(), amount, new byte[0]);

        // Assert
        assertEquals(BigInteger.ZERO, balanceOf(alice));
        assertEquals(amount, balanceOf(receiverContract.account));
        verify(tokenSpy).Transfer(alice.getAddress(), receiverContract.getAddress(), amount, new byte[0]);
        verify(receiverContract.mock).tokenFallback(alice.getAddress(), amount, new byte[0]);    }


    @Test
    void hubTransfer_ICONUserToXCallUser() {
        // Arrange
        Account alice = sm.createAccount();
        NetworkAddress bob = new NetworkAddress("01.eth", "0x1");
        BigInteger amount = BigInteger.TWO.pow(18);
        addBalance(alice, amount);

        // Act
        tokenScore.invoke(alice, "hubTransfer", bob.toString(), amount, new byte[0]);

        // Assert
        assertEquals(BigInteger.ZERO, balanceOf(alice));
        assertEquals(amount, balanceOf(bob));
        verify(tokenSpy).Transfer(alice.getAddress(), SpokeTokenImpl.ZERO_ADDRESS, amount, new byte[0]);
        verify(tokenSpy).HubTransfer(SpokeTokenImpl.ZERO_ADDRESS.toString(), bob.toString(), amount, new byte[0]);
    }

    @Test
    void hubTransfer_XCallUserToICONUser() {
        // Arrange
        NetworkAddress alice = new NetworkAddress("01.eth", "0x1");
        Account bob = sm.createAccount();
        BigInteger amount = BigInteger.TWO.multiply(BigInteger.TEN.pow(18));
        addBalance(alice, amount);

        // Act
        byte[] msg = SpokeTokenMessages.xHubTransfer(new NetworkAddress(ICON_NID, bob.getAddress()).toString(), amount, new byte[0]);

        tokenScore.invoke(xCall.account, "handleCallMessage", alice.toString(), msg);

        // Assert
        assertEquals(BigInteger.ZERO, balanceOf(alice));
        assertEquals(amount, balanceOf(bob));
        verify(tokenSpy).HubTransfer(alice.toString(), SpokeTokenImpl.ZERO_ADDRESS.toString(), amount, new byte[0]);
        verify(tokenSpy).Transfer(SpokeTokenImpl.ZERO_ADDRESS, bob.getAddress(), amount, new byte[0]);
    }

    @Test
    void hubTransfer_XCallUserToICONContract() {
        // Arrange
        NetworkAddress alice = new NetworkAddress("01.eth", "0x1");
        BigInteger amount = BigInteger.TWO.pow(18);
        NetworkAddress receiverContractNetworkAddress = new NetworkAddress(ICON_NID, receiverContract.getAddress().toString());
        addBalance(alice, amount);

        // Act
        byte[] msg = SpokeTokenMessages.xHubTransfer(receiverContractNetworkAddress.toString(), amount, new byte[0]);
        tokenScore.invoke(xCall.account, "handleCallMessage", alice.toString(), msg);

        // Assert
        assertEquals(BigInteger.ZERO, balanceOf(alice));
        assertEquals(amount, balanceOf(receiverContract.account));
        verify(tokenSpy).HubTransfer(alice.toString(), SpokeTokenImpl.ZERO_ADDRESS.toString(), amount, new byte[0]);
        verify(tokenSpy).Transfer(SpokeTokenImpl.ZERO_ADDRESS, receiverContract.getAddress(), amount, new byte[0]);
        verify(receiverContract.mock).xTokenFallback(alice.toString(), amount, new byte[0]);
    }

    @Test
    void migrateOldPosition_onTransfer() {
        // Arrange
        Account alice = sm.createAccount();
        Account bob = sm.createAccount();
        BigInteger amount = BigInteger.TEN.pow(20);
        BigInteger transferAmount = BigInteger.TEN.pow(19);
        tokenScore.invoke(owner, "addOldBalance", bob.getAddress(), amount);

        // Act
        tokenScore.invoke(bob, "transfer", alice.getAddress(), transferAmount, new byte[0]);

        // Assert
        assertEquals(transferAmount, balanceOf(alice));
        assertEquals(amount.subtract(transferAmount), balanceOf(bob));
    }

    @Test
    void migrateOldPosition_onReceive() {
        // Arrange
        Account alice = sm.createAccount();
        Account bob = sm.createAccount();
        BigInteger amount = BigInteger.TEN.pow(19);
        BigInteger transferAmount = BigInteger.TEN.pow(20);
        addBalance(alice, transferAmount);
        tokenScore.invoke(owner, "addOldBalance", bob.getAddress(), amount);

        // Act
        tokenScore.invoke(alice, "transfer", bob.getAddress(), transferAmount, new byte[0]);

        // Assert
        assertEquals(BigInteger.ZERO, balanceOf(alice));
        assertEquals(amount.add(transferAmount), balanceOf(bob));
    }

    void addBalance(Account account, BigInteger amount) {
        tokenScore.invoke(owner, "transfer", account.getAddress(), amount, new byte[0]);
    }

    void addBalance(NetworkAddress account, BigInteger amount) {
        tokenScore.invoke(owner, "hubTransfer", account.toString(), amount, new byte[0]);
    }

    BigInteger balanceOf(Account account) {
        return (BigInteger)tokenScore.call("balanceOf", account.getAddress());
    }

    BigInteger balanceOf(NetworkAddress account) {
        return (BigInteger)tokenScore.call("xBalanceOf", account.toString());
    }
}