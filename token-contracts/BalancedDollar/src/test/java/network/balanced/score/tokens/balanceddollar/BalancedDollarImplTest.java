/*
 * Copyright (c) 2022-2023 Balanced.network.
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

package network.balanced.score.tokens.balanceddollar;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;

import network.balanced.score.lib.test.mock.MockBalanced;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import score.Address;
import score.Context;
import xcall.score.lib.util.NetworkAddress;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.test.UnitTest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BalancedDollarImplTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();

    private static MockBalanced mockBalanced;
    private static Account governanceScore;
    private static Score bnUSDScore;
    private BalancedDollarImpl bnUSDSpy;

    private String name = "Balanced Dollar";


    @BeforeEach
    void deploy() throws Exception {
        mockBalanced = new MockBalanced(sm, owner);
        governanceScore = mockBalanced.governance.account;
        when(mockBalanced.xCall.mock.getNetworkId()).thenReturn("0x1.ICON");

        bnUSDScore = sm.deploy(owner, BalancedDollarImpl.class, governanceScore.getAddress());

        bnUSDSpy = (BalancedDollarImpl) spy(bnUSDScore.getInstance());
        bnUSDScore.setInstance(bnUSDSpy);
    }

    @Test
    void govTransfer() {
        Account nonGovernance = Account.newScoreAccount(scoreCount++);
        String expectedErrorMessage =
                "Reverted(0): Authorization Check: Authorization failed. Caller: " + nonGovernance.getAddress() + " Authorized " +
                        "Caller: " + governanceScore.getAddress();
        Executable nonGovernanceTransfer = () -> bnUSDScore.invoke(nonGovernance, "govTransfer", owner.getAddress(),
                sm.createAccount().getAddress(), ICX, new byte[0]);
        expectErrorMessage(nonGovernanceTransfer, expectedErrorMessage);

        bnUSDScore.invoke(mockBalanced.loans.account, "mint", BigInteger.valueOf(123).multiply(ICX), new byte[0]);

        Account receiverAccount = sm.createAccount();
        bnUSDScore.invoke(governanceScore, "govTransfer", mockBalanced.loans.getAddress(), receiverAccount.getAddress(), ICX,
                new byte[0]);
        assertEquals(ICX, bnUSDScore.call("balanceOf", receiverAccount.getAddress()));
    }

    @Test
    void govHubTransfer() {
        Account nonGovernance = Account.newScoreAccount(scoreCount++);
        NetworkAddress receiverAccount = new NetworkAddress("0x1.ETH", sm.createAccount().getAddress());
        NetworkAddress loansNetworkAddress = new NetworkAddress("0x1.ICON", mockBalanced.loans.getAddress());

        String expectedErrorMessage =
                "Reverted(0): Authorization Check: Authorization failed. Caller: " + nonGovernance.getAddress() + " Authorized " +
                        "Caller: " + governanceScore.getAddress();
        Executable nonGovernanceTransfer = () -> bnUSDScore.invoke(nonGovernance, "govHubTransfer", loansNetworkAddress.toString(),
            receiverAccount.toString(), ICX, new byte[0]);
        expectErrorMessage(nonGovernanceTransfer, expectedErrorMessage);

        bnUSDScore.invoke(mockBalanced.loans.account, "mint", BigInteger.valueOf(123).multiply(ICX), new byte[0]);


        bnUSDScore.invoke(governanceScore, "govHubTransfer", loansNetworkAddress.toString(), receiverAccount.toString(), ICX,
                new byte[0]);
        assertEquals(ICX, bnUSDScore.call("xBalanceOf", receiverAccount.toString()));
    }

    @Test
    void mintTo_withTwoMinters() {
        mintTests(mockBalanced.stability.account);
        mintTests(mockBalanced.loans.account);
    }

    public void mintTests(Account minter) {
        Account newReceiver = sm.createAccount();
        BigInteger mintAmount = BigInteger.valueOf(193).multiply(ICX);

        Account nonMinter = sm.createAccount();
        String expectedErrorMessage =
                "Reverted(0): Authorization Check: Authorization failed. Caller: " + nonMinter.getAddress() + " Authorized Caller:" +
                        " " + mockBalanced.loans.getAddress() + " or " + mockBalanced.stability.getAddress();
        Executable nonMinterCall = () -> bnUSDScore.invoke(nonMinter, "mint", mintAmount, new byte[0]);
        expectErrorMessage(nonMinterCall, expectedErrorMessage);

        Executable nonMinterMintToCall = () -> bnUSDScore.invoke(nonMinter, "mintTo", sm.createAccount().getAddress()
                , mintAmount, new byte[0]);
        expectErrorMessage(nonMinterMintToCall, expectedErrorMessage);

        expectedErrorMessage = "Reverted(0): " + name+ ": Owner address cannot be zero address";
        Executable zeroAddressMint = () -> bnUSDScore.invoke(minter, "mintTo", new Address(new byte[Address.LENGTH]),
                mintAmount, new byte[0]);
        expectErrorMessage(zeroAddressMint, expectedErrorMessage);

        expectedErrorMessage ="Reverted(0): " + name + ": Amount needs to be positive";
        Executable negativeAmountMint = () -> bnUSDScore.invoke(minter, "mintTo", newReceiver.getAddress(),
                ICX.negate(), new byte[0]);
        expectErrorMessage(negativeAmountMint, expectedErrorMessage);

        BigInteger beforeTotalSupply = (BigInteger) bnUSDScore.call("totalSupply");
        bnUSDScore.invoke(minter, "mintTo", newReceiver.getAddress(), mintAmount, new byte[0]);
        assertEquals(mintAmount, bnUSDScore.call("balanceOf", newReceiver.getAddress()));
        assertEquals(beforeTotalSupply.add(mintAmount), bnUSDScore.call("totalSupply"));
        verify(bnUSDSpy).Transfer(new Address(new byte[Address.LENGTH]), newReceiver.getAddress(), mintAmount, "mint".getBytes());
    }
}
