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

import java.io.Console;
import java.math.BigInteger;
import java.util.Map;

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

    private DexMock dexSpy;

    private void expectErrorMessage(Executable _contractCall, String _expectedErrorMessage) {
        AssertionError e = Assertions.assertThrows(AssertionError.class, _contractCall);
        assertEquals(_expectedErrorMessage, e.getMessage());
    }

    @BeforeEach
    public void setup() throws Exception {
        rebalancingScore = sm.deploy(owner, Rebalancing.class, governanceScore.getAddress());
        assert (rebalancingScore.getAddress().isContract());

        dexMock = sm.deploy(owner, DexMock.class);
        assert (dexMock.getAddress().isContract());

        loansMock = sm.deploy(owner, LoansMock.class);
        assert (loansMock.getAddress().isContract());

        dexSpy = (DexMock) spy(dexMock.getInstance());
        dexMock.setInstance(dexSpy);

        sICXScore = sm.deploy(owner, SicxToken.class, "Sicx Token", "sICX", 18, BigInteger.valueOf(1000000000));
        bnUSDScore = sm.deploy(owner, BnusdToken.class, "Baln Token", "bnUSD", 18, BigInteger.valueOf(1000000000));
    }

    void setAndGetDex() {
        Account sender = sm.createAccount();
        Executable setDexWithoutAdmin = () -> rebalancingScore.invoke(adminAccount, "setDex", dexMock.getAddress());
        String expectedAdminNotSetErrorMessage = "Rebalancing: Admin address not set";
        expectErrorMessage(setDexWithoutAdmin, expectedAdminNotSetErrorMessage);

        Executable setAdmin = () -> rebalancingScore.invoke(governanceScore, "setAdmin", adminAccount.getAddress());        
        Executable setDexNotFromAdmin = () -> rebalancingScore.invoke(sender, "setDex", dexMock.getAddress());
        String expectedNotFromAdminErrorMessage = "Rebalancing: Sender not admin";
        expectErrorMessage(setDexNotFromAdmin, expectedNotFromAdminErrorMessage);

        Executable setDexToInvalidAddress = () -> rebalancingScore.invoke(adminAccount, "setDex", defaultAddress);
        String expectedNotAContractErrorMessage = "Rebalancing: Address provided is an EOA address. A contract address is required.";
        expectErrorMessage(setDexToInvalidAddress, expectedNotAContractErrorMessage);

        rebalancingScore.invoke(adminAccount, "setDex", dexMock.getAddress());
        Address actualDex = (Address) rebalancingScore.call("getDex");
        assertEquals(dexMock.getAddress(), actualDex);
    }

    @Test
    void setAndGetGovernance() {
        Account sender = sm.createAccount();
        Executable setGovernanceNotFromOwner = () -> rebalancingScore.invoke(sender, "setGovernance",
                governanceScore.getAddress());
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + sender.getAddress() + "Owner=" + owner.getAddress();
        expectErrorMessage(setGovernanceNotFromOwner, expectedErrorMessage);

        rebalancingScore.invoke(owner, "setGovernance", governanceScore.getAddress());
        Address actualGovernance = (Address) rebalancingScore.call("getGovernance");
        assertEquals(governanceScore.getAddress(), actualGovernance);
    }

    
    @Test
    void setAndGetAdmin() {
        Executable setAdminNotFromGovernance = () -> rebalancingScore.invoke(sm.createAccount(), "setAdmin", 
                adminAccount.getAddress());
        String expectedErrorMessage = "Rebalancing: Sender not governance contract";
        expectErrorMessage(setAdminNotFromGovernance, expectedErrorMessage);

        rebalancingScore.invoke(governanceScore, "setAdmin", adminAccount.getAddress());
        Address actualAdmin = (Address) rebalancingScore.call("getAdmin");
        assertEquals(adminAccount.getAddress(), actualAdmin);
    }

    @Test
    void setAndGetSicx() {
        Account sender = sm.createAccount();
        Executable setSicxWithoutAdmin = () -> rebalancingScore.invoke(adminAccount, "setSicx", sICXScore.getAddress());
        String expectedAdminNotSetErrorMessage = "Rebalancing: Admin address not set";
        expectErrorMessage(setSicxWithoutAdmin, expectedAdminNotSetErrorMessage);

        rebalancingScore.invoke(governanceScore, "setAdmin", adminAccount.getAddress());
        Executable setSicxNotFromAdmin = () -> rebalancingScore.invoke(sender, "setSicx", sICXScore.getAddress());
        String expectedNotFromAdminErrorMessage = "Rebalancing: Sender not admin";
        expectErrorMessage(setSicxNotFromAdmin, expectedNotFromAdminErrorMessage);

        Executable setSicxToInvalidAddress = () -> rebalancingScore.invoke(adminAccount, "setSicx", defaultAddress);
        String expectedNotAContractErrorMessage = "Rebalancing: Address provided is an EOA address. A contract address is required.";
        expectErrorMessage(setSicxToInvalidAddress, expectedNotAContractErrorMessage);

        rebalancingScore.invoke(adminAccount, "setSicx", sICXScore.getAddress());
        Address actualSicx = (Address) rebalancingScore.call("getSicx");
        assertEquals(sICXScore.getAddress(), actualSicx);
    }

    @Test
    void setAndGetBnusd() {
        Account sender = sm.createAccount();
        Executable setBnUsdWithoutAdmin = () -> rebalancingScore.invoke(adminAccount, "setBnusd", bnUSDScore.getAddress());
        String expectedAdminNotSetErrorMessage = "Rebalancing: Admin address not set";
        expectErrorMessage(setBnUsdWithoutAdmin, expectedAdminNotSetErrorMessage);

        rebalancingScore.invoke(governanceScore, "setAdmin", adminAccount.getAddress());        
        Executable setBnusdNotFromAdmin = () -> rebalancingScore.invoke(sender, "setBnusd", bnUSDScore.getAddress());
        String expectedNotFromAdminErrorMessage = "Rebalancing: Sender not admin";
        expectErrorMessage(setBnusdNotFromAdmin, expectedNotFromAdminErrorMessage);

        Executable setBalnToInvalidAddress = () -> rebalancingScore.invoke(adminAccount, "setBnusd", defaultAddress);
        String expectedNotAContractErrorMessage = "Rebalancing: Address provided is an EOA address. A contract address is required.";
        expectErrorMessage(setBalnToInvalidAddress, expectedNotAContractErrorMessage);

        rebalancingScore.invoke(adminAccount, "setBnusd", bnUSDScore.getAddress());
        Address actualBnusd = (Address) rebalancingScore.call("getBnusd");
        assertEquals(bnUSDScore.getAddress(), actualBnusd);
    }

    @Test
    void setAndGetLoans() {
        Account sender = sm.createAccount();
        Executable setLoansWithoutAdmin = () -> rebalancingScore.invoke(adminAccount, "setLoans", loansMock.getAddress());
        String expectedAdminNotSetErrorMessage = "Rebalancing: Admin address not set";
        expectErrorMessage(setLoansWithoutAdmin, expectedAdminNotSetErrorMessage);

        rebalancingScore.invoke(governanceScore, "setAdmin", adminAccount.getAddress());        
        Executable setLoansNotFromAdmin = () -> rebalancingScore.invoke(sender, "setLoans", loansMock.getAddress());
        String expectedNotFromAdminErrorMessage = "Rebalancing: Sender not admin";
        expectErrorMessage(setLoansNotFromAdmin, expectedNotFromAdminErrorMessage);

        Executable setLoansToInvalidAddress = () -> rebalancingScore.invoke(adminAccount, "setLoans", defaultAddress);
        String expectedNotAContractErrorMessage = "Rebalancing: Address provided is an EOA address. A contract address is required.";
        expectErrorMessage(setLoansToInvalidAddress, expectedNotAContractErrorMessage);

        rebalancingScore.invoke(adminAccount, "setLoans", loansMock.getAddress());
        Address actualLoans = (Address) rebalancingScore.call("getLoans");
        assertEquals(loansMock.getAddress(), actualLoans);
    }
}
