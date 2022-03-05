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

package network.balanced.score.core.daofund;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.daofund.DAOfund.Disbursement;
import static network.balanced.score.core.daofund.DAOfund.TAG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class DAOfundTest extends TestBase {
    public static final ServiceManager sm = getServiceManager();

    public static final Account owner = sm.createAccount();
    public static final Account admin = sm.createAccount();
    public static final Account receiver = sm.createAccount();

    public static final Account governanceScore = Account.newScoreAccount(1);
    public static final Account loansScore = Account.newScoreAccount(2);
    public static final Account sicxScore = Account.newScoreAccount(3);
    public static final Account balnScore = Account.newScoreAccount(4);
    public static final Account bnUSDScore = Account.newScoreAccount(5);

    private Score daofundScore;
    private DAOfund daofundSpy;

    private final BigInteger amount = new BigInteger("54321");

    private void expectErrorMessage(Executable contractCall, String expectedErrorMessage) {
        AssertionError e = Assertions.assertThrows(AssertionError.class, contractCall);
        assertEquals(expectedErrorMessage, e.getMessage());
    }

    @BeforeEach
    public void setup() throws Exception {
        daofundScore = sm.deploy(owner, DAOfund.class, governanceScore.getAddress());
        assert (daofundScore.getAddress().isContract());

        daofundSpy = (DAOfund) spy(daofundScore.getInstance());
        daofundScore.setInstance(daofundSpy);
    }

    @Test
    void name() {
        assertEquals("Balanced DAOfund", daofundScore.call("name"));
    }

    @Test
    void setAndGetGovernance() {
        assertEquals(governanceScore.getAddress(), daofundScore.call("getGovernance"));

        Account newGovernance = Account.newScoreAccount(100);
        daofundScore.invoke(owner, "setGovernance", newGovernance.getAddress());
        assertEquals(newGovernance.getAddress(), daofundScore.call("getGovernance"));

        Account nonScore = sm.createAccount();
        Executable setGovernanceWithNonContract = () -> daofundScore.invoke(owner, "setGovernance",
                nonScore.getAddress());
        expectErrorMessage(setGovernanceWithNonContract, TAG + ": Address provided is an EOA address. A contract " +
                "address is required.");
    }

    @Test
    void setAndGetAdmin() {
        Executable notGovernanceInvoke = () -> daofundScore.invoke(owner, "setAdmin", admin.getAddress());
        expectErrorMessage(notGovernanceInvoke, TAG + ": Sender not governance contract");

        daofundScore.invoke(governanceScore, "setAdmin", admin.getAddress());
        assertEquals(admin.getAddress(), daofundScore.call("getAdmin"));
    }

    @Test
    void setAndGetLoans() {
        setAndGetAdmin();

        Executable nonAdminCall = () -> daofundScore.invoke(owner, "setLoans", loansScore.getAddress());
        expectErrorMessage(nonAdminCall, TAG + ": Sender not admin");

        daofundScore.invoke(admin, "setLoans", loansScore.getAddress());
        assertEquals(loansScore.getAddress(), daofundScore.call("getLoans"));
    }

    @Test
    void addSymbolToSetdb() {
        setAndGetLoans();
        try (MockedStatic<Context> loansMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS)) {
            loansMock
                    .when(() -> Context.call(loansScore.getAddress(), "getAssetTokens"))
                    .thenReturn(Map.of("sICX", sicxScore.getAddress().toString(),
                            "BALN", balnScore.getAddress().toString(),
                            "bnUSD", bnUSDScore.getAddress().toString()));
            daofundScore.invoke(owner, "addAddressToSetdb");
            Map<String, BigInteger> expectedBalances = Map.of(sicxScore.getAddress().toString(), BigInteger.ZERO,
                    balnScore.getAddress().toString(), BigInteger.ZERO,
                    bnUSDScore.getAddress().toString(), BigInteger.ZERO);
            assertEquals(expectedBalances, daofundScore.call("getBalances"));
        }
    }

    @Test
    void receiveTokens() {
        addSymbolToSetdb();

        Executable depositFromNonAllowedToken = () -> daofundScore.invoke(owner, "tokenFallback", owner.getAddress(),
                BigInteger.TEN.pow(20), new byte[0]);
        expectErrorMessage(depositFromNonAllowedToken, TAG + ": Daofund can't receive this token");

        daofundScore.invoke(sicxScore, "tokenFallback", owner.getAddress(), BigInteger.TEN.pow(20), new byte[0]);
        Map<String, BigInteger> expectedBalances = Map.of(sicxScore.getAddress().toString(), BigInteger.TEN.pow(20),
                balnScore.getAddress().toString(), BigInteger.ZERO,
                bnUSDScore.getAddress().toString(), BigInteger.ZERO);
        assertEquals(expectedBalances, daofundScore.call("getBalances"));
    }

    @Test
    @DisplayName("Allocate the tokens for disbursement")
    void disburseTokens() {
        receiveTokens();

        Disbursement disbursement = new Disbursement();
        disbursement.address = sicxScore.getAddress();
        disbursement.amount = amount;
        Disbursement[] amounts = new Disbursement[]{disbursement};
        daofundScore.invoke(governanceScore, "disburse", receiver.getAddress(), amounts);

        Map<String, Object> expectedDisbursement = Map.of("user", receiver.getAddress(),
                "claimableTokens", Map.of(sicxScore.getAddress().toString(), amount,
                        balnScore.getAddress().toString(), BigInteger.ZERO,
                        bnUSDScore.getAddress().toString(), BigInteger.ZERO));
        assertEquals(expectedDisbursement, daofundScore.call("getDisbursementDetail", receiver.getAddress()));

        disbursement.address = balnScore.getAddress();
        Executable disburseInsufficientFund = () -> daofundScore.invoke(governanceScore, "disburse",
                receiver.getAddress(), new Disbursement[]{disbursement});
        expectErrorMessage(disburseInsufficientFund,
                TAG + ": Insufficient balance of asset " + balnScore.getAddress().toString() + " in DAOfund");
    }

    @Test
    void claimTokens() {
        disburseTokens();

        try (MockedStatic<Context> tokenMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS)) {
            tokenMock
                    .when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class),
                            any(BigInteger.class), any(byte[].class)))
                    .thenReturn(null);

            daofundScore.invoke(receiver, "claim");
            verify(daofundSpy).TokenTransfer(receiver.getAddress(), amount,
                    "Balanced DAOfund disbursement " + amount + " sent to " + receiver.getAddress());
        }

        Map<String, Object> expectedDisbursement = Map.of("user", receiver.getAddress(),
                "claimableTokens", Map.of(sicxScore.getAddress().toString(), BigInteger.ZERO,
                        balnScore.getAddress().toString(), BigInteger.ZERO,
                        bnUSDScore.getAddress().toString(), BigInteger.ZERO));
        assertEquals(expectedDisbursement, daofundScore.call("getDisbursementDetail", receiver.getAddress()));
    }

}