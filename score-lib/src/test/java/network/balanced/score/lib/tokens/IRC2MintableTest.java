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

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import score.Address;

import java.math.BigInteger;

import static network.balanced.score.lib.test.UnitTest.expectErrorMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class IRC2MintableTest extends TestBase {
    static final String name = "Test IRC2 Token";
    static final String symbol = "TEST";
    static final BigInteger decimals = BigInteger.valueOf(18);
    static final BigInteger initialSupply = BigInteger.valueOf(1000);

    static final BigInteger totalSupply = initialSupply.multiply(BigInteger.TEN.pow(decimals.intValue()));
    static final ServiceManager sm = getServiceManager();
    static final Account owner = sm.createAccount();
    private static Score tokenScore;
    private static IRC2Mintable tokenSpy;

    @BeforeEach
    void setup() throws Exception {
        tokenScore = sm.deploy(owner, IRC2Mintable.class, name, symbol, decimals);

        tokenScore.invoke(owner, "setMinter", owner.getAddress());
        tokenScore.invoke(owner, "mint", totalSupply, new byte[0]);
        owner.addBalance(symbol, totalSupply);

        tokenSpy = (IRC2Mintable) spy(tokenScore.getInstance());
        tokenScore.setInstance(tokenSpy);
    }

    @Test
    void name() {
        assertEquals(name, tokenScore.call("name"));
    }

    @Test
    void symbol() {
        assertEquals(symbol, tokenScore.call("symbol"));
    }

    @Test
    void decimals() {
        assertEquals(decimals, tokenScore.call("decimals"));
    }

    @Test
    void totalSupply() {
        assertEquals(totalSupply, tokenScore.call("totalSupply"));
    }

    @Test
    void balanceOf() {
        assertEquals(totalSupply, tokenScore.call("balanceOf", owner.getAddress()));
    }

    @Test
    void transfer() {
        Account alice = sm.createAccount();
        BigInteger value = BigInteger.TEN.pow(decimals.intValue());
        BigInteger ownerBalance = (BigInteger) tokenScore.call("balanceOf", owner.getAddress());

        tokenScore.invoke(owner, "transfer", alice.getAddress(), value, new byte[0]);
        assertEquals(ownerBalance.subtract(value), tokenScore.call("balanceOf", tokenScore.getOwner().getAddress()));
        assertEquals(value, tokenScore.call("balanceOf", alice.getAddress()));

        tokenScore.invoke(alice, "transfer", alice.getAddress(), value, new byte[0]);
        assertEquals(value, tokenScore.call("balanceOf", alice.getAddress()));
        verify(tokenSpy).Transfer(alice.getAddress(), alice.getAddress(), value, new byte[0]);
    }

    @Test
    void getMinter() {
        assertEquals(owner.getAddress(), tokenScore.call("getMinter"));
    }

    @Test
    void mintTo() {
        Account newReceiver = sm.createAccount();
        BigInteger mintAmount = BigInteger.valueOf(193).multiply(ICX);

        Account nonMinter = sm.createAccount();
        String expectedErrorMessage =
                "Authorization Check: Authorization failed. Caller: " + nonMinter.getAddress() + " Authorized Caller:" +
                        " " + owner.getAddress();
        Executable nonMinterCall = () -> tokenScore.invoke(nonMinter, "mint", mintAmount, new byte[0]);
        expectErrorMessage(nonMinterCall, expectedErrorMessage);

        Executable nonMinterMintToCall = () -> tokenScore.invoke(nonMinter, "mintTo", sm.createAccount().getAddress()
                , mintAmount, new byte[0]);
        expectErrorMessage(nonMinterMintToCall, expectedErrorMessage);

        expectedErrorMessage = name + ": Owner address cannot be zero address";
        Executable zeroAddressMint = () -> tokenScore.invoke(owner, "mintTo", new Address(new byte[Address.LENGTH]),
                mintAmount, new byte[0]);
        expectErrorMessage(zeroAddressMint, expectedErrorMessage);

        expectedErrorMessage = name + ": Amount needs to be positive";
        Executable negativeAmountMint = () -> tokenScore.invoke(owner, "mintTo", newReceiver.getAddress(),
                ICX.negate(), new byte[0]);
        expectErrorMessage(negativeAmountMint, expectedErrorMessage);

        BigInteger beforeTotalSupply = (BigInteger) tokenScore.call("totalSupply");
        tokenScore.invoke(owner, "mintTo", newReceiver.getAddress(), mintAmount, new byte[0]);
        assertEquals(mintAmount, tokenScore.call("balanceOf", newReceiver.getAddress()));
        assertEquals(beforeTotalSupply.add(mintAmount), tokenScore.call("totalSupply"));
        verify(tokenSpy).Transfer(IRC2Base.ZERO_ADDRESS, newReceiver.getAddress(), mintAmount, "mint".getBytes());
    }
}