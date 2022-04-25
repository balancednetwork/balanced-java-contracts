package network.balanced.score.tokens.sicx;

import foundation.icon.icx.Wallet;
import org.json.JSONObject;
import org.junit.jupiter.api.Order;
import foundation.icon.jsonrpc.Address;
import network.balanced.score.lib.test.ScoreIntegrationTest;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.*;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SicxIntegrationTest implements ScoreIntegrationTest {
    static Wallet tester = ScoreIntegrationTest.getOrGenerateWallet(null);
    private static final Address testerAddress = Address.of(tester);

    static Wallet owner = ScoreIntegrationTest.getOrGenerateWallet(System.getProperties());
    private static final foundation.icon.jsonrpc.Address ownerAddress = foundation.icon.jsonrpc.Address.of(owner);

    static DefaultScoreClient sicxClient = DefaultScoreClient.of(System.getProperties(), Map.of("_admin", ownerAddress));

    static DefaultScoreClient stakingClient = DefaultScoreClient.of("staking.", System.getProperties());

    @ScoreClient
    static
    Staking stakingScore = new StakingScoreClient(stakingClient);

    @ScoreClient
    Sicx sicxScore = new SicxScoreClient(sicxClient);

    @Test
    @Order(1)
    void name() {
        System.setProperty("scoreFilePath", "../../token-contracts/sicx/build/libs/sicx-0.1.0-optimized.jar");
        System.setProperty("isUpdate", "true");
        System.setProperty("address", String.valueOf(sicxClient._address()));
        DefaultScoreClient.of(System.getProperties(), Map.of("_admin", ownerAddress));
        assertEquals("sICX", sicxScore.getPeg());
        stakingScore.toggleStakingOn();
        stakingScore.setSicxAddress(sicxClient._address());

    }

    @Test
    @Order(2)
    void getSymbol() {
        assertEquals("sICX", sicxScore.symbol());
    }

    @Test
    @Order(3)
    void setAndGetStaking() {
        sicxScore.setStaking(stakingClient._address());
        assertEquals(stakingClient._address(), sicxScore.getStaking());

    }

    @Test
    @Order(4)
    void setMinterAddress() {
        sicxScore.setMinter(stakingClient._address());
        assertEquals(stakingClient._address(), sicxScore.getMinter());
    }

    @Test
    @Order(5)
    void mint() {
        BigInteger value = new BigInteger("20000000000000000000");
        BigInteger previousSupply = sicxScore.totalSupply();
        BigInteger previousBalance = sicxScore.balanceOf(ownerAddress);
        ((StakingScoreClient) stakingScore).stakeICX(value, null
                , null);
        assertEquals(previousSupply.add(value), sicxScore.totalSupply());
        assertEquals(previousBalance.add(value), sicxScore.balanceOf(ownerAddress));
    }

    @Test
    @Order(6)
    void Transfer() {
        BigInteger value = new BigInteger("5000000000000000000");
        BigInteger previousSupply = sicxScore.totalSupply();
        BigInteger previousOwnerBalance = sicxScore.balanceOf(ownerAddress);
        BigInteger previousTesterBalance = sicxScore.balanceOf(testerAddress);
        sicxScore.transfer(testerAddress, value, null);
        assertEquals(previousSupply, sicxScore.totalSupply());
        assertEquals(previousTesterBalance.add(value), sicxScore.balanceOf(testerAddress));
        assertEquals(previousOwnerBalance.subtract(value), sicxScore.balanceOf(ownerAddress));
    }

    @Test
    @Order(7)
    void Burn() {
        BigInteger previousSupply = sicxScore.totalSupply();
        BigInteger previousOwnerBalance = sicxScore.balanceOf(ownerAddress);
        BigInteger previousTesterBalance = sicxScore.balanceOf(testerAddress);
        JSONObject data = new JSONObject();
        data.put("method", "unstake");
        BigInteger value = new BigInteger("10000000000000000000");
        sicxScore.transfer(stakingClient._address(), value, data.toString().getBytes());
        assertEquals(previousSupply.subtract(value), sicxScore.totalSupply());
        assertEquals(previousTesterBalance, sicxScore.balanceOf(testerAddress));
        assertEquals(previousOwnerBalance.subtract(value), sicxScore.balanceOf(ownerAddress));
    }

    @Test
    @Order(8)
    void mintTo() {
        sicxScore.setMinter(ownerAddress);
        BigInteger previousSupply = sicxScore.totalSupply();
        BigInteger previousOwnerBalance = sicxScore.balanceOf(ownerAddress);
        BigInteger previousTesterBalance = sicxScore.balanceOf(testerAddress);
        BigInteger value = new BigInteger("20000000000000000000");
        sicxScore.mintTo(testerAddress, value, null);
        assertEquals(previousSupply.add(value), sicxScore.totalSupply());
        assertEquals(previousTesterBalance.add(value), sicxScore.balanceOf(testerAddress));
        assertEquals(previousOwnerBalance, sicxScore.balanceOf(ownerAddress));
    }

    @Test
    @Order(9)
    void BurnFrom() {
        sicxScore.setMinter(ownerAddress);
        BigInteger previousSupply = sicxScore.totalSupply();
        BigInteger previousOwnerBalance = sicxScore.balanceOf(ownerAddress);
        BigInteger previousTesterBalance = sicxScore.balanceOf(testerAddress);
        BigInteger value = new BigInteger("10000000000000000000");
        sicxScore.burnFrom(testerAddress, value);
        assertEquals(previousSupply.subtract(value), sicxScore.totalSupply());
        assertEquals(previousTesterBalance.subtract(value), sicxScore.balanceOf(testerAddress));
        assertEquals(previousOwnerBalance, sicxScore.balanceOf(ownerAddress));
    }

}
