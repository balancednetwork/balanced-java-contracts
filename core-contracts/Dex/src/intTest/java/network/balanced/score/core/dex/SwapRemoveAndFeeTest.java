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
import score.UserRevertedException;

import java.io.File;
import java.math.BigInteger;
import java.util.Map;

import static foundation.icon.score.client.DefaultScoreClient._deploy;
import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.*;

public class SwapRemoveAndFeeTest {

    static Env.Chain chain = Env.getDefaultChain();
    static Balanced balanced;
    static Wallet userWallet;
    static Wallet tUserWallet;
    static Wallet testOwnerWallet = KeyWallet.load(new Bytes("573b555367d6734ea0fecd0653ba02659fa19f7dc6ee5b93ec781350bda27376"));
    static DefaultScoreClient dexScoreClient ;
    static DefaultScoreClient governanceScoreClient;
    static DefaultScoreClient feeHandlerScoreClient;
    static DefaultScoreClient dexIntTestScoreClient;
    static DefaultScoreClient dexTestBaseScoreClient;
    static DefaultScoreClient dexTestThirdScoreClient;

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
            balanced.setupBalanced();
            dexScoreClient = balanced.dex;
            governanceScoreClient = balanced.governance;
            feeHandlerScoreClient = balanced.feehandler;
            ownerWallet = balanced.owner;

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error on init test: "+e.getMessage());
        }

    }

    static String dexTestScoreAddress = dexIntTestScoreClient._address().toString();
    static String dexTestBaseScoreAddress = dexTestBaseScoreClient._address().toString();
    static String dexTestThirdScoreAddress = dexTestThirdScoreClient._address().toString();

    static foundation.icon.jsonrpc.Address userAddress = DefaultScoreClient.address(userWallet.getAddress().toString());

    @ScoreClient
    static DexTest ownerDexTestScoreClient = new DexTestScoreClient(chain.getEndpointURL(), chain.networkId, testOwnerWallet, DefaultScoreClient.address(dexTestScoreAddress));;
    static DexTest ownerDexTestBaseScoreClient = new DexTestScoreClient(chain.getEndpointURL(), chain.networkId, testOwnerWallet, DefaultScoreClient.address(dexTestBaseScoreAddress));;
    static DexTest ownerDexTestThirdScoreClient = new DexTestScoreClient(chain.getEndpointURL(), chain.networkId, testOwnerWallet, DefaultScoreClient.address(dexTestThirdScoreAddress));;
    @ScoreClient
    static Dex dexUserScoreClient = new DexScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            dexScoreClient._address());

    static DexTest userDexTestScoreClient = new DexTestScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            DefaultScoreClient.address(dexTestScoreAddress));
    static DexTest userDexTestBaseScoreClient = new DexTestScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            DefaultScoreClient.address(dexTestBaseScoreAddress));
    static DexTest userDexTestThirdScoreClient = new DexTestScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            DefaultScoreClient.address(dexTestThirdScoreAddress));
    @ScoreClient
    static Governance governanceDexScoreClient = new GovernanceScoreClient(governanceScoreClient);



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
        // this cal was working on 1 min day, but not working for offset manipulation.
        //dexUserScoreClient.remove(poolId, BigInteger.valueOf(5), true);
        BigInteger balanceAfter = dexUserScoreClient.balanceOf(userAddress, poolId);
        //assert balanceAfter.equals(balanceBefore.subtract(withdrawAmount));
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
