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

package network.balanced.score.core.dex;

import foundation.icon.icx.Wallet;
import foundation.icon.jsonrpc.Address;
import foundation.icon.jsonrpc.model.TransactionResult;
import foundation.icon.score.client.DefaultScoreClient;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.Env;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigInteger;
import java.util.function.Consumer;

import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NonStakedLPRewardsTest {

    private static StakingScoreClient staking;
    private static LoansScoreClient loans;
    private static RewardsScoreClient rewards;
    private static BalancedTokenScoreClient baln;

    static Env.Chain chain = Env.getDefaultChain();
    private static Balanced balanced;
    private static Wallet userWallet;
    private static DefaultScoreClient dexScoreClient;
    private static DefaultScoreClient governanceScoreClient;
    private static DefaultScoreClient sIcxScoreClient;
    private static DefaultScoreClient balnScoreClient;
    private static DefaultScoreClient rewardsScoreClient;


    private static DefaultScoreClient daoFund;
    static File jarfile = new File("src/intTest/java/network/balanced/score/core/dex/testtokens/DexIntTestToken.jar");

    static {
        try {
            balanced = new Balanced();
            Wallet testOwnerWallet = balanced.owner;
            userWallet = ScoreIntegrationTest.createWalletWithBalance(BigInteger.valueOf(800).multiply(EXA));
            Wallet tUserWallet = ScoreIntegrationTest.createWalletWithBalance(BigInteger.valueOf(500).multiply(EXA));
            balanced.setupBalanced();
            dexScoreClient = balanced.dex;
            governanceScoreClient = balanced.governance;
            DefaultScoreClient stakingScoreClient = balanced.staking;
            sIcxScoreClient = balanced.sicx;
            DefaultScoreClient dividendScoreClient = balanced.dividends;
            balnScoreClient = balanced.baln;
            rewardsScoreClient = balanced.rewards;
            DefaultScoreClient feeHandlerScoreClient = balanced.feehandler;
            daoFund = balanced.daofund;
            staking = new StakingScoreClient(stakingScoreClient);
            rewards = new RewardsScoreClient(rewardsScoreClient);
            loans = new LoansScoreClient(balanced.loans);
            baln = new BalancedTokenScoreClient(balnScoreClient);
            Sicx sicx = new SicxScoreClient(sIcxScoreClient);
            StakedLP stakedLp = new StakedLPScoreClient(balanced.stakedLp);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error on init test: " + e.getMessage());
        }

    }

    private static final Address userAddress = Address.of(userWallet);

    private static final DexScoreClient dexUserScoreClient = new DexScoreClient(dexScoreClient.endpoint(),
            dexScoreClient._nid(), userWallet, dexScoreClient._address());
    private static final SicxScoreClient userSicxScoreClient = new SicxScoreClient(dexScoreClient.endpoint(),
            dexScoreClient._nid(), userWallet, sIcxScoreClient._address());
    private static final RewardsScoreClient userWalletRewardsClient =
            new RewardsScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
                    rewardsScoreClient._address());
    private static final BalancedTokenScoreClient userBalnScoreClient = new BalancedTokenScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet, balnScoreClient._address());
    private static final GovernanceScoreClient governanceDexScoreClient = new GovernanceScoreClient(governanceScoreClient);
    private static final DAOfundScoreClient userDaoFundScoreClient = new DAOfundScoreClient(daoFund);


    @Test
    void testNonStakedLpRewards() {
        // test if the non staked lp token is rewarded or not once continuous rewards is activated.
        //balanced = new Balanced();
        //balanced.setupBalanced();
        userDaoFundScoreClient.addAddressToSetdb();
        balanced.syncDistributions();

        byte[] tokenDeposit = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();
        staking.stakeICX(BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18)), userAddress
                , null);

        BigInteger loanAmount = BigInteger.valueOf(150).multiply(BigInteger.TEN.pow(18));
        BigInteger collateral = BigInteger.valueOf(1000).multiply(BigInteger.TEN.pow(18));


        loans.depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);

        waitForADay();
        balanced.syncDistributions();
        rewards.claimRewards();

        baln.transfer(userAddress, loanAmount, null);

        // deposit base token
        userSicxScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(100).multiply(EXA), tokenDeposit);
        //deposit quote token
        userBalnScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(100).multiply(EXA), tokenDeposit);
        dexUserScoreClient.add(balanced.baln._address(), balanced.sicx._address(), BigInteger.valueOf(100).multiply(EXA), BigInteger.valueOf(100).multiply(EXA), false);

        waitForADay();
        balanced.syncDistributions();
        userWalletRewardsClient.claimRewards();
        if(dexUserScoreClient.getContinuousRewardsDay()==null) {
            governanceDexScoreClient.setContinuousRewardsDay(dexUserScoreClient.getDay().add(BigInteger.ONE));
        }
        waitForADay();

        // continuous rewards starts here
        balanced.syncDistributions();
        userWalletRewardsClient.claimRewards();
        waitForADay();

        // next day starts
        Consumer<TransactionResult> distributeConsumer = result -> {};
        for(int i =0; i<10; i++){
            balanced.ownerClient.rewards.distribute(distributeConsumer);
        }
        waitForADay();

        // next day starts
        for(int i =0; i<10; i++){
            balanced.ownerClient.rewards.distribute(distributeConsumer);
        }
        // users without staking LP tokens will get 0 rewards
        assertEquals(BigInteger.ZERO, rewards.getBalnHolding(userAddress));

        byte[] stakeLp = "{\"method\":\"_stake\"}".getBytes();
        dexUserScoreClient.transfer(balanced.stakedLp._address(), BigInteger.valueOf(90),BigInteger.valueOf(4), stakeLp);

        // user gets rewards after lp token is staked
        assertTrue(rewards.getBalnHolding(userAddress).compareTo(BigInteger.ZERO) > 0);
        BigInteger previousUserBalance = baln.balanceOf(userAddress);
        userWalletRewardsClient.claimRewards();
        BigInteger newBalance = baln.balanceOf(userAddress);
        assertTrue(newBalance.compareTo(previousUserBalance) > 0);
    }

    void waitForADay(){
        balanced.increaseDay(1);
    }
}
