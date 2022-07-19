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
import foundation.icon.score.client.DefaultScoreClient;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.interfaces.dex.DexTestScoreClient;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.Env;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import score.UserRevertedException;

import java.io.File;
import java.math.BigInteger;
import java.util.Map;

import static foundation.icon.score.client.DefaultScoreClient._deploy;
import static network.balanced.score.core.dex.utils.Const.SICXICX_MARKET_NAME;
import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.createWalletWithBalance;
import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.*;

class DexIntegrationTest {

    private static final Env.Chain chain = Env.getDefaultChain();
    private static Balanced balanced;
    private static Wallet userWallet;
    private static Wallet tUserWallet;
    private static Wallet testOwnerWallet;

    private static DefaultScoreClient dexScoreClient;
    private static DefaultScoreClient governanceScoreClient;
    private static DefaultScoreClient stakingScoreClient;
    private static DefaultScoreClient sIcxScoreClient;
    private static DefaultScoreClient balnScoreClient;
    private static DefaultScoreClient rewardsScoreClient;
    private static DefaultScoreClient tokenAClient;
    private static DefaultScoreClient tokenBClient;
    private static DefaultScoreClient tokenCClient;
    private static DefaultScoreClient tokenDClient;
    private static DefaultScoreClient daoFundScoreClient;

    private static final File jarfile = new File("src/intTest/java/network/balanced/score/core/dex/testtokens" +
            "/DexIntTestToken.jar");

    static {
        try {
            tUserWallet = createWalletWithBalance(BigInteger.valueOf(500).multiply(EXA));
            userWallet = createWalletWithBalance(BigInteger.valueOf(800).multiply(EXA));

            balanced = new Balanced();
            testOwnerWallet = balanced.owner;

            tokenAClient = _deploy(chain.getEndpointURL(), chain.networkId, testOwnerWallet, jarfile.getPath(),
                    Map.of("name", "Test Token", "symbol", "TT"));
            tokenBClient = _deploy(chain.getEndpointURL(), chain.networkId, testOwnerWallet, jarfile.getPath(),
                    Map.of("name", "Test Base Token", "symbol", "TB"));
            tokenCClient = _deploy(chain.getEndpointURL(), chain.networkId, testOwnerWallet, jarfile.getPath(),
                    Map.of("name", "Test Third Token", "symbol", "TTD"));
            tokenDClient = _deploy(chain.getEndpointURL(), chain.networkId, testOwnerWallet, jarfile.getPath(),
                    Map.of("name", "Test Fourth Token", "symbol", "TFD"));

            balanced.setupBalanced();
            dexScoreClient = balanced.dex;
            governanceScoreClient = balanced.governance;
            stakingScoreClient = balanced.staking;
            sIcxScoreClient = balanced.sicx;
            balnScoreClient = balanced.baln;
            rewardsScoreClient = balanced.rewards;
            daoFundScoreClient = balanced.daofund;

            Rewards rewards = new RewardsScoreClient(balanced.rewards);
            Loans loans = new LoansScoreClient(balanced.loans);
            BalancedToken baln = new BalancedTokenScoreClient(balanced.baln);
            Sicx sicx = new SicxScoreClient(balanced.sicx);
            StakedLP stakedLp = new StakedLPScoreClient(balanced.stakedLp);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error on init test: " + e.getMessage());
        }

    }

    private static final Address tokenAAddress = tokenAClient._address();
    private static final Address tokenBAddress = tokenBClient._address();
    private static final Address tokenCAddress = tokenCClient._address();
    private static final Address tokenDAddress = tokenDClient._address();

    private static final Address userAddress = Address.of(userWallet);
    private static final Address tUserAddress = Address.of(tUserWallet);

    private static final DexTestScoreClient ownerDexTestScoreClient = new DexTestScoreClient(chain.getEndpointURL(),
            chain.networkId, testOwnerWallet, tokenAAddress);
    private static final DexTestScoreClient ownerDexTestBaseScoreClient = new DexTestScoreClient(chain.getEndpointURL(),
            chain.networkId, testOwnerWallet, tokenBAddress);
    private static final DexTestScoreClient ownerDexTestThirdScoreClient =
            new DexTestScoreClient(chain.getEndpointURL(), chain.networkId, testOwnerWallet, tokenCAddress);
    private static final DexTestScoreClient ownerDexTestFourthScoreClient =
            new DexTestScoreClient(chain.getEndpointURL(), chain.networkId, testOwnerWallet, tokenDAddress);
    private static final DexScoreClient dexUserScoreClient = new DexScoreClient(dexScoreClient.endpoint(),
            dexScoreClient._nid(), userWallet, dexScoreClient._address());
    private static final Staking userStakeScoreClient = new StakingScoreClient(dexScoreClient.endpoint(),
            dexScoreClient._nid(), userWallet, stakingScoreClient._address());
    private static final SicxScoreClient userSicxScoreClient = new SicxScoreClient(dexScoreClient.endpoint(),
            dexScoreClient._nid(), userWallet, sIcxScoreClient._address());
    static Rewards userWalletRewardsClient = new RewardsScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(),
            userWallet, rewardsScoreClient._address());
    private static final BalancedTokenScoreClient userBalnScoreClient = new BalancedTokenScoreClient(dexScoreClient.endpoint(),
            dexScoreClient._nid(), userWallet, balnScoreClient._address());
    private static final DexTestScoreClient userDexTestScoreClient = new DexTestScoreClient(dexScoreClient.endpoint(),
            dexScoreClient._nid(), userWallet, tokenAAddress);
    private static final DexTestScoreClient userDexTestBaseScoreClient =
            new DexTestScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet, tokenBAddress);
    private static final DexTestScoreClient userDexTestThirdScoreClient =
            new DexTestScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet, tokenCAddress);
    private static final DexTestScoreClient userDexTestFourthScoreClient =
            new DexTestScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet, tokenDAddress);

    private static final GovernanceScoreClient governanceDexScoreClient =
            new GovernanceScoreClient(governanceScoreClient);
    private static final RewardsScoreClient userRewardScoreClient = new RewardsScoreClient(rewardsScoreClient);
    private static final DAOfundScoreClient userDaoFundScoreClient = new DAOfundScoreClient(daoFundScoreClient);

    private static final DefaultScoreClient userClient = new DefaultScoreClient(chain.getEndpointURL(),
            chain.networkId, userWallet, DefaultScoreClient.ZERO_ADDRESS);

    private static final DefaultScoreClient tUserClient = new DefaultScoreClient(chain.getEndpointURL(),
            chain.networkId, tUserWallet, DefaultScoreClient.ZERO_ADDRESS);


    @Test
    @Order(1)
    void testGovernanceAddress() {
        assertEquals("Balanced DEX", dexUserScoreClient.name());
        score.Address governanceAddress = dexUserScoreClient.getGovernance();
        assertEquals(governanceAddress, governanceScoreClient._address());
    }

    @Test
    @Order(2)
    void testAdminAddress() {
        score.Address adminAddress = dexUserScoreClient.getGovernance();
        assertEquals(adminAddress, governanceScoreClient._address());
    }

    @Test
    @Order(3)
    void testICXTransferSwapEarningAndCancelOrder(){
        assertEquals(SICXICX_MARKET_NAME, dexUserScoreClient.getPoolName(BigInteger.ONE));
        BigInteger defaultPoolId = dexUserScoreClient.lookupPid(SICXICX_MARKET_NAME);
        assertEquals(BigInteger.ONE, defaultPoolId);

        Map<String, Object>  poolStats = dexUserScoreClient.getPoolStats(defaultPoolId);
        assertEquals(poolStats.get("base_token").toString(), sIcxScoreClient._address().toString());
        assertNull(poolStats.get("quote_token"));
        assertEquals(hexToBigInteger(poolStats.get("base").toString()), BigInteger.ZERO);
        assertEquals(hexToBigInteger(poolStats.get("quote").toString()), BigInteger.ZERO);
        assertEquals(hexToBigInteger(poolStats.get("total_supply").toString()), BigInteger.ZERO);

        //test icx transfer and verify stats
        balanced.syncDistributions();
        userClient._transfer(dexScoreClient._address(), BigInteger.valueOf(200).multiply(EXA), null);
        poolStats = dexUserScoreClient.getPoolStats(defaultPoolId);

        assertEquals(poolStats.get("base_token").toString(), sIcxScoreClient._address().toString());
        assertNull(poolStats.get("quote_token"));
        assertEquals(hexToBigInteger(poolStats.get("base").toString()), BigInteger.ZERO);
        assertEquals(hexToBigInteger(poolStats.get("quote").toString()), BigInteger.valueOf(200).multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("total_supply").toString()), BigInteger.valueOf(200).multiply(EXA));
        BigInteger beforeSwapPrice = hexToBigInteger(poolStats.get("price").toString());

        //test swap
        byte[] data = "testData".getBytes();
        ((StakingScoreClient) userStakeScoreClient).stakeICX(BigInteger.valueOf(100).multiply(EXA), userAddress, data);

        byte[] swapIcx = "{\"method\":\"_swap_icx\",\"params\":{\"none\":\"none\"}}".getBytes();
        userSicxScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(50).multiply(EXA), swapIcx);

        poolStats = dexUserScoreClient.getPoolStats(defaultPoolId);
        BigInteger afterSwapPrice = hexToBigInteger(poolStats.get("price").toString());
        // price should be same after swap
        assertEquals(beforeSwapPrice, afterSwapPrice);

        defaultPoolId = dexUserScoreClient.lookupPid(SICXICX_MARKET_NAME);
        poolStats = dexUserScoreClient.getPoolStats(defaultPoolId);

        assertEquals(poolStats.get("base_token").toString(), sIcxScoreClient._address().toString());
        assertNull(poolStats.get("quote_token"));
        assertEquals(hexToBigInteger(poolStats.get("base").toString()), BigInteger.ZERO);
        assertEquals(hexToBigInteger(poolStats.get("quote").toString()).divide(EXA), BigInteger.valueOf(150));
        assertEquals(hexToBigInteger(poolStats.get("total_supply").toString()).divide(EXA), BigInteger.valueOf(150));

        System.out.println(" day is: " + dexUserScoreClient.getDay());
        waitForADay();
        //release lock by distributing rewards
        balanced.syncDistributions();
        //verify sicx earning and make withdraw
        BigInteger sicxEarning = dexUserScoreClient.getSicxEarnings(userAddress);
        assertNotNull(sicxEarning);
        dexUserScoreClient.withdrawSicxEarnings(sicxEarning);
        
        balanced.syncDistributions();
        dexUserScoreClient.cancelSicxicxOrder();
    }

    /*@Test
    @Order(4)
    void testBalnPoolTokenTransferableOnContinuousRewards(){

        if(dexUserScoreClient.getContinuousRewardsDay()==null) {
            governanceDexScoreClient.setContinuousRewardsDay(dexUserScoreClient.getDay().add(BigInteger.ONE));
        }
        waitForADay();
        balanced.syncDistributions();
        //continuous starts
        byte[] tokenDeposit = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();
        mintAndTransferTestTokens(tokenDeposit);
        dexUserScoreClient.add(Address.fromString(dexTestBaseScoreAddress), Address.fromString(dexTestFourthScoreClient._address().toString()), BigInteger.valueOf(50).multiply(EXA), BigInteger.valueOf(50).multiply(EXA), false);
        BigInteger poolId = dexUserScoreClient.getPoolId(Address.fromString(dexTestBaseScoreAddress), Address.fromString(dexTestFourthScoreAddress));
        //assert pool id is less than 5
        assert poolId.compareTo(BigInteger.valueOf(6)) < 0;
        BigInteger liquidity = (BigInteger.valueOf(50).multiply(EXA).multiply(BigInteger.valueOf(50).multiply(EXA))).sqrt();
        BigInteger balance = dexUserScoreClient.balanceOf(userAddress, poolId);
        BigInteger tUsersPrevBalance = dexUserScoreClient.balanceOf(tUserAddress, poolId);

        assertEquals(balance, liquidity);
        dexUserScoreClient.transfer(tUserAddress, BigInteger.valueOf(5).multiply(EXA), poolId, new byte[0]);
        BigInteger tUsersBalance = dexUserScoreClient.balanceOf(tUserAddress, poolId);
        assertEquals(tUsersPrevBalance.add(BigInteger.valueOf(5).multiply(EXA)), tUsersBalance);
    }*/

    @Test
    @Order(6)
    void testWithdraw(){
        byte[] tokenDeposit = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();
        this.mintAndTransferTestTokens(tokenDeposit);
        BigInteger withdrawAMount = BigInteger.valueOf(50);
        BigInteger balanceBeforeWithdraw = dexUserScoreClient.depositOfUser(userAddress, tokenAAddress);
        //withdraw test token
        dexUserScoreClient.withdraw(tokenAAddress, withdrawAMount);

        BigInteger balanceAfterWithdraw = dexUserScoreClient.depositOfUser(userAddress, tokenAAddress);

        assert balanceBeforeWithdraw.equals(balanceAfterWithdraw.add(withdrawAMount));
    }

    @Test
    @Order(7)
    void testLpTokensAndTransfer() {
        byte[] tokenDeposit = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();
        mintAndTransferTestTokens(tokenDeposit);
        transferSicxToken();
        dexUserScoreClient.add(tokenBAddress, Address.fromString(sIcxScoreClient._address().toString()),
                BigInteger.valueOf(50).multiply(EXA), BigInteger.valueOf(25).multiply(EXA), false);
        mintAndTransferTestTokens(tokenDeposit);
        transferSicxToken();
        dexUserScoreClient.add(Address.fromString(sIcxScoreClient._address().toString()), tokenAAddress,
                BigInteger.valueOf(50).multiply(EXA), BigInteger.valueOf(25).multiply(EXA), false);
        mintAndTransferTestTokens(tokenDeposit);
        transferSicxToken();
        dexUserScoreClient.add(tokenBAddress, tokenAAddress, BigInteger.valueOf(50).multiply(EXA),
                BigInteger.valueOf(25).multiply(EXA), false);
        mintAndTransferTestTokens(tokenDeposit);
        transferSicxToken();
        dexUserScoreClient.add(Address.fromString(sIcxScoreClient._address().toString()), tokenCAddress,
                BigInteger.valueOf(50).multiply(EXA), BigInteger.valueOf(25).multiply(EXA), false);
        mintAndTransferTestTokens(tokenDeposit);
        transferSicxToken();
        dexUserScoreClient.add(tokenBAddress, tokenCAddress, BigInteger.valueOf(50).multiply(EXA),
                BigInteger.valueOf(25).multiply(EXA), false);

        waitForADay();

        //take pool id > 5 so that it can be transferred
        BigInteger poolId = dexUserScoreClient.getPoolId(tokenBAddress, tokenCAddress);
        System.out.println(poolId);
        BigInteger balance = dexUserScoreClient.balanceOf(userAddress, poolId);
        assertNotEquals(balance, BigInteger.ZERO);
        dexUserScoreClient.transfer(Address.fromString(tUserAddress.toString()), BigInteger.valueOf(5).multiply(EXA),
                poolId, new byte[0]);
        BigInteger tUsersBalance = dexUserScoreClient.balanceOf(Address.fromString(tUserAddress.toString()), poolId);
        assert BigInteger.ZERO.compareTo(tUsersBalance) < 0;

    }

    @Test
    @Order(8)
    void testNonContinuousAndContinuousReward(){
        userDaoFundScoreClient.addAddressToSetdb();
        balanced.syncDistributions();
        BigInteger balnHolding = userRewardScoreClient.getBalnHolding(tUserAddress);
        tUserClient._transfer(dexScoreClient._address(), BigInteger.valueOf(200).multiply(EXA), null);


        balanced.syncDistributions();
        System.out.println("Baln total supply is: "+userBalnScoreClient.totalSupply());
        BigInteger updatedBalnHolding = userRewardScoreClient.getBalnHolding(tUserAddress);
        System.out.println("baln holding: "+balnHolding);
        System.out.println("updated baln holding: "+updatedBalnHolding);
        assert balnHolding.compareTo(updatedBalnHolding)<0;
                BigInteger beforeSleepDay = dexUserScoreClient.getDay();
        try {
                Thread.sleep(5000); //wait some time
        }catch (Exception e){
                System.out.println(e.getMessage());
        }

        BigInteger nextUpdatedBalnHolding = userRewardScoreClient.getBalnHolding(tUserAddress);
        assertEquals(beforeSleepDay, dexUserScoreClient.getDay());

        System.out.println("updated baln holding: "+updatedBalnHolding);
        System.out.println("next updated baln holding: "+nextUpdatedBalnHolding);
        assert updatedBalnHolding.compareTo(nextUpdatedBalnHolding)<0;

    }

    void transferSicxToken(){
        byte[] data = "testData".getBytes();
        ((StakingScoreClient) userStakeScoreClient).stakeICX(BigInteger.valueOf(80).multiply(EXA), userAddress, data);

        byte[] tokenDeposit = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();
        userSicxScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(60).multiply(EXA), tokenDeposit);
    }

    void mintAndTransferTestTokens(byte[] tokenDeposit) {

        ownerDexTestScoreClient.mintTo(userAddress, BigInteger.valueOf(200).multiply(EXA));
        ownerDexTestBaseScoreClient.mintTo(userAddress, BigInteger.valueOf(200).multiply(EXA));
        ownerDexTestThirdScoreClient.mintTo(userAddress, BigInteger.valueOf(200).multiply(EXA));
        ownerDexTestFourthScoreClient.mintTo(userAddress, BigInteger.valueOf(200).multiply(EXA));


        //deposit base token
        userDexTestBaseScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(190).multiply(EXA),
                tokenDeposit);
        //deposit quote token
        userDexTestScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(190).multiply(EXA), tokenDeposit);
        userDexTestThirdScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(190).multiply(EXA),
                tokenDeposit);
        userDexTestFourthScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(190).multiply(EXA),
                tokenDeposit);

        //check isQuoteCoinAllowed for test token if not added
        if (!dexUserScoreClient.isQuoteCoinAllowed(tokenAAddress)) {
            governanceDexScoreClient.dexAddQuoteCoin(tokenAAddress);
        }
        if (!dexUserScoreClient.isQuoteCoinAllowed(tokenBAddress)) {
            governanceDexScoreClient.dexAddQuoteCoin(tokenBAddress);
        }
        if (!dexUserScoreClient.isQuoteCoinAllowed(tokenCAddress)) {
            governanceDexScoreClient.dexAddQuoteCoin(tokenCAddress);
        }
        if (!dexUserScoreClient.isQuoteCoinAllowed(tokenDAddress)) {
            governanceDexScoreClient.dexAddQuoteCoin(tokenDAddress);
        }
    }

    void waitForADay(){
        balanced.increaseDay(1);
    }

    BigInteger hexToBigInteger(String hex){
        return new BigInteger(hex.replace("0x", ""), 16);
    }

}
