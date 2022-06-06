package network.balanced.score.core.dex;

import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.Wallet;
import foundation.icon.icx.data.Bytes;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.DAOfund;
import network.balanced.score.lib.interfaces.DAOfundScoreClient;
import network.balanced.score.lib.interfaces.Governance;
import network.balanced.score.lib.interfaces.GovernanceScoreClient;
import network.balanced.score.lib.interfaces.dex.*;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.Env;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import org.junit.jupiter.api.Test;
import score.Address;

import java.io.File;
import java.math.BigInteger;
import java.util.Map;

import static foundation.icon.score.client.DefaultScoreClient._deploy;
import static network.balanced.score.core.dex.utils.Const.SICXICX_MARKET_NAME;
import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.*;

public class DexIntegrationTest {

    static Env.Chain chain = Env.getDefaultChain();

    static Balanced balanced = new Balanced();

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

    static DefaultScoreClient daoFund;
    static Wallet ownerWallet = balanced.owner;

    static {
        try {
            userWallet = ScoreIntegrationTest.createWalletWithBalance(BigInteger.valueOf(800).multiply(EXA));
            tUserWallet = ScoreIntegrationTest.createWalletWithBalance(BigInteger.valueOf(500).multiply(EXA));
            balanced.deployBalanced();
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
        } catch (Exception e) {
            System.out.println("Error on init test: "+e.getMessage());
        }

    }

    static File testToken = new File("src/intTest/java/network/balanced/score/core/dex/testtokens/DexIntTestToken-0.1-optimized.jar");
    static File testBaseToken = new File("src/intTest/java/network/balanced/score/core/dex/testtokens/DexBaseTestToken-0.1-optimized.jar");
    static File testThirdToken = new File("src/intTest/java/network/balanced/score/core/dex/testtokens/DexIntTestThirdToken-0.1-optimized.jar");

    static DefaultScoreClient dexIntTestScoreClient =  _deploy(chain.getEndpointURL(), chain.networkId, testOwnerWallet, testToken.getPath(), null);
    static DefaultScoreClient dexTestBaseScoreClient =  _deploy(chain.getEndpointURL(), chain.networkId, testOwnerWallet, testBaseToken.getPath(), null);
    static DefaultScoreClient dexTestThirdScoreClient =  _deploy(chain.getEndpointURL(), chain.networkId, testOwnerWallet, testThirdToken.getPath(), null);
    static String dexTestScoreAddress = dexIntTestScoreClient._address().toString();
    static String dexTestBaseScoreAddress = dexTestBaseScoreClient._address().toString();
    static String dexTestThirdScoreAddress = dexTestThirdScoreClient._address().toString();

    static foundation.icon.jsonrpc.Address userAddress = DefaultScoreClient.address(userWallet.getAddress().toString());
    static foundation.icon.jsonrpc.Address tUserAddress = DefaultScoreClient.address(tUserWallet.getAddress().toString());

    @ScoreClient
    static OwnerDexTest ownerDexTestScoreClient = new OwnerDexTestScoreClient(chain.getEndpointURL(), chain.networkId, testOwnerWallet, DefaultScoreClient.address(dexTestScoreAddress));;
    @ScoreClient
    static OwnerDexTest ownerDexTestBaseScoreClient = new OwnerDexTestScoreClient(chain.getEndpointURL(), chain.networkId, testOwnerWallet, DefaultScoreClient.address(dexTestBaseScoreAddress));;

    @ScoreClient
    static OwnerDexTest ownerDexTestThirdScoreClient = new OwnerDexTestScoreClient(chain.getEndpointURL(), chain.networkId, testOwnerWallet, DefaultScoreClient.address(dexTestThirdScoreAddress));;
    @ScoreClient
    static DexUser dexUserScoreClient = new DexUserScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            dexScoreClient._address());
    @ScoreClient
    static UserStake userStakeScoreClient = new UserStakeScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            stakingScoreClient._address());
    @ScoreClient
    static UserSicx userSicxScoreClient = new UserSicxScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            sIcxScoreClient._address());
    @ScoreClient
    static UserBaln userBalnScoreClient = new UserBalnScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            balnScoreClient._address());
    @ScoreClient
    static UserDexTest userDexTestScoreClient = new UserDexTestScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            DefaultScoreClient.address(dexTestScoreAddress));
    @ScoreClient
    static UserDexTest userDexTestBaseScoreClient = new UserDexTestScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            DefaultScoreClient.address(dexTestBaseScoreAddress));
    @ScoreClient
    static UserDexTest userDexTestThirdScoreClient = new UserDexTestScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            DefaultScoreClient.address(dexTestThirdScoreAddress));
    @ScoreClient
    static Governance governanceDexScoreClient = new GovernanceScoreClient(governanceScoreClient);
    @ScoreClient
    static DexGeneral dexReadOnlyScoreClient = new DexGeneralScoreClient(dexScoreClient);

    @ScoreClient
    static UserReward userRewardScoreClient = new UserRewardScoreClient(rewardsScoreClient);

    @ScoreClient
    static DAOfund userDaoFundScoreClient = new DAOfundScoreClient(daoFund);

    /*@BeforeEach
    void addTokenAddressesInDaoFund(){
        userDaoFundScoreClient.addAddressToSetdb();
    }*/

    @Test
    void testGovernanceAddress(){
        assertEquals("Balanced DEX", dexReadOnlyScoreClient.name());
        Address governanceAddress = dexReadOnlyScoreClient.getGovernance();
        assertEquals(governanceAddress, governanceScoreClient._address());
    }

    @Test
    void testAdminAddress() {
        Address adminAddress = dexReadOnlyScoreClient.getGovernance();
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
    void testICXTransferSwapEarningAndCancelOrder(){
        assertEquals(SICXICX_MARKET_NAME, dexReadOnlyScoreClient.getPoolName(BigInteger.ONE));
        BigInteger defaultPoolId = dexReadOnlyScoreClient.lookupPid(SICXICX_MARKET_NAME);
        assertEquals(BigInteger.ONE, defaultPoolId);

        Map<String, Object>  poolStats = dexReadOnlyScoreClient.getPoolStats(defaultPoolId);
        assertEquals(poolStats.get("base_token").toString(), sIcxScoreClient._address().toString());
        assertNull(poolStats.get("quote_token"));
        assertEquals(hexToBigInteger(poolStats.get("base").toString()), BigInteger.ZERO);
        assertEquals(hexToBigInteger(poolStats.get("quote").toString()), BigInteger.ZERO);
        assertEquals(hexToBigInteger(poolStats.get("total_supply").toString()), BigInteger.ZERO);

        //test icx transfer and verify stats
        userRewardScoreClient.distribute();
        userRewardScoreClient.distribute();
        userRewardScoreClient.distribute();
        userClient._transfer(dexScoreClient._address(), BigInteger.valueOf(200).multiply(EXA), null);
        poolStats = dexReadOnlyScoreClient.getPoolStats(defaultPoolId);

        assertEquals(poolStats.get("base_token").toString(), sIcxScoreClient._address().toString());
        assertNull(poolStats.get("quote_token"));
        assertEquals(hexToBigInteger(poolStats.get("base").toString()), BigInteger.ZERO);
        assertEquals(hexToBigInteger(poolStats.get("quote").toString()), BigInteger.valueOf(200).multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("total_supply").toString()), BigInteger.valueOf(200).multiply(EXA));

        //test swap
        byte[] data = "testData".getBytes();
        ((UserStakeScoreClient) userStakeScoreClient).stakeICX(BigInteger.valueOf(100).multiply(EXA), userAddress, data);

        byte[] swapIcx = "{\"method\":\"_swap_icx\",\"params\":{\"none\":\"none\"}}".getBytes();
        userSicxScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(50).multiply(EXA), swapIcx);

        defaultPoolId = dexReadOnlyScoreClient.lookupPid(SICXICX_MARKET_NAME);
        poolStats = dexReadOnlyScoreClient.getPoolStats(defaultPoolId);

        assertEquals(poolStats.get("base_token").toString(), sIcxScoreClient._address().toString());
        assertNull(poolStats.get("quote_token"));
        assertEquals(hexToBigInteger(poolStats.get("base").toString()), BigInteger.ZERO);
        assertEquals(hexToBigInteger(poolStats.get("quote").toString()).divide(EXA), BigInteger.valueOf(150));
        assertEquals(hexToBigInteger(poolStats.get("total_supply").toString()).divide(EXA), BigInteger.valueOf(150));

        waitForADay();

        //release lock by distributing rewards
        userRewardScoreClient.distribute();
        userRewardScoreClient.distribute();
        userRewardScoreClient.distribute();

        //verify sicx earning and make withdraw
        BigInteger sicxEarning = dexReadOnlyScoreClient.getSicxEarnings(userAddress);
        assertNotNull(sicxEarning);
        dexUserScoreClient.withdrawSicxEarnings(BigInteger.ONE);

        //cancel order
        BigInteger sicxEarnings = dexReadOnlyScoreClient.getSicxEarnings(userAddress);
        System.out.println("sicx earnings is: "+sicxEarnings);
        dexUserScoreClient.cancelSicxicxOrder();
    }


    @Test
    void testMultipleAdd(){
        //testMultipleAdd
        byte[] tokenDeposit = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();

        this.mintAndTransferTestTokens(tokenDeposit);
        //add the pool of test token and sicx
        dexUserScoreClient.add(Address.fromString(dexTestThirdScoreAddress), Address.fromString(dexTestScoreAddress), BigInteger.valueOf(50).multiply(EXA), BigInteger.valueOf(50).multiply(EXA), true);
        BigInteger poolId = dexReadOnlyScoreClient.getPoolId(Address.fromString(dexTestThirdScoreAddress), Address.fromString(dexTestScoreAddress));
        Map<String, Object> poolStats = dexReadOnlyScoreClient.getPoolStats(poolId);
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

        this.mintAndTransferTestTokens(tokenDeposit);

        dexUserScoreClient.add(Address.fromString(dexTestThirdScoreAddress), Address.fromString(dexTestScoreAddress), BigInteger.valueOf(80).multiply(EXA), BigInteger.valueOf(60).multiply(EXA), true);
        poolId = dexReadOnlyScoreClient.getPoolId(Address.fromString(dexTestThirdScoreAddress), Address.fromString(dexTestScoreAddress));
        poolStats = dexReadOnlyScoreClient.getPoolStats(poolId);
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
        Map<String, Object> updatedPoolStats = dexReadOnlyScoreClient.getPoolStats(poolId);
        assertEquals(updatedPoolStats.get("name").toString(), "DTT/DTBT");
    }


    @Test
    void testSwapTokensVerifySendsFeeAndRemove(){
        //check balance of fee handler in from token
        BigInteger feeBalanceOfTestToken = userDexTestScoreClient.balanceOf(feeHandlerScoreClient._address());
        byte[] tokenDeposit = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();
        this.mintAndTransferTestTokens(tokenDeposit);

        dexUserScoreClient.add(Address.fromString(dexTestBaseScoreAddress), Address.fromString(dexTestScoreAddress), BigInteger.valueOf(50).multiply(EXA), BigInteger.valueOf(50).multiply(EXA), true);

        BigInteger poolId = dexReadOnlyScoreClient.getPoolId(Address.fromString(dexTestBaseScoreAddress), Address.fromString(dexTestScoreAddress));
        assertNotNull(poolId);
        //governanceDexScoreClient.disable_fee_handler();
        String swapString = "{\"method\":\"_swap\",\"params\":{\"toToken\":\""+dexTestBaseScoreAddress+"\"}}";
        userDexTestScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(100).multiply(EXA), swapString.getBytes());
        Map<String, Object> poolStats = dexReadOnlyScoreClient.getPoolStats(poolId);
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

        try{
            dexUserScoreClient.remove(poolId, BigInteger.valueOf(5), true);
        }catch (Exception e){
            assertEquals(e.getMessage(), "Reverted(0)");  //locked
        }

        waitForADay();

        //release lock by distributing rewards
        userRewardScoreClient.distribute();
        userRewardScoreClient.distribute();
        userRewardScoreClient.distribute();

        dexUserScoreClient.remove(poolId, BigInteger.valueOf(5), true);
    }

    @Test
    void testWithdraw(){
        byte[] tokenDeposit = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();
        this.mintAndTransferTestTokens(tokenDeposit);

        //withdraw test token
        dexUserScoreClient.withdraw(DefaultScoreClient.address(dexTestScoreAddress), BigInteger.valueOf(90));
    }

    @Test
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
        BigInteger poolId = dexReadOnlyScoreClient.getPoolId(Address.fromString(dexTestBaseScoreAddress), Address.fromString(dexTestThirdScoreAddress));
        System.out.println(poolId);
        BigInteger balance = dexReadOnlyScoreClient.balanceOf(userAddress, poolId);
        assertNotEquals(balance, BigInteger.ZERO);
        dexUserScoreClient.transfer(Address.fromString(tUserAddress.toString()), BigInteger.valueOf(5).multiply(EXA), poolId, new byte[0]);
        BigInteger tUsersBalance = dexReadOnlyScoreClient.balanceOf(Address.fromString(tUserAddress.toString()), poolId);
        assert BigInteger.ZERO.compareTo(tUsersBalance) < 0;
    }

    @Test
    void testNonContinuousAndContinuousReward(){
        userDaoFundScoreClient.addAddressToSetdb();
        userRewardScoreClient.distribute();
        userRewardScoreClient.distribute();
        userRewardScoreClient.distribute();
        BigInteger balnHolding = userRewardScoreClient.getBalnHolding(tUserAddress.toString());
        tUserClient._transfer(dexScoreClient._address(), BigInteger.valueOf(200).multiply(EXA), null);
        if(dexReadOnlyScoreClient.getContinuousRewardsDay()==null) {
            governanceDexScoreClient.setContinuousRewardsDay(dexReadOnlyScoreClient.getDay().add(BigInteger.ONE));
        }
        System.out.println("Baln total supply is: "+userBalnScoreClient.totalSupply());
        waitForADay();

       userRewardScoreClient.distribute();
       userRewardScoreClient.distribute();
       userRewardScoreClient.distribute();
       userRewardScoreClient.distribute();
       userRewardScoreClient.distribute();
       userRewardScoreClient.distribute();
       userRewardScoreClient.distribute();
       System.out.println("Baln total supply is: "+userBalnScoreClient.totalSupply());
       BigInteger updatedBalnHolding = userRewardScoreClient.getBalnHolding(tUserAddress.toString());
       System.out.println("baln holding: "+balnHolding);
       System.out.println("updated baln holding: "+updatedBalnHolding);
       assert balnHolding.compareTo(updatedBalnHolding)<0;
            BigInteger beforeSleepDay = dexReadOnlyScoreClient.getDay();
        try {
            Thread.sleep(5000); //day change
        }catch (Exception e){
            System.out.println(e.getMessage());
        }

        BigInteger nextUpdatedBalnHolding = userRewardScoreClient.getBalnHolding(tUserAddress.toString());
        assertEquals(beforeSleepDay, dexReadOnlyScoreClient.getDay());

        System.out.println("updated baln holding: "+updatedBalnHolding);
        System.out.println("next updated baln holding: "+nextUpdatedBalnHolding);
        assert updatedBalnHolding.compareTo(nextUpdatedBalnHolding)<0;

    }

    void transferSicxToken(){
        byte[] data = "testData".getBytes();
        ((UserStakeScoreClient) userStakeScoreClient).stakeICX(BigInteger.valueOf(80).multiply(EXA), userAddress, data);

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
        if(!dexReadOnlyScoreClient.isQuoteCoinAllowed(Address.fromString(dexTestScoreAddress))) {
            governanceDexScoreClient.dexAddQuoteCoin(Address.fromString(dexTestScoreAddress));
        }
        if(!dexReadOnlyScoreClient.isQuoteCoinAllowed(Address.fromString(dexTestBaseScoreAddress))) {
            governanceDexScoreClient.dexAddQuoteCoin(Address.fromString(dexTestBaseScoreAddress));
        }
        if(!dexReadOnlyScoreClient.isQuoteCoinAllowed(Address.fromString(dexTestThirdScoreAddress))) {
            governanceDexScoreClient.dexAddQuoteCoin(Address.fromString(dexTestThirdScoreAddress));
        }
    }

    void waitForADay(){
        try {
            Thread.sleep(65000); //day change
        }catch (Exception e){
            System.out.println(e.getMessage());
        }
    }

    BigInteger hexToBigInteger(String hex){
        return new BigInteger(hex.replace("0x", ""), 16);
    }


}
