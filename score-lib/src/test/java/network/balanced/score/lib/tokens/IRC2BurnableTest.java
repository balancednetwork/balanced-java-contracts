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
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;

import java.math.BigInteger;

import static network.balanced.score.lib.test.UnitTest.expectErrorMessage;
import static network.balanced.score.lib.tokens.IRC2MintableTest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class IRC2BurnableTest extends TestBase {

    private static Score tokenScore;
    private static IRC2Burnable tokenSpy;

    @BeforeAll
    static void setup() throws Exception {
        tokenScore = sm.deploy(owner, IRC2Burnable.class, name, symbol, decimals);

        tokenScore.invoke(owner, "setMinter", owner.getAddress());
        tokenScore.invoke(owner, "mint", totalSupply, new byte[0]);

        tokenSpy = (IRC2Burnable) spy(tokenScore.getInstance());
        tokenScore.setInstance(tokenSpy);
    }

    @Test
    void burn() {
        BigInteger beforeBalance = ICX;
        BigInteger burnAmount = BigInteger.valueOf(1239);
        Account alice = sm.createAccount();
        tokenScore.invoke(owner, "transfer", alice.getAddress(), beforeBalance, new byte[0]);

        Account nonMinter = sm.createAccount();
        String expectedErrorMessage =
                "Authorization Check: Authorization failed. Caller: " + nonMinter.getAddress() + " Authorized Caller:" +
                        " " + owner.getAddress();
        Executable nonMinterCall = () -> tokenScore.invoke(nonMinter, "burnFrom", alice.getAddress(), burnAmount);
        expectErrorMessage(nonMinterCall, expectedErrorMessage);

        expectedErrorMessage = name + ": Owner address cannot be zero address";
        Executable zeroAddressBurn = () -> tokenScore.invoke(owner, "burnFrom", new Address(new byte[Address.LENGTH])
                , burnAmount);
        expectErrorMessage(zeroAddressBurn, expectedErrorMessage);

        expectedErrorMessage = name + ": Amount needs to be positive";
        Executable negativeAmountBurn = () -> tokenScore.invoke(owner, "burnFrom", alice.getAddress(), burnAmount.negate());
        expectErrorMessage(negativeAmountBurn, expectedErrorMessage);

        expectedErrorMessage = name + ": Insufficient Balance";
        Executable burnMoreThanBalance = () -> tokenScore.invoke(owner, "burnFrom", alice.getAddress(),
                ICX.add(BigInteger.ONE));
        expectErrorMessage(burnMoreThanBalance, expectedErrorMessage);

        BigInteger beforeTotalSupply = (BigInteger) tokenScore.call("totalSupply");
        tokenScore.invoke(owner, "burnFrom", alice.getAddress(), burnAmount);
        assertEquals(beforeBalance.subtract(burnAmount), tokenScore.call("balanceOf", alice.getAddress()));
        assertEquals(beforeTotalSupply.subtract(burnAmount), tokenScore.call("totalSupply"));
        verify(tokenSpy).Transfer(alice.getAddress(), IRC2Base.ZERO_ADDRESS, burnAmount, "burn".getBytes());

        beforeBalance = (BigInteger) tokenScore.call("balanceOf", owner.getAddress());
        tokenScore.invoke(owner, "burn", burnAmount);
        assertEquals(beforeBalance.subtract(burnAmount), tokenScore.call("balanceOf", owner.getAddress()));
    }
}
