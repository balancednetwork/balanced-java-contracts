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

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonArray;
import foundation.icon.icx.Wallet;
import foundation.icon.jsonrpc.Address;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.xcall.NetworkAddress;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.interfaces.dex.DexTestScoreClient;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.Env;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import score.ByteArrayObjectWriter;
import score.Context;

import java.io.File;
import java.math.BigInteger;
import java.util.Map;

import static foundation.icon.score.client.DefaultScoreClient._deploy;
import static network.balanced.score.core.dex.utils.Const.SICXICX_MARKET_NAME;
import static network.balanced.score.lib.test.integration.BalancedUtils.createParameter;
import static network.balanced.score.lib.test.integration.BalancedUtils.createTransaction;
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
    private static BalancedClient owner;

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
            owner = balanced.ownerClient;

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
    private static final BalancedTokenScoreClient userBalnScoreClient =
            new BalancedTokenScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
                    balnScoreClient._address());
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
    @Order(3)
    void testICXTransferSwapEarningAndCancelOrder() {
        assertEquals(SICXICX_MARKET_NAME, dexUserScoreClient.getPoolName(BigInteger.ONE));
        BigInteger defaultPoolId = dexUserScoreClient.lookupPid(SICXICX_MARKET_NAME);
        assertEquals(BigInteger.ONE, defaultPoolId);

        Map<String, Object> poolStats = dexUserScoreClient.getPoolStats(defaultPoolId);
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


    @Test
    @Order(6)
    void testWithdraw() {
        byte[] tokenDeposit = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();
        this.mintAndTransferTestTokens(tokenDeposit);
        BigInteger withdrawAMount = BigInteger.valueOf(50);
        BigInteger balanceBeforeWithdraw = dexUserScoreClient.getDeposit(tokenAAddress, userAddress);
        //withdraw test token
        dexUserScoreClient.withdraw(tokenAAddress, withdrawAMount);

        BigInteger balanceAfterWithdraw = dexUserScoreClient.getDeposit(tokenAAddress, userAddress);

        assert balanceBeforeWithdraw.equals(balanceAfterWithdraw.add(withdrawAMount));
    }

    @Test
    @Order(7)
    void testLpTokensAndTransfer() {
        byte[] tokenDeposit = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();
        mintAndTransferTestTokens(tokenDeposit);
        transferSicxToken();
        dexUserScoreClient.add(tokenBAddress, Address.fromString(sIcxScoreClient._address().toString()),
                BigInteger.valueOf(50).multiply(EXA), BigInteger.valueOf(25).multiply(EXA), false, BigInteger.valueOf(100));
        mintAndTransferTestTokens(tokenDeposit);
        transferSicxToken();
        dexUserScoreClient.add(Address.fromString(sIcxScoreClient._address().toString()), tokenAAddress,
                BigInteger.valueOf(50).multiply(EXA), BigInteger.valueOf(25).multiply(EXA), false, BigInteger.valueOf(100));
        mintAndTransferTestTokens(tokenDeposit);
        transferSicxToken();
        dexUserScoreClient.add(tokenBAddress, tokenAAddress, BigInteger.valueOf(50).multiply(EXA),
                BigInteger.valueOf(25).multiply(EXA), false, BigInteger.valueOf(100));
        mintAndTransferTestTokens(tokenDeposit);
        transferSicxToken();
        dexUserScoreClient.add(Address.fromString(sIcxScoreClient._address().toString()), tokenCAddress,
                BigInteger.valueOf(50).multiply(EXA), BigInteger.valueOf(25).multiply(EXA), false, BigInteger.valueOf(100));
        mintAndTransferTestTokens(tokenDeposit);
        transferSicxToken();
        dexUserScoreClient.add(tokenBAddress, tokenCAddress, BigInteger.valueOf(50).multiply(EXA),
                BigInteger.valueOf(25).multiply(EXA), false, BigInteger.valueOf(100));

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
    void testNonContinuousAndContinuousReward() {
        balanced.syncDistributions();
        BigInteger balnHolding = userRewardScoreClient.getBalnHolding(tUserAddress.toString());
        tUserClient._transfer(dexScoreClient._address(), BigInteger.valueOf(200).multiply(EXA), null);


        balanced.syncDistributions();
        System.out.println("Baln total supply is: " + userBalnScoreClient.totalSupply());
        BigInteger updatedBalnHolding = userRewardScoreClient.getBalnHolding(tUserAddress.toString());
        System.out.println("baln holding: " + balnHolding);
        System.out.println("updated baln holding: " + updatedBalnHolding);
        assert balnHolding.compareTo(updatedBalnHolding) < 0;
        BigInteger beforeSleepDay = dexUserScoreClient.getDay();
        try {
            Thread.sleep(5000); //wait some time
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        BigInteger nextUpdatedBalnHolding = userRewardScoreClient.getBalnHolding(tUserAddress.toString());
        assertEquals(beforeSleepDay, dexUserScoreClient.getDay());


        assert updatedBalnHolding.compareTo(nextUpdatedBalnHolding) < 0;

    }

    @Test
    @Order(9)
    void crossChainDepositViaAssetManagerDirect() {
        //Arrange
        NetworkAddress ethAccount = new NetworkAddress(balanced.ETH_NID, "0x123");
        BigInteger amount = BigInteger.valueOf(100).multiply(EXA);
        NetworkAddress ethAssetAddress = new NetworkAddress(balanced.ETH_NID, balanced.ETH_TOKEN_ADDRESS);
        String toNetworkAddress = new NetworkAddress(balanced.ICON_NID, dexScoreClient._address()).toString();
        score.Address assetAddress = owner.assetManager.getAssetAddress(ethAssetAddress.toString());

        // Act
        byte[] depositData = tokenData("_deposit",
                new JsonObject().set( "address", ethAccount.toString()));
        byte[] deposit = AssetManagerMessages.deposit(balanced.ETH_TOKEN_ADDRESS, ethAccount.account(), toNetworkAddress,  amount, depositData);
        owner.xcall.recvCall(owner.assetManager._address(), new NetworkAddress(balanced.ETH_NID, balanced.ETH_ASSET_MANAGER).toString(), deposit);

        // Verify
        BigInteger retrievedValue = dexUserScoreClient.getDepositV2(assetAddress, ethAccount.toString());
        assertEquals(amount, retrievedValue);
    }

    @Test
    @Order(10)
    void crossChainDeposit() {
        //Arrange
        NetworkAddress ethAccount = new NetworkAddress(balanced.ETH_NID, "0x123");
        BigInteger amount = BigInteger.valueOf(100).multiply(EXA);
        NetworkAddress ethAssetAddress = new NetworkAddress(balanced.ETH_NID, balanced.ETH_TOKEN_ADDRESS);
        String toNetworkAddress = new NetworkAddress(balanced.ICON_NID, dexScoreClient._address()).toString();
        score.Address assetAddress = owner.assetManager.getAssetAddress(ethAssetAddress.toString());

        // Arrange - Initial deposit
        byte[] deposit = AssetManagerMessages.deposit(balanced.ETH_TOKEN_ADDRESS, ethAccount.account(), "",  amount, new byte[0]);
        owner.xcall.recvCall(owner.assetManager._address(), new NetworkAddress(balanced.ETH_NID, balanced.ETH_ASSET_MANAGER).toString(), deposit);

        // Act
        byte[] message = depositMsg(ethAccount.toString(), toNetworkAddress, amount, tokenData("_deposit",
                new JsonObject().set( "address", ethAccount.toString())));
        owner.xcall.recvCall(assetAddress, ethAccount.toString(), message);

        // Verify
        BigInteger retrievedValue = dexUserScoreClient.getDepositV2(assetAddress, ethAccount.toString());
        assertEquals(amount.add(amount), retrievedValue);
    }

    @Test
    @Order(11)
    void crossChainLP() {
        // Arrange
        NetworkAddress ethBaseAssetAddress = new NetworkAddress(balanced.ETH_NID, balanced.ETH_TOKEN_ADDRESS);
        NetworkAddress ethQuoteAssetAddress = new NetworkAddress(balanced.ETH_NID, "ox100");
        NetworkAddress ethAccount = new NetworkAddress(balanced.ETH_NID, "0x123");
        BigInteger amount = BigInteger.valueOf(100).multiply(EXA);
        String toNetworkAddress = new NetworkAddress(balanced.ICON_NID, dexScoreClient._address()).toString();

        // Arrange - deploy a new token
        JsonArray addAssetParams = new JsonArray()
                .add(createParameter(ethQuoteAssetAddress.toString()))
                .add(createParameter("ETHZ"))
                .add(createParameter("ETHZ"))
                .add(createParameter(BigInteger.valueOf(18)));
        JsonObject addAsset = createTransaction(balanced.assetManager._address(), "deployAsset", addAssetParams);
        JsonArray transactions = new JsonArray()
                .add(addAsset);
        balanced.governanceClient.execute(transactions.toString());

        score.Address baseAssetAddress = owner.assetManager.getAssetAddress(ethBaseAssetAddress.toString());
        score.Address quoteAssetAddress = owner.assetManager.getAssetAddress(ethQuoteAssetAddress.toString());

        // Arrange - deposits
        byte[] deposit1 = AssetManagerMessages.deposit(balanced.ETH_TOKEN_ADDRESS, ethAccount.account(), toNetworkAddress,  amount, tokenData("_deposit",
                new JsonObject().set( "address", ethAccount.toString())));
        owner.xcall.recvCall(owner.assetManager._address(), new NetworkAddress(balanced.ETH_NID, balanced.ETH_ASSET_MANAGER).toString(), deposit1);

        byte[] deposit2 = AssetManagerMessages.deposit("ox100", ethAccount.account(), toNetworkAddress,  amount, tokenData("_deposit",
                new JsonObject().set( "address", ethAccount.toString())));
        owner.xcall.recvCall(owner.assetManager._address(), new NetworkAddress(balanced.ETH_NID, balanced.ETH_ASSET_MANAGER).toString(), deposit2);

       // Arrange add quote token
        dexAddQuoteCoin((Address) quoteAssetAddress);

        // Act
        byte[] xaddMessage = getAddLPData(baseAssetAddress.toString(), quoteAssetAddress.toString(), amount, amount, false, BigInteger.valueOf(5) );
        owner.xcall.recvCall(dexScoreClient._address(), ethAccount.toString(), xaddMessage);

        // Verify
        BigInteger poolId = dexUserScoreClient.getPoolId(baseAssetAddress,
                quoteAssetAddress);
        assert poolId.compareTo(BigInteger.valueOf(6)) < 0;
        BigInteger liquidity =
                (amount.multiply(amount)).sqrt();
        BigInteger balance = dexUserScoreClient.xBalanceOf(ethAccount.toString(), poolId);

        assertEquals(balance, liquidity);
    }

    //depends on crossChainLP test
    @Test
    @Order(12)
    void xRemove(){
        // Arrange
        NetworkAddress ethAccount = new NetworkAddress(balanced.ETH_NID, "0x123");
        NetworkAddress ethBaseAssetAddress = new NetworkAddress(balanced.ETH_NID, balanced.ETH_TOKEN_ADDRESS);
        NetworkAddress ethQuoteAssetAddress = new NetworkAddress(balanced.ETH_NID, "ox100");

        score.Address baseAssetAddress = owner.assetManager.getAssetAddress(ethBaseAssetAddress.toString());
        score.Address quoteAssetAddress = owner.assetManager.getAssetAddress(ethQuoteAssetAddress.toString());

        BigInteger poolId = dexUserScoreClient.getPoolId(baseAssetAddress,
                quoteAssetAddress);
        BigInteger balance = dexUserScoreClient.xBalanceOf(ethAccount.toString(), poolId);
        BigInteger withdrawAmount = balance.divide(BigInteger.TWO);
        JsonArray setXCallFeePermissionParameters = new JsonArray()
                .add(createParameter(balanced.dex._address())).add(createParameter(balanced.ETH_NID)).add(createParameter(true));
        JsonArray actions = new JsonArray()
                .add(createTransaction(balanced.daofund._address(), "setXCallFeePermission", setXCallFeePermissionParameters));
        owner.governance.execute(actions.toString());

        // Act
        byte[] removeLPMsg = getXRemoveData(poolId, withdrawAmount, true);
        owner.xcall.recvCall(dexScoreClient._address(), ethAccount.toString(), removeLPMsg);

        // Verify
        BigInteger updatedBalance = dexUserScoreClient.xBalanceOf(ethAccount.toString(), poolId);
        assertEquals(withdrawAmount, updatedBalance);
    }

    public static byte[] tokenData(String method, JsonObject params) {
        JsonObject data = new JsonObject();
        data.set("method", method);
        data.set("params", params);
        return data.toString().getBytes();
    }

    static byte[] depositMsg(String from, String to, BigInteger amount, byte[] data) {
        ByteArrayObjectWriter writer = Context.newByteArrayObjectWriter("RLPn");
        writer.beginList(4);
        writer.write("xhubtransfer");
        writer.write(to);
        writer.write(amount);
        writer.write(data);
        writer.end();
        return writer.toByteArray();
    }
    static byte[] getXRemoveData(BigInteger poolId, BigInteger lpTokenBalance, Boolean withdraw) {
        ByteArrayObjectWriter writer = Context.newByteArrayObjectWriter("RLPn");
        writer.beginList(4);
        writer.write("xremove");
        writer.write(poolId);
        writer.write(lpTokenBalance);
        writer.write(withdraw);
        writer.end();
        return writer.toByteArray();
    }

    static byte[] getStakeData(BigInteger poolId, BigInteger amount, String to, byte[] data) {
        ByteArrayObjectWriter writer = Context.newByteArrayObjectWriter("RLPn");
        writer.beginList(4);
        writer.write("xhubtransfer");
        writer.write(to);
        writer.write(amount);
        writer.write(poolId);
        writer.write(data);
        writer.end();
        return writer.toByteArray();
    }

    static byte[] getAddLPData(String baseToken, String quoteToken, BigInteger baseValue, BigInteger quoteValue, Boolean withdraw_unused, BigInteger slippagePercentage) {
        ByteArrayObjectWriter writer = Context.newByteArrayObjectWriter("RLPn");
        writer.beginList(7);
        writer.write("xadd");
        writer.write(baseToken);
        writer.write(quoteToken);
        writer.write(baseValue);
        writer.write(quoteValue);
        writer.write(withdraw_unused);
        writer.write(slippagePercentage);
        writer.end();
        return writer.toByteArray();
    }

    void transferSicxToken() {
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
            dexAddQuoteCoin(tokenAAddress);
        }
        if (!dexUserScoreClient.isQuoteCoinAllowed(tokenBAddress)) {
            dexAddQuoteCoin(tokenBAddress);
        }
        if (!dexUserScoreClient.isQuoteCoinAllowed(tokenCAddress)) {
            dexAddQuoteCoin(tokenCAddress);
        }
        if (!dexUserScoreClient.isQuoteCoinAllowed(tokenDAddress)) {
            dexAddQuoteCoin(tokenDAddress);
        }
    }

    private static void dexAddQuoteCoin(Address address) {
        JsonArray addQuoteCoinParameters = new JsonArray()
                .add(createParameter(address));

        JsonArray actions = new JsonArray()
                .add(createTransaction(balanced.dex._address(), "addQuoteCoin", addQuoteCoinParameters));

        balanced.ownerClient.governance.execute(actions.toString());
    }

    void waitForADay() {
        balanced.increaseDay(1);
    }

    BigInteger hexToBigInteger(String hex) {
        return new BigInteger(hex.replace("0x", ""), 16);
    }

}
