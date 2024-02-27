package network.balanced.score.core.asset.manager;

import com.eclipsesource.json.JsonArray;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.RevertedException;
import foundation.icon.xcall.NetworkAddress;
import network.balanced.score.lib.interfaces.AssetManagerMessages;
import network.balanced.score.lib.interfaces.tokens.SpokeTokenScoreClient;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.Env;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import score.Address;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.lib.test.integration.BalancedUtils.createParameter;
import static network.balanced.score.lib.test.integration.BalancedUtils.createSingleTransaction;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AssetManagerIntTest {

    private static final Env.Chain chain = Env.getDefaultChain();
    private static Balanced balanced;
    private static BalancedClient owner;
    private static BalancedClient reader;
    private static BalancedClient user;

    public String INJ_NID = "0x4.INJ";
    String injAsset1Address = "inj1x3";

    NetworkAddress injSpoke = new NetworkAddress(INJ_NID, "inj1x1");

    @BeforeAll
    static void setup() throws Exception {
        balanced = new Balanced();
        balanced.setupBalanced();

        owner = balanced.ownerClient;
        reader = balanced.newClient(BigInteger.ZERO);
        user = balanced.newClient();
    }

    @Test
    @Order(1)
    void linkToken() {
        // Arrange
        NetworkAddress tokenNetworkAddress = new NetworkAddress(INJ_NID, injAsset1Address);
        Address linkTokenAddress = owner.sicx._address();


        //Act
        RevertedException thrown = assertThrows(
                RevertedException.class, () ->  owner.governance.execute(createSingleTransaction(balanced.assetManager._address(), "linkToken",
                    new JsonArray().add(createParameter(tokenNetworkAddress.toString())).add(createParameter(linkTokenAddress))).toString())
        );
        assertTrue(thrown.getMessage().contains("UnknownFailure"));

        owner.governance.execute(createSingleTransaction(balanced.assetManager._address(), "addSpokeManager",
                new JsonArray().add(createParameter(injSpoke.toString()))).toString());

        owner.governance.execute(createSingleTransaction(balanced.assetManager._address(), "linkToken",
                new JsonArray().add(createParameter(tokenNetworkAddress.toString())).add(createParameter(linkTokenAddress))).toString());

        thrown = assertThrows(
                RevertedException.class, () ->  owner.governance.execute(createSingleTransaction(balanced.assetManager._address(), "linkToken",
                    new JsonArray().add(createParameter(tokenNetworkAddress.toString())).add(createParameter(linkTokenAddress))).toString())
        );
        assertTrue(thrown.getMessage().contains("UnknownFailure"));

        //Assert
        Address assetAddress = user.assetManager.getAssetAddress(tokenNetworkAddress.toString());
        assert  assetAddress.equals(linkTokenAddress);

        List<String> nativeAssetAddresses = user.assetManager.getNativeAssetAddress(linkTokenAddress);
        assert nativeAssetAddresses.get(0).equals(tokenNetworkAddress.toString());

    }

    @Test
    @Order(2)
    void removeToken() {
        //Arrange
        Address linkTokenAddress = owner.sicx._address();

        //Act
        owner.governance.execute(createSingleTransaction(balanced.assetManager._address(), "removeToken",
                new JsonArray().add(createParameter(linkTokenAddress)).add(createParameter(INJ_NID))).toString());

        // Assert
        RevertedException thrown = assertThrows(
                RevertedException.class, () ->  owner.governance.execute(createSingleTransaction(balanced.assetManager._address(), "linkToken",
                    new JsonArray().add(createParameter(linkTokenAddress)).add(createParameter(INJ_NID))).toString())
        );
        assertTrue(thrown.getMessage().contains("UnknownFailure"));
    }

    @Test
    @Order(3)
    void getAssets() {
        //Act
        Map<String, String> assets = user.assetManager.getAssets();

        // Assert
        assert assets.size() == 3;
    }

    @Test
    @Order(4)
    void getSpokes() {
        // Act
        String[] spokes = user.assetManager.getSpokes();
        // Assert
        assert spokes.length == 3 ;
    }

    @Test
    @Order(5)
    void deposit(){
        //Arrange
        NetworkAddress ethAccount = new NetworkAddress(balanced.ETH_NID, "0x123");
        BigInteger amount = BigInteger.TEN;
        NetworkAddress nativeAssetAddress = new NetworkAddress(balanced.ETH_NID, balanced.ETH_TOKEN_ADDRESS);

        //setup client
        Address assetAddress = user.assetManager.getAssetAddress(nativeAssetAddress.toString());
        DefaultScoreClient defaultScoreClient = new SpokeTokenScoreClient(chain.getEndpointURL(), chain.networkId, chain.godWallet, (foundation.icon.jsonrpc.Address) assetAddress);
        SpokeTokenScoreClient spokeTokenScoreClient = new SpokeTokenScoreClient(defaultScoreClient);

        // Act
        //deposit
        byte[] deposit = AssetManagerMessages.deposit(balanced.ETH_TOKEN_ADDRESS, ethAccount.account(), "", amount, new byte[0]);
        owner.xcall.sendCall(owner.assetManager._address(), new NetworkAddress(balanced.ETH_NID, balanced.ETH_ASSET_MANAGER).toString(), deposit);

        // Assert
        //verify deposited amount
        BigInteger balance = spokeTokenScoreClient.xBalanceOf(ethAccount.toString());
        assert balance.equals(amount.add(amount));
    }

    @Test
    @Order(6)
    void xWithdraw(){
        // Arrange
        NetworkAddress ethAccount = new NetworkAddress(balanced.ETH_NID, "0x123");
        BigInteger amount = BigInteger.TEN;
        NetworkAddress nativeAssetAddress = new NetworkAddress(balanced.ETH_NID, balanced.ETH_TOKEN_ADDRESS);

        //setup client
        Address assetAddress = user.assetManager.getAssetAddress(nativeAssetAddress.toString());
        DefaultScoreClient defaultScoreClient = new SpokeTokenScoreClient(chain.getEndpointURL(), chain.networkId, chain.godWallet, (foundation.icon.jsonrpc.Address) assetAddress);
        SpokeTokenScoreClient spokeTokenScoreClient = new SpokeTokenScoreClient(defaultScoreClient);

        // Act
        //deposit
        byte[] deposit = AssetManagerMessages.deposit(balanced.ETH_TOKEN_ADDRESS, ethAccount.account(), "", amount, new byte[0]);
        owner.xcall.sendCall(owner.assetManager._address(), new NetworkAddress(balanced.ETH_NID, balanced.ETH_ASSET_MANAGER).toString(), deposit);

        //withdraw
        owner.governance.execute(createSingleTransaction(balanced.daofund._address(), "setXCallFeePermission",
                new JsonArray().add(createParameter(balanced.assetManager._address())).add(createParameter(balanced.ETH_NID)).add(createParameter(true))).toString());
        byte[] withdraw = AssetManagerMessages.xWithdraw(assetAddress, amount);
        owner.xcall.sendCall(owner.assetManager._address(), ethAccount.toString(), withdraw);

        // Assert
        //verify deposited amount
        BigInteger balance = spokeTokenScoreClient.xBalanceOf(ethAccount.toString());
        assert balance.equals(amount);
    }

    @Test
    @Order(7)
    void withdrawRollback(){
        // Arrange
        NetworkAddress ethAccount = new NetworkAddress(balanced.ETH_NID, "0x123");
        BigInteger amount = BigInteger.TEN;
        NetworkAddress nativeAssetAddress = new NetworkAddress(balanced.ETH_NID, balanced.ETH_TOKEN_ADDRESS);

        //setup client
        Address assetAddress = user.assetManager.getAssetAddress(nativeAssetAddress.toString());
        DefaultScoreClient defaultScoreClient = new SpokeTokenScoreClient(chain.getEndpointURL(), chain.networkId, chain.godWallet, (foundation.icon.jsonrpc.Address) assetAddress);
        SpokeTokenScoreClient spokeTokenScoreClient = new SpokeTokenScoreClient(defaultScoreClient);

        // Act
        //deposit
        byte[] deposit = AssetManagerMessages.deposit(balanced.ETH_TOKEN_ADDRESS, ethAccount.account(), "", amount, new byte[0]);
        owner.xcall.sendCall(owner.assetManager._address(), new NetworkAddress(balanced.ETH_NID, balanced.ETH_ASSET_MANAGER).toString(), deposit);

        //withdraw
        owner.governance.execute(createSingleTransaction(balanced.daofund._address(), "setXCallFeePermission",
                new JsonArray().add(createParameter(balanced.assetManager._address())).add(createParameter(balanced.ETH_NID)).add(createParameter(true))).toString());
        byte[] withdraw = AssetManagerMessages.xWithdraw(assetAddress, amount);
        owner.xcall.sendCall(owner.assetManager._address(), ethAccount.toString(), withdraw);

        //withdraw rollback
        owner.governance.execute(createSingleTransaction(balanced.daofund._address(), "setXCallFeePermission",
                new JsonArray().add(createParameter(balanced.assetManager._address())).add(createParameter(balanced.ETH_NID)).add(createParameter(true))).toString());
        byte[] withdrawRollback = AssetManagerMessages.withdrawRollback(nativeAssetAddress.toString(), ethAccount.toString(), amount);
        owner.xcall.sendCall(owner.assetManager._address(), new NetworkAddress(balanced.ICON_NID, owner.xcall._address()).toString(), withdrawRollback);

        // Assert
        //verify deposited amount
        BigInteger balance = spokeTokenScoreClient.xBalanceOf(ethAccount.toString());
        assert balance.equals(amount);
    }

    @Test
    @Order(8)
    void withdrawTo() {
        // Arrange
        NetworkAddress ethAccount = new NetworkAddress(balanced.ETH_NID, "0x123");
        BigInteger amount = BigInteger.TEN;
        NetworkAddress nativeAssetAddress = new NetworkAddress(balanced.ETH_NID, balanced.ETH_TOKEN_ADDRESS);
        Address assetAddress = user.assetManager.getAssetAddress(nativeAssetAddress.toString());
        DefaultScoreClient defaultScoreClient = new SpokeTokenScoreClient(chain.getEndpointURL(), chain.networkId, chain.godWallet, (foundation.icon.jsonrpc.Address) assetAddress);
        SpokeTokenScoreClient spokeTokenScoreClient = new SpokeTokenScoreClient(defaultScoreClient);
        NetworkAddress iconAccount = new NetworkAddress(balanced.ICON_NID, user.getAddress());

        // Act
        //deposit amount to withdraw
        byte[] deposit = AssetManagerMessages.deposit(balanced.ETH_TOKEN_ADDRESS, ethAccount.account(), iconAccount.toString(), amount, new byte[0]);
        owner.xcall.sendCall(owner.assetManager._address(), new NetworkAddress(balanced.ETH_NID, balanced.ETH_ASSET_MANAGER).toString(), deposit);
        //withdraw the amount
        user.assetManager.withdrawTo(BigInteger.ONE, assetAddress, ethAccount.toString(), amount);

        // Assert
        //validate balance after withdraw
        BigInteger balance = spokeTokenScoreClient.xBalanceOf(iconAccount.toString());
        assert balance.equals(BigInteger.ZERO);
    }


    //withdrawNativeTo method seems identical with withdrawTo except method name on icon side. integration test identical too
    @Test
    @Order(9)
    void withdrawNativeTo() {
        // Arrange
        NetworkAddress ethAccount = new NetworkAddress(balanced.ETH_NID, "0x123");
        BigInteger amount = BigInteger.TEN;
        NetworkAddress nativeAssetAddress = new NetworkAddress(balanced.ETH_NID, balanced.ETH_TOKEN_ADDRESS);
        Address assetAddress = user.assetManager.getAssetAddress(nativeAssetAddress.toString());
        DefaultScoreClient defaultScoreClient = new SpokeTokenScoreClient(chain.getEndpointURL(), chain.networkId, chain.godWallet, (foundation.icon.jsonrpc.Address) assetAddress);
        SpokeTokenScoreClient spokeTokenScoreClient = new SpokeTokenScoreClient(defaultScoreClient);
        NetworkAddress iconAccount = new NetworkAddress(balanced.ICON_NID, user.getAddress());

        // Act
        //deposit amount to withdraw
        byte[] deposit = AssetManagerMessages.deposit(balanced.ETH_TOKEN_ADDRESS, ethAccount.account(), iconAccount.toString(), amount, new byte[0]);
        owner.xcall.sendCall(owner.assetManager._address(), new NetworkAddress(balanced.ETH_NID, balanced.ETH_ASSET_MANAGER).toString(), deposit);
        //withdraw the amount
        user.assetManager.withdrawNativeTo(BigInteger.ONE, assetAddress, ethAccount.toString(), amount);

        // Assert
        BigInteger balance = spokeTokenScoreClient.xBalanceOf(iconAccount.toString());
        assert balance.equals(BigInteger.ZERO);
    }


}
