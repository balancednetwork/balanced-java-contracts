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

package network.balanced.score.util.stability;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static network.balanced.score.lib.test.UnitTest.*;
import static network.balanced.score.util.stability.StabilityImpl.TAG;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class StabilityImplTest extends TestBase {

    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();

    private Score stabilityScore;

    private static final Account feeHandler = Account.newScoreAccount(scoreCount++);
    private static final Account bnusd = Account.newScoreAccount(scoreCount++);
    private static final BigInteger feeIn = BigInteger.valueOf(5).multiply(BigInteger.TEN.pow(17));
    private static final BigInteger feeOut = BigInteger.valueOf(5).multiply(BigInteger.TEN.pow(17));

    private static final Account iusdc = Account.newScoreAccount(scoreCount++);
    private static final BigInteger limit = BigInteger.valueOf(1_000_000);

    private final MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS);

    @BeforeEach
    void setUp() throws Exception {
        stabilityScore = sm.deploy(owner, StabilityImpl.class, feeHandler.getAddress(), bnusd.getAddress(), feeIn, feeOut);
    }

    @Test
    void name() {
        assertEquals("Balanced Peg Stability", stabilityScore.call("name"));
    }

    @Test
    void setAndGetFeeHandler() {
        testOwnerControlMethods(stabilityScore, "setFeeHandler", "getFeeHandler", feeHandler.getAddress());
        testIsContract(owner, stabilityScore, "setFeeHandler", feeHandler.getAddress(), "getFeeHandler");
    }

    @Test
    void setAndGetBnusdAddress() {
        testOwnerControlMethods(stabilityScore, "setBnusd", "getBnusd", bnusd.getAddress());
        testIsContract(owner, stabilityScore, "setBnusd", bnusd.getAddress(), "getBnusd");
    }

    private void testIsValidPercentage(String setterMethod, String getterMethod, BigInteger percentageToSet) {
        BigInteger negativePercentage = BigInteger.TEN.negate();
        String expectedErrorMessage = TAG + ": Percentage can't be negative";
        Executable negativeCall = () -> stabilityScore.invoke(owner, setterMethod, negativePercentage);
        expectErrorMessage(negativeCall, expectedErrorMessage);

        BigInteger greaterThanHundred = BigInteger.valueOf(100).multiply(ICX).add(BigInteger.ONE);
        expectedErrorMessage = TAG + ": Percentage can't be greater than hundred percentage.";
        Executable outOfLimitCall = () -> stabilityScore.invoke(owner, setterMethod, greaterThanHundred);
        expectErrorMessage(outOfLimitCall, expectedErrorMessage);

        stabilityScore.invoke(owner, setterMethod, percentageToSet);
        assertEquals(percentageToSet, stabilityScore.call(getterMethod));
    }

    @Test
    void setAndGetFeeIn() {
        assertEquals(feeIn, stabilityScore.call("getFeeIn"));
        testOwnerControlMethods(stabilityScore, "setFeeIn", "getFeeIn", feeIn);
        testIsValidPercentage("setFeeIn", "getFeeIn", feeIn);
    }

    @Test
    void setAndGetFeeOut() {
        assertEquals(feeOut, stabilityScore.call("getFeeOut"));
        testOwnerControlMethods(stabilityScore, "setFeeOut", "getFeeOut", feeOut);
        testIsValidPercentage("setFeeOut", "getFeeOut", feeOut);
    }

    @SuppressWarnings("unchecked")
    @Test
    void whitelistTokens() {

        Account nonOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + nonOwner.getAddress() + "Owner=" + owner.getAddress();
        Executable notOwnerCall = () -> stabilityScore.invoke(nonOwner, "whitelistTokens", iusdc.getAddress(), limit);
        expectErrorMessage(notOwnerCall, expectedErrorMessage);

        Account nonContractAddress = sm.createAccount();
        expectedErrorMessage = "Address Check: Address provided is an EOA address. A contract address is required.";
        Executable nonContractParameter = () -> stabilityScore.invoke(owner, "whitelistTokens",
                nonContractAddress.getAddress(), limit);
        expectErrorMessage(nonContractParameter, expectedErrorMessage);

        BigInteger negativeLimit = limit.negate();
        expectedErrorMessage = TAG + ": Limit can't be set negative";
        Executable negativeLimitCall = () -> stabilityScore.invoke(owner, "whitelistTokens", iusdc.getAddress(),
                negativeLimit);
        expectErrorMessage(negativeLimitCall, expectedErrorMessage);

        BigInteger iusdcDecimals = BigInteger.valueOf(6);
        contextMock.when(() -> Context.call(iusdc.getAddress(), "decimals")).thenReturn(iusdcDecimals);
        stabilityScore.invoke(owner, "whitelistTokens", iusdc.getAddress(), limit);
        assertEquals(limit.multiply(BigInteger.TEN.pow(iusdcDecimals.intValue())), stabilityScore.call("getLimit",
                iusdc.getAddress()));

        List<Address> tokens = new ArrayList<>();
        tokens.add(iusdc.getAddress());
        assertArrayEquals(tokens.toArray(), ((List<Address>) stabilityScore.call("getAcceptedTokens")).toArray());

        expectedErrorMessage = TAG + ": Already whitelisted";
        Executable doubleListing = () -> stabilityScore.invoke(owner, "whitelistTokens", iusdc.getAddress(), limit);
        expectErrorMessage(doubleListing, expectedErrorMessage);
    }

    @Test
    void updateLimit() {
        BigInteger newLimit = BigInteger.valueOf(1_000);

        Account nonOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + nonOwner.getAddress() + "Owner=" + owner.getAddress();
        Executable notOwnerCall = () -> stabilityScore.invoke(nonOwner, "updateLimit", iusdc.getAddress(), newLimit);
        expectErrorMessage(notOwnerCall, expectedErrorMessage);

        BigInteger negativeLimit = newLimit.negate();
        expectedErrorMessage = TAG + ": Limit can't be set negative";
        Executable negativeLimitCall = () -> stabilityScore.invoke(owner, "updateLimit", iusdc.getAddress(),
                negativeLimit);
        expectErrorMessage(negativeLimitCall, expectedErrorMessage);

        expectedErrorMessage = TAG + ": Address not white listed previously";
        Executable nonWhiteListed = () -> stabilityScore.invoke(owner, "updateLimit", iusdc.getAddress(), newLimit);
        expectErrorMessage(nonWhiteListed, expectedErrorMessage);

        BigInteger iusdcDecimals = BigInteger.valueOf(6);
        contextMock.when(() -> Context.call(iusdc.getAddress(), "decimals")).thenReturn(iusdcDecimals);
        stabilityScore.invoke(owner, "whitelistTokens", iusdc.getAddress(), limit);
        assertEquals(limit.multiply(BigInteger.TEN.pow(iusdcDecimals.intValue())), stabilityScore.call("getLimit",
                iusdc.getAddress()));

        stabilityScore.invoke(owner, "updateLimit", iusdc.getAddress(), newLimit);
        assertEquals(newLimit.multiply(BigInteger.TEN.pow(iusdcDecimals.intValue())), stabilityScore.call("getLimit",
                iusdc.getAddress()));
    }

    @Nested
    class TokenFallback {
        Address ZERO_ADDRESS = new Address(new byte[Address.LENGTH]);
        BigInteger iusdcDecimals = BigInteger.valueOf(6);
        BigInteger usdcAmount = BigInteger.valueOf(3217).multiply(BigInteger.TEN.pow(iusdcDecimals.intValue()));
        Account user = sm.createAccount();
        BigInteger bnusdAmount = BigInteger.valueOf(317).multiply(ICX);
        byte[] iusdcData = iusdc.getAddress().toString().getBytes();

        @Test
        @DisplayName("No interaction with from being zero address in case of mint")
        void noInteraction() {
            stabilityScore.invoke(iusdc, "tokenFallback", ZERO_ADDRESS, usdcAmount, new byte[0]);
            contextMock.verify(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class),
                    any(BigInteger.class)), never());
        }

        @Test
        void zeroOrNegativeAmount() {
            String expectedErrorMessage = TAG + ": Transfer amount must be greater than zero";
            Executable negativeAmount = () -> stabilityScore.invoke(iusdc, "tokenFallback", user.getAddress(),
                    usdcAmount.negate(), new byte[0]);
            expectErrorMessage(negativeAmount, expectedErrorMessage);
            Executable zeroAmount = () -> stabilityScore.invoke(iusdc, "tokenFallback", user.getAddress(),
                    BigInteger.ZERO, new byte[0]);
            expectErrorMessage(zeroAmount, expectedErrorMessage);
        }

        @Test
        void nonWhiteListed() {
            String expectedErrorMessage = TAG + ": Only whitelisted tokens or bnusd is accepted.";
            Executable nonWhiteListed = () -> stabilityScore.invoke(iusdc, "tokenFallback", user.getAddress(),
                    usdcAmount
                    , new byte[0]);
            expectErrorMessage(nonWhiteListed, expectedErrorMessage);
        }

        @Test
        void exceedingLimit() {
            // Whitelist iusdc token
            iusdcDecimals = BigInteger.valueOf(20);
            contextMock.when(() -> Context.call(iusdc.getAddress(), "decimals")).thenReturn(iusdcDecimals);
            stabilityScore.invoke(owner, "whitelistTokens", iusdc.getAddress(), limit);

            // Error from exceeding limit
            contextMock.when(() -> Context.call(iusdc.getAddress(), "balanceOf", stabilityScore.getAddress())).
                    thenReturn(limit.multiply(BigInteger.TEN.pow(iusdcDecimals.intValue())));
            String expectedErrorMessage = TAG + ": Asset to exchange with bnusd limit crossed.";
            Executable exceedLimit = () -> stabilityScore.invoke(iusdc, "tokenFallback", user.getAddress(), usdcAmount,
                    new byte[0]);
            expectErrorMessage(exceedLimit, expectedErrorMessage);

            // Error from zero bnusd to mint
            contextMock.when(() -> Context.call(iusdc.getAddress(), "balanceOf", stabilityScore.getAddress())).thenReturn(BigInteger.ZERO);
            expectedErrorMessage = TAG + ": Bnusd amount must be greater than zero";
            Executable mintZeroAmount = () -> stabilityScore.invoke(iusdc, "tokenFallback", user.getAddress(),
                    BigInteger.TEN, new byte[0]);
            expectErrorMessage(mintZeroAmount, expectedErrorMessage);

            // Error from zero fee
            expectedErrorMessage = TAG + ": Fee must be greater than zero";
            Executable zeroFee = () -> stabilityScore.invoke(iusdc, "tokenFallback", user.getAddress(),
                    BigInteger.valueOf(100), new byte[0]);
            expectErrorMessage(zeroFee, expectedErrorMessage);
        }

        @Test
        void verifyBnusdMint() {
            contextMock.when(() -> Context.call(iusdc.getAddress(), "decimals")).thenReturn(iusdcDecimals);
            stabilityScore.invoke(owner, "whitelistTokens", iusdc.getAddress(), limit);
            contextMock.when(() -> Context.call(iusdc.getAddress(), "balanceOf", stabilityScore.getAddress())).thenReturn(BigInteger.ZERO);

            contextMock.when(() -> Context.call(eq(bnusd.getAddress()), eq("mint"), any(BigInteger.class))).thenReturn(null);
            contextMock.when(() -> Context.call(eq(bnusd.getAddress()), eq("transfer"), any(Address.class),
                    any(BigInteger.class))).thenReturn(null);
            stabilityScore.invoke(iusdc, "tokenFallback", user.getAddress(), usdcAmount, new byte[0]);

            BigInteger equivalentBnusd = usdcAmount.multiply(ICX).divide(BigInteger.TEN.pow(iusdcDecimals.intValue()));
            BigInteger fee = feeIn.multiply(equivalentBnusd).divide(BigInteger.valueOf(100).multiply(ICX));
            contextMock.verify(() -> Context.call(bnusd.getAddress(), "mint", equivalentBnusd));
            contextMock.verify(() -> Context.call(bnusd.getAddress(), "transfer", feeHandler.getAddress(), fee));
            contextMock.verify(() -> Context.call(bnusd.getAddress(), "transfer", user.getAddress(),
                    equivalentBnusd.subtract(fee)));
        }

        @Test
        void nonWhitelistedReturn() {
            String expectedErrorMessage = TAG + ": Whitelisted tokens can only be sent";
            Executable nonWhitelistedAsset = () -> stabilityScore.invoke(bnusd, "tokenFallback", user.getAddress(),
                    bnusdAmount, iusdcData);
            expectErrorMessage(nonWhitelistedAsset, expectedErrorMessage);
        }

        @Test
        void outAssetZero() {
            contextMock.when(() -> Context.call(iusdc.getAddress(), "decimals")).thenReturn(iusdcDecimals);
            stabilityScore.invoke(owner, "whitelistTokens", iusdc.getAddress(), limit);

            String expectedErrorMessage = TAG + ": Fee must be greater than zero";
            Executable zeroFee = () -> stabilityScore.invoke(bnusd, "tokenFallback", user.getAddress(),
                    BigInteger.ONE, iusdcData);
            expectErrorMessage(zeroFee, expectedErrorMessage);

            expectedErrorMessage = TAG + ": Asset to return can't be zero or less";
            Executable zeroAssetOut = () -> stabilityScore.invoke(bnusd, "tokenFallback", user.getAddress(),
                    BigInteger.TEN.pow(11), iusdcData);
            expectErrorMessage(zeroAssetOut, expectedErrorMessage);

            contextMock.when(() -> Context.call(iusdc.getAddress(), "balanceOf", stabilityScore.getAddress())).thenReturn(BigInteger.ZERO);
            expectedErrorMessage = TAG + ": Insufficient asset out balance in the contract";
            Executable noBalanceToTransfer = () -> stabilityScore.invoke(bnusd, "tokenFallback", user.getAddress(),
                    bnusdAmount, iusdcData);
            expectErrorMessage(noBalanceToTransfer, expectedErrorMessage);
        }

        @Test
        void verifyBnusdBurn() {
            contextMock.when(() -> Context.call(iusdc.getAddress(), "decimals")).thenReturn(iusdcDecimals);
            stabilityScore.invoke(owner, "whitelistTokens", iusdc.getAddress(), limit);

            BigInteger fee = feeOut.multiply(bnusdAmount).divide(BigInteger.valueOf(100).multiply(ICX));
            BigInteger bnusdToConvert = bnusdAmount.subtract(fee);
            BigInteger equivalentAssetOut = bnusdToConvert.multiply(BigInteger.TEN.pow(iusdcDecimals.intValue())).divide(ICX);

            contextMock.when(() -> Context.call(iusdc.getAddress(), "balanceOf", stabilityScore.getAddress())).thenReturn(equivalentAssetOut);

            contextMock.when(() -> Context.call(eq(bnusd.getAddress()), eq("burn"), any(BigInteger.class))).thenReturn(null);
            contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class),
                    any(BigInteger.class))).thenReturn(null);
            stabilityScore.invoke(bnusd, "tokenFallback", user.getAddress(), bnusdAmount, iusdcData);

            contextMock.verify(() -> Context.call(bnusd.getAddress(), "burn", bnusdToConvert));
            contextMock.verify(() -> Context.call(bnusd.getAddress(), "transfer", feeHandler.getAddress(), fee));
            contextMock.verify(() -> Context.call(iusdc.getAddress(), "transfer", user.getAddress(),
                    equivalentAssetOut));
        }
    }

    @AfterEach
    void closeMock() {
        contextMock.close();
    }
}