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
import network.balanced.score.lib.structs.Disbursement;
import network.balanced.score.lib.structs.PrepDelegations;
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

import static network.balanced.score.core.daofund.DAOfundImpl.TAG;
import static network.balanced.score.lib.test.UnitTest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class DAOfundImplTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();

    private static final Account owner = sm.createAccount();
    private static final Account admin = sm.createAccount();
    private static final Account receiver = sm.createAccount();
    private static final Account prep_address = sm.createAccount();

    private static final Account governanceScore = Account.newScoreAccount(scoreCount++);
    private static final Account loansScore = Account.newScoreAccount(scoreCount++);
    private static final Account sicxScore = Account.newScoreAccount(scoreCount++);
    private static final Account balnScore = Account.newScoreAccount(scoreCount++);
    private static final Account bnUSDScore = Account.newScoreAccount(scoreCount++);
    private static final Account stakingScore = Account.newScoreAccount(scoreCount++);

    private Score daofundScore;
    private DAOfundImpl daofundSpy;

    private final BigInteger amount = new BigInteger("54321");

    @BeforeEach
    void setup() throws Exception {
        daofundScore = sm.deploy(owner, DAOfundImpl.class, governanceScore.getAddress());
        assert (daofundScore.getAddress().isContract());

        daofundSpy = (DAOfundImpl) spy(daofundScore.getInstance());
        daofundScore.setInstance(daofundSpy);
    }

    @Test
    void name() {
        assertEquals("Balanced DAOfund", daofundScore.call("name"));
    }

    @Test
    void setAndGetGovernance() {
        testGovernance(daofundScore, governanceScore, owner);
    }

    @Test
    void setAndGetAdmin() {
        testAdmin(daofundScore, governanceScore, admin);
    }

    @Test
    void setAndGetLoans() {
        testContractSettersAndGetters(daofundScore, governanceScore, admin, "setLoans", loansScore.getAddress(),
                "getLoans");
    }

    @Test
    void delegate() {
        MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS);

        PrepDelegations prep = new PrepDelegations();
        prep._address = prep_address.getAddress();
        prep._votes_in_per = BigInteger.valueOf(100);
        PrepDelegations[] preps = new PrepDelegations[]{prep};

        contextMock.when(() -> Context.call(governanceScore.getAddress(), "getContractAddress", "staking")).thenReturn(stakingScore.getAddress());
        contextMock.when(() -> Context.call(eq(stakingScore.getAddress()), eq("delegate"), any())).thenReturn(
                "Staking delegate called");
        daofundScore.invoke(governanceScore, "delegate", (Object) preps);

        contextMock.verify(() -> Context.call(governanceScore.getAddress(), "getContractAddress", "staking"));
        contextMock.verify(() -> Context.call(eq(stakingScore.getAddress()), eq("delegate"), any()));
        contextMock.close();
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
        expectErrorMessage(depositFromNonAllowedToken, "Reverted(0): " + TAG + ": Daofund can't receive this token");

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
                "Reverted(0): " + TAG + ": Insufficient balance of asset " + balnScore.getAddress().toString() + " in" +
                        " DAOfund");
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