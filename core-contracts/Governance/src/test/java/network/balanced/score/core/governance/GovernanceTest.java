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

package network.balanced.score.core.governance;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.iconloop.score.test.Account;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.structs.PrepDelegations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.governance.utils.GovernanceConstants.TAG;
import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class GovernanceTest extends GovernanceTestBase {
    @BeforeEach
    public void setup() throws Exception {
        super.setup();
    }

    @Test
    void name() {
        assertEquals("Balanced Governance", governance.call("name"));
    }

    @Test
    void getContractAddress() {
        assertEquals(loans.getAddress(), governance.call("getContractAddress", "loans"));
    }

    @Test
    void getVotingWeight() {
        // Arrange
        Account user = sm.createAccount();
        BigInteger day = BigInteger.TEN;
        BigInteger expectedWeight = BigInteger.ONE;

        when(baln.mock.stakedBalanceOfAt(user.getAddress(), day)).thenReturn(expectedWeight);

        // Act
        BigInteger votingWeight = (BigInteger) governance.call("myVotingWeight", user.getAddress(), day);

        // Assert
        assertEquals(expectedWeight, votingWeight);
    }

    @Test
    void setAdmins() {
         // Arrange
         Account notOwner = sm.createAccount();
         String expectedErrorMessage = "SenderNotScoreOwnerOrContract: Sender=" + notOwner.getAddress() + " Owner=" + owner.getAddress() + " Contract=" + governance.getAddress();
         
         // Act & Assert
         Executable withNotOwner = () -> governance.invoke(notOwner, "setAdmins");
         expectErrorMessage(withNotOwner, expectedErrorMessage);
 
         // Act
         governance.invoke(owner, "setAdmins") ;

         // Assert
         //TODO
    }

    @Test
    void addCollateral() {
        // Arrange
        Address tokenAddress = bwt.getAddress();
        boolean active = false;
        BigInteger lockingRatio = BigInteger.valueOf(30_000);
        BigInteger liquidationRatio = BigInteger.valueOf(10_000);
        BigInteger debtCeiling = BigInteger.TEN.pow(20);
        String symbol = "BALW";
        String peg = "BTC";
        Account notOwner = sm.createAccount();
        String expectedErrorMessage = "SenderNotScoreOwnerOrContract: Sender=" + notOwner.getAddress() + " Owner=" + owner.getAddress() + " Contract=" + governance.getAddress();
        
        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "addCollateral", tokenAddress, active, peg,
                lockingRatio, liquidationRatio, debtCeiling);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Arrange
        when(bwt.mock.symbol()).thenReturn(symbol);

        // Act & Assert
        when(balancedOracle.mock.getPriceInLoop(symbol)).thenReturn(BigInteger.ZERO);
        expectedErrorMessage = "Reverted(0): Balanced oracle return a invalid icx price for " + symbol + "/" + peg;
        Executable withFaultyPeg = () -> governance.invoke(owner, "addCollateral", tokenAddress, active, peg,
                lockingRatio, liquidationRatio, debtCeiling);
        expectErrorMessage(withFaultyPeg, expectedErrorMessage);

        // Act
        when(balancedOracle.mock.getPriceInLoop(symbol)).thenReturn(ICX);
        governance.invoke(owner, "addCollateral", tokenAddress, active, peg, lockingRatio, liquidationRatio,
                debtCeiling);

        // Assert
        verify(loans.mock, times(2)).addAsset(tokenAddress, active, true);
        verify(balancedOracle.mock, times(2)).setPeg(symbol, peg);
        verify(loans.mock).setLockingRatio(symbol, lockingRatio);
        verify(loans.mock).setLiquidationRatio(symbol, liquidationRatio);
        verify(loans.mock).setDebtCeiling(symbol, debtCeiling);
    }

    @Test
    void addDexPricedCollateral() {
        // Arrange
        Address tokenAddress = bwt.getAddress();
        boolean active = false;
        String symbol = "BALW";
        BigInteger lockingRatio = BigInteger.valueOf(30_000);
        BigInteger liquidationRatio = BigInteger.valueOf(10_000);
        BigInteger debtCeiling = BigInteger.TEN.pow(20);
        BigInteger poolID = BigInteger.valueOf(7);
        Account notOwner = sm.createAccount();
        String expectedErrorMessage = "SenderNotScoreOwnerOrContract: Sender=" + notOwner.getAddress() + " Owner=" + owner.getAddress() + " Contract=" + governance.getAddress();
        
        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "addDexPricedCollateral", tokenAddress, active,
                lockingRatio, liquidationRatio, debtCeiling);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Arrange
        when(bwt.mock.symbol()).thenReturn(symbol);
        when(dex.mock.getPoolId(tokenAddress, bnUSD.getAddress())).thenReturn(poolID);

        // Act & Assert
        when(balancedOracle.mock.getPriceInLoop(symbol)).thenReturn(BigInteger.ZERO);
        expectedErrorMessage = "Reverted(0): Balanced oracle return a invalid icx price for " + symbol;
        Executable withFaultyPeg = () -> governance.invoke(owner, "addDexPricedCollateral", tokenAddress, active,
                lockingRatio, liquidationRatio, debtCeiling);
        expectErrorMessage(withFaultyPeg, expectedErrorMessage);

        // Act
        when(balancedOracle.mock.getPriceInLoop(symbol)).thenReturn(ICX);
        governance.invoke(owner, "addDexPricedCollateral", tokenAddress, active, lockingRatio, liquidationRatio,
                debtCeiling);

        // Assert
        verify(loans.mock, times(2)).addAsset(tokenAddress, active, true);
        verify(balancedOracle.mock, times(2)).addDexPricedAsset(symbol, poolID);
        verify(loans.mock).setLockingRatio(symbol, lockingRatio);
        verify(loans.mock).setLiquidationRatio(symbol, liquidationRatio);
        verify(loans.mock).setDebtCeiling(symbol, debtCeiling);
    }

    @Test
    void delegate() {
        // Arrange
        PrepDelegations delegation1 = new PrepDelegations();
        delegation1._address = Address.fromString("cx66d4d90f5f113eba575bf793570135f9b1011111");
        delegation1._votes_in_per = BigInteger.valueOf(70);
        PrepDelegations delegation2 = new PrepDelegations();
        delegation2._address = Address.fromString("cx66d4d90f5f113eba575bf793570135f9b1022222");
        delegation2._votes_in_per = BigInteger.valueOf(30);

        PrepDelegations[] delegations = new PrepDelegations[] {
            delegation1,
            delegation2
        };

        Account notOwner = sm.createAccount();
        String expectedErrorMessage = "SenderNotScoreOwnerOrContract: Sender=" + notOwner.getAddress() + " Owner=" + owner.getAddress() + " Contract=" + governance.getAddress();
        
        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "delegate", "loans", (Object) delegations);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "delegate", "loans", (Object) delegations);

        // Assert
        verify(loans.mock).delegate(any(PrepDelegations[].class));

    }

    @Test
    void balwAdminTransfer() {
        // Arrange
        Address _from = Account.newScoreAccount(scoreCount++).getAddress();
        Address _to = Account.newScoreAccount(scoreCount++).getAddress();
        BigInteger _value = BigInteger.TEN;
        byte[] _data  = new byte[0];
        Account notOwner = sm.createAccount();
        String expectedErrorMessage = "SenderNotScoreOwnerOrContract: Sender=" + notOwner.getAddress() + " Owner=" + owner.getAddress() + " Contract=" + governance.getAddress();
        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "balwAdminTransfer", _from, _to, _value, _data);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "balwAdminTransfer", _from, _to, _value, _data);

        // Assert
        verify(bwt.mock).adminTransfer(_from, _to, _value, _data);
    }

    @Test
    void setAddressesOnContract() {
        // Arrange
        String _contract = "loans";
        Account notOwner = sm.createAccount();
        String expectedErrorMessage = "SenderNotScoreOwnerOrContract: Sender=" + notOwner.getAddress() + " Owner=" + owner.getAddress() + " Contract=" + governance.getAddress();
        
        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setAddressesOnContract", _contract);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setAddressesOnContract", _contract);

        // Assert
        verify(loans.mock, times(2)).setDividends(dividends.getAddress());
        verify(loans.mock, times(2)).setStaking(staking.getAddress());
        verify(loans.mock, times(2)).setReserve(reserve.getAddress());
        verify(loans.mock, times(2)).setRewards(rewards.getAddress());

    }

    @Test
    void configureBalanced() {
        // Act
        governance.invoke(owner, "configureBalanced");

        // Assert
        verify(loans.mock).addAsset(sicx.getAddress(), true, true);
        verify(loans.mock).addAsset(bnUSD.getAddress(), true, false);
        verify(loans.mock).addAsset(baln.getAddress(), false, true);
    }

    @Test
    void launchBalanced() {
        // Arrange
        configureBalanced();

        // Act
        governance.invoke(owner, "launchBalanced");

        // Assert
        verify(loans.mock).setTimeOffset(any(BigInteger.class));
        verify(rewards.mock).setTimeOffset(any(BigInteger.class));
        verify(dex.mock).setTimeOffset(any(BigInteger.class));

        verify(rewards.mock).addNewDataSource("Loans", loans.getAddress());
        verify(rewards.mock).addNewDataSource("sICX/ICX", dex.getAddress());

        verify(rewards.mock).updateBalTokenDistPercentage(any(DistributionPercentage[].class));
    }

    @Test
    void createBnusdMarket() {
        // Arrange
        launchBalanced();
        BigInteger initialICX = BigInteger.TEN.pow(23);
        BigInteger bnusdPrice = BigInteger.ONE.pow(18);

        BigInteger bnUSDValue = BigInteger.TEN.pow(23);
        BigInteger sICXValue = BigInteger.TEN.pow(23);

        BigInteger sicxBnusdPid = BigInteger.TWO;
        BigInteger stakeAmount = initialICX.divide(BigInteger.valueOf(7));

        when(bnUSD.mock.priceInLoop()).thenReturn(bnusdPrice);

        when(bnUSD.mock.balanceOf(governance.getAddress())).thenReturn(bnUSDValue);
        when(sicx.mock.balanceOf(governance.getAddress())).thenReturn(sICXValue);
        when(dex.mock.getPoolId(sicx.getAddress(), bnUSD.getAddress())).thenReturn(sicxBnusdPid);

        // Act 
        sm.call(owner, initialICX, governance.getAddress(), "createBnusdMarket");

        // Assert
        verify(staking.mock).stakeICX(eq(governance.getAddress()), any(byte[].class));

        BigInteger amount = EXA.multiply(initialICX).divide(bnusdPrice.multiply(BigInteger.valueOf(7)));
        verify(loans.mock).depositAndBorrow("bnUSD", amount, governance.getAddress(), BigInteger.ZERO);

        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        verify(bnUSD.mock).transfer(dex.getAddress(), bnUSDValue, depositData.toString().getBytes());
        verify(sicx.mock).transfer(dex.getAddress(), sICXValue, depositData.toString().getBytes());

        verify(dex.mock).add(sicx.getAddress(), bnUSD.getAddress(), sICXValue, bnUSDValue, false);
        verify(dex.mock).setMarketName(sicxBnusdPid, "sICX/bnUSD");

        verify(rewards.mock).addNewDataSource("sICX/bnUSD", dex.getAddress());
        verify(stakedLp.mock).addPool(sicxBnusdPid);
        verify(rewards.mock, times(2)).updateBalTokenDistPercentage(any(DistributionPercentage[].class));
    }

    @Test
    void createBalnMarket() {
        // Arrange
        createBnusdMarket();
        BigInteger bnUSDValue = BigInteger.TEN.pow(23);
        BigInteger balnValue = BigInteger.TEN.pow(23);

        BigInteger balnBnusdPid = BigInteger.valueOf(3);

        when(dex.mock.getPoolId(baln.getAddress(), bnUSD.getAddress())).thenReturn(balnBnusdPid);

        // Act 
        governance.invoke(owner, "createBalnMarket", bnUSDValue, balnValue);

        // Assert
        verify(rewards.mock).claimRewards();
        verify(loans.mock).depositAndBorrow("bnUSD", bnUSDValue, governance.getAddress(), BigInteger.ZERO);

        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        verify(bnUSD.mock, times(2)).transfer(dex.getAddress(), bnUSDValue, depositData.toString().getBytes());
        verify(baln.mock).transfer(dex.getAddress(), balnValue, depositData.toString().getBytes());

        verify(dex.mock).add(baln.getAddress(), bnUSD.getAddress(), balnValue, bnUSDValue, false);
        verify(dex.mock).setMarketName(balnBnusdPid, "BALN/bnUSD");

        verify(rewards.mock).addNewDataSource("BALN/bnUSD", dex.getAddress());
        verify(stakedLp.mock).addPool(balnBnusdPid);
        verify(rewards.mock, times(3)).updateBalTokenDistPercentage(any(DistributionPercentage[].class));
    }

    @Test
    void createBalnSicxMarket() {
        // Arrange
        createBalnMarket();
        BigInteger initialICX = BigInteger.TEN.pow(23);
        BigInteger bnusdPrice = BigInteger.ONE.pow(18);

        BigInteger sicxValue = BigInteger.TEN.pow(23);
        BigInteger balnValue = BigInteger.TEN.pow(23);

        BigInteger balnSicxPid = BigInteger.valueOf(4);

        when(dex.mock.getPoolId(baln.getAddress(), sicx.getAddress())).thenReturn(balnSicxPid);

        // Act 
        governance.invoke(owner, "createBalnSicxMarket", sicxValue, balnValue);

        // Assert
        verify(rewards.mock, times(2)).claimRewards();

        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        verify(sicx.mock, times(2)).transfer(dex.getAddress(), sicxValue, depositData.toString().getBytes());
        verify(baln.mock, times(2)).transfer(dex.getAddress(), balnValue, depositData.toString().getBytes());

        verify(dex.mock).add(baln.getAddress(), sicx.getAddress(), balnValue, sicxValue, false);
        verify(dex.mock).setMarketName(balnSicxPid, "BALN/sICX");

        verify(rewards.mock).addNewDataSource("BALN/sICX", dex.getAddress());
        verify(stakedLp.mock).addPool(balnSicxPid);
        verify(rewards.mock, times(4)).updateBalTokenDistPercentage(any(DistributionPercentage[].class));
    }
}