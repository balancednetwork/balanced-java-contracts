package network.balanced.score.core.governance;
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

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Context;
import score.Address;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static network.balanced.score.lib.test.UnitTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.booleanThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.MockedStatic.Verification;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import network.balanced.score.lib.structs.BalancedAddresses;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.core.governance.interfaces.*;

import static network.balanced.score.core.governance.GovernanceConstants.*;

public class GovernanceTest extends GovernanceTestBase {
    @BeforeEach
    public void setup() throws Exception {
       super.setup();
    }

    @Test
    void getVotingWeight() {
        // Arrange
        Account user = sm.createAccount();
        BigInteger day = BigInteger.TEN;        
        BigInteger expectedWeight = BigInteger.ONE;

        when(baln.mock.stakedBalanceOfAt(user.getAddress(), day)).thenReturn(expectedWeight);
        
        // Act
        BigInteger votingWeight  = (BigInteger) governance.call("myVotingWeight", user.getAddress(), day);

        // Assert
        assertEquals(expectedWeight, votingWeight);
    }

    @Test
    void rebalancingSetBnusd() {
        // Arrange
        Address _address = Account.newScoreAccount(scoreCount++).getAddress();
        Account notOwner = sm.createAccount();
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
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
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
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
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
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
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
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
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setLoansRebalance", _address);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setLoansRebalance", _address);

        // Assert
        verify(loans.mock).setRebalancing(_address);
    }

    @Test
    void setLoansDex() {
        // Arrange
        Address _address = Account.newScoreAccount(scoreCount++).getAddress();
        Account notOwner = sm.createAccount();
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
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
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
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
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setRebalancingThreshold", _value);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setRebalancingThreshold", _value);

        // Assert
        verify(rebalancing.mock).setPriceDiffThreshold(_value);
    }

    @Test
    void setAdmins() {
         // Arrange
         Account notOwner = sm.createAccount();
         String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
         
         // Act & Assert
         Executable withNotOwner = () -> governance.invoke(notOwner, "setAdmins");
         expectErrorMessage(withNotOwner, expectedErrorMessage);
 
         // Act
         governance.invoke(owner, "setAdmins") ;

         // Assert
         //TODO
    }

   
    @Test
    void toggleBalancedOn() {
        // Arrange
        Account notOwner = sm.createAccount();
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "toggleBalancedOn");
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "toggleBalancedOn");

        // Assert
        verify(loans.mock).toggleLoansOn();
    }

    @Test
    void addAsset() {
        // Arrange
        Address tokenAddress = bwt.getAddress();
        boolean active = false;
        boolean collateral = false;
        Account notOwner = sm.createAccount();
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "addAsset", tokenAddress, active, collateral);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "addAsset", tokenAddress, active, collateral);

        // Assert
        verify(loans.mock).addAsset(tokenAddress, active, collateral);
        verify(bwt.mock).setAdmin(loans.getAddress()); 
    }

    @Test
    void toggleAssetActive() {
        // Arrange
        String _symbol = "test";
        Account notOwner = sm.createAccount();
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
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
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
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
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "removeDataSource", name);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "removeDataSource", name);

        // Assert
        verify(rewards.mock).removeDataSource(name);

    }

    // @Test
    // void updateBalTokenDistPercentage(DistributionPercentage[] _recipient_list) {
    //     onlyOwner();
    //     RewardsScoreInterface rewards = new RewardsScoreInterface(Addresses.get("rewards"));
    //     Object test = (Object)_recipient_list;
    //     rewards.updateBalTokenDistPercentage((DistributionPercentage[])test);
    // }

    // @Test
    // void bonusDist() {
    //     // Arrange
    //     Address[] _addresses, BigInteger[] _amounts = null;
    //     Account notOwner = sm.createAccount();
    //     String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
    //     // Act & Assert
    //     Executable withNotOwner = () -> governance.invoke(notOwner, "bonusDist", _amounts);
    //     expectErrorMessage(withNotOwner, expectedErrorMessage);

    //     // Act
    //     governance.invoke(notOwner, "bonusDist", _amounts);

    //     // Assert
    //     verify(rewards.mock).bonusDist(_amounts);

    // }

    @Test
    void setDay() {
        // Arrange
        BigInteger _day = BigInteger.TEN;
        Account notOwner = sm.createAccount();
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setDay", _day);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setDay", _day);

        // Assert
        verify(rewards.mock).setDay(_day);

    }

    @Test
    void dexPermit() {
        // Arrange
        BigInteger _id = BigInteger.TEN;
        boolean _permission = true;
        Account notOwner = sm.createAccount();
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "dexPermit", _id, _permission);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "dexPermit", _id, _permission);

        // Assert
        verify(dex.mock).permit(_id, _permission);
    }

    @Test
    void dexAddQuoteCoin() {
        // Arrange
        Address _address = Account.newScoreAccount(scoreCount++).getAddress();
        Account notOwner = sm.createAccount();
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
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
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setMarketName", _id, _name);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setMarketName", _id, _name);

        // Assert
        verify(dex.mock).setMarketName(_id, _name);
    }

    // @Test
    // void delegate() {
    //     // Arrange
    //     PrepDelegations[] _delegations = null;
    //     Account notOwner = sm.createAccount();
    //     String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
    //     // Act & Assert
    //     Executable withNotOwner = () -> governance.invoke(notOwner, "delegate", _delegations);
    //     expectErrorMessage(withNotOwner, expectedErrorMessage);

    //     // Act
    //     governance.invoke(notOwner, "delegate", _delegations);

    //     // Assert
    //     verify(loans.mock).delegate(_delegations);

    // }

    @Test
    void balwAdminTransfer() {
        // Arrange
        Address _from = Account.newScoreAccount(scoreCount++).getAddress();
        Address _to = Account.newScoreAccount(scoreCount++).getAddress();
        BigInteger _value = BigInteger.TEN;
        byte[] _data  = new byte[0];
        Account notOwner = sm.createAccount();
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
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
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
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
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
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
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
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
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
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
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
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
         String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
         
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
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
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
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
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
          String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
          
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
        String _symbol = "sicx";
        Address oracleAddress = oracle.getAddress();
        Account notOwner = sm.createAccount();
        Map<String, Address> tokens = Map.of(_symbol, sicx.getAddress());
        when(loans.mock.getAssetTokens()).thenReturn(tokens);

        // Act & Assert
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        Executable withNotOwner = () -> governance.invoke(notOwner, "setAssetOracle", _symbol, oracleAddress);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        String nonSupportedToken = "test";
        expectedErrorMessage =  TAG + ": " + nonSupportedToken + " is not a supported asset in Balanced.";
        Executable withNonSupportedToken = () -> governance.invoke(owner, "setAssetOracle", nonSupportedToken, oracleAddress);
        expectErrorMessage(withNonSupportedToken, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setAssetOracle", _symbol, oracleAddress);

        // Assert
        verify(sicx.mock).setOracle(oracleAddress);
    }

    @Test
    void setAssetOracleName() {
        // Arrange
        String _symbol = "sicx";
        String oracleName = "oracleName";
        Account notOwner = sm.createAccount();
        Map<String, Address> tokens = Map.of(_symbol, sicx.getAddress());
        when(loans.mock.getAssetTokens()).thenReturn(tokens);

        // Act & Assert
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        Executable withNotOwner = () -> governance.invoke(notOwner, "setAssetOracleName", _symbol, oracleName);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        String nonSupportedToken = "test";
        expectedErrorMessage =  TAG + ": " + nonSupportedToken + " is not a supported asset in Balanced.";
        Executable withNonSupportedToken = () -> governance.invoke(owner, "setAssetOracleName", nonSupportedToken, oracleName);
        expectErrorMessage(withNonSupportedToken, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setAssetOracleName", _symbol, oracleName);

        // Assert
        verify(sicx.mock).setOracleName(oracleName);
    }

    @Test
    void setAssetMinInterval() {
        // Arrange
        String _symbol = "sicx";
        BigInteger minInterval = BigInteger.TEN;
        Account notOwner = sm.createAccount();
        Map<String, Address> tokens = Map.of(_symbol, sicx.getAddress());
        when(loans.mock.getAssetTokens()).thenReturn(tokens);

        // Act & Assert
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        Executable withNotOwner = () -> governance.invoke(notOwner, "setAssetMinInterval", _symbol, minInterval);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        String nonSupportedToken = "test";
        expectedErrorMessage =  TAG + ": " + nonSupportedToken + " is not a supported asset in Balanced.";
        Executable withNonSupportedToken = () -> governance.invoke(owner, "setAssetMinInterval", nonSupportedToken, minInterval);
        expectErrorMessage(withNonSupportedToken, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setAssetMinInterval", _symbol, minInterval);

        // Assert
        verify(sicx.mock).setMinInterval(minInterval);
    }

    @Test
    void bnUSDSetOracle() {
        // Arrange
        Address _address = Account.newScoreAccount(scoreCount++).getAddress();
        Account notOwner = sm.createAccount();
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
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
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
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
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "bnUSDSetMinInterval", _interval);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "bnUSDSetMinInterval", _interval);

        // Assert
        verify(bnUSD.mock).setMinInterval(_interval);

    }

    // @Test
    // void addUsersToActiveAddresses() {
    //     // Arrange
    //     BigInteger _poolId, Address[] _addressList = BigInteger.TEN;
    //     Account notOwner = sm.createAccount();
    //     String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
    //     // Act & Assert
    //     Executable withNotOwner = () -> governance.invoke(notOwner, "addUsersToActiveAddresses", _addressList);
    //     expectErrorMessage(withNotOwner, expectedErrorMessage);

    //     // Act
    //     governance.invoke(notOwner, "addUsersToActiveAddresses", _addressList);

    //     // Assert
    //     verify(dex.mock).addLpAddresses(_addressList);
    // }

    @Test
    void setRedemptionFee() {
        // Arrange
        BigInteger _fee = BigInteger.TEN;
        Account notOwner = sm.createAccount();
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
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
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
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
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setRedeemBatchSize", _value);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setRedeemBatchSize", _value);

        // Assert
        verify(loans.mock).setRedeemBatchSize(_value);

    }

    @Test
    void addPoolOnStakedLp() {
        // Arrange
        BigInteger _id = BigInteger.TEN;
        Account notOwner = sm.createAccount();
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "addPoolOnStakedLp", _id);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "addPoolOnStakedLp", _id);

        // Assert
        verify(stakedLp.mock).addPool(_id);

    }

    @Test
    void setAddressesOnContract() {
        // Arrange
        String _contract = "loans";
        Account notOwner = sm.createAccount();
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setAddressesOnContract", _contract);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setAddressesOnContract", _contract);

        // Assert
        verify(loans.mock, times(2)).setRewards(rewards.getAddress());
        verify(loans.mock, times(2)).setDividends(dividends.getAddress());
        verify(loans.mock, times(2)).setStaking(staking.getAddress());
        verify(loans.mock, times(2)).setReserve(reserve.getAddress());
    }

    @Test
    void setRouter() {
        // Arrange
        Address _router = Account.newScoreAccount(scoreCount++).getAddress();
        Account notOwner = sm.createAccount();
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "setRouter", _router);
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "setRouter", _router);

        // Assert
        Map<String, Address> addresses =  (Map<String, Address>) governance.call("getAddresses");
        assertEquals(_router, addresses.get("router"));
    }

    @Test
    void enable_fee_handler() {
        // Arrange
        Account notOwner = sm.createAccount();
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
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
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + notOwner.getAddress() + "Owner=" + owner.getAddress();
        
        // Act & Assert
        Executable withNotOwner = () -> governance.invoke(notOwner, "disable_fee_handler");
        expectErrorMessage(withNotOwner, expectedErrorMessage);

        // Act
        governance.invoke(owner, "disable_fee_handler");

        // Assert
        verify(feehandler.mock).disable();
    }
}