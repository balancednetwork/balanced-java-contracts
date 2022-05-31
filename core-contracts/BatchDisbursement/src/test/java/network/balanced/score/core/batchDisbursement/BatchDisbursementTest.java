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

package network.balanced.score.core.batchDisbursement;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import com.iconloop.score.token.irc2.IRC2Mintable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;

import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BatchDisbursementTest extends TestBase {

    public static final ServiceManager sm = getServiceManager();
    public static final Account owner = sm.createAccount();
    public static final Account governanceScore = Account.newScoreAccount(1);

    private Score batchDisbursement;
    private Score balnTokenScore;
    private Score daofundScore;
    private Score reserveFundScore;

    List<Account> recipients = new ArrayList<>();

    public static class BalnToken extends IRC2Mintable {
        public BalnToken(String _name, String _symbol, int _decimals) {
            super(_name, _symbol, _decimals);
        }
    }

    private void expectErrorMessage(Executable contractCall, String expectedErrorMessage) {
        AssertionError e = Assertions.assertThrows(AssertionError.class, contractCall);
        assertEquals(expectedErrorMessage, e.getMessage());
    }

    @BeforeEach
    void setUp() throws Exception {
        batchDisbursement = sm.deploy(owner, BatchDisbursement.class, governanceScore.getAddress());
        assert (batchDisbursement.getAddress().isContract());

        balnTokenScore = sm.deploy(owner, BalnToken.class, "Balanced Token", "BALN", 18);
        assert (balnTokenScore.getAddress().isContract());

        daofundScore = sm.deploy(owner, MockDaofund.class, balnTokenScore.getAddress());
        assert (daofundScore.getAddress().isContract());

        reserveFundScore = sm.deploy(owner, MockReserve.class, balnTokenScore.getAddress());
        assert (reserveFundScore.getAddress().isContract());

        balnTokenScore.invoke(owner, "mintTo", daofundScore.getAddress(), BigInteger.TEN.pow(23));
        balnTokenScore.invoke(owner, "mintTo", reserveFundScore.getAddress(), BigInteger.TEN.pow(23));

        for (int i = 0; i < 10; i++) {
            recipients.add(sm.createAccount());
        }
    }

    @Test
    @DisplayName("Deployment with non contract address")
    void testDeploy() {
        Account notContract = sm.createAccount();
        Executable deploymentWithNonContract = () -> sm.deploy(owner, BatchDisbursement.class,
                notContract.getAddress());

        String expectedErrorMessage = "Reverted(0): BatchDisbursement: Governance address should be a contract";
        InvocationTargetException e = Assertions.assertThrows(InvocationTargetException.class,
                deploymentWithNonContract);
        assertEquals(expectedErrorMessage, e.getCause().getMessage());
    }

    @Test
    void name() {
        String contractName = "Balanced Batch Disbursement";
        assertEquals(contractName, batchDisbursement.call("name"));
    }

    @Test
    void setAndGetGovernance() {
        assertEquals(governanceScore.getAddress(), batchDisbursement.call("getGovernance"));
        Address newGovernance = Account.newScoreAccount(2).getAddress();
        batchDisbursement.invoke(owner, "setGovernance", newGovernance);
        assertEquals(newGovernance, batchDisbursement.call("getGovernance"));
    }

    @Test
    void setAndGetDaofund() {
        Account nonOwner = sm.createAccount();

        Executable nonOwnerCall = () -> batchDisbursement.invoke(nonOwner, "setDaofund", daofundScore.getAddress());
        String expectedErrorMessage =
                "Reverted(0): SenderNotScoreOwner: Sender=" + nonOwner.getAddress() + "Owner=" + owner.getAddress();
        expectErrorMessage(nonOwnerCall, expectedErrorMessage);

        batchDisbursement.invoke(owner, "setDaofund", daofundScore.getAddress());
        assertEquals(daofundScore.getAddress(), batchDisbursement.call("getDaofund"));
    }

    @Test
    void setAndGetReserveFund() {
        batchDisbursement.invoke(owner, "setReserveFund", reserveFundScore.getAddress());
        assertEquals(reserveFundScore.getAddress(), batchDisbursement.call("getReserveFund"));
    }

    @Test
    void getTokenBalances() {
        assertEquals(Map.of(), batchDisbursement.call("getTokenBalances"));

        setAndGetDaofund();
        setAndGetReserveFund();

        batchDisbursement.invoke(governanceScore, "batchDisburse", daofundScore.getAddress());
        assertEquals(Map.of(balnTokenScore.getAddress().toString(), BigInteger.TEN.pow(22)), batchDisbursement.call(
                "getTokenBalances"));

        batchDisbursement.invoke(governanceScore, "batchDisburse", reserveFundScore.getAddress());
        assertEquals(Map.of(balnTokenScore.getAddress().toString(), BigInteger.TEN.pow(22).multiply(BigInteger.TWO)),
                batchDisbursement.call("getTokenBalances"));
    }

    @Test
    void uploadDisbursementData() {
        getTokenBalances();

        BatchDisbursement.DisbursementRecipient[] batchRecipients = new BatchDisbursement.DisbursementRecipient[10];
        BatchDisbursement.Disbursement disbursement = new BatchDisbursement.Disbursement();
        disbursement.tokenAddress = balnTokenScore.getAddress();
        disbursement.tokenAmount = BigInteger.valueOf(500L);

        for (int j = 0; j < 10; j++) {
            batchRecipients[j] = new BatchDisbursement.DisbursementRecipient();
            batchRecipients[j].recipient = recipients.get(j).getAddress();
            batchRecipients[j].disbursement = new BatchDisbursement.Disbursement[]{disbursement};
        }

        batchDisbursement.invoke(owner, "uploadDisbursementData", (Object) batchRecipients);
        for (int i = 0; i < 10; i++) {
            Address expectedRecipient = batchRecipients[i].recipient;
            BigInteger expectedAmount = batchRecipients[i].disbursement[0].tokenAmount;
            Address expectedTokenAddress = batchRecipients[i].disbursement[0].tokenAddress;

            Map<String, Object> disbursementDetail = (Map<String, Object>) batchDisbursement.call(
                    "getDisbursementDetail", expectedRecipient);
            Address actualRecipient = (Address) disbursementDetail.get("user");
            Map<String, BigInteger> claimableTokens = (Map<String, BigInteger>) disbursementDetail.get(
                    "claimableTokens");
            BigInteger actualAmount = claimableTokens.get(expectedTokenAddress.toString());

            assertEquals(expectedRecipient, actualRecipient);
            assertEquals(expectedAmount, actualAmount);
        }
    }

    @Test
    void claim() {
        uploadDisbursementData();

        for (int i = 0; i < 10; i++) {
            Account user = recipients.get(i);
            assertEquals(BigInteger.ZERO, balnTokenScore.call("balanceOf", user.getAddress()));
            batchDisbursement.invoke(recipients.get(i), "claim");
            assertEquals(BigInteger.valueOf(500L), balnTokenScore.call("balanceOf", user.getAddress()));

            // Try claiming once more, should not disburse tokens
            batchDisbursement.invoke(recipients.get(i), "claim");
            assertEquals(BigInteger.valueOf(500L), balnTokenScore.call("balanceOf", user.getAddress()));
        }
    }

    @Test
    void tokenFallback() {
        setAndGetDaofund();
        setAndGetReserveFund();

        balnTokenScore.invoke(owner, "mint", BigInteger.TEN.pow(25));
        Executable transferFromInvalidAddress = () -> balnTokenScore.invoke(owner, "transfer",
                batchDisbursement.getAddress(), BigInteger.TEN.pow(21), new byte[0]);
        String expectedErrorMessage = "Reverted(0): BatchDisbursement: Only receivable from daofund or reserve contract";
        expectErrorMessage(transferFromInvalidAddress, expectedErrorMessage);
    }
}