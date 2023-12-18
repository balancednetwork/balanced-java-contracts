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

package network.balanced.score.core.savings;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import network.balanced.score.lib.interfaces.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import score.Address;
import foundation.icon.xcall.NetworkAddress;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;

import static network.balanced.score.lib.test.integration.BalancedUtils.*;
import static network.balanced.score.lib.utils.Constants.*;
import static org.junit.jupiter.api.Assertions.*;

class SavingsIntegrationTest implements ScoreIntegrationTest {
    private static Balanced balanced;
    private static BalancedClient owner;
    private static BalancedClient reader;
    private static String externalOracle = "cx111";
    private static String hyUSDCAddress = "cx222";
    private static String hyUSDCSymbol = "hyUSDC";
    private static Address hyUSDCAsset;
    private static BigInteger hyUSDCDecimals = BigInteger.valueOf(12);
    private static BigInteger rate = EXA;
    // Use high interest rate on loans so we can see that it accrues in real time
    // during test
    // 1000%
    private static BigInteger interestRate = POINTS.multiply(BigInteger.TEN);
    // 50%
    private static BigInteger savingsShare = POINTS.divide(BigInteger.TWO);

    // setup a normal stable asset
    private static Address stableAsset;
    private static BigInteger stableAssetDecimals = BigInteger.valueOf(6);
    private static String stableAssetSymbol = "USDC";

    private static BigInteger stabilityLimit = BigInteger.TEN.pow(30);

    private static BalancedClient bsrHolder;
    private static BalancedClient bsrStaker;

    @BeforeAll
    static void setup() throws Exception {
        balanced = new Balanced();
        balanced.setupBalanced();

        owner = balanced.ownerClient;
        reader = balanced.newClient(BigInteger.ZERO);
        bsrHolder = balanced.newClient();
        bsrStaker = balanced.newClient();

        JsonArray addAssetParams = new JsonArray()
                .add(createParameter(new NetworkAddress(balanced.ETH_NID, hyUSDCAddress).toString()))
                .add(createParameter(hyUSDCSymbol))
                .add(createParameter(hyUSDCSymbol))
                .add(createParameter(hyUSDCDecimals));
        JsonObject addAsset = createTransaction(balanced.assetManager._address(), "deployAsset", addAssetParams);

        JsonArray setInterestRateParams = new JsonArray()
                .add(createParameter("sICX"))
                .add(createParameter(interestRate));
        JsonObject setInterestRate = createTransaction(balanced.loans._address(), "setInterestRate",
                setInterestRateParams);

        JsonArray setSavingsRateShareParams = new JsonArray()
                .add(createParameter(savingsShare));
        JsonObject setSavingsRateShare = createTransaction(balanced.loans._address(), "setSavingsRateShare",
                setSavingsRateShareParams);

        JsonArray addExternalPriceProxyParams = new JsonArray()
                .add(createParameter(hyUSDCSymbol))
                .add(createParameter(new NetworkAddress(balanced.ETH_NID, externalOracle).toString()));
        JsonObject addExternalPriceProxy = createTransaction(balanced.balancedOracle._address(),
                "addExternalPriceProxy",
                addExternalPriceProxyParams);

        JsonArray transactions = new JsonArray()
                .add(addAsset)
                .add(setInterestRate)
                .add(setSavingsRateShare)
                .add(addExternalPriceProxy);
        owner.governance.execute(transactions.toString());

        stableAsset = createIRC2Token(owner, stableAssetSymbol, stableAssetSymbol, stableAssetDecimals);
        owner.irc2(stableAsset).setMinter(owner.getAddress());
        hyUSDCAsset = reader.assetManager
                .getAssetAddress(new NetworkAddress(balanced.ETH_NID, hyUSDCAddress).toString());
        whitelistToken(balanced, new foundation.icon.jsonrpc.Address(stableAsset.toString()), stabilityLimit);
        whitelistToken(balanced, new foundation.icon.jsonrpc.Address(hyUSDCAsset.toString()), stabilityLimit, true);

        // set initial rate to 1 USD
        updatePrice(rate);
    }

    @Test
    @Order(1)
    void takeLoanAndLockInBSR() throws Exception {
        BigInteger collateralAmount = BigInteger.TEN.pow(23);
        BigInteger loanAmount = BigInteger.TEN.pow(21);
        bsrHolder.stakeDepositAndBorrow(collateralAmount, loanAmount);
        bsrStaker.stakeDepositAndBorrow(collateralAmount, loanAmount);

        JsonObject deposit = new JsonObject().add("method", "_deposit");
        JsonObject lock = new JsonObject().add("method", "_lock");
        bsrHolder.bnUSD.transfer(balanced.savings._address(), loanAmount, deposit.toString().getBytes());
        bsrStaker.bnUSD.transfer(balanced.savings._address(), loanAmount, deposit.toString().getBytes());
        bsrStaker.bsr.transfer(balanced.savings._address(), loanAmount, lock.toString().getBytes());

        assertEquals(loanAmount, reader.bsr.balanceOf(bsrHolder.getAddress()));
        assertEquals(loanAmount, reader.savings.getLockedAmount(bsrStaker.getAddress().toString()));
    }

    @Test
    @Order(2)
    void depositTokensToStability() throws Exception {
        // Arrange
        BalancedClient depositor = balanced.newClient();
        BalancedClient arbitrager = balanced.newClient();
        BigInteger USDCAmount = BigInteger.valueOf(10000).multiply(BigInteger.TEN.pow(stableAssetDecimals.intValue()));
        BigInteger HyUSDCAmount = BigInteger.valueOf(10000).multiply(BigInteger.TEN.pow(hyUSDCDecimals.intValue()));
        String from = new NetworkAddress(balanced.ETH_NID, "cxdepositor").toString();
        String depositorNetworkAddress = new NetworkAddress(balanced.ICON_NID, depositor.getAddress()).toString();
        String arbitragerNetworkAddress = new NetworkAddress(balanced.ICON_NID, arbitrager.getAddress()).toString();
        String stabilityNetworkAddress = new NetworkAddress(balanced.ICON_NID, balanced.stability._address().toString())
                .toString();
        owner.irc2(stableAsset).mintTo(depositor.getAddress(), USDCAmount, new byte[0]);
        depositHyUSDC(from, depositorNetworkAddress, HyUSDCAmount, new byte[0]);
        depositHyUSDC(from, arbitragerNetworkAddress, HyUSDCAmount, new byte[0]);

        // Act
        depositor.irc2(stableAsset).transfer(balanced.stability._address(), USDCAmount, null);
        depositor.irc2(hyUSDCAsset).transfer(balanced.stability._address(), HyUSDCAmount, null);
        arbitrager.irc2(hyUSDCAsset).transfer(balanced.stability._address(), HyUSDCAmount,
                stableAsset.toString().getBytes());
        JsonObject deposit = new JsonObject().add("receiver", depositor.getAddress().toString());
        depositHyUSDC(from, stabilityNetworkAddress, HyUSDCAmount, deposit.toString().getBytes());

        // Assert
        // in fee is 0%
        assertEquals(BigInteger.ZERO, reader.irc2(stableAsset).balanceOf(balanced.stability._address()));
        assertEquals(USDCAmount, reader.irc2(stableAsset).balanceOf(arbitrager.getAddress()));
        assertEquals(HyUSDCAmount.multiply(BigInteger.valueOf(3)),
                reader.irc2(hyUSDCAsset).balanceOf(balanced.stability._address()));
        assertEquals(BigInteger.valueOf(30000), reader.bnUSD.balanceOf(depositor.getAddress()).divide(EXA));

        BigInteger doafundBalance = reader.bnUSD.balanceOf(balanced.daofund._address());
        // adding assets should not change any backing
        bsrHolder.stability.mintExcess();
        assertEquals(doafundBalance, reader.bnUSD.balanceOf(balanced.daofund._address()));
    }

    @Test
    @Order(3)
    void increaseHyUSDRateAndMintExcess() throws Exception {
        // Arrange
        BigInteger backingHyUSDC = reader.irc2(hyUSDCAsset).balanceOf(balanced.stability._address())
                .divide(BigInteger.TEN.pow(hyUSDCDecimals.intValue()));
        BigInteger daofundBalance = reader.bnUSD.balanceOf(balanced.daofund._address());
        BigInteger expectedMint = backingHyUSDC.multiply(EXA).divide(BigInteger.TEN);
        // up by 10%
        rate = EXA.multiply(BigInteger.valueOf(11)).divide(BigInteger.TEN);
        updatePrice(rate);

        // Act
        bsrHolder.stability.mintExcess();

        // Assert
        // We should have minted 10% of the backing HyUSDC
        BigInteger newDaofundBalance = reader.bnUSD.balanceOf(balanced.daofund._address());
        assertEquals(daofundBalance.add(expectedMint), newDaofundBalance);
    }

    @Test
    @Order(4)
    void applyAndClaimLoansInterest() throws Exception {
        // Arrange
        BigInteger previousDebt = reader.loans.getTotalDebt("");
        BalancedClient user = balanced.newClient();
        BigInteger savingsBalance = reader.bnUSD.balanceOf(balanced.savings._address());

        // Act
        user.loans.applyInterest();
        user.loans.claimInterest();

        // Assert
        BigInteger newDebt = reader.loans.getTotalDebt("");
        BigInteger newSavingsBalance = reader.bnUSD.balanceOf(balanced.savings._address());
        BigInteger accruedInterest = newDebt.subtract(previousDebt);
        BigInteger savingsAmount = accruedInterest.multiply(savingsShare).divide(POINTS);
        assertEquals(savingsBalance.add(savingsAmount), newSavingsBalance);
        assertEquals(newSavingsBalance.multiply(EXA).divide(reader.bsr.xTotalSupply()), reader.savings.getRate());
    }

    @Test
    @Order(5)
    void donateStakedICXToSavings() throws Exception {
        // Arrange
        JsonArray addAcceptedTokenParams = new JsonArray()
                .add(createParameter(balanced.sicx._address()));
        JsonArray addAcceptedToken = createSingleTransaction(balanced.savings._address(), "addAcceptedToken",
                addAcceptedTokenParams);
        owner.governance.execute(addAcceptedToken.toString());

        BigInteger amount = BigInteger.valueOf(100).multiply(EXA);
        owner.staking.stakeICX(amount, balanced.savings._address(), null);
        BigInteger balance = reader.sicx.balanceOf(bsrStaker.getAddress());

        // Act
        Map<String, BigInteger> rewardsStaker = reader.savings.getUnclaimedRewards(bsrStaker.getAddress().toString());
        Map<String, BigInteger> rewardsHolder = reader.savings.getUnclaimedRewards(bsrHolder.getAddress().toString());
        bsrStaker.savings.claimRewards();
        Map<String, BigInteger> rewardsStakerAfterClaim = reader.savings
                .getUnclaimedRewards(bsrStaker.getAddress().toString());

        // Assert
        assertEquals(amount, rewardsStaker.get(balanced.sicx._address().toString()));
        assertEquals(BigInteger.ZERO, rewardsHolder.get(balanced.sicx._address().toString()));
        assertEquals(BigInteger.ZERO, rewardsStakerAfterClaim.get(balanced.sicx._address().toString()));
        assertEquals(balance.add(amount), reader.sicx.balanceOf(bsrStaker.getAddress()));
    }

    @Order(6)
    void depositYieldAssetToStability() throws Exception {
        // Arrange
        BalancedClient depositor = balanced.newClient();
        BalancedClient arbitrager = balanced.newClient();
        BigInteger _hyUSDCDecimals = BigInteger.TEN.pow(hyUSDCDecimals.intValue());
        BigInteger _stableDecimals = BigInteger.TEN.pow(stableAssetDecimals.intValue());
        BigInteger HyUSDCAmount = BigInteger.valueOf(10000).multiply(_hyUSDCDecimals);
        BigInteger expectedBnUSDAmount = HyUSDCAmount.multiply(rate).divide(_hyUSDCDecimals);
        BigInteger expectedStableAmount = HyUSDCAmount.multiply(rate).multiply(_stableDecimals).divide(_hyUSDCDecimals)
                .divide(EXA);

        String from = new NetworkAddress(balanced.ETH_NID, "cxdepositor").toString();
        owner.irc2(stableAsset).mintTo(owner.getAddress(), expectedStableAmount, new byte[0]);

        String depositorNetworkAddress = new NetworkAddress(balanced.ICON_NID, depositor.getAddress()).toString();
        String arbitragerNetworkAddress = new NetworkAddress(balanced.ICON_NID, arbitrager.getAddress()).toString();
        depositHyUSDC(from, depositorNetworkAddress, HyUSDCAmount, new byte[0]);
        depositHyUSDC(from, arbitragerNetworkAddress, HyUSDCAmount, new byte[0]);

        // Act
        owner.irc2(stableAsset).transfer(balanced.stability._address(), expectedStableAmount, null);
        depositor.irc2(hyUSDCAsset).transfer(balanced.stability._address(), HyUSDCAmount, null);
        arbitrager.irc2(hyUSDCAsset).transfer(balanced.stability._address(), HyUSDCAmount,
                stableAsset.toString().getBytes());

        // Assert
        // in fee is 0%
        assertEquals(expectedStableAmount, reader.irc2(stableAsset).balanceOf(arbitrager.getAddress()));
        assertEquals(expectedBnUSDAmount, reader.bnUSD.balanceOf(depositor.getAddress()));

        // adding assets should not change any backing
        BigInteger doafundBalance = reader.bnUSD.balanceOf(balanced.daofund._address());
        bsrHolder.stability.mintExcess();
        assertEquals(doafundBalance, reader.bnUSD.balanceOf(balanced.daofund._address()));
    }

    @Test
    @Order(7)
    void withdrawYieldAssetFromStability() throws Exception {
        // Arrange
        BigInteger collateralAmount = BigInteger.TEN.pow(22);
        BigInteger amount = BigInteger.TEN.pow(20);
        bsrHolder.stakeDepositAndBorrow(collateralAmount, amount);
        // Fee is 1% in bnUSD
        BigInteger expectedFee = amount.divide(BigInteger.valueOf(100));
        BigInteger expectedYieldAsset = amount.subtract(expectedFee)
                .multiply(BigInteger.TEN.pow(hyUSDCDecimals.intValue())).divide(rate);

        // Act
        bsrHolder.bnUSD.transfer(balanced.stability._address(), amount, hyUSDCAsset.toString().getBytes());

        // Assert
        assertEquals(expectedYieldAsset, reader.irc2(hyUSDCAsset).balanceOf(bsrHolder.getAddress()));

        // withdrawing should not change any backing
        BigInteger doafundBalance = reader.bnUSD.balanceOf(balanced.daofund._address());
        bsrHolder.stability.mintExcess();
        assertEquals(doafundBalance, reader.bnUSD.balanceOf(balanced.daofund._address()));
    }

    private static void depositHyUSDC(String from, String to, BigInteger amount, byte[] data) {
        byte[] deposit = AssetManagerMessages.deposit(hyUSDCAddress, from, to, amount, data);
        owner.xcall.sendCall(balanced.assetManager._address(),
                new NetworkAddress(balanced.ETH_NID, balanced.ETH_ASSET_MANAGER).toString(), deposit);
    }

    private static void updatePrice(BigInteger rate) {
        BigInteger time = BigInteger.valueOf(Instant.now().getEpochSecond()).multiply(MICRO_SECONDS_IN_A_SECOND);
        byte[] priceUpdate = BalancedOracleMessages.updatePriceData(hyUSDCSymbol, rate, time);
        owner.xcall.sendCall(balanced.balancedOracle._address(),
                new NetworkAddress(balanced.ETH_NID, externalOracle).toString(), priceUpdate);
    }
}
