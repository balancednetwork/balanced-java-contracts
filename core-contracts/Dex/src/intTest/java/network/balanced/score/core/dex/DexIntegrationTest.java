package network.balanced.score.core.dex;

import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.Wallet;
import foundation.icon.icx.data.Bytes;
import foundation.icon.jsonrpc.model.TransactionResult;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.interfaces.addresses.BnusdAddress;
import network.balanced.score.lib.interfaces.dex.*;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.Env;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import score.Address;
import score.UserRevertedException;

import java.io.File;
import java.math.BigInteger;
import java.util.Map;
import java.util.function.Consumer;

import static foundation.icon.score.client.DefaultScoreClient._deploy;
import static foundation.icon.score.client.DefaultScoreClient.balance;
import static network.balanced.score.core.dex.utils.Const.SICXICX_MARKET_NAME;
import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.*;

public class DexIntegrationTest {

    @ScoreClient
    static Staking staking;

    @ScoreClient
    static Loans loans;

    @ScoreClient
    static Rewards rewards;

    @ScoreClient
    static Sicx sicx;

    @ScoreClient
    static StakedLP stakedLp;


    @ScoreClient
    static Baln baln;

    static Env.Chain chain = Env.getDefaultChain();
    static Balanced balanced;
    static Wallet userWallet;
    static Wallet tUserWallet;
    static Wallet testOwnerWallet = KeyWallet.load(new Bytes("573b555367d6734ea0fecd0653ba02659fa19f7dc6ee5b93ec781350bda27376"));
    static DefaultScoreClient dexScoreClient ;
    static DefaultScoreClient governanceScoreClient;
    static DefaultScoreClient stakingScoreClient;
    static DefaultScoreClient sIcxScoreClient;
    static DefaultScoreClient dividendScoreClient;
    static DefaultScoreClient balnScoreClient;
    static DefaultScoreClient rewardsScoreClient;
    static DefaultScoreClient feeHandlerScoreClient;
    static DefaultScoreClient dexIntTestScoreClient;
    static DefaultScoreClient dexTestBaseScoreClient;
    static DefaultScoreClient dexTestThirdScoreClient;
    static DefaultScoreClient dexTestFourthScoreClient;


    static DefaultScoreClient daoFund;
    static Wallet ownerWallet;
    static File jarfile = new File("src/intTest/java/network/balanced/score/core/dex/testtokens/DexIntTestToken.jar");
    static {
        try {
            balanced = new Balanced();
            ownerWallet = balanced.owner;
            userWallet = ScoreIntegrationTest.createWalletWithBalance(BigInteger.valueOf(800).multiply(EXA));
            tUserWallet = ScoreIntegrationTest.createWalletWithBalance(BigInteger.valueOf(500).multiply(EXA));
            dexIntTestScoreClient =  _deploy(chain.getEndpointURL(), chain.networkId, testOwnerWallet, jarfile.getPath(), Map.of("name", "Test Token", "symbol", "TT") );
            dexTestBaseScoreClient =  _deploy(chain.getEndpointURL(), chain.networkId, testOwnerWallet, jarfile.getPath(), Map.of("name", "Test Base Token", "symbol", "TB") );
            dexTestThirdScoreClient =  _deploy(chain.getEndpointURL(), chain.networkId, testOwnerWallet, jarfile.getPath(), Map.of("name", "Test Third Token", "symbol", "TTD") );
            dexTestFourthScoreClient =  _deploy(chain.getEndpointURL(), chain.networkId, testOwnerWallet, jarfile.getPath(), Map.of("name", "Test Fourth Token", "symbol", "TFD") );
            balanced.setupBalanced();
            dexScoreClient = balanced.dex;
            governanceScoreClient = balanced.governance;
            stakingScoreClient = balanced.staking;
            sIcxScoreClient = balanced.sicx;
            dividendScoreClient = balanced.dividends;
            balnScoreClient = balanced.baln;
            rewardsScoreClient = balanced.rewards;
            feeHandlerScoreClient = balanced.feehandler;
            daoFund = balanced.daofund;
            ownerWallet = balanced.owner;
            staking = new StakingScoreClient(stakingScoreClient);
            rewards = new RewardsScoreClient(rewardsScoreClient);
            loans = new LoansScoreClient(balanced.loans);
            baln = new BalnScoreClient(balnScoreClient);
            sicx = new SicxScoreClient(sIcxScoreClient);
            stakedLp = new StakedLPScoreClient(balanced.stakedLp);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error on init test: "+e.getMessage());
        }

    }

    static String dexTestScoreAddress = dexIntTestScoreClient._address().toString();
    static String dexTestBaseScoreAddress = dexTestBaseScoreClient._address().toString();
    static String dexTestThirdScoreAddress = dexTestThirdScoreClient._address().toString();

    static foundation.icon.jsonrpc.Address userAddress = DefaultScoreClient.address(userWallet.getAddress().toString());
    static foundation.icon.jsonrpc.Address tUserAddress = DefaultScoreClient.address(tUserWallet.getAddress().toString());

    @ScoreClient
    static DexTest ownerDexTestScoreClient = new DexTestScoreClient(chain.getEndpointURL(), chain.networkId, testOwnerWallet, DefaultScoreClient.address(dexTestScoreAddress));;
    static DexTest ownerDexTestBaseScoreClient = new DexTestScoreClient(chain.getEndpointURL(), chain.networkId, testOwnerWallet, DefaultScoreClient.address(dexTestBaseScoreAddress));;
    static DexTest ownerDexTestThirdScoreClient = new DexTestScoreClient(chain.getEndpointURL(), chain.networkId, testOwnerWallet, DefaultScoreClient.address(dexTestThirdScoreAddress));;
    @ScoreClient
    static Dex dexUserScoreClient = new DexScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            dexScoreClient._address());
    @ScoreClient
    static Staking userStakeScoreClient = new StakingScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            stakingScoreClient._address());
    @ScoreClient
    static Sicx userSicxScoreClient = new SicxScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            sIcxScoreClient._address());
    @ScoreClient
    static Rewards userWalletRewardsClient = new RewardsScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            rewardsScoreClient._address());
    @ScoreClient
    static Baln userBalnScoreClient = new BalnScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            balnScoreClient._address());
    static DexTest userDexTestScoreClient = new DexTestScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            DefaultScoreClient.address(dexTestScoreAddress));
    static DexTest userDexTestBaseScoreClient = new DexTestScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            DefaultScoreClient.address(dexTestBaseScoreAddress));
    static DexTest userDexTestThirdScoreClient = new DexTestScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            DefaultScoreClient.address(dexTestThirdScoreAddress));
    @ScoreClient
    static Governance governanceDexScoreClient = new GovernanceScoreClient(governanceScoreClient);
    /*@ScoreClient
    static DexGeneral dexUserScoreClient = new DexGeneralScoreClient(dexScoreClient);
*/
    @ScoreClient
    static Rewards userRewardScoreClient = new RewardsScoreClient(rewardsScoreClient);

    @ScoreClient
    static DAOfund userDaoFundScoreClient = new DAOfundScoreClient(daoFund);


    @Test
    @Order(1)
    void testGovernanceAddress(){
        assertEquals("Balanced DEX", dexUserScoreClient.name());
        Address governanceAddress = dexUserScoreClient.getGovernance();
        assertEquals(governanceAddress, governanceScoreClient._address());
    }

    @Test
    @Order(2)
    void testAdminAddress() {
        Address adminAddress = dexUserScoreClient.getGovernance();
        assertEquals(adminAddress, governanceScoreClient._address());
    }

   static DefaultScoreClient userClient  = new DefaultScoreClient(
            chain.getEndpointURL(),
            chain.networkId,
            userWallet,
            DefaultScoreClient.ZERO_ADDRESS
    );

    static DefaultScoreClient tUserClient  = new DefaultScoreClient(
            chain.getEndpointURL(),
            chain.networkId,
            tUserWallet,
            DefaultScoreClient.ZERO_ADDRESS
    );

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

        System.out.println(" day is: "+ dexUserScoreClient.getDay());
        waitForADay();
        //release lock by distributing rewards
        balanced.syncDistributions();
        //verify sicx earning and make withdraw
        BigInteger sicxEarning = dexUserScoreClient.getSicxEarnings(userAddress);
        assertNotNull(sicxEarning);
        dexUserScoreClient.withdrawSicxEarnings(sicxEarning);

        UserRevertedException exception = assertThrows(UserRevertedException.class, () -> {
            dexUserScoreClient.cancelSicxicxOrder();
        });
        assertEquals(exception.getMessage(), "Reverted(0)");  //locked
        //cancel order
        waitForADay();
        balanced.syncDistributions();
        // this cal was working on 1 min day, but not working for offset manipulation.
        //dexUserScoreClient.cancelSicxicxOrder();
    }


    @Test
    @Order(4)
    void testMultipleAdd(){
        //testMultipleAdd
        BigInteger previousUserBalance = ownerDexTestScoreClient.balanceOf(userAddress);
        BigInteger previousSecondUserBalance = ownerDexTestThirdScoreClient.balanceOf(userAddress);
        byte[] tokenDeposit = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();

        this.mintAndTransferTestTokens(tokenDeposit);
        //add the pool of test token and sicx
        dexUserScoreClient.add(Address.fromString(dexTestThirdScoreAddress), Address.fromString(dexTestScoreAddress), BigInteger.valueOf(50).multiply(EXA), BigInteger.valueOf(50).multiply(EXA), true);
        BigInteger poolId = dexUserScoreClient.getPoolId(Address.fromString(dexTestThirdScoreAddress), Address.fromString(dexTestScoreAddress));
        Map<String, Object> poolStats = dexUserScoreClient.getPoolStats(poolId);
        assertNull(poolStats.get("name"));
        assertEquals(poolStats.get("base_token").toString(), dexTestThirdScoreAddress);
        assertEquals(poolStats.get("quote_token").toString(), dexTestScoreAddress);
        assertEquals(hexToBigInteger(poolStats.get("base").toString()), BigInteger.valueOf(50).multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("quote").toString()), BigInteger.valueOf(50).multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("total_supply").toString()), BigInteger.valueOf(50).multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("price").toString()), BigInteger.ONE.multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("base_decimals").toString()), BigInteger.valueOf(18));
        assertEquals(hexToBigInteger(poolStats.get("quote_decimals").toString()), BigInteger.valueOf(18));
        assertEquals(hexToBigInteger(poolStats.get("min_quote").toString()), BigInteger.ZERO);

        System.out.println("here " + previousSecondUserBalance);
        System.out.println("here " + previousUserBalance);
        // after lp is added to the pool, remaining balance is checked
        assertEquals(previousUserBalance.add(BigInteger.valueOf(150).multiply(EXA)), ownerDexTestScoreClient.balanceOf(userAddress));
        assertEquals(previousSecondUserBalance.add(BigInteger.valueOf(150).multiply(EXA)), ownerDexTestThirdScoreClient.balanceOf(userAddress));

        this.mintAndTransferTestTokens(tokenDeposit);

        dexUserScoreClient.add(Address.fromString(dexTestThirdScoreAddress), Address.fromString(dexTestScoreAddress), BigInteger.valueOf(80).multiply(EXA), BigInteger.valueOf(60).multiply(EXA), true);

        // after lp is added to the pool, remaining balance is checked
        assertEquals(BigInteger.valueOf(290).multiply(EXA), ownerDexTestScoreClient.balanceOf(userAddress));
        assertEquals(BigInteger.valueOf(290).multiply(EXA), ownerDexTestThirdScoreClient.balanceOf(userAddress));

        poolId = dexUserScoreClient.getPoolId(Address.fromString(dexTestThirdScoreAddress), Address.fromString(dexTestScoreAddress));
        poolStats = dexUserScoreClient.getPoolStats(poolId);
        assertNull(poolStats.get("name"));
        assertEquals(poolStats.get("base_token").toString(), dexTestThirdScoreAddress);
        assertEquals(poolStats.get("quote_token").toString(), dexTestScoreAddress);
        assertEquals(hexToBigInteger(poolStats.get("base").toString()), BigInteger.valueOf(110).multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("quote").toString()), BigInteger.valueOf(110).multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("total_supply").toString()), BigInteger.valueOf(110).multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("price").toString()), BigInteger.ONE.multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("base_decimals").toString()), BigInteger.valueOf(18));
        assertEquals(hexToBigInteger(poolStats.get("quote_decimals").toString()), BigInteger.valueOf(18));
        assertEquals(hexToBigInteger(poolStats.get("min_quote").toString()), BigInteger.ZERO);

        //change name and verify
        governanceDexScoreClient.setMarketName(poolId, "DTT/DTBT");
        Map<String, Object> updatedPoolStats = dexUserScoreClient.getPoolStats(poolId);
        assertEquals(updatedPoolStats.get("name").toString(), "DTT/DTBT");
    }


    @Test
    @Order(5)
    void testSwapTokensVerifySendsFeeAndRemove(){
        //check balance of fee handler in from token
        BigInteger feeBalanceOfTestToken = userDexTestScoreClient.balanceOf(feeHandlerScoreClient._address());
        byte[] tokenDeposit = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();
        this.mintAndTransferTestTokens(tokenDeposit);

        dexUserScoreClient.add(Address.fromString(dexTestBaseScoreAddress), Address.fromString(dexTestScoreAddress), BigInteger.valueOf(50).multiply(EXA), BigInteger.valueOf(50).multiply(EXA), true);

        BigInteger poolId = dexUserScoreClient.getPoolId(Address.fromString(dexTestBaseScoreAddress), Address.fromString(dexTestScoreAddress));
        assertNotNull(poolId);
        //governanceDexScoreClient.disable_fee_handler();
        String swapString = "{\"method\":\"_swap\",\"params\":{\"toToken\":\""+dexTestBaseScoreAddress+"\"}}";
        userDexTestScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(100).multiply(EXA), swapString.getBytes());
        Map<String, Object> poolStats = dexUserScoreClient.getPoolStats(poolId);
        assertEquals(poolStats.get("base_token").toString(), dexTestBaseScoreAddress);
        assertEquals(poolStats.get("quote_token").toString(), dexTestScoreAddress);
        assertEquals(hexToBigInteger(poolStats.get("base").toString()).divide(EXA), BigInteger.valueOf(16));
        assertEquals(hexToBigInteger(poolStats.get("quote").toString()).divide(EXA), BigInteger.valueOf(149));
        assertEquals(hexToBigInteger(poolStats.get("total_supply").toString()).divide(EXA), BigInteger.valueOf(50));
        assertEquals(hexToBigInteger(poolStats.get("price").toString()).divide(EXA), BigInteger.valueOf(8));
        assertEquals(hexToBigInteger(poolStats.get("base_decimals").toString()), BigInteger.valueOf(18));
        assertEquals(hexToBigInteger(poolStats.get("quote_decimals").toString()), BigInteger.valueOf(18));
        assertEquals(hexToBigInteger(poolStats.get("min_quote").toString()), BigInteger.ZERO);
        BigInteger updatedFeeBalanceOfTestToken = userDexTestScoreClient.balanceOf(feeHandlerScoreClient._address());
        assert updatedFeeBalanceOfTestToken.compareTo(feeBalanceOfTestToken)>0;
        assertEquals( BigInteger.valueOf(150).multiply(EXA).divide(BigInteger.valueOf(1000)),updatedFeeBalanceOfTestToken);

        UserRevertedException exception = assertThrows(UserRevertedException.class, () -> {
                    dexUserScoreClient.remove(poolId, BigInteger.valueOf(5), true);
                });
        assertEquals(exception.getMessage(), "Reverted(0)");  //locked


        waitForADay();
        balanced.syncDistributions();
        BigInteger withdrawAmount = BigInteger.valueOf(5);
        BigInteger balanceBefore = dexUserScoreClient.balanceOf(userAddress, poolId);
        dexUserScoreClient.remove(poolId, BigInteger.valueOf(5), true);
        BigInteger balanceAfter = dexUserScoreClient.balanceOf(userAddress, poolId);
        assert balanceAfter.equals(balanceBefore.subtract(withdrawAmount));
    }

    @Test
    @Order(6)
    void testWithdraw(){
        byte[] tokenDeposit = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();
        this.mintAndTransferTestTokens(tokenDeposit);
        BigInteger withdrawAMount = BigInteger.valueOf(50);
        BigInteger balanceBeforeWithdraw = dexUserScoreClient.balanceOfToken(userAddress, DefaultScoreClient.address(dexTestScoreAddress));
        //withdraw test token
        dexUserScoreClient.withdraw(DefaultScoreClient.address(dexTestScoreAddress), withdrawAMount);

        BigInteger balanceAfterWithdraw = dexUserScoreClient.balanceOfToken(userAddress, DefaultScoreClient.address(dexTestScoreAddress));

        assert balanceBeforeWithdraw.equals(balanceAfterWithdraw.add(withdrawAMount));
    }

    @Test
    @Order(7)
    void testLpTokensAndTransfer() {
        byte[] tokenDeposit = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();
        mintAndTransferTestTokens(tokenDeposit);
        transferSicxToken();
        dexUserScoreClient.add(Address.fromString(dexTestBaseScoreAddress), Address.fromString(sIcxScoreClient._address().toString()), BigInteger.valueOf(50).multiply(EXA), BigInteger.valueOf(25).multiply(EXA), false);
        mintAndTransferTestTokens(tokenDeposit);
        transferSicxToken();
        dexUserScoreClient.add(Address.fromString(sIcxScoreClient._address().toString()), Address.fromString(dexTestScoreAddress), BigInteger.valueOf(50).multiply(EXA), BigInteger.valueOf(25).multiply(EXA), false);
        mintAndTransferTestTokens(tokenDeposit);
        transferSicxToken();
        dexUserScoreClient.add(Address.fromString(dexTestBaseScoreAddress), Address.fromString(dexTestScoreAddress), BigInteger.valueOf(50).multiply(EXA), BigInteger.valueOf(25).multiply(EXA), false);
        mintAndTransferTestTokens(tokenDeposit);
        transferSicxToken();
        dexUserScoreClient.add(Address.fromString(sIcxScoreClient._address().toString()), Address.fromString(dexTestThirdScoreAddress), BigInteger.valueOf(50).multiply(EXA), BigInteger.valueOf(25).multiply(EXA), false);
        mintAndTransferTestTokens(tokenDeposit);
        transferSicxToken();
        dexUserScoreClient.add(Address.fromString(dexTestBaseScoreAddress), Address.fromString(dexTestThirdScoreAddress), BigInteger.valueOf(50).multiply(EXA), BigInteger.valueOf(25).multiply(EXA), false);

        waitForADay();

        //take pool id > 5 so that it can be transferred
        BigInteger poolId = dexUserScoreClient.getPoolId(Address.fromString(dexTestBaseScoreAddress), Address.fromString(dexTestThirdScoreAddress));
        System.out.println(poolId);
        BigInteger balance = dexUserScoreClient.balanceOf(userAddress, poolId);
        assertNotEquals(balance, BigInteger.ZERO);
        dexUserScoreClient.transfer(Address.fromString(tUserAddress.toString()), BigInteger.valueOf(5).multiply(EXA), poolId, new byte[0]);
        BigInteger tUsersBalance = dexUserScoreClient.balanceOf(Address.fromString(tUserAddress.toString()), poolId);
        assert BigInteger.ZERO.compareTo(tUsersBalance) < 0;
        // TODO : make it continuous and test transfer of pool id 2
        // withdraw from second user
    }

    @Test
    @Order(8)
    void testNonContinuousAndContinuousReward(){
        userDaoFundScoreClient.addAddressToSetdb();
        balanced.syncDistributions();
        BigInteger balnHolding = userRewardScoreClient.getBalnHolding(tUserAddress);
        tUserClient._transfer(dexScoreClient._address(), BigInteger.valueOf(200).multiply(EXA), null);
        if(dexUserScoreClient.getContinuousRewardsDay()==null) {
            governanceDexScoreClient.setContinuousRewardsDay(dexUserScoreClient.getDay().add(BigInteger.ONE));
        }

        System.out.println("Baln total supply is: "+userBalnScoreClient.totalSupply());
        waitForADay();

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

//    @Test
//    @Order(9)
//    void testBalnPoolTokenTransferrable(){
//        byte[] tokenDeposit = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();
//        mintAndTransferTestTokens(tokenDeposit);
//        BigInteger balance = dexUserScoreClient.balanceOf(tUserAddress, BigInteger.TWO);
//        assertNotEquals(balance, BigInteger.ZERO);
//        dexUserScoreClient.transfer(Address.fromString(tUserAddress.toString()), BigInteger.valueOf(5).multiply(EXA), BigInteger.TWO, new byte[0]);
//        BigInteger tUsersBalance = dexUserScoreClient.balanceOf(Address.fromString(tUserAddress.toString()), BigInteger.TWO);
//        assert BigInteger.ZERO.compareTo(tUsersBalance) < 0;
//
//    }

//    @Test
//    void testNonStakedLpRewards() throws Exception {
//        // test if the non staked lp token is rewarded or not once continuous rewards is activated.
//        balanced = new Balanced();
//        balanced.setupBalanced();
//        userDaoFundScoreClient.addAddressToSetdb();
//        balanced.syncDistributions();
//
//        byte[] tokenDeposit = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();
//        ((StakingScoreClient) staking).stakeICX(BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18)), userAddress
//                , null);
//
//        BigInteger loanAmount = BigInteger.valueOf(150).multiply(BigInteger.TEN.pow(18));
//        BigInteger collateral = BigInteger.valueOf(1000).multiply(BigInteger.TEN.pow(18));
//
//
//        ((LoansScoreClient) loans).depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);
//
//        waitForADay();
//        balanced.syncDistributions();
//        rewards.claimRewards();
//
//        baln.transfer(userAddress, loanAmount, null);
//
//        // deposit base token
//        userSicxScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(100).multiply(EXA), tokenDeposit);
//        //deposit quote token
//        userBalnScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(100).multiply(EXA), tokenDeposit);
//        dexUserScoreClient.add(balanced.baln._address(), balanced.sicx._address(), BigInteger.valueOf(100).multiply(EXA), BigInteger.valueOf(100).multiply(EXA), false);
//
//        waitForADay();
//        balanced.syncDistributions();
//        userWalletRewardsClient.claimRewards();
//        if(dexUserScoreClient.getContinuousRewardsDay()==null) {
//            governanceDexScoreClient.setContinuousRewardsDay(dexUserScoreClient.getDay().add(BigInteger.ONE));
//        }
//        waitForADay();
//
//        // continuous rewards starts here
//        balanced.syncDistributions();
//        userWalletRewardsClient.claimRewards();
//        waitForADay();
//
//        // next day starts
//        Consumer<TransactionResult> distributeConsumer = result -> {};
//        for(int i =0; i<10; i++){
//            balanced.ownerClient.rewards.distribute(distributeConsumer);
//        }
//        waitForADay();
//
//        // next day starts
//        for(int i =0; i<10; i++){
//            balanced.ownerClient.rewards.distribute(distributeConsumer);
//        }
//        // users without staking LP tokens will get 0 rewards
//        assertEquals(BigInteger.ZERO, rewards.getBalnHolding(userAddress));
//
//        byte[] stakeLp = "{\"method\":\"_stake\"}".getBytes();
//        dexUserScoreClient.transfer(balanced.stakedLp._address(), BigInteger.valueOf(90),BigInteger.valueOf(4), stakeLp);
//
//        // user gets rewards after lp token is staked
//        assertTrue(rewards.getBalnHolding(userAddress).compareTo(BigInteger.ZERO) > 0);
//        BigInteger previousUserBalance = baln.balanceOf(userAddress);
//        userWalletRewardsClient.claimRewards();
//        BigInteger newBalance = baln.balanceOf(userAddress);
//        assertTrue(newBalance.compareTo(previousUserBalance) > 0);
//    }

    void transferSicxToken(){
        byte[] data = "testData".getBytes();
        ((StakingScoreClient) userStakeScoreClient).stakeICX(BigInteger.valueOf(80).multiply(EXA), userAddress, data);

        byte[] tokenDeposit = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();
        userSicxScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(60).multiply(EXA), tokenDeposit);
    }

    void mintAndTransferTestTokens(byte[] tokenDeposit){

        ownerDexTestScoreClient.mintTo(userAddress, BigInteger.valueOf(200).multiply(EXA));
        ownerDexTestBaseScoreClient.mintTo(userAddress, BigInteger.valueOf(200).multiply(EXA));
        ownerDexTestThirdScoreClient.mintTo(userAddress, BigInteger.valueOf(200).multiply(EXA));


        //deposit base token
        userDexTestBaseScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(190).multiply(EXA), tokenDeposit);
        //deposit quote token
        userDexTestScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(190).multiply(EXA), tokenDeposit);
        userDexTestThirdScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(190).multiply(EXA), tokenDeposit);

        //check isQuoteCoinAllowed for test token if not added
        if(!dexUserScoreClient.isQuoteCoinAllowed(Address.fromString(dexTestScoreAddress))) {
            governanceDexScoreClient.dexAddQuoteCoin(Address.fromString(dexTestScoreAddress));
        }
        if(!dexUserScoreClient.isQuoteCoinAllowed(Address.fromString(dexTestBaseScoreAddress))) {
            governanceDexScoreClient.dexAddQuoteCoin(Address.fromString(dexTestBaseScoreAddress));
        }
        if(!dexUserScoreClient.isQuoteCoinAllowed(Address.fromString(dexTestThirdScoreAddress))) {
            governanceDexScoreClient.dexAddQuoteCoin(Address.fromString(dexTestThirdScoreAddress));
        }
    }

    void waitForADay(){
        balanced.increaseDay(1);
    }

    BigInteger hexToBigInteger(String hex){
        return new BigInteger(hex.replace("0x", ""), 16);
    }


}
