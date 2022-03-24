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

package network.balanced.score.core.rebalancing;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.token.irc2.IRC2Basic;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;
import static network.balanced.score.core.rebalancing.Checks.*;
import static network.balanced.score.core.rebalancing.Constants.*;

import java.beans.Transient;
import java.io.Console;
import java.math.BigInteger;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;


public class RebalancingTest extends TestBase {

    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private static final Account adminAccount = sm.createAccount();
    private static final Account governanceScore = Account.newScoreAccount(0);

    private Score rebalancingScore;
    private Score dexMock;
    private Score loansMock;
    private Score bnUSDScore;
    private Score sICXScore;

    private LoansMock loansSpy;

    private void expectErrorMessage(Executable _contractCall, String _expectedErrorMessage) {
        AssertionError e = Assertions.assertThrows(AssertionError.class, _contractCall);
        assertEquals(_expectedErrorMessage, e.getMessage());
    }

    @BeforeEach
    public void setup() throws Exception {
        rebalancingScore = sm.deploy(owner, Rebalancing.class, governanceScore.getAddress());
        dexMock = sm.deploy(owner, DexMock.class);
        loansMock = sm.deploy(owner, LoansMock.class);

        loansSpy = (LoansMock) spy(loansMock.getInstance());
        loansMock.setInstance(loansSpy);

        sICXScore = sm.deploy(owner, sICX.class, "Staked ICX", "sICX", 18, BigInteger.valueOf(1000000000).pow(18));
        bnUSDScore = sm.deploy(owner, bnUSD.class, "Balanced USD", "bnUSD", 18, BigInteger.valueOf(1000000000).pow(18));

        rebalancingScore.invoke(governanceScore, "setAdmin", adminAccount.getAddress());
        rebalancingScore.invoke(adminAccount, "setSicx", sICXScore.getAddress());
        rebalancingScore.invoke(adminAccount, "setBnusd", bnUSDScore.getAddress());
        rebalancingScore.invoke(adminAccount, "setLoans", loansMock.getAddress());
        rebalancingScore.invoke(adminAccount, "setDex", dexMock.getAddress());
    }

    @Test
    void setDex() {
        // Arrange
        Account sender = sm.createAccount();

        // Act & Assert 
        rebalancingScore.invoke(adminAccount, "setDex", dexMock.getAddress());
    }

    @Test
    void setDex_AdminNotSet() {
        // Arrange
        Account sender = sm.createAccount();
        rebalancingScore.invoke(governanceScore, "setAdmin", defaultAddress);
        String expectedErrorMessage = "Rebalancing: Admin address not set";

        // Act & Assert
        Executable setDexWithoutAdmin = () -> rebalancingScore.invoke(adminAccount, "setDex", dexMock.getAddress());
        expectErrorMessage(setDexWithoutAdmin, expectedErrorMessage);
    }

    @Test
    void setDex_NotFromAdmin() {
        // Arrange
        Account sender = sm.createAccount();
        String expectedErrorMessage = "Rebalancing: Sender not admin";

        // Act & Assert
        Executable setDexNotFromAdmin = () -> rebalancingScore.invoke(sender, "setDex", dexMock.getAddress());
        expectErrorMessage(setDexNotFromAdmin, expectedErrorMessage);
    }

    @Test
    void setDex_NonContractAddress() {
        // Arrange
        Account sender = sm.createAccount();
        String expectedErrorMessage = "Rebalancing: Address provided is an EOA address. A contract address is required.";

        // Act & Assert
        Executable setDexToInvalidAddress = () -> rebalancingScore.invoke(adminAccount, "setDex", sender.getAddress());
        expectErrorMessage(setDexToInvalidAddress, expectedErrorMessage);
    }

    @Test
    void setGovernance() {
        // Arrange
        Account sender = sm.createAccount();

        // Act
        rebalancingScore.invoke(owner, "setGovernance", governanceScore.getAddress());

        // Assert
        Address governanceAddress = (Address) rebalancingScore.call("getGovernance");
        assertEquals(governanceScore.getAddress(), governanceAddress);
    }

    @Test
    void setGovernance_notOwner() {
        // Arrange
        Account sender = sm.createAccount();
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + sender.getAddress() + "Owner=" + owner.getAddress();
        
         // Act & Assert
        Executable setGovernanceNotFromOwner = () -> rebalancingScore.invoke(sender, "setGovernance", governanceScore.getAddress());
        expectErrorMessage(setGovernanceNotFromOwner, expectedErrorMessage);
    }

    @Test
    void setAdmin() {
        // Arrange
        Account account = adminAccount;

        // Act 
        rebalancingScore.invoke(governanceScore, "setAdmin", account.getAddress());
        
        //Assert
        Address adminAddress = (Address) rebalancingScore.call("getAdmin");
        assertEquals(account.getAddress(), adminAddress);
    }

    @Test
    void setAdmin_notGovernance() {
        // Arrange
        Account account = adminAccount;
        String expectedErrorMessage = "Rebalancing: Sender not governance contract";

        // Act & Assert
        Executable setAdminNotFromGovernance = () -> rebalancingScore.invoke(sm.createAccount(), "setAdmin", account.getAddress());
        expectErrorMessage(setAdminNotFromGovernance, expectedErrorMessage);
    }

    @Test
    void getSICX() {
        // Arrange
        Account sender = sm.createAccount();

        // Act
        rebalancingScore.invoke(adminAccount, "setSicx", sICXScore.getAddress());

        // Assert
        Address sICXAdddress = (Address) rebalancingScore.call("getSicx");
        assertEquals(sICXScore.getAddress(), sICXAdddress);
    }

    @Test
    void setSICX_AdminNotSet() {
        // Arrange
        Account sender = sm.createAccount();
        rebalancingScore.invoke(governanceScore, "setAdmin", defaultAddress);
        String expectedErrorMessage = "Rebalancing: Admin address not set";

        // Act & Assert
        Executable setSICXWithoutAdmin = () -> rebalancingScore.invoke(adminAccount, "setSicx", sICXScore.getAddress());
        expectErrorMessage(setSICXWithoutAdmin, expectedErrorMessage);
    }

    @Test
    void setSICX_NotFromAdmin() {
        // Arrange
        Account sender = sm.createAccount();
        String expectedErrorMessage = "Rebalancing: Sender not admin";

        // Act & Assert
        Executable setSICXNotFromAdmin = () -> rebalancingScore.invoke(sender, "setSicx", sICXScore.getAddress());
        expectErrorMessage(setSICXNotFromAdmin, expectedErrorMessage);
    }

    @Test
    void setSICX_NonContractsAddress() {
        // Arrange
        Account sender = sm.createAccount();
        String expectedErrorMessage = "Rebalancing: Address provided is an EOA address. A contract address is required.";

        // Act & Assert
        Executable setSICXToInvalidAddress = () -> rebalancingScore.invoke(adminAccount, "setSicx", sender.getAddress());
        expectErrorMessage(setSICXToInvalidAddress, expectedErrorMessage);
    }
    
    @Test
    void getBnusd() {
        // Arrange
        Account sender = sm.createAccount();

        // Act
        rebalancingScore.invoke(adminAccount, "setBnusd", bnUSDScore.getAddress());

        // Assert
        Address bnUSDAddress = (Address) rebalancingScore.call("getBnusd");
        assertEquals(bnUSDScore.getAddress(), bnUSDAddress);
    }

    @Test
    void setBnusd_notAdmin() {
        //Arrange
        Account sender = sm.createAccount();
        String expectedErrorMessage = "Rebalancing: Sender not admin";

        // Act & Assert
        Executable setBnUSDNotFromAdmin = () -> rebalancingScore.invoke(sender, "setBnusd", bnUSDScore.getAddress());
        expectErrorMessage(setBnUSDNotFromAdmin, expectedErrorMessage);
    }

    @Test
    void setBnusd_notContract() {
        //Arrange
        Account sender = sm.createAccount();
        String expectedErrorMessage = "Rebalancing: Address provided is an EOA address. A contract address is required.";

        // Act & Assert
        Executable setBnUSDToInvalidAddress = () -> rebalancingScore.invoke(adminAccount, "setBnusd", sender.getAddress());
        expectErrorMessage(setBnUSDToInvalidAddress, expectedErrorMessage);
    }

    @Test
    void getLoans() {
        // Arrange
        Account sender = sm.createAccount();

        // Act
        rebalancingScore.invoke(adminAccount, "setLoans", loansMock.getAddress());
       
        // Assert
        Address loansAddress = (Address) rebalancingScore.call("getLoans");
        assertEquals(loansMock.getAddress(), loansAddress);
    }

    @Test
    void setLoans_notAdmin() {
        //Arrange
        Account sender = sm.createAccount();
        String expectedErrorMessage = "Rebalancing: Sender not admin";

        // Act & Assert
        Executable setLoansNotFromAdmin = () -> rebalancingScore.invoke(sender, "setLoans", loansMock.getAddress());
        expectErrorMessage(setLoansNotFromAdmin, expectedErrorMessage);
    }

    @Test
    void setLoans_notContract() {
        //Arrange
        Account sender = sm.createAccount();
        String expectedErrorMessage = "Rebalancing: Address provided is an EOA address. A contract address is required.";

        // Act & Assert
        Executable setLoansToInvalidAddress = () -> rebalancingScore.invoke(adminAccount, "setLoans", sender.getAddress());
        expectErrorMessage(setLoansToInvalidAddress, expectedErrorMessage);
    }

    @Test
    void GetPriceDiffThreshold() {
        Account sender = sm.createAccount();
        BigInteger expectedThreshold = BigInteger.ONE;
  
        rebalancingScore.invoke(governanceScore, "setPriceDiffThreshold", expectedThreshold);
        BigInteger threshold = (BigInteger) rebalancingScore.call("getPriceChangeThreshold");
        assertEquals(expectedThreshold, threshold);
    }

    @Test
    void setPriceDiffThreshold_notGovernance() {
        //Arrange
        Account sender = sm.createAccount();
        BigInteger threshold = BigInteger.ONE;
        String expectedErrorMessage = "Rebalancing: Sender not governance contract";

        // Act & Assert
        Executable setThresholdNotFromGovernance = () -> rebalancingScore.invoke(sm.createAccount(), "setPriceDiffThreshold",threshold);
        expectErrorMessage(setThresholdNotFromGovernance, expectedErrorMessage);
    }

    @Test
    void checkRebalancingStatus_raisePrice() {
        //Arrange
        BigInteger bnUSDPrice = BigInteger.TEN.pow(12);
        BigInteger sICXPrice = BigInteger.TEN.pow(12);
        BigInteger priceDifferenceThreshold = BigInteger.valueOf(8).pow(18);
        BigInteger poolBase = BigInteger.TEN.pow(11);
        BigInteger poolQuote = BigInteger.TEN.pow(13);
        BigInteger price = bnUSDPrice.multiply(EXA).divide(sICXPrice);
    
        BigInteger expectedTokensToSell = price.multiply(poolBase).multiply(poolQuote).divide(EXA).sqrt().subtract(poolBase);

        sICXScore.invoke(adminAccount, "setLastPriceInLoop", bnUSDPrice);
        bnUSDScore.invoke(adminAccount, "setLastPriceInLoop", sICXPrice);
        rebalancingScore.invoke(governanceScore, "setPriceDiffThreshold", priceDifferenceThreshold);
        dexMock.invoke(adminAccount, "setPoolStatsBase", poolBase);
        dexMock.invoke(adminAccount, "setPoolStatsQuote", poolQuote);

        //Act
        List<Object> results = (List<Object>) rebalancingScore.call("getRebalancingStatus");
        
        //Assert
        assertEquals((boolean)results.get(0), true);
        assertEquals(expectedTokensToSell, (BigInteger) results.get(1)); 
        assertEquals((boolean)results.get(2), false);
    }

    @Test
    void rebalance_raisePrice() {
        //Arrange
        BigInteger bnUSDPrice = EXA;
        BigInteger sICXPrice = EXA;
        //10%
        BigInteger priceDifferenceThreshold = BigInteger.valueOf(1).multiply(BigInteger.TEN.pow(17));
        BigInteger poolBase = BigInteger.valueOf(9).multiply(EXA);
        BigInteger poolQuote = BigInteger.valueOf(10).multiply(EXA).add(BigInteger.TEN);
        BigInteger price = bnUSDPrice.multiply(EXA).divide(sICXPrice);

        BigInteger expectedTokensToSell = price.multiply(poolBase).multiply(poolQuote).divide(EXA).sqrt().subtract(poolBase);

        sICXScore.invoke(adminAccount, "setLastPriceInLoop", bnUSDPrice);
        bnUSDScore.invoke(adminAccount, "setLastPriceInLoop", sICXPrice);
        rebalancingScore.invoke(governanceScore, "setPriceDiffThreshold", priceDifferenceThreshold);
        dexMock.invoke(adminAccount, "setPoolStatsBase", poolBase);
        dexMock.invoke(adminAccount, "setPoolStatsQuote", poolQuote);

        // Act
        rebalancingScore.invoke(sm.createAccount(), "rebalance");
        
        //Assert
        verify(loansSpy).raisePrice(expectedTokensToSell);
    }

    @Test
    void rebalance_raisePrice_lowerThanThreshold() {
        //Arrange
        BigInteger bnUSDPrice = EXA;
        BigInteger sICXPrice = EXA;
        // 10%
        BigInteger priceDifferenceThreshold = BigInteger.valueOf(1).multiply(BigInteger.TEN.pow(17));
        BigInteger poolBase = BigInteger.valueOf(9).multiply(EXA);
        BigInteger poolQuote = BigInteger.valueOf(10).multiply(EXA).subtract(BigInteger.TEN);
        BigInteger price = bnUSDPrice.multiply(EXA).divide(sICXPrice);

        BigInteger expectedTokensToSell = price.multiply(poolBase).multiply(poolQuote).divide(EXA).sqrt().subtract(poolBase);

        sICXScore.invoke(adminAccount, "setLastPriceInLoop", bnUSDPrice);
        bnUSDScore.invoke(adminAccount, "setLastPriceInLoop", sICXPrice);
        rebalancingScore.invoke(governanceScore, "setPriceDiffThreshold", priceDifferenceThreshold);
        dexMock.invoke(adminAccount, "setPoolStatsBase", poolBase);
        dexMock.invoke(adminAccount, "setPoolStatsQuote", poolQuote);

        // Act
        rebalancingScore.invoke(sm.createAccount(), "rebalance");
        
        //Assert
        verify(loansSpy, never()).raisePrice(any());
        verify(loansSpy, never()).lowerPrice(any());
    }

    @Test
    void rebalance_lowerPrice() {
        //Arrange
        BigInteger bnUSDPrice = EXA;
        BigInteger sICXPrice = EXA;
        // 10%
        BigInteger priceDifferenceThreshold = BigInteger.valueOf(1).multiply(BigInteger.TEN.pow(17));
        BigInteger poolBase = BigInteger.valueOf(11).multiply(EXA);
        BigInteger poolQuote = BigInteger.valueOf(10).multiply(EXA).subtract(BigInteger.TEN);
        BigInteger price = bnUSDPrice.multiply(EXA).divide(sICXPrice);

        BigInteger expectedTokensToSell = price.multiply(poolBase).multiply(poolQuote).divide(EXA).sqrt().subtract(poolBase);

        sICXScore.invoke(adminAccount, "setLastPriceInLoop", bnUSDPrice);
        bnUSDScore.invoke(adminAccount, "setLastPriceInLoop", sICXPrice);
        rebalancingScore.invoke(governanceScore, "setPriceDiffThreshold", priceDifferenceThreshold);
        dexMock.invoke(adminAccount, "setPoolStatsBase", poolBase);
        dexMock.invoke(adminAccount, "setPoolStatsQuote", poolQuote);

        // Act
        rebalancingScore.invoke(sm.createAccount(), "rebalance");
        
        //Assert
        verify(loansSpy).lowerPrice(expectedTokensToSell.abs());
    }

    @Test
    void rebalance_lowerPrice_lowerThanThreshold() {
        //Arrange
        BigInteger bnUSDPrice = EXA;
        BigInteger sICXPrice = EXA;
        // 10%
        BigInteger priceDifferenceThreshold = BigInteger.valueOf(1).multiply(BigInteger.TEN.pow(17));
        BigInteger poolBase = BigInteger.valueOf(11).multiply(EXA);
        BigInteger poolQuote = BigInteger.valueOf(10).multiply(EXA).add(BigInteger.TEN);;
        BigInteger price = bnUSDPrice.multiply(EXA).divide(sICXPrice);

        BigInteger expectedTokensToSell = price.multiply(poolBase).multiply(poolQuote).divide(EXA).sqrt().subtract(poolBase);

        sICXScore.invoke(adminAccount, "setLastPriceInLoop", bnUSDPrice);
        bnUSDScore.invoke(adminAccount, "setLastPriceInLoop", sICXPrice);
        rebalancingScore.invoke(governanceScore, "setPriceDiffThreshold", priceDifferenceThreshold);
        dexMock.invoke(adminAccount, "setPoolStatsBase", poolBase);
        dexMock.invoke(adminAccount, "setPoolStatsQuote", poolQuote);

        // Act
        rebalancingScore.invoke(sm.createAccount(), "rebalance");
        
        //Assert
        verify(loansSpy, never()).lowerPrice(any());
        verify(loansSpy, never()).raisePrice(any());
    }

    @Test
    void tokenFallback() {
        // Arrange 
        Account account = owner;
        BigInteger expectedTokenAmount = BigInteger.TEN.pow(18);

        // Act
        sICXScore.invoke(account, "transfer", rebalancingScore.getAddress(), expectedTokenAmount, new byte[0]);

        // Assert
        BigInteger balance = (BigInteger) sICXScore.call("balanceOf", rebalancingScore.getAddress());
        assertEquals(expectedTokenAmount, balance);
    }
}
