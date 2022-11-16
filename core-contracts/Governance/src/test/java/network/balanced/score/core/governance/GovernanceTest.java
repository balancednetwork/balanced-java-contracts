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
import score.Context;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.governance.GovernanceConstants.TAG;
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
        BigInteger block = BigInteger.valueOf(Context.getBlockHeight());
        BigInteger expectedWeight = BigInteger.ONE;

        when(bBaln.mock.balanceOfAt(user.getAddress(), block)).thenReturn(expectedWeight);
        // Act
        BigInteger votingWeight = (BigInteger) governance.call("myVotingWeight", user.getAddress(), block);

        // Assert
        assertEquals(expectedWeight, votingWeight);
    }

    @Test
    void rebalancingSetBnusd() {
        // Arrange
        Address _address = Account.newScoreAccount(scoreCount++).getAddress();
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "rebalancingSetBnusd", _address);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "rebalancingSetBnusd", _address);

        // Assert
        verify(rebalancing.mock).setBnusd(_address);
    }

    @Test
    void rebalancingSetSicx() {
        // Arrange
        Address _address = Account.newScoreAccount(scoreCount++).getAddress();
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "rebalancingSetSicx", _address);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "rebalancingSetSicx", _address);

        // Assert
        verify(rebalancing.mock).setSicx(_address);
    }

    @Test
    void rebalancingSetDex() {
        // Arrange
        Address _address = Account.newScoreAccount(scoreCount++).getAddress();
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "rebalancingSetDex", _address);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "rebalancingSetDex", _address);

        // Assert
        verify(rebalancing.mock).setDex(_address);
    }

    @Test
    void rebalancingSetLoans() {
        // Arrange
        Address _address = Account.newScoreAccount(scoreCount++).getAddress();
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "rebalancingSetLoans", _address);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "rebalancingSetLoans", _address);

        // Assert
        verify(rebalancing.mock).setLoans(_address);
    }

    @Test
    void setLoansRebalance() {
        // Arrange
        Address _address = Account.newScoreAccount(scoreCount++).getAddress();
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setLoansRebalance", _address);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setLoansRebalance", _address);

        // Assert
        verify(loans.mock).setRebalance(_address);
    }

    @Test
    void setLoansDex() {
        // Arrange
        Address _address = Account.newScoreAccount(scoreCount++).getAddress();
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setLoansDex", _address);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setLoansDex", _address);

        // Assert
        verify(loans.mock).setDex(_address);
    }

    @Test
    void setRebalancing() {
        // Arrange
        Address _address = Account.newScoreAccount(scoreCount++).getAddress();
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setRebalancing", _address);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        governance.invoke(owner, "setRebalancing", _address);
    }

    @Test
    void setRebalancingThreshold() {
        // Arrange
        BigInteger _value = BigInteger.TEN;
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setRebalancingThreshold", _value);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setRebalancingThreshold", _value);

        // Assert
        verify(rebalancing.mock).setPriceDiffThreshold(_value);
    }

    @Test
    void setGetVoteDurationLimits() {
        // Arrange
        BigInteger min = BigInteger.TWO;
        BigInteger max = BigInteger.TEN;
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setVoteDurationLimits", min, max);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        expectedErrorMessage = "Reverted(0): Minimum vote duration has to be above 1";
        Executable withToLowMin = () -> governance.invoke(owner, "setVoteDurationLimits", BigInteger.ZERO, max);
        expectErrorMessage(withToLowMin, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setVoteDurationLimits", min, max);

        // Assert
        BigInteger minVoteDuration = (BigInteger) governance.call("getMinVoteDuration");
        BigInteger maxVoteDuration = (BigInteger) governance.call("getMaxVoteDuration");
        assertEquals(min, minVoteDuration);
        assertEquals(max, maxVoteDuration);
    }

    @Test
    void setAdmins() {
        // Arrange
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setAdmins");
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setAdmins");

        // Assert
        //TODO
    }

    @Test
    void toggleBalancedOn() {
        // Arrange
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "toggleBalancedOn");
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "toggleBalancedOn");

        // Assert
        verify(loans.mock).toggleLoansOn();
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
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

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
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

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
    void setDebtCeiling() {
        // Arrange
        String symbol = "TEST";
        BigInteger limit = EXA;
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setDebtCeiling", symbol, limit);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setDebtCeiling", symbol, limit);

        // Assert
        verify(loans.mock).setDebtCeiling(symbol, limit);
    }

    @Test
    void setPeg() {
        // Arrange
        String symbol = "TEST";
        String peg = "BTC";
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setPeg", symbol, peg);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setPeg", symbol, peg);

        // Assert
        verify(balancedOracle.mock).setPeg(symbol, peg);
    }

    @Test
    void addDexPricedAsset() {
        // Arrange
        String symbol = "TEST";
        BigInteger poolId = BigInteger.TWO;
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "addDexPricedAsset", symbol, poolId);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "addDexPricedAsset", symbol, poolId);

        // Assert
        verify(balancedOracle.mock).addDexPricedAsset(symbol, poolId);
    }

    @Test
    void removeDexPricedAsset() {
        // Arrange
        String symbol = "TEST";
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "removeDexPricedAsset", symbol);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "removeDexPricedAsset", symbol);

        // Assert
        verify(balancedOracle.mock).removeDexPricedAsset(symbol);
    }

    @Test
    void toggleAssetActive() {
        // Arrange
        String _symbol = "test";
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "toggleAssetActive", _symbol);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "toggleAssetActive", _symbol);

        // Assert
        verify(loans.mock).toggleAssetActive(_symbol);
    }

    @Test
    void addNewDataSource() {
        // Arrange
        String name = "test";
        Address address = Account.newScoreAccount(scoreCount++).getAddress();
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "addNewDataSource", name, address.toString());
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "addNewDataSource", name, address.toString());

        // Assert
        verify(rewards.mock).addNewDataSource(name, address);
    }

    @Test
    void removeDataSource() {
        // Arrange
        String name = "test";
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "removeDataSource", name);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "removeDataSource", name);

        // Assert
        verify(rewards.mock).removeDataSource(name);
    }

    @Test
    void updateBalTokenDistPercentage() {
        // Arrange
        DistributionPercentage[] distributionPercentage = GovernanceConstants.RECIPIENTS;
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "updateBalTokenDistPercentage",
                (Object) distributionPercentage);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "updateBalTokenDistPercentage", (Object) distributionPercentage);

        // Assert
        verify(rewards.mock).updateBalTokenDistPercentage(distributionPercentage);
    }

    @Test
    void dexPermit() {
        // Arrange
        BigInteger _id = BigInteger.TEN;
        boolean _permission = true;
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "dexPermit", _id, _permission);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "dexPermit", _id, _permission);

        // Assert
        verify(dex.mock).permit(_id, _permission);
    }

    @Test
    void setAcceptedDividendTokens() {
        // Arrange
        Address[] _tokens = new Address[]{Account.newScoreAccount(scoreCount).getAddress(),
                Account.newScoreAccount(scoreCount).getAddress()};

        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setAcceptedDividendTokens", (Object) _tokens);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setAcceptedDividendTokens", (Object) _tokens);

        // Assert
        verify(feehandler.mock).setAcceptedDividendTokens(_tokens);
    }

    @Test
    void setRoute() {
        // Arrange
        Address from = Account.newScoreAccount(scoreCount).getAddress();
        Address to = Account.newScoreAccount(scoreCount).getAddress();
        Address[] _path = new Address[]{Account.newScoreAccount(scoreCount).getAddress(),
                Account.newScoreAccount(scoreCount).getAddress()};

        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setRoute", from, to, (Object) _path);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setRoute", from, to, (Object) _path);

        // Assert
        verify(feehandler.mock).setRoute(from, to, _path);
    }

    @Test
    void dexAddQuoteCoin() {
        // Arrange
        Address _address = Account.newScoreAccount(scoreCount++).getAddress();
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "dexAddQuoteCoin", _address);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "dexAddQuoteCoin", _address);

        // Assert
        verify(dex.mock).addQuoteCoin(_address);
    }

    @Test
    void setMarketName() {
        // Arrange
        BigInteger _id = BigInteger.ONE;
        String _name = "test";
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setMarketName", _id, _name);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setMarketName", _id, _name);

        // Assert
        verify(dex.mock).setMarketName(_id, _name);
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

        PrepDelegations[] delegations = new PrepDelegations[]{
                delegation1,
                delegation2
        };

        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

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
        byte[] _data = new byte[0];
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "balwAdminTransfer", _from, _to, _value, _data);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "balwAdminTransfer", _from, _to, _value, _data);

        // Assert
        verify(bwt.mock).adminTransfer(_from, _to, _value, _data);
    }

    @Test
    void setbnUSD() {
        // Arrange
        Address _address = Account.newScoreAccount(scoreCount++).getAddress();
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setbnUSD", _address);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setbnUSD", _address);

        // Assert
        verify(baln.mock).setBnusd(_address);
    }

    @Test
    void setDividends() {
        // Arrange
        Address _score = Account.newScoreAccount(scoreCount++).getAddress();
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setDividends", _score);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setDividends", _score);

        // Assert
        verify(baln.mock).setDividends(_score);

    }

    @Test
    void balanceSetDex() {
        // Arrange
        Address _address = Account.newScoreAccount(scoreCount++).getAddress();
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "balanceSetDex", _address);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "balanceSetDex", _address);

        // Assert
        verify(baln.mock).setDex(_address);

    }

    @Test
    void balanceSetOracleName() {
        // Arrange
        String _name = "test";
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "balanceSetOracleName", _name);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "balanceSetOracleName", _name);

        // Assert
        verify(baln.mock).setOracleName(_name);

    }

    @Test
    void balanceSetMinInterval() {
        // Arrange
        BigInteger _interval = BigInteger.TEN;
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "balanceSetMinInterval", _interval);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "balanceSetMinInterval", _interval);

        // Assert
        verify(baln.mock).setMinInterval(_interval);

    }

    @Test
    void balanceToggleStakingEnabled() {
        // Arrange
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "balanceToggleStakingEnabled");
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "balanceToggleStakingEnabled");

        // Assert
        verify(baln.mock).toggleStakingEnabled();
    }

    @Test
    void balanceSetMinimumStake() {
        // Arrange
        BigInteger _amount = BigInteger.TEN;
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "balanceSetMinimumStake", _amount);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "balanceSetMinimumStake", _amount);

        // Assert
        verify(baln.mock).setMinimumStake(_amount);

    }

    @Test
    void balanceSetUnstakingPeriod() {
        // Arrange
        BigInteger _time = BigInteger.TEN;
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "balanceSetUnstakingPeriod", _time);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "balanceSetUnstakingPeriod", _time);

        // Assert
        verify(baln.mock).setUnstakingPeriod(_time);

    }

    @Test
    void addAcceptedTokens() {
        // Arrange
        Address token = Account.newScoreAccount(scoreCount++).getAddress();
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "addAcceptedTokens", token.toString());
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "addAcceptedTokens", token.toString());

        // Assert
        verify(dividends.mock).addAcceptedTokens(token);
    }

    @Test
    void setAssetOracle() {
        // Arrange
        String _symbol = "bnUSD";
        Address oracleAddress = oracle.getAddress();
        Account notOwner = sm.createAccount();
        Map<String, String> tokens = Map.of(_symbol, bnUSD.getAddress().toString());
        when(loans.mock.getAssetTokens()).thenReturn(tokens);

        // Act & Assert
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        Executable withNotOwner = () -> governance.invoke(notOwner, "setAssetOracle", _symbol, oracleAddress);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        String nonSupportedToken = "test";
        expectedErrorMessage = TAG + ": " + nonSupportedToken + " is not a supported asset in Balanced.";
        Executable withNonSupportedToken = () -> governance.invoke(owner, "setAssetOracle", nonSupportedToken,
                oracleAddress);
        expectErrorMessage(withNonSupportedToken, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setAssetOracle", _symbol, oracleAddress);

        // Assert
        verify(bnUSD.mock, times(2)).setOracle(oracleAddress);
    }

    @Test
    void setAssetOracleName() {
        // Arrange
        String _symbol = "bnUSD";
        String oracleName = "oracleName";
        Account notOwner = sm.createAccount();
        Map<String, String> tokens = Map.of(_symbol, bnUSD.getAddress().toString());
        when(loans.mock.getAssetTokens()).thenReturn(tokens);

        // Act & Assert
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        Executable withNotOwner = () -> governance.invoke(notOwner, "setAssetOracleName", _symbol, oracleName);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        String nonSupportedToken = "test";
        expectedErrorMessage = TAG + ": " + nonSupportedToken + " is not a supported asset in Balanced.";
        Executable withNonSupportedToken = () -> governance.invoke(owner, "setAssetOracleName", nonSupportedToken,
                oracleName);
        expectErrorMessage(withNonSupportedToken, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setAssetOracleName", _symbol, oracleName);

        // Assert
        verify(bnUSD.mock).setOracleName(oracleName);
    }

    @Test
    void setAssetMinInterval() {
        // Arrange
        String _symbol = "bnUSD";
        BigInteger minInterval = BigInteger.TEN;
        Account notOwner = sm.createAccount();
        Map<String, String> tokens = Map.of(_symbol, bnUSD.getAddress().toString());
        when(loans.mock.getAssetTokens()).thenReturn(tokens);

        // Act & Assert
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        Executable withNotOwner = () -> governance.invoke(notOwner, "setAssetMinInterval", _symbol, minInterval);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        String nonSupportedToken = "test";
        expectedErrorMessage = TAG + ": " + nonSupportedToken + " is not a supported asset in Balanced.";
        Executable withNonSupportedToken = () -> governance.invoke(owner, "setAssetMinInterval", nonSupportedToken,
                minInterval);
        expectErrorMessage(withNonSupportedToken, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setAssetMinInterval", _symbol, minInterval);

        // Assert
        verify(bnUSD.mock).setMinInterval(minInterval);
    }

    @Test
    void bnUSDSetOracle() {
        // Arrange
        Address _address = Account.newScoreAccount(scoreCount++).getAddress();
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "bnUSDSetOracle", _address);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "bnUSDSetOracle", _address);

        // Assert
        verify(bnUSD.mock).setOracle(_address);

    }

    @Test
    void bnUSDSetOracleName() {
        // Arrange
        String _name = "test";
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "bnUSDSetOracleName", _name);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "bnUSDSetOracleName", _name);

        // Assert
        verify(bnUSD.mock).setOracleName(_name);

    }

    @Test
    void bnUSDSetMinInterval() {
        // Arrange
        BigInteger _interval = BigInteger.TEN;
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "bnUSDSetMinInterval", _interval);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "bnUSDSetMinInterval", _interval);

        // Assert
        verify(bnUSD.mock).setMinInterval(_interval);

    }

    @Test
    void addUsersToActiveAddresses() {
        // Arrange
        BigInteger _poolId = BigInteger.TEN;
        Address[] _addressList = new Address[]{Account.newScoreAccount(scoreCount).getAddress(),
                Account.newScoreAccount(scoreCount).getAddress()};
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "addUsersToActiveAddresses", _poolId,
                (Object) _addressList);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "addUsersToActiveAddresses", _poolId, (Object) _addressList);

        // Assert
        verify(dex.mock).addLpAddresses(_poolId, _addressList);
    }

    @Test
    void setRedemptionFee() {
        // Arrange
        BigInteger _fee = BigInteger.TEN;
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setRedemptionFee", _fee);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setRedemptionFee", _fee);

        // Assert
        verify(loans.mock).setRedemptionFee(_fee);

    }

    @Test
    void setMaxRetirePercent() {
        // Arrange
        BigInteger _value = BigInteger.TEN;
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setMaxRetirePercent", _value);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setMaxRetirePercent", _value);

        // Assert
        verify(loans.mock).setMaxRetirePercent(_value);

    }

    @Test
    void setRedeemBatchSize() {
        // Arrange
        BigInteger _value = BigInteger.TEN;
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setRedeemBatchSize", _value);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setRedeemBatchSize", _value);

        // Assert
        verify(loans.mock).setRedeemBatchSize(_value.intValue());

    }

    @Test
    void addStakedLpDataSource() {
        // Arrange
        BigInteger id = BigInteger.TEN;
        String name = "testSource";
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "addStakedLpDataSource", name, id, 1);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "addStakedLpDataSource", name, id, 1);

        // Assert
        verify(stakedLp.mock).addDataSource(id, name);
        verify(rewards.mock).createDataSource(name, stakedLp.getAddress(), 1);
    }

    @Test
    void setAddressesOnContract() {
        // Arrange
        String _contract = "loans";
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

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

    @SuppressWarnings("unchecked")
    @Test
    void setRouter() {
        // Arrange
        Address _router = Account.newScoreAccount(scoreCount++).getAddress();
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setRouter", _router);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setRouter", _router);

        // Assert
        Map<String, Address> addresses = (Map<String, Address>) governance.call("getAddresses");
        assertEquals(_router, addresses.get("router"));
    }

    @Test
    void setFeeProcessingInterval() {
        // Arrange
        BigInteger interval = BigInteger.TEN;
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setFeeProcessingInterval", interval);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setFeeProcessingInterval", interval);

        // Assert
        verify(feehandler.mock).setFeeProcessingInterval(interval);

    }

    @Test
    void deleteRoute() {
        // Arrange
        Address from = Account.newScoreAccount(scoreCount++).getAddress();
        Address to = Account.newScoreAccount(scoreCount++).getAddress();
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "deleteRoute", from, to);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "deleteRoute", from, to);

        // Assert
        verify(feehandler.mock).deleteRoute(from, to);
    }

    @Test
    void enable_fee_handler() {
        // Arrange
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "enable_fee_handler");
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "enable_fee_handler");

        // Assert
        verify(feehandler.mock).enable();

    }

    @Test
    void disable_fee_handler() {
        // Arrange
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "disable_fee_handler");
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "disable_fee_handler");

        // Assert
        verify(feehandler.mock).disable();
    }

    @Test
    void reserveTransfer() {
        // Arrange
        BigInteger amount = BigInteger.TEN;
        Account notOwner = sm.createAccount();
        String expectedErrorMessage =
                "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();

        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "reserveTransfer", sicx.getAddress(),
                loans.getAddress(), amount);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "reserveTransfer", sicx.getAddress(), loans.getAddress(), amount);

        // Assert
        verify(reserve.mock).transfer(sicx.getAddress(), loans.getAddress(), amount);
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

        verify(baln.mock).toggleStakingEnabled();
        verify(loans.mock).turnLoansOn();
        verify(dex.mock).turnDexOn();
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

        verify(rewards.mock).addNewDataSource("sICX/bnUSD", stakedLp.getAddress());
        verify(stakedLp.mock).addDataSource(sicxBnusdPid, "sICX/bnUSD");
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
        String[] sources = new String[]{"Loans", "sICX/bnUSD"};
        verify(rewards.mock).claimRewards(sources);
        verify(loans.mock).depositAndBorrow("bnUSD", bnUSDValue, governance.getAddress(), BigInteger.ZERO);

        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        verify(bnUSD.mock, times(2)).transfer(dex.getAddress(), bnUSDValue, depositData.toString().getBytes());
        verify(baln.mock).transfer(dex.getAddress(), balnValue, depositData.toString().getBytes());

        verify(dex.mock).add(baln.getAddress(), bnUSD.getAddress(), balnValue, bnUSDValue, false);
        verify(dex.mock).setMarketName(balnBnusdPid, "BALN/bnUSD");

        verify(rewards.mock).addNewDataSource("BALN/bnUSD", stakedLp.getAddress());
        verify(stakedLp.mock).addDataSource(balnBnusdPid, "BALN/bnUSD");
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
        String[] sources = new String[]{"Loans", "sICX/bnUSD", "BALN/bnUSD"};
        verify(rewards.mock, times(1)).claimRewards(sources);

        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        verify(sicx.mock, times(2)).transfer(dex.getAddress(), sicxValue, depositData.toString().getBytes());
        verify(baln.mock, times(2)).transfer(dex.getAddress(), balnValue, depositData.toString().getBytes());

        verify(dex.mock).add(baln.getAddress(), sicx.getAddress(), balnValue, sicxValue, false);
        verify(dex.mock).setMarketName(balnSicxPid, "BALN/sICX");

        verify(rewards.mock).addNewDataSource("BALN/sICX", stakedLp.getAddress());
        verify(stakedLp.mock).addDataSource(balnSicxPid, "BALN/sICX");
        verify(rewards.mock, times(4)).updateBalTokenDistPercentage(any(DistributionPercentage[].class));
    }
}