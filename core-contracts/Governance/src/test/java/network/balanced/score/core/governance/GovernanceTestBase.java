/*
 * Copyright (c) 2022-2023 Balanced.network.
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

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import foundation.icon.xcall.NetworkAddress;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.lib.utils.Names;
import org.junit.jupiter.api.function.Executable;
import score.Address;

import java.math.BigInteger;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class GovernanceTestBase extends UnitTest {
    protected static final Long DAY = 43200L;
    protected static final Long WEEK = 7 * DAY;

    protected static final ServiceManager sm = getServiceManager();
    protected static final Account owner = sm.createAccount();
    protected static final Account adminAccount = sm.createAccount();

    protected static final Account oracle = Account.newScoreAccount(scoreCount);

    protected MockContract<XCall> xCall;
    protected MockContract<Loans> loans;
    protected MockContract<Dex> dex;
    protected MockContract<Staking> staking;
    protected MockContract<Rewards> rewards;
    protected MockContract<Reserve> reserve;
    protected MockContract<Dividends> dividends;
    protected MockContract<DAOfund> daofund;
    protected MockContract<Sicx> sicx;
    protected MockContract<BalancedDollar> bnUSD;
    protected MockContract<BalancedToken> baln;
    protected MockContract<BoostedBaln> bBaln;
    protected MockContract<WorkerToken> bwt;
    protected MockContract<Router> router;
    protected MockContract<Rebalancing> rebalancing;
    protected MockContract<FeeHandler> feehandler;
    protected MockContract<StakedLP> stakedLp;
    protected MockContract<Stability> stability;
    protected MockContract<BalancedOracle> balancedOracle;

    protected Score governance;
    public final String NATIVE_NID = "0x1.ICON";

    protected JsonObject createJsonDistribution(String name, BigInteger dist) {
        return new JsonObject()
                .add("recipient_name", name)
                .add("dist_percent", dist.toString());
    }

    protected JsonObject createJsonDisbursement(String token, BigInteger amount) {
        return new JsonObject()
                .add("address", token)
                .add("amount", amount.intValue());
    }

    public static JsonObject createParameter(String value) {
        return new JsonObject()
                .add("type", "String")
                .add("value", value);
    }

    public static JsonObject createParameter(Address value) {
        return new JsonObject()
                .add("type", "Address")
                .add("value", value.toString());
    }

    public static JsonObject createParameter(BigInteger value) {
        return new JsonObject()
                .add("type", "int")
                .add("value", value.intValue());
    }

    public static JsonObject createParameter(Boolean value) {
        return new JsonObject()
                .add("type", "boolean")
                .add("value", value);
    }

    public static JsonObject createParameter(String type, JsonObject value) {
        return new JsonObject()
                .add("type", type)
                .add("value", value);
    }

    public static JsonObject createParameter(String type, JsonArray value) {
        return new JsonObject()
                .add("type", type)
                .add("value", value);
    }

    public static JsonObject createTransaction(Address address, String method, JsonArray parameters) {
        return new JsonObject()
                .add("address", address.toString())
                .add("method", method)
                .add("parameters", parameters);
    }

    private void setupAddresses() {
        governance.invoke(owner, "addExternalContract", Names.LOANS, loans.getAddress());
        governance.invoke(owner, "addExternalContract", Names.DEX, dex.getAddress());
        governance.invoke(owner, "addExternalContract", Names.STAKING, staking.getAddress());
        governance.invoke(owner, "addExternalContract", Names.REWARDS, rewards.getAddress());
        governance.invoke(owner, "addExternalContract", Names.RESERVE, reserve.getAddress());
        governance.invoke(owner, "addExternalContract", Names.DIVIDENDS, dividends.getAddress());
        governance.invoke(owner, "addExternalContract", Names.DAOFUND, daofund.getAddress());
        governance.invoke(owner, "addExternalContract", Names.ORACLE, oracle.getAddress());
        governance.invoke(owner, "addExternalContract", Names.SICX, sicx.getAddress());
        governance.invoke(owner, "addExternalContract", Names.BNUSD, bnUSD.getAddress());
        governance.invoke(owner, "addExternalContract", Names.BALN, baln.getAddress());
        governance.invoke(owner, "addExternalContract", Names.WORKERTOKEN, bwt.getAddress());
        governance.invoke(owner, "addExternalContract", Names.ROUTER, router.getAddress());
        governance.invoke(owner, "addExternalContract", Names.REBALANCING, rebalancing.getAddress());
        governance.invoke(owner, "addExternalContract", Names.FEEHANDLER, feehandler.getAddress());
        governance.invoke(owner, "addExternalContract", Names.STAKEDLP, stakedLp.getAddress());
        governance.invoke(owner, "addExternalContract", Names.STABILITY, stability.getAddress());
        governance.invoke(owner, "addExternalContract", Names.BALANCEDORACLE, balancedOracle.getAddress());
        governance.invoke(owner, "addExternalContract", Names.BOOSTED_BALN, bBaln.getAddress());
        governance.invoke(owner, "addExternalContract", Names.XCALL, xCall.getAddress());
    }

    protected BigInteger executeVoteWithActions(String actions) {
        sm.getBlock().increase(DAY);

        BigInteger day = (BigInteger) governance.call("getDay");
        String name = "test";
        String description = "test vote";
        BigInteger voteStart = day.add(BigInteger.TWO);
        String forumLink = "https://gov.balanced.network/";

        when(bBaln.mock.totalSupplyAt(any(BigInteger.class))).thenReturn(BigInteger.valueOf(20).multiply(ICX));
        when(bBaln.mock.xBalanceOfAt(eq(NetworkAddress.valueOf(owner.getAddress().toString(), NATIVE_NID).toString()), any(BigInteger.class))).thenReturn(BigInteger.TEN.multiply(ICX));

        governance.invoke(owner, "defineVote", name, description, voteStart, BigInteger.TWO, forumLink, actions);
        BigInteger id = (BigInteger) governance.call("getVoteIndex", name);

        when(bBaln.mock.totalSupplyAt(any(BigInteger.class))).thenReturn(BigInteger.valueOf(6).multiply(ICX));

        Map<String, Object> vote = getVote(id);
        goToDay((BigInteger) vote.get("start day"));

        when(bBaln.mock.balanceOfAt(eq(owner.getAddress()), any(BigInteger.class))).thenReturn(BigInteger.valueOf(8).multiply(ICX));
        governance.invoke(owner, "castVote", id, true);

        goToDay((BigInteger) vote.get("end day"));
        governance.invoke(owner, "evaluateVote", id);

        return id;
    }

    protected BigInteger createVoteWith(String name, BigInteger totalSupply, BigInteger forVotes,
                                        BigInteger againstVotes) {
        Account forVoter = sm.createAccount();
        Account againstVoter = sm.createAccount();

        BigInteger id = defineTestVoteWithName(name);
        Map<String, Object> vote = getVote(id);

        when(bBaln.mock.totalSupplyAt(any(BigInteger.class))).thenReturn(totalSupply);
        when(bBaln.mock.xBalanceOfAt(eq(NetworkAddress.valueOf(forVoter.getAddress().toString(), NATIVE_NID).toString()), any(BigInteger.class))).thenReturn(forVotes);
        when(bBaln.mock.xBalanceOfAt(eq(NetworkAddress.valueOf(againstVoter.getAddress().toString(), NATIVE_NID).toString()), any(BigInteger.class))).thenReturn(againstVotes);

        goToDay((BigInteger) vote.get("start day"));

        //Act
        governance.invoke(forVoter, "castVote", id, true);
        governance.invoke(againstVoter, "castVote", id, false);

        return id;
    }

    protected BigInteger defineTestVote() {
        return defineTestVoteWithName("test");
    }

    protected BigInteger defineTestVoteWithName(String name) {
        sm.getBlock().increase(DAY);

        BigInteger day = (BigInteger) governance.call("getDay");
        String description = "test vote";
        String actions = "[]";
        BigInteger voteStart = day.add(BigInteger.TWO);
        String forumLink = "https://gov.balanced.network/";

        when(bBaln.mock.totalSupplyAt(any(BigInteger.class))).thenReturn(BigInteger.TEN.multiply(ICX));
        when(bBaln.mock.xBalanceOfAt(eq(NetworkAddress.valueOf(owner.getAddress().toString(), NATIVE_NID).toString()), any(BigInteger.class))).thenReturn(BigInteger.ONE.multiply(ICX));

        governance.invoke(owner, "defineVote", name, description, voteStart, BigInteger.TWO, forumLink, actions);

        BigInteger id = (BigInteger) governance.call("getVoteIndex", name);

        when(bBaln.mock.totalSupplyAt(any(BigInteger.class))).thenReturn(BigInteger.valueOf(6).multiply(ICX));
        return id;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> getVote(BigInteger id) {
        return (Map<String, Object>) governance.call("checkVote", id);
    }

    protected void goToDay(BigInteger targetDay) {
        BigInteger day = (BigInteger) governance.call("getDay");
        BigInteger diff = targetDay.subtract(day);
        sm.getBlock().increase(DAY * diff.intValue());
    }

    protected void setup() throws Exception {
        xCall = new MockContract<>(XCallScoreInterface.class, sm, owner);
        loans = new MockContract<>(LoansScoreInterface.class, sm, owner);
        dex = new MockContract<>(DexScoreInterface.class, sm, owner);
        staking = new MockContract<>(StakingScoreInterface.class, sm, owner);
        rewards = new MockContract<>(RewardsScoreInterface.class, sm, owner);
        reserve = new MockContract<>(ReserveScoreInterface.class, sm, owner);
        dividends = new MockContract<>(DividendsScoreInterface.class, sm, owner);
        daofund = new MockContract<>(DAOfundScoreInterface.class, sm, owner);
        sicx = new MockContract<>(SicxScoreInterface.class, sm, owner);
        bnUSD = new MockContract<>(BalancedDollarScoreInterface.class, sm, owner);
        baln = new MockContract<>(BalancedTokenScoreInterface.class, sm, owner);
        bBaln = new MockContract<>(BoostedBalnScoreInterface.class, sm, owner);
        bwt = new MockContract<>(WorkerTokenScoreInterface.class, sm, owner);
        router = new MockContract<>(RouterScoreInterface.class, sm, owner);
        rebalancing = new MockContract<>(RebalancingScoreInterface.class, sm, owner);
        feehandler = new MockContract<>(FeeHandlerScoreInterface.class, sm, owner);
        stakedLp = new MockContract<>(StakedLPScoreInterface.class, sm, owner);
        stability = new MockContract<>(StabilityScoreInterface.class, sm, owner);
        bBaln = new MockContract<>(BoostedBalnScoreInterface.class, sm, owner);
        balancedOracle = new MockContract<>(BalancedOracleScoreInterface.class, sm, owner);

        when(xCall.mock.getNetworkId()).thenReturn(NATIVE_NID);
        governance = sm.deploy(owner, GovernanceImpl.class);

        setupAddresses();
        governance.invoke(owner, "setBalnVoteDefinitionCriterion", BigInteger.valueOf(100)); //1%
        governance.invoke(owner, "setVoteDefinitionFee", ICX); //1%
        governance.invoke(owner, "setQuorum", BigInteger.ONE);
        governance.invoke(owner, "setVoteDurationLimits", BigInteger.TWO, BigInteger.TEN);
    }

    protected void assertOnlyCallableByContractOrOwner(String method, Object... params) {
        Account nonAuthorizedCaller = sm.createAccount();
        String expectedErrorMessage =
                "Reverted(0): SenderNotScoreOwnerOrContract: Sender=" + nonAuthorizedCaller.getAddress() +
                        " Owner=" + owner.getAddress() + " Contract=" + governance.getAccount().getAddress();
        Executable unAuthorizedCall = () -> governance.invoke(nonAuthorizedCaller, method, params);
        expectErrorMessage(unAuthorizedCall, expectedErrorMessage);
    }

}
