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
import network.balanced.score.lib.test.mock.MockBalanced;
import network.balanced.score.lib.test.mock.MockContract;
import score.Address;
import com.eclipsesource.json.JsonObject;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import network.balanced.score.lib.interfaces.tokens.IRC2Mintable;
import network.balanced.score.lib.interfaces.tokens.IRC2MintableScoreInterface;

import static network.balanced.score.lib.test.UnitTest.*;
import static network.balanced.score.util.stability.StabilityImpl.TAG;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StabilityImplTest extends TestBase {

    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();

    private Score stabilityScore;

    private static BigInteger feeIn = BigInteger.valueOf(5).multiply(BigInteger.TEN.pow(17));
    private static BigInteger feeOut = BigInteger.valueOf(5).multiply(BigInteger.TEN.pow(17));
    private static final BigInteger iusdcDecimals = BigInteger.valueOf(6);
    protected MockContract<? extends IRC2Mintable> iusdc;
    private static final BigInteger limit = BigInteger.valueOf(1_000_000);

    private MockBalanced mockBalanced;

    @BeforeEach
    void setUp() throws Exception {
        mockBalanced = new MockBalanced(sm, owner);
        iusdc = new MockContract<>(IRC2MintableScoreInterface.class, sm, owner);
        when(iusdc.mock.symbol()).thenReturn("IUSDC");
        when(iusdc.mock.decimals()).thenReturn(iusdcDecimals);
        when(iusdc.mock.balanceOf(any(Address.class))).thenReturn(BigInteger.ZERO);

        stabilityScore = sm.deploy(owner, StabilityImpl.class, mockBalanced.governance.getAddress());
        stabilityScore.invoke(owner, "setFeeIn", feeIn);
        stabilityScore.invoke(owner, "setFeeOut", feeOut);
    }

    @Test
    void name() {
        assertEquals("Balanced Peg Stability", stabilityScore.call("name"));
    }

    private void testIsValidPercentage(String setterMethod, String getterMethod, BigInteger percentageToSet) {
        BigInteger negativePercentage = BigInteger.TEN.negate();
        String expectedErrorMessage = "Reverted(0): " + TAG + ": Percentage can't be negative";
        Executable negativeCall = () -> stabilityScore.invoke(owner, setterMethod, negativePercentage);
        expectErrorMessage(negativeCall, expectedErrorMessage);

        BigInteger greaterThanHundred = BigInteger.valueOf(100).multiply(ICX).add(BigInteger.ONE);
        expectedErrorMessage = "Reverted(0): " + TAG + ": Percentage can't be greater than hundred percentage.";
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
        String expectedErrorMessage = "Reverted(0): SenderNotScoreOwner: Sender=" + nonOwner.getAddress()
                + "Owner=" + owner.getAddress();
        Executable notOwnerCall = () -> stabilityScore.invoke(nonOwner, "whitelistTokens", iusdc.getAddress(),
                limit, false);
        expectErrorMessage(notOwnerCall, expectedErrorMessage);

        Account nonContractAddress = sm.createAccount();
        expectedErrorMessage = "Reverted(0): Address Check: Address provided is an EOA address. A contract address is required.";
        Executable nonContractParameter = () -> stabilityScore.invoke(owner, "whitelistTokens",
                nonContractAddress.getAddress(), limit, false);
        expectErrorMessage(nonContractParameter, expectedErrorMessage);

        BigInteger negativeLimit = limit.negate();
        expectedErrorMessage = "Reverted(0): " + TAG + ": Limit can't be set negative";
        Executable negativeLimitCall = () -> stabilityScore.invoke(owner, "whitelistTokens", iusdc.getAddress(),
                negativeLimit, false);
        expectErrorMessage(negativeLimitCall, expectedErrorMessage);

        stabilityScore.invoke(owner, "whitelistTokens", iusdc.getAddress(), limit, false);
        assertEquals(limit.multiply(BigInteger.TEN.pow(iusdcDecimals.intValue())),
                stabilityScore.call("getLimit",
                        iusdc.getAddress()));

        List<Address> tokens = new ArrayList<>();
        tokens.add(iusdc.getAddress());
        assertArrayEquals(tokens.toArray(),
                ((List<Address>) stabilityScore.call("getAcceptedTokens")).toArray());

        expectedErrorMessage = "Reverted(0): " + TAG + ": Already whitelisted";
        Executable doubleListing = () -> stabilityScore.invoke(owner, "whitelistTokens", iusdc.getAddress(),
                limit, false);
        expectErrorMessage(doubleListing, expectedErrorMessage);
    }

    @Test
    void updateLimit() {
        BigInteger newLimit = BigInteger.valueOf(1_000);

        Account nonOwner = sm.createAccount();
        String expectedErrorMessage = "Reverted(0): SenderNotScoreOwner: Sender=" + nonOwner.getAddress()
                + "Owner=" + owner.getAddress();
        Executable notOwnerCall = () -> stabilityScore.invoke(nonOwner, "updateLimit", iusdc.getAddress(),
                newLimit);
        expectErrorMessage(notOwnerCall, expectedErrorMessage);

        BigInteger negativeLimit = newLimit.negate();
        expectedErrorMessage = "Reverted(0): " + TAG + ": Limit can't be set negative";
        Executable negativeLimitCall = () -> stabilityScore.invoke(owner, "updateLimit", iusdc.getAddress(),
                negativeLimit);
        expectErrorMessage(negativeLimitCall, expectedErrorMessage);

        expectedErrorMessage = "Reverted(0): " + TAG + ": Address not white listed previously";
        Executable nonWhiteListed = () -> stabilityScore.invoke(owner, "updateLimit", iusdc.getAddress(),
                newLimit);
        expectErrorMessage(nonWhiteListed, expectedErrorMessage);

        BigInteger iusdcDecimals = BigInteger.valueOf(6);
        stabilityScore.invoke(owner, "whitelistTokens", iusdc.getAddress(), limit, false);
        assertEquals(limit.multiply(BigInteger.TEN.pow(iusdcDecimals.intValue())),
                stabilityScore.call("getLimit", iusdc.getAddress()));

        stabilityScore.invoke(owner, "updateLimit", iusdc.getAddress(), newLimit);
        assertEquals(newLimit.multiply(BigInteger.TEN.pow(iusdcDecimals.intValue())),
                stabilityScore.call("getLimit", iusdc.getAddress()));
    }

    @Nested
    class TokenFallback {
        Address ZERO_ADDRESS = new Address(new byte[Address.LENGTH]);
        BigInteger iusdcDecimals = BigInteger.valueOf(6);
        BigInteger usdcAmount = BigInteger.valueOf(3217).multiply(BigInteger.TEN.pow(iusdcDecimals.intValue()));
        Account user = sm.createAccount();
        BigInteger bnusdAmount = BigInteger.valueOf(317).multiply(ICX);
        byte[] iusdcData;

        @BeforeEach
        void setUp() throws Exception {
            iusdcData = iusdc.getAddress().toString().getBytes();
        }

        @Test
        @DisplayName("No interaction with from being zero address in case of mint")
        void noInteraction() {
            stabilityScore.invoke(mockBalanced.bnUSD.account, "tokenFallback", ZERO_ADDRESS, bnusdAmount, new byte[0]);
            verify(mockBalanced.bnUSD.mock, never()).transfer(any(), any(), any());
            verify(mockBalanced.bnUSD.mock, never()).mint(any(), any());
            verify(iusdc.mock, never()).transfer(any(), any(), any());
        }

        @Test
        void zeroOrNegativeAmount() {
            String expectedErrorMessage = "Reverted(0): " + TAG
                    + ": Transfer amount must be greater than zero";
            Executable negativeAmount = () -> stabilityScore.invoke(iusdc.account, "tokenFallback",
                    user.getAddress(),
                    usdcAmount.negate(), new byte[0]);
            expectErrorMessage(negativeAmount, expectedErrorMessage);
            Executable zeroAmount = () -> stabilityScore.invoke(iusdc.account, "tokenFallback", user.getAddress(),
                    BigInteger.ZERO, new byte[0]);
            expectErrorMessage(zeroAmount, expectedErrorMessage);
        }

        @Test
        void nonWhiteListed() {
            String expectedErrorMessage = TAG + ": Only whitelisted tokens or bnusd is accepted.";
            Executable nonWhiteListed = () -> stabilityScore.invoke(iusdc.account, "tokenFallback",
                    user.getAddress(),
                    usdcAmount, new byte[0]);
            expectErrorMessage(nonWhiteListed, expectedErrorMessage);
        }

        @Test
        void exceedingLimit() {
            // Whitelist iusdc token
            iusdcDecimals = BigInteger.valueOf(20);
            when(iusdc.mock.decimals()).thenReturn(iusdcDecimals);
            when(iusdc.mock.balanceOf(stabilityScore.getAddress()))
                    .thenReturn(limit.multiply(BigInteger.TEN.pow(iusdcDecimals.intValue()).add(BigInteger.ONE)));
            stabilityScore.invoke(owner, "whitelistTokens", iusdc.getAddress(), limit, false);

            String expectedErrorMessage = "Reverted(0): " + TAG
                    + ": Asset to exchange with bnusd limit crossed.";
            Executable exceedLimit = () -> stabilityScore.invoke(iusdc.account, "tokenFallback", user.getAddress(),
                    usdcAmount,
                    new byte[0]);
            expectErrorMessage(exceedLimit, expectedErrorMessage);

            // Error from zero bnusd to mint
            when(iusdc.mock.balanceOf(stabilityScore.getAddress())).thenReturn(BigInteger.ZERO);

            expectedErrorMessage = "Reverted(0): " + TAG + ": Bnusd amount must be greater than zero";
            Executable mintZeroAmount = () -> stabilityScore.invoke(iusdc.account, "tokenFallback",
                    user.getAddress(),
                    BigInteger.TEN, new byte[0]);
            expectErrorMessage(mintZeroAmount, expectedErrorMessage);
        }

        @Test
        void verifyBnusdMint() {
            stabilityScore.invoke(owner, "whitelistTokens", iusdc.getAddress(), limit, false);
            stabilityScore.invoke(iusdc.account, "tokenFallback", user.getAddress(), usdcAmount, new byte[0]);

            BigInteger equivalentBnusd = usdcAmount.multiply(ICX)
                    .divide(BigInteger.TEN.pow(iusdcDecimals.intValue()));
            BigInteger fee = feeIn.multiply(equivalentBnusd).divide(BigInteger.valueOf(100).multiply(ICX));
            verify(mockBalanced.bnUSD.mock).mint(equivalentBnusd, new byte[0]);
            verify(mockBalanced.bnUSD.mock).transfer(mockBalanced.feehandler.getAddress(), fee, new byte[0]);
            verify(mockBalanced.bnUSD.mock).transfer(user.getAddress(), equivalentBnusd.subtract(fee), new byte[0]);
        }

        @Test
        void nonWhitelistedReturn() {
            String expectedErrorMessage = "Reverted(0): " + TAG + ": Whitelisted tokens can only be sent";
            Executable nonWhitelistedAsset = () -> stabilityScore.invoke(mockBalanced.bnUSD.account, "tokenFallback",
                    user.getAddress(),
                    bnusdAmount, iusdcData);
            expectErrorMessage(nonWhitelistedAsset, expectedErrorMessage);
        }

        @Test
        void outAssetZero() {
            stabilityScore.invoke(owner, "whitelistTokens", iusdc.getAddress(), limit, false);

            String expectedErrorMessage = "Reverted(0): " + TAG + ": Fee must be greater than zero";
            Executable zeroFee = () -> stabilityScore.invoke(mockBalanced.bnUSD.account, "tokenFallback",
                    user.getAddress(),
                    BigInteger.ONE, iusdcData);
            expectErrorMessage(zeroFee, expectedErrorMessage);

            expectedErrorMessage = "Reverted(0): " + TAG + ": Asset to return can't be zero or less";
            Executable zeroAssetOut = () -> stabilityScore.invoke(mockBalanced.bnUSD.account, "tokenFallback",
                    user.getAddress(),
                    BigInteger.TEN.pow(11), iusdcData);
            expectErrorMessage(zeroAssetOut, expectedErrorMessage);

            expectedErrorMessage = "Reverted(0): " + TAG
                    + ": Insufficient asset out balance in the contract";
            Executable noBalanceToTransfer = () -> stabilityScore.invoke(mockBalanced.bnUSD.account, "tokenFallback",
                    user.getAddress(),
                    bnusdAmount, iusdcData);
            expectErrorMessage(noBalanceToTransfer, expectedErrorMessage);
        }

        @Test
        void verifyBnusdBurn() {
            stabilityScore.invoke(owner, "whitelistTokens", iusdc.getAddress(), limit, false);

            BigInteger fee = feeOut.multiply(bnusdAmount).divide(BigInteger.valueOf(100).multiply(ICX));
            BigInteger bnusdToConvert = bnusdAmount.subtract(fee);
            BigInteger equivalentAssetOut = bnusdToConvert
                    .multiply(BigInteger.TEN.pow(iusdcDecimals.intValue())).divide(ICX);

            when(iusdc.mock.balanceOf(stabilityScore.getAddress())).thenReturn(equivalentAssetOut);
            stabilityScore.invoke(mockBalanced.bnUSD.account, "tokenFallback", user.getAddress(), bnusdAmount,
                    iusdcData);

            verify(mockBalanced.bnUSD.mock).burn(bnusdToConvert);
            verify(mockBalanced.bnUSD.mock).transfer(mockBalanced.feehandler.getAddress(), fee, new byte[0]);
            verify(iusdc.mock).transfer(user.getAddress(), equivalentAssetOut, new byte[0]);
        }
    }

    @Nested
    class YieldBearing {
        MockContract<? extends IRC2Mintable> hyUSDC;
        String hyUSDCSymbol = "hyUSDC";
        BigInteger hyUSDCDecimals = BigInteger.valueOf(18);
        BigInteger limit = BigInteger.TEN.pow(30);
        BigInteger priceDelayInDays = BigInteger.ONE;
        Account user = sm.createAccount();

        @BeforeEach
        void setUp() throws Exception {
            stabilityScore.invoke(owner, "setFeeIn", BigInteger.ZERO);
            stabilityScore.invoke(owner, "setMaxPriceDelay", priceDelayInDays);
            hyUSDC = new MockContract<>(IRC2MintableScoreInterface.class, sm, owner);
            when(hyUSDC.mock.symbol()).thenReturn(hyUSDCSymbol);
            when(hyUSDC.mock.decimals()).thenReturn(hyUSDCDecimals);
            when(hyUSDC.mock.balanceOf(any(Address.class))).thenReturn(BigInteger.ZERO);
            stabilityScore.invoke(owner, "whitelistTokens", hyUSDC.getAddress(), limit, true);
        }

        @Test
        void yieldBearingToBnUSD() {
            // Arrange
            BigInteger decimals = BigInteger.TEN.pow(hyUSDCDecimals.intValue());
            BigInteger rate = BigInteger.valueOf(11).multiply(decimals);
            BigInteger timestamp = BigInteger.valueOf(sm.getBlock().getTimestamp());
            setRate(rate, timestamp);
            BigInteger amount = BigInteger.valueOf(200).multiply(decimals);
            BigInteger expectedBnUSDAmount = amount.multiply(rate).divide(decimals);

            // Act
            stabilityScore.invoke(hyUSDC.account, "tokenFallback", user.getAddress(), amount, new byte[0]);

            // Assert
            verify(mockBalanced.bnUSD.mock).mint(expectedBnUSDAmount, new byte[0]);
            verify(mockBalanced.bnUSD.mock).transfer(user.getAddress(), expectedBnUSDAmount, new byte[0]);
        }

        @Test
        void bnUSDToYieldBearing() {
            // Arrange
            BigInteger decimals = BigInteger.TEN.pow(hyUSDCDecimals.intValue());
            BigInteger rate = BigInteger.valueOf(11).multiply(decimals);
            BigInteger timestamp = BigInteger.valueOf(sm.getBlock().getTimestamp());
            setRate(rate, timestamp);
            BigInteger amount = BigInteger.valueOf(200).multiply(EXA);
            BigInteger fee = feeOut.multiply(amount).divide(BigInteger.valueOf(100).multiply(EXA));
            BigInteger expectedHYAmount = amount.subtract(fee).multiply(decimals).divide(rate);

            when(hyUSDC.mock.balanceOf(stabilityScore.getAddress())).thenReturn(expectedHYAmount);

            // Act
            stabilityScore.invoke(mockBalanced.bnUSD.account, "tokenFallback", user.getAddress(), amount,
                    hyUSDC.getAddress().toString().getBytes());

            // Assert
            verify(mockBalanced.bnUSD.mock).burn(amount.subtract(fee));
            verify(mockBalanced.bnUSD.mock).transfer(mockBalanced.feehandler.getAddress(), fee, new byte[0]);
            verify(hyUSDC.mock).transfer(user.getAddress(), expectedHYAmount, new byte[0]);
        }

        @Test
        void yieldBearingToAsset() {
            // Arrange
            stabilityScore.invoke(owner, "whitelistTokens", iusdc.getAddress(), limit, false);
            BigInteger decimals = BigInteger.TEN.pow(hyUSDCDecimals.intValue());
            BigInteger usdcDecimals = BigInteger.TEN.pow(iusdcDecimals.intValue());
            BigInteger rate = BigInteger.valueOf(11).multiply(decimals);
            BigInteger timestamp = BigInteger.valueOf(sm.getBlock().getTimestamp());
            setRate(rate, timestamp);
            BigInteger amount = BigInteger.valueOf(200).multiply(decimals);
            BigInteger expectedUSDCAmount = amount.multiply(rate).multiply(usdcDecimals).divide(decimals)
                    .divide(decimals);

            // Act
            stabilityScore.invoke(hyUSDC.account, "tokenFallback", user.getAddress(), amount,
                    iusdc.getAddress().toString().getBytes());

            // Assert
            verify(iusdc.mock).transfer(user.getAddress(), expectedUSDCAmount, new byte[0]);
        }

        @Test
        void yieldBearingToAsset_ToBnUSD() {
            // Arrange
            String expectedErrorMessage = "Reverted(0): " + TAG + ": Only whitelisted tokens is allowed";
            BigInteger decimals = BigInteger.TEN.pow(hyUSDCDecimals.intValue());
            BigInteger rate = BigInteger.valueOf(11).multiply(decimals);
            BigInteger timestamp = BigInteger.valueOf(sm.getBlock().getTimestamp());
            setRate(rate, timestamp);
            BigInteger amount = BigInteger.valueOf(200).multiply(decimals);

            // Act
            Executable yieldToBnUSDwithData = () -> stabilityScore.invoke(hyUSDC.account, "tokenFallback",
                    user.getAddress(), amount, mockBalanced.bnUSD.getAddress().toString().getBytes());

            // Assert
            expectErrorMessage(yieldToBnUSDwithData, expectedErrorMessage);
        }

        @Test
        void yieldBearing_toOld() {
            // Arrange
            String expectedErrorMessage = "Reverted(0): " + TAG
                    + ": Price for hyUSDC has to be updated before using the stability fund";
            BigInteger decimals = BigInteger.TEN.pow(hyUSDCDecimals.intValue());
            BigInteger rate = BigInteger.valueOf(11).multiply(decimals);
            BigInteger timestamp = BigInteger.valueOf(sm.getBlock().getTimestamp()).subtract(MICRO_SECONDS_IN_A_DAY);
            setRate(rate, timestamp);
            BigInteger amount = BigInteger.valueOf(200).multiply(decimals);

            // Act
            Executable oraclePriceToOld = () -> stabilityScore.invoke(hyUSDC.account, "tokenFallback",
                    user.getAddress(), amount, new byte[0]);

            // Assert
            expectErrorMessage(oraclePriceToOld, expectedErrorMessage);
        }

        @Test
        void yieldBearing_toYieldBearing() {
            // Arrange
            String expectedErrorMessage = "Reverted(0): " + TAG + ": Only swaps to non yield bering assets is allowed";
            BigInteger decimals = BigInteger.TEN.pow(hyUSDCDecimals.intValue());
            BigInteger rate = BigInteger.valueOf(11).multiply(decimals);
            BigInteger timestamp = BigInteger.valueOf(sm.getBlock().getTimestamp());
            setRate(rate, timestamp);
            BigInteger amount = BigInteger.valueOf(200).multiply(decimals);

            // Act
            Executable yieldToYield = () -> stabilityScore.invoke(hyUSDC.account, "tokenFallback", user.getAddress(),
                    amount, hyUSDC.getAddress().toString().getBytes());

            // Assert
            expectErrorMessage(yieldToYield, expectedErrorMessage);
        }

        @Test
        void xTokenFallback_toBnUSD() {
            // Arrange
            BigInteger decimals = BigInteger.TEN.pow(hyUSDCDecimals.intValue());
            BigInteger rate = BigInteger.valueOf(11).multiply(decimals);
            BigInteger timestamp = BigInteger.valueOf(sm.getBlock().getTimestamp());
            setRate(rate, timestamp);
            BigInteger amount = BigInteger.valueOf(200).multiply(decimals);
            BigInteger expectedBnUSDAmount = amount.multiply(rate).divide(decimals);

            // Act
            JsonObject data = new JsonObject()
                .add("receiver", user.getAddress().toString());
            stabilityScore.invoke(hyUSDC.account, "xTokenFallback", "0x1.avax/cx1", amount, data.toString().getBytes());

            // Assert
            verify(mockBalanced.bnUSD.mock).mint(expectedBnUSDAmount, new byte[0]);
            verify(mockBalanced.bnUSD.mock).transfer(user.getAddress(), expectedBnUSDAmount, new byte[0]);
        }

        @Test
        void xTokenFallback_toAsset() {
            // Arrange
            stabilityScore.invoke(owner, "whitelistTokens", iusdc.getAddress(), limit, false);
            BigInteger decimals = BigInteger.TEN.pow(hyUSDCDecimals.intValue());
            BigInteger usdcDecimals = BigInteger.TEN.pow(iusdcDecimals.intValue());
            BigInteger rate = BigInteger.valueOf(11).multiply(decimals);
            BigInteger timestamp = BigInteger.valueOf(sm.getBlock().getTimestamp());
            setRate(rate, timestamp);
            BigInteger amount = BigInteger.valueOf(200).multiply(decimals);
            BigInteger expectedUSDCAmount = amount.multiply(rate).multiply(usdcDecimals).divide(decimals)
                    .divide(decimals);

            // Act
            JsonObject data = new JsonObject()
                .add("receiver", user.getAddress().toString())
                .add("toAsset", iusdc.getAddress().toString());
            stabilityScore.invoke(hyUSDC.account, "xTokenFallback", "0x1.avax/cx1", amount, data.toString().getBytes());

            // Assert
            verify(iusdc.mock).transfer(user.getAddress(), expectedUSDCAmount, new byte[0]);
        }

        @Test
        void getSetPriceDelay() {
            testOwnerControlMethods(stabilityScore, "setMaxPriceDelay", "getMaxPriceDelay", BigInteger.ONE);
        }

        @Test
        void mintExcess() {
            stabilityScore.invoke(owner, "whitelistTokens", iusdc.getAddress(), limit, false);
            BigInteger totalSupply = BigInteger.valueOf(1000).multiply(EXA);
            BigInteger totalDebt = BigInteger.valueOf(700).multiply(EXA);

            BigInteger usdcDecimals = BigInteger.TEN.pow(iusdcDecimals.intValue());
            BigInteger hyUSDCDecimals = BigInteger.TEN.pow(this.hyUSDCDecimals.intValue());

            BigInteger USDCBacking = BigInteger.valueOf(200).multiply(usdcDecimals);
            BigInteger amountYieldBearingTokens =  BigInteger.valueOf(100).multiply(EXA);
            BigInteger hyUSDCRate = hyUSDCDecimals.add(hyUSDCDecimals.divide(BigInteger.TEN)); // 1.1 USD
            // this gives a mint of 10 USD,-10 = 1000-700-200-110
            BigInteger expectedMint = BigInteger.TEN.multiply(hyUSDCDecimals); // 100 supply , 70 debt, 20 USDC 10
            BigInteger timestamp = BigInteger.valueOf(sm.getBlock().getTimestamp());

            when(mockBalanced.bnUSD.mock.xTotalSupply()).thenReturn(totalSupply);
            when(mockBalanced.loans.mock.getTotalDebt("")).thenReturn(totalDebt);

            when(iusdc.mock.balanceOf(stabilityScore.getAddress())).thenReturn(USDCBacking);
            when(hyUSDC.mock.balanceOf(stabilityScore.getAddress())).thenReturn(amountYieldBearingTokens);
            setRate(hyUSDCRate, timestamp);

            // Act
            stabilityScore.invoke(sm.createAccount(), "mintExcess");

            // Assert
            verify(mockBalanced.loans.mock).claimInterest();
            verify(mockBalanced.bnUSD.mock).mint(expectedMint, new byte[0]);
            verify(mockBalanced.bnUSD.mock).transfer(mockBalanced.daofund.getAddress(), expectedMint, new byte[0]);
            verify(mockBalanced.feehandler.mock).accrueStabilityYieldFee(expectedMint);
        }

        protected void setRate(BigInteger rate, BigInteger timestamp) {
            when(mockBalanced.balancedOracle.mock.getPriceDataInUSD(hyUSDCSymbol))
                    .thenReturn(Map.of("rate", rate, "timestamp", timestamp));
        }
    }
}