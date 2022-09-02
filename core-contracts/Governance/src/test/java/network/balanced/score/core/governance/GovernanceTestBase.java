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

import com.eclipsesource.json.JsonObject;
import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.structs.BalancedAddresses;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockContract;

import java.math.BigInteger;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GovernanceTestBase extends UnitTest {
    protected static final Long DAY = 43200L;
    protected static final Long WEEK = 7 * DAY;

    protected static final ServiceManager sm = getServiceManager();
    protected static final Account owner = sm.createAccount();
    protected static final Account adminAccount = sm.createAccount();

    protected static final Account oracle = Account.newScoreAccount(scoreCount);

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
    protected MockContract<WorkerToken> bwt;
    protected MockContract<Router> router;
    protected MockContract<Rebalancing> rebalancing;
    protected MockContract<FeeHandler> feehandler;
    protected MockContract<StakedLP> stakedLp;
    protected MockContract<BoostedBaln> bBaln;
    protected MockContract<BalancedOracle> balancedOracle;

    protected BalancedAddresses balancedAddresses = new BalancedAddresses();

    protected Score governance;

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

    protected JsonObject createParameter(String type, String value) {

        return new JsonObject()
                .add("type", type)
                .add("value", value);
    }

    protected JsonObject createParameter(String type, BigInteger value) {

        return new JsonObject()
                .add("type", type)
                .add("value", value.intValue());
    }

    protected JsonObject createParameter(String type, Boolean value) {

        return new JsonObject()
                .add("type", type)
                .add("value", value);
    }

    private void setupAddresses() {
        balancedAddresses.loans = loans.getAddress();
        balancedAddresses.dex = dex.getAddress();
        balancedAddresses.staking = staking.getAddress();
        balancedAddresses.rewards = rewards.getAddress();
        balancedAddresses.reserve = reserve.getAddress();
        balancedAddresses.dividends = dividends.getAddress();
        balancedAddresses.daofund = daofund.getAddress();
        balancedAddresses.oracle = oracle.getAddress();
        balancedAddresses.sicx = sicx.getAddress();
        balancedAddresses.bnUSD = bnUSD.getAddress();
        balancedAddresses.baln = baln.getAddress();
        balancedAddresses.bwt = bwt.getAddress();
        balancedAddresses.router = router.getAddress();
        balancedAddresses.rebalancing = rebalancing.getAddress();
        balancedAddresses.feehandler = feehandler.getAddress();
        balancedAddresses.stakedLp = stakedLp.getAddress();
        balancedAddresses.bBaln = bBaln.getAddress();
        balancedAddresses.balancedOracle = balancedOracle.getAddress();

        governance.invoke(owner, "setAddresses", balancedAddresses);
        governance.invoke(owner, "setContractAddresses");

        verify(loans.mock).setRewards(rewards.getAddress());
        verify(loans.mock).setDividends(dividends.getAddress());
        verify(loans.mock).setStaking(staking.getAddress());
        verify(loans.mock).setReserve(reserve.getAddress());
        verify(loans.mock).setOracle(balancedOracle.getAddress());

        verify(dex.mock).setRewards(rewards.getAddress());
        verify(dex.mock).setDividends(dividends.getAddress());
        verify(dex.mock).setStaking(staking.getAddress());
        verify(dex.mock).setSicx(sicx.getAddress());
        verify(dex.mock).setBnusd(bnUSD.getAddress());
        verify(dex.mock).setBaln(baln.getAddress());
        verify(dex.mock).setFeehandler(feehandler.getAddress());
        verify(dex.mock).setStakedLp(stakedLp.getAddress());

        verify(rewards.mock).setReserve(reserve.getAddress());
        verify(rewards.mock).setBwt(bwt.getAddress());
        verify(rewards.mock).setBaln(baln.getAddress());
        verify(rewards.mock).setDaofund(daofund.getAddress());
        verify(rewards.mock).setBoostedBaln(bBaln.getAddress());

        verify(dividends.mock).setDex(dex.getAddress());
        verify(dividends.mock).setLoans(loans.getAddress());
        verify(dividends.mock).setDaofund(daofund.getAddress());
        verify(dividends.mock).setBaln(baln.getAddress());

        verify(daofund.mock).setLoans(loans.getAddress());

        verify(reserve.mock).setLoans(loans.getAddress());
        verify(reserve.mock).setBaln(baln.getAddress());
        verify(reserve.mock).setSicx(sicx.getAddress());

        verify(bnUSD.mock).setOracle(oracle.getAddress());

        verify(baln.mock).setDividends(dividends.getAddress());
        verify(baln.mock).setOracle(oracle.getAddress());
        verify(baln.mock).setDex(dex.getAddress());
        verify(baln.mock).setBnusd(bnUSD.getAddress());

        verify(bwt.mock).setBaln(baln.getAddress());

        verify(router.mock).setDex(dex.getAddress());
        verify(router.mock).setSicx(sicx.getAddress());
        verify(router.mock).setStaking(staking.getAddress());

        verify(stakedLp.mock).setDex(dex.getAddress());
        verify(stakedLp.mock).setRewards(rewards.getAddress());

        verify(rebalancing.mock).setLoans(loans.getAddress());
        verify(rebalancing.mock).setDex(dex.getAddress());
        verify(rebalancing.mock).setBnusd(bnUSD.getAddress());
        verify(rebalancing.mock).setSicx(sicx.getAddress());
        verify(rebalancing.mock).setOracle(balancedOracle.getAddress());

        verify(balancedOracle.mock).setDex(dex.getAddress());
        verify(balancedOracle.mock).setStaking(staking.getAddress());
        verify(balancedOracle.mock).setOracle(oracle.getAddress());
    }

    protected BigInteger executeVoteWithActions(String actions) {
        sm.getBlock().increase(DAY);
        BigInteger day = (BigInteger) governance.call("getDay");
        String name = "test";
        String description = "test vote";
        BigInteger voteStart = day.add(BigInteger.TWO);
        BigInteger snapshot = day.add(BigInteger.ONE);

        when(baln.mock.totalSupply()).thenReturn(BigInteger.valueOf(20).multiply(ICX));
        when(baln.mock.stakedBalanceOf(owner.getAddress())).thenReturn(BigInteger.TEN.multiply(ICX));

        governance.invoke(owner, "defineVote", name, description, voteStart, snapshot, actions);
        BigInteger id = (BigInteger) governance.call("getVoteIndex", name);

        when(baln.mock.totalStakedBalanceOfAt(snapshot)).thenReturn(BigInteger.valueOf(6).multiply(ICX));


        Map<String, Object> vote = getVote(id);
        goToDay((BigInteger) vote.get("start day"));

        when(baln.mock.stakedBalanceOfAt(owner.getAddress(), snapshot)).thenReturn(BigInteger.valueOf(8).multiply(ICX));
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

        when(baln.mock.totalSupply()).thenReturn(totalSupply);
        when(baln.mock.stakedBalanceOfAt(eq(forVoter.getAddress()), any(BigInteger.class))).thenReturn(forVotes);
        when(baln.mock.stakedBalanceOfAt(eq(againstVoter.getAddress()), any(BigInteger.class))).thenReturn(againstVotes);

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
        BigInteger snapshot = day.add(BigInteger.ONE);

        when(baln.mock.totalSupply()).thenReturn(BigInteger.TEN.multiply(ICX));
        when(baln.mock.stakedBalanceOf(owner.getAddress())).thenReturn(BigInteger.ONE.multiply(ICX));

        governance.invoke(owner, "defineVote", name, description, voteStart, snapshot, actions);

        BigInteger id = (BigInteger) governance.call("getVoteIndex", name);

        when(baln.mock.totalStakedBalanceOfAt(snapshot)).thenReturn(BigInteger.valueOf(6).multiply(ICX));
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
        bwt = new MockContract<>(WorkerTokenScoreInterface.class, sm, owner);
        router = new MockContract<>(RouterScoreInterface.class, sm, owner);
        rebalancing = new MockContract<>(RebalancingScoreInterface.class, sm, owner);
        feehandler = new MockContract<>(FeeHandlerScoreInterface.class, sm, owner);
        stakedLp = new MockContract<>(StakedLPScoreInterface.class, sm, owner);
        bBaln = new MockContract<>(BoostedBalnScoreInterface.class, sm, owner);
        balancedOracle = new MockContract<>(BalancedOracleScoreInterface.class, sm, owner);
        governance = sm.deploy(owner, GovernanceImpl.class);

        setupAddresses();
        governance.invoke(owner, "setBalnVoteDefinitionCriterion", BigInteger.valueOf(100)); //1%
        governance.invoke(owner, "setVoteDefinitionFee", ICX); //1%
        governance.invoke(owner, "setQuorum", BigInteger.ONE);
        governance.invoke(owner, "setVoteDuration", BigInteger.TWO);
        governance.invoke(owner, "setVoteDuration", BigInteger.TWO);
    }


}
