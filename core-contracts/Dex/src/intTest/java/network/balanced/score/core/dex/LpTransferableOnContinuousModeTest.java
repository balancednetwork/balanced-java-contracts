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

public class LpTransferableOnContinuousModeTest {

    static Env.Chain chain = Env.getDefaultChain();
    static Balanced balanced;
    static Wallet userWallet;
    static Wallet tUserWallet;
    static Wallet testOwnerWallet = KeyWallet.load(new Bytes("573b555367d6734ea0fecd0653ba02659fa19f7dc6ee5b93ec781350bda27376"));
    static DefaultScoreClient dexScoreClient ;
    static DefaultScoreClient governanceScoreClient;
    static DefaultScoreClient dexTestBaseScoreClient;
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
            dexTestBaseScoreClient =  _deploy(chain.getEndpointURL(), chain.networkId, testOwnerWallet, jarfile.getPath(), Map.of("name", "Test Base Token", "symbol", "TB") );
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

    static String dexTestBaseScoreAddress = dexTestBaseScoreClient._address().toString();
    static String dexTestFourthScoreAddress = dexTestFourthScoreClient._address().toString();

    static foundation.icon.jsonrpc.Address userAddress = DefaultScoreClient.address(userWallet.getAddress().toString());
    static foundation.icon.jsonrpc.Address tUserAddress = DefaultScoreClient.address(tUserWallet.getAddress().toString());

    @ScoreClient
    static DexTest ownerDexTestBaseScoreClient = new DexTestScoreClient(chain.getEndpointURL(), chain.networkId, testOwnerWallet, DefaultScoreClient.address(dexTestBaseScoreAddress));;
    static DexTest ownerDexTestFourthScoreClient = new DexTestScoreClient(chain.getEndpointURL(), chain.networkId, testOwnerWallet, DefaultScoreClient.address(dexTestFourthScoreAddress));;
    @ScoreClient
    static Dex dexUserScoreClient = new DexScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            dexScoreClient._address());

    static DexTest userDexTestBaseScoreClient = new DexTestScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            DefaultScoreClient.address(dexTestBaseScoreAddress));
    static DexTest userDexTestFourthScoreClient = new DexTestScoreClient(dexScoreClient.endpoint(), dexScoreClient._nid(), userWallet,
            DefaultScoreClient.address(dexTestFourthScoreAddress));
    @ScoreClient
    static Governance governanceDexScoreClient = new GovernanceScoreClient(governanceScoreClient);



    @Test
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
    }

    void mintAndTransferTestTokens(byte[] tokenDeposit){

        ownerDexTestBaseScoreClient.mintTo(userAddress, BigInteger.valueOf(200).multiply(EXA));
        ownerDexTestFourthScoreClient.mintTo(userAddress, BigInteger.valueOf(200).multiply(EXA));


        //deposit base token
        userDexTestBaseScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(190).multiply(EXA), tokenDeposit);
        //deposit quote token
        userDexTestFourthScoreClient.transfer(dexScoreClient._address(), BigInteger.valueOf(190).multiply(EXA), tokenDeposit);

        //check isQuoteCoinAllowed for test token if not added

        if(!dexUserScoreClient.isQuoteCoinAllowed(Address.fromString(dexTestBaseScoreAddress))) {
            governanceDexScoreClient.dexAddQuoteCoin(Address.fromString(dexTestBaseScoreAddress));
        }
        if(!dexUserScoreClient.isQuoteCoinAllowed(Address.fromString(dexTestFourthScoreAddress))) {
            governanceDexScoreClient.dexAddQuoteCoin(Address.fromString(dexTestFourthScoreAddress));
        }
    }

    void waitForADay(){
        balanced.increaseDay(1);
    }
}
