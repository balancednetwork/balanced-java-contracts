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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.MockedStatic.Verification;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import network.balanced.score.lib.structs.BalancedAddresses;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.core.governance.interfaces.*;

import static network.balanced.score.core.governance.GovernanceConstants.*;

public class GovernanceTestBase extends UnitTest {
    protected static final Long DAY = 43200L;
    protected static final Long WEEK = 7 * DAY;

    protected static final ServiceManager sm = getServiceManager();
    protected static final Account owner = sm.createAccount();
    protected static final Account adminAccount = sm.createAccount();

    protected MockContract<LoansScoreInterface> loans;
    protected MockContract<DexScoreInterface> dex;
    protected MockContract<StakingScoreInterface> staking;
    protected MockContract<RewardsScoreInterface> rewards;
    protected final Account reserve = Account.newScoreAccount(scoreCount++);
    protected MockContract<DividendsScoreInterface> dividends; 
    protected final Account daofund = Account.newScoreAccount(scoreCount++);
    protected final Account oracle = Account.newScoreAccount(scoreCount++);
    protected MockContract<AssetScoreInterface> sicx;
    protected MockContract<BnUSDScoreInterface> bnUSD; 
    protected MockContract<BalnScoreInterface> baln;
    protected MockContract<BwtScoreInterface> bwt;
    protected MockContract<RouterScoreInterface> router; 
    protected MockContract<RebalancingScoreInterface> rebalancing;
    protected MockContract<FeehandlerScoreInterface> feehandler;
    protected MockContract<StakedLpScoreInterface> stakedLp;

    private BalancedAddresses balancedAddresses = new BalancedAddresses();

    protected Score governance;

    protected JsonObject createJsonDistribtion(String name, BigInteger dist) {
        JsonObject distribution = new JsonObject()
            .add("recipient_name", name)
            .add("dist_percent", dist.toString());
            
        return distribution;
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

        governance.invoke(owner, "setAddresses", balancedAddresses);
        governance.invoke(owner, "setContractAddresses");
    }

    protected void executeVoteWithActions(String actions) {
        sm.getBlock().increase(DAY);
        BigInteger day = (BigInteger) governance.call("getDay");
        String name = "test";
        String description = "test vote";
        BigInteger voteStart = day.add(BigInteger.TWO);
        BigInteger snapshot = day.add(BigInteger.ONE);
        
        when(baln.mock.totalSupply()).thenReturn(BigInteger.TEN);
        when(baln.mock.stakedBalanceOf(owner.getAddress())).thenReturn(BigInteger.TEN);

        when(baln.mock.totalSupply()).thenReturn(BigInteger.TEN);
        when(baln.mock.stakedBalanceOf(owner.getAddress())).thenReturn(BigInteger.ONE);
        
        governance.invoke(owner, "defineVote", name, description, voteStart, snapshot, actions);

        BigInteger id = (BigInteger) governance.call("getVoteIndex", name);

        when(baln.mock.totalStakedBalanceOfAt(snapshot)).thenReturn(BigInteger.TEN);
        when(dex.mock.totalBalnAt(BALNBNUSD_ID, snapshot, false)).thenReturn(BigInteger.TEN);
        when(dex.mock.totalBalnAt(BALNSICX_ID, snapshot, false)).thenReturn(BigInteger.TEN);

        sm.getBlock().increase(DAY);

        when(baln.mock.stakedBalanceOfAt(owner.getAddress(), snapshot)).thenReturn(BigInteger.valueOf(8));
        governance.invoke(owner, "castVote", id, true);

        sm.getBlock().increase(DAY);
        sm.getBlock().increase(DAY);

        governance.invoke(owner, "evaluateVote", id);
    }

    public void setup() throws Exception {
        loans = new MockContract<LoansScoreInterface>(LoansScoreInterface.class, sm, owner);
        dex = new MockContract<DexScoreInterface>(DexScoreInterface.class, sm, owner);
        staking = new MockContract<StakingScoreInterface>(StakingScoreInterface.class, sm, owner);
        rewards = new MockContract<RewardsScoreInterface>(RewardsScoreInterface.class, sm, owner);
        dividends = new MockContract<DividendsScoreInterface>(DividendsScoreInterface.class, sm, owner); 
        sicx = new MockContract<AssetScoreInterface>(AssetScoreInterface.class, sm, owner);
        bnUSD  = new MockContract<BnUSDScoreInterface>(BnUSDScoreInterface.class, sm, owner); 
        baln = new MockContract<BalnScoreInterface>(BalnScoreInterface.class, sm, owner);
        bwt = new MockContract<BwtScoreInterface>(BwtScoreInterface.class, sm, owner);
        router = new MockContract<RouterScoreInterface>(RouterScoreInterface.class, sm, owner); 
        rebalancing = new MockContract<RebalancingScoreInterface>(RebalancingScoreInterface.class, sm, owner);
        feehandler = new MockContract<FeehandlerScoreInterface>(FeehandlerScoreInterface.class, sm, owner);
        stakedLp = new MockContract<StakedLpScoreInterface>(StakedLpScoreInterface.class, sm, owner);
        governance = sm.deploy(owner, GovernanceImpl.class);

        setupAddresses();
        governance.invoke(owner, "setBalnVoteDefinitionCriterion", BigInteger.valueOf(100)); //1%
        governance.invoke(owner, "setVoteDefinitionFee", ICX); //1% 
        governance.invoke(owner, "setQuorum", BigInteger.ONE);
        governance.invoke(owner, "setVoteDuration", BigInteger.ONE);
    }





}
