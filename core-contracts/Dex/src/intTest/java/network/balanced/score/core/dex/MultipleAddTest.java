package network.balanced.score.core.dex;

import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.Wallet;
import foundation.icon.icx.data.Bytes;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.interfaces.dex.DexTest;
import network.balanced.score.lib.interfaces.dex.DexTestScoreClient;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.Env;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import score.Address;

import java.io.File;
import java.math.BigInteger;
import java.util.Map;

import static foundation.icon.score.client.DefaultScoreClient._deploy;
import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MultipleAddTest {


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
            dexTestThirdScoreClient =  _deploy(chain.getEndpointURL(), chain.networkId, testOwnerWallet, jarfile.getPath(), Map.of("name", "Test Third Token", "symbol", "TTD") );
            dexTestFourthScoreClient =  _deploy(chain.getEndpointURL(), chain.networkId, testOwnerWallet, jarfile.getPath(), Map.of("name", "Test Fourth Token", "symbol", "TFD") );
            balanced.setupBalanced();
            dexScoreClient = balanced.dex;
            governanceScoreClient = balanced.governance;
            ownerWallet = balanced.owner;

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error on init test: "+e.getMessage());
        }

    }

    static String dexTestThirdScoreAddress = dexTestThirdScoreClient._address().toString();
    static String dexTestFourthScoreAddress = dexTestFourthScoreClient._address().toString();

    static foundation.icon.jsonrpc.Address userAddress = DefaultScoreClient.address(userWallet.getAddress().toString());

    @ScoreClient
    static DexTest ownerDexTestThirdScoreClient = new DexTestScoreClient(chain.getEndpointURL(), chain.networkId, testOwnerWallet, DefaultScoreClient.address(dexTestThirdScoreAddress));;
    static DexTest ownerDexTestFourthScoreClient = new DexTestScoreClient(chain.getEndpointURL(), chain.networkId, testOwnerWallet, DefaultScoreClient.address(dexTestFourthScoreAddress));;
    @ScoreClient
    static Dex dexUserScoreClient = new DexScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            dexScoreClient._address());

    static DexTest userDexTestThirdScoreClient = new DexTestScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            DefaultScoreClient.address(dexTestThirdScoreAddress));
    static DexTest userDexTestFourthScoreClient = new DexTestScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            DefaultScoreClient.address(dexTestFourthScoreAddress));
    @ScoreClient
    static Governance governanceDexScoreClient = new GovernanceScoreClient(governanceScoreClient);

    @Test
    @Order(4)
    void testMultipleAdd(){
        //testMultipleAdd
        BigInteger previousUserBalance = ownerDexTestFourthScoreClient.balanceOf(userAddress);
        BigInteger previousSecondUserBalance = ownerDexTestThirdScoreClient.balanceOf(userAddress);
        byte[] tokenDeposit = "{\"method\":\"_deposit\",\"params\":{\"none\":\"none\"}}".getBytes();

        this.mintAndTransferTestTokens(tokenDeposit);
        //add the pool of test token and sicx
        dexUserScoreClient.add(Address.fromString(dexTestThirdScoreAddress), Address.fromString(dexTestFourthScoreAddress), BigInteger.valueOf(50).multiply(EXA), BigInteger.valueOf(50).multiply(EXA), true);
        BigInteger poolId = dexUserScoreClient.getPoolId(Address.fromString(dexTestThirdScoreAddress), Address.fromString(dexTestFourthScoreAddress));
        Map<String, Object> poolStats = dexUserScoreClient.getPoolStats(poolId);
        assertNull(poolStats.get("name"));
        assertEquals(poolStats.get("base_token").toString(), dexTestThirdScoreAddress);
        assertEquals(poolStats.get("quote_token").toString(), dexTestFourthScoreAddress);
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
        assertEquals(previousUserBalance.add(BigInteger.valueOf(150).multiply(EXA)), ownerDexTestFourthScoreClient.balanceOf(userAddress));
        assertEquals(previousSecondUserBalance.add(BigInteger.valueOf(150).multiply(EXA)), ownerDexTestThirdScoreClient.balanceOf(userAddress));

        this.mintAndTransferTestTokens(tokenDeposit);

        dexUserScoreClient.add(Address.fromString(dexTestThirdScoreAddress), Address.fromString(dexTestFourthScoreAddress), BigInteger.valueOf(80).multiply(EXA), BigInteger.valueOf(60).multiply(EXA), true);

        // after lp is added to the pool, remaining balance is checked
        assertEquals(BigInteger.valueOf(290).multiply(EXA), ownerDexTestFourthScoreClient.balanceOf(userAddress));
        assertEquals(BigInteger.valueOf(290).multiply(EXA), ownerDexTestThirdScoreClient.balanceOf(userAddress));

        poolId = dexUserScoreClient.getPoolId(Address.fromString(dexTestThirdScoreAddress), Address.fromString(dexTestFourthScoreAddress));
        poolStats = dexUserScoreClient.getPoolStats(poolId);
        assertNull(poolStats.get("name"));
        assertEquals(poolStats.get("base_token").toString(), dexTestThirdScoreAddress);
        assertEquals(poolStats.get("quote_token").toString(), dexTestFourthScoreAddress);
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

    void mintAndTransferTestTokens(byte[] tokenDeposit){

        ownerDexTestThirdScoreClient.mintTo(userAddress, BigInteger.valueOf(200).multiply(EXA));
        ownerDexTestFourthScoreClient.mintTo(userAddress, BigInteger.valueOf(200).multiply(EXA));


        userDexTestThirdScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(190).multiply(EXA), tokenDeposit);
        userDexTestFourthScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(190).multiply(EXA), tokenDeposit);

        //check isQuoteCoinAllowed for test token if not added
        if(!dexUserScoreClient.isQuoteCoinAllowed(Address.fromString(dexTestThirdScoreAddress))) {
            governanceDexScoreClient.dexAddQuoteCoin(Address.fromString(dexTestThirdScoreAddress));
        }
        if(!dexUserScoreClient.isQuoteCoinAllowed(Address.fromString(dexTestFourthScoreAddress))) {
            governanceDexScoreClient.dexAddQuoteCoin(Address.fromString(dexTestFourthScoreAddress));
        }
    }

    BigInteger hexToBigInteger(String hex){
        return new BigInteger(hex.replace("0x", ""), 16);
    }


}
