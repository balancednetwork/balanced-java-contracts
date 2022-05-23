package network.balanced.score.core.dex;

import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.Wallet;
import foundation.icon.icx.data.Bytes;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.dex.*;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.Env;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import org.junit.jupiter.api.Test;
import score.Address;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.dex.Const.SICXICX_MARKET_NAME;
import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DexIntegrationTest {
    static Wallet ownerWallet = KeyWallet.load(new Bytes("0x2dfe2c49672a84eab72ce6419f71e8e8e9f9c1ca8ce8284ab677aa957f819f7f"));
    static Wallet testOwnerWallet = KeyWallet.load(new Bytes("fc4e5330eed9a4a4ed4d940d49dd4fa6f3daaf5e9a9a2c3406827312b7195467"));
    static String scoreAddress = "cx3e4b3a2b34d17b92e45bf644fc58c5d0c2034a3b";
    static String governScoreAddress = "cxcbb8e2d96bc93e2897d7414949652caa36996c5f";
    static String stakingScoreAddress = "cx2359675be5505c05f53a33d14ba1d8567a70334a";
    static String sIcxScoreAddress = "cx724b2c91d909d72519c1d2130fd766830613ae31";
    static String dividendScoreAddress = "cxc0a5404089b84a87d40a1793a759886cfe483eda"; //null;//
    static String balnScoreAddress = "cxacff7ff1e0fac52521ea36ede890eb63148d8814"; //null;//
    static String dexTestScoreAddress = "cx789581c589ea50bc3ac16839ff3019ab55bba2e9";
    static String dexTestBaseScoreAddress = "cx15fafc9fbc2f76923cdd14ed2bbe66e8b92350e1";
    static Env.Chain chain = Env.getDefaultChain();
    //private static Wallet governanceWallet = null;
    private static Wallet userWallet = KeyWallet.load(new Bytes("0x5f22749393e586fd0461d740bbed3173574ff16cd3f2f8d529f5a5d0ce463a88"));
    static DefaultScoreClient dexScoreClient = null;
    static DefaultScoreClient governanceScoreClient = null;
    static DefaultScoreClient stakingScoreClient = null;
    static DefaultScoreClient sIcxScoreClient = null;
    static DefaultScoreClient balnScoreClient = null;
    static DefaultScoreClient dividendScoreClient = null;
    static Balanced balanced = new Balanced();

    static {
        try {
            if(scoreAddress==null) {
                userWallet = ScoreIntegrationTest.createWalletWithBalance(BigInteger.valueOf(1000).multiply(EXA));
            }
            if(scoreAddress!=null){
                dexScoreClient = new DefaultScoreClient(chain.getEndpointURL(), chain.networkId, ownerWallet, DefaultScoreClient.address(scoreAddress));
                governanceScoreClient = new DefaultScoreClient(chain.getEndpointURL(), chain.networkId, ownerWallet, DefaultScoreClient.address(governScoreAddress));
                stakingScoreClient = new DefaultScoreClient(chain.getEndpointURL(), chain.networkId, ownerWallet, DefaultScoreClient.address(stakingScoreAddress));
                sIcxScoreClient = new DefaultScoreClient(chain.getEndpointURL(), chain.networkId, ownerWallet, DefaultScoreClient.address(sIcxScoreAddress));
                dividendScoreClient = new DefaultScoreClient(chain.getEndpointURL(), chain.networkId, ownerWallet, DefaultScoreClient.address(dividendScoreAddress));
                balnScoreClient = new DefaultScoreClient(chain.getEndpointURL(), chain.networkId, ownerWallet, DefaultScoreClient.address(balnScoreAddress));
            }else{
                balanced.deployBalanced();
                dexScoreClient = balanced.dex;
                governanceScoreClient = balanced.governance;
                stakingScoreClient = balanced.staking;
                sIcxScoreClient = balanced.sicx;
                dividendScoreClient = balanced.dividends;
                balnScoreClient = balanced.baln;
                ownerWallet = balanced.owner;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static foundation.icon.jsonrpc.Address userAddress = DefaultScoreClient.address(userWallet.getAddress().toString());

    static DefaultScoreClient dexTestScoreClient = new DefaultScoreClient(chain.getEndpointURL(), chain.networkId, testOwnerWallet, DefaultScoreClient.address(dexTestScoreAddress));;
    static DefaultScoreClient dexTestBaseScoreClient = new DefaultScoreClient(chain.getEndpointURL(), chain.networkId, testOwnerWallet, DefaultScoreClient.address(dexTestBaseScoreAddress));;

    static DefaultScoreClient generalUserScoreClient = new DefaultScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            dexScoreClient._address());

    static DefaultScoreClient stakingUserScoreClient = new DefaultScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            stakingScoreClient._address());

    static DefaultScoreClient sicxUserScoreClient = new DefaultScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            sIcxScoreClient._address());

    static DefaultScoreClient balnUserScoreClient = new DefaultScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            balnScoreClient._address());

    static DefaultScoreClient dexTestUserScoreClient = new DefaultScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            dexTestScoreClient._address());

    static DefaultScoreClient dexTestBaseUserScoreClient = new DefaultScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            dexTestBaseScoreClient._address());

    @ScoreClient
    static GovernanceDex governanceDexScoreClient = new GovernanceDexScoreClient(governanceScoreClient);

    @ScoreClient
    static DexUser dexUserScoreClient = new DexUserScoreClient(generalUserScoreClient);

    @ScoreClient
    static DexGeneral dexReadOnlyScoreClient = new DexGeneralScoreClient(dexScoreClient);

    @ScoreClient
    static UserStake userStakeScoreClient = new UserStakeScoreClient(stakingUserScoreClient);

    @ScoreClient
    static UserSicx userSicxScoreClient = new UserSicxScoreClient(sicxUserScoreClient);

    @ScoreClient
    static UserBaln userBalnScoreClient = new UserBalnScoreClient(balnUserScoreClient);

    @ScoreClient
    static UserDexTest userDexTestScoreClient = new UserDexTestScoreClient(dexTestUserScoreClient);

    @ScoreClient
    static UserDexTest userDexTestBaseScoreClient = new UserDexTestScoreClient(dexTestBaseUserScoreClient);


    @Test
    void testGovernanceAddress(){
        assertEquals("Balanced DEX", dexReadOnlyScoreClient.name());
        Address governanceAddress = dexReadOnlyScoreClient.getGovernance();
        assertEquals(governanceAddress, governanceScoreClient._address());
    }

    @Test
    void testAdminAddress(){
        Address adminAddress = dexReadOnlyScoreClient.getGovernance();
        assertEquals(adminAddress, governanceScoreClient._address());
    }

    @Test
    void testCreateICXPool(){
        byte[] data = "test".getBytes();
        byte[] sicxData = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();
        ((UserStakeScoreClient) userStakeScoreClient).stakeICX(BigInteger.valueOf(100).multiply(EXA), userAddress, data);
        userSicxScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(50).multiply(EXA), sicxData);
        assertEquals(BigInteger.valueOf(50).multiply(EXA), dexReadOnlyScoreClient.getDeposit(sIcxScoreClient._address(), userAddress));
        assertEquals(SICXICX_MARKET_NAME, dexReadOnlyScoreClient.getPoolName(BigInteger.ONE));
        assertEquals(BigInteger.ONE, dexReadOnlyScoreClient.lookupPid(SICXICX_MARKET_NAME));
    }

    @Test
    void testCreateTestTokenSIcxPool(){
        byte[] data = "test".getBytes();
        byte[] tokenDeposit = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();
        //stake icx to get sicx
        ((UserStakeScoreClient) userStakeScoreClient).stakeICX(BigInteger.valueOf(200).multiply(EXA), userAddress, data);
        //deposit sicx
        userSicxScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(50).multiply(EXA), tokenDeposit);

        //deposit test token
        userDexTestScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(50).multiply(EXA), tokenDeposit);

        //check isQuoteCoinAllowed for test token if not added
        if(!dexReadOnlyScoreClient.isQuoteCoinAllowed(Address.fromString(dexTestScoreAddress))) {
            governanceDexScoreClient.dexAddQuoteCoin(Address.fromString(dexTestScoreAddress));
        }

        //add the pool of test token and sicx
        dexUserScoreClient.add(Address.fromString(sIcxScoreAddress), Address.fromString(dexTestScoreAddress), BigInteger.valueOf(50).multiply(EXA), BigInteger.valueOf(50).multiply(EXA), true);

        //test values
        assertEquals(BigInteger.TWO, dexReadOnlyScoreClient.getPoolId(Address.fromString(sIcxScoreAddress), Address.fromString(dexTestScoreAddress)));
        assertEquals(BigInteger.valueOf(50).multiply(EXA), dexReadOnlyScoreClient.totalSupply(BigInteger.TWO));
        assertEquals(Address.fromString(sIcxScoreAddress), dexReadOnlyScoreClient.getPoolBase(BigInteger.TWO));
        assertEquals(Address.fromString(dexTestScoreAddress), dexReadOnlyScoreClient.getPoolQuote(BigInteger.TWO));
        Map<String, Object> poolStats = dexReadOnlyScoreClient.getPoolStats(BigInteger.TWO);
        assertNull(poolStats.get("name"));
        assertEquals(poolStats.get("base_token").toString(), sIcxScoreAddress);
        assertEquals(poolStats.get("quote_token").toString(), dexTestScoreAddress);
        assertEquals(hexToBigInteger(poolStats.get("base").toString()), BigInteger.valueOf(50).multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("quote").toString()), BigInteger.valueOf(50).multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("total_supply").toString()), BigInteger.valueOf(50).multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("price").toString()), BigInteger.ONE.multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("base_decimals").toString()), BigInteger.valueOf(18));
        assertEquals(hexToBigInteger(poolStats.get("quote_decimals").toString()), BigInteger.valueOf(18));
        assertEquals(hexToBigInteger(poolStats.get("min_quote").toString()), BigInteger.ZERO);

        //set pool name and test get pool name
        governanceDexScoreClient.setMarketName(BigInteger.TWO, "DTT/SIcx");
        Map<String, Object> updatedPoolStats = dexReadOnlyScoreClient.getPoolStats(BigInteger.TWO);
        assertEquals(updatedPoolStats.get("name").toString(), "DTT/SIcx");
    }

    @Test
    void testMultipleAdd(){
        byte[] tokenDeposit = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();
        //deposit sicx
        userDexTestBaseScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(300).multiply(EXA), tokenDeposit);

        //deposit test token
        userDexTestScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(300).multiply(EXA), tokenDeposit);

        //check isQuoteCoinAllowed for test token if not added
        if(!dexReadOnlyScoreClient.isQuoteCoinAllowed(Address.fromString(dexTestScoreAddress))) {
            governanceDexScoreClient.dexAddQuoteCoin(Address.fromString(dexTestScoreAddress));
        }
        if(!dexReadOnlyScoreClient.isQuoteCoinAllowed(Address.fromString(dexTestBaseScoreAddress))) {
            governanceDexScoreClient.dexAddQuoteCoin(Address.fromString(dexTestBaseScoreAddress));
        }

        //add the pool of test token and sicx
        dexUserScoreClient.add(Address.fromString(dexTestBaseScoreAddress), Address.fromString(dexTestScoreAddress), BigInteger.valueOf(50).multiply(EXA), BigInteger.valueOf(50).multiply(EXA), true);
        BigInteger poolId = dexReadOnlyScoreClient.getPoolId(Address.fromString(dexTestBaseScoreAddress), Address.fromString(dexTestScoreAddress));
        Map<String, Object> poolStats = dexReadOnlyScoreClient.getPoolStats(poolId);
        assertNull(poolStats.get("name"));
        assertEquals(poolStats.get("base_token").toString(), sIcxScoreAddress);
        assertEquals(poolStats.get("quote_token").toString(), dexTestScoreAddress);
        assertEquals(hexToBigInteger(poolStats.get("base").toString()), BigInteger.valueOf(50).multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("quote").toString()), BigInteger.valueOf(50).multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("total_supply").toString()), BigInteger.valueOf(50).multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("price").toString()), BigInteger.ONE.multiply(EXA));
        assertEquals(hexToBigInteger(poolStats.get("base_decimals").toString()), BigInteger.valueOf(18));
        assertEquals(hexToBigInteger(poolStats.get("quote_decimals").toString()), BigInteger.valueOf(18));
        assertEquals(hexToBigInteger(poolStats.get("min_quote").toString()), BigInteger.ZERO);



    }





    BigInteger hexToBigInteger(String hex){
        return new BigInteger(hex.replace("0x", ""), 16);
    }

}
