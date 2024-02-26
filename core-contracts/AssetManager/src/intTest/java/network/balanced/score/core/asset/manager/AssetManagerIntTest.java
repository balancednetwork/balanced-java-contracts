package network.balanced.score.core.asset.manager;

import com.eclipsesource.json.JsonArray;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.xcall.NetworkAddress;
import network.balanced.score.lib.interfaces.AssetManagerMessages;
import network.balanced.score.lib.interfaces.tokens.SpokeTokenScoreClient;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.Env;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import score.Address;

import java.io.File;
import java.math.BigInteger;
import java.util.Map;

import static foundation.icon.score.client.DefaultScoreClient._deploy;
import static network.balanced.score.lib.test.integration.BalancedUtils.createParameter;
import static network.balanced.score.lib.test.integration.BalancedUtils.createSingleTransaction;

public class AssetManagerIntTest {

    private static final Env.Chain chain = Env.getDefaultChain();
    private static Balanced balanced;
    private static BalancedClient owner;
    private static BalancedClient reader;
    private static BalancedClient user;

    public String INJ_NID = "0x4.INJ";
    String injAsset1Address = "inj1x3";

    NetworkAddress injSpoke = new NetworkAddress(INJ_NID, "inj1x1");
    private static DefaultScoreClient injLinkAsset;

    NetworkAddress ethAssetNativeAddress = new NetworkAddress(balanced.ETH_NID, balanced.ETH_TOKEN_ADDRESS);

    private static final File jarfile = new File("src/intTest/java/network/balanced/score/core/asset/manager/testjarfiles" +
            "/testToken.jar");

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
        injLinkAsset = _deploy(chain.getEndpointURL(), chain.networkId, balanced.owner, jarfile.getPath(),
                Map.of("name", "Link Token", "symbol", "LT"));
        NetworkAddress tokenNetworkAddress = new NetworkAddress(INJ_NID, injAsset1Address);

        Address linkTokenAddress = injLinkAsset._address();

        try{
            owner.governance.execute(createSingleTransaction(balanced.assetManager._address(), "linkToken",
                    new JsonArray().add(createParameter(tokenNetworkAddress.toString())).add(createParameter(linkTokenAddress))).toString());
        } catch (Exception e){
            System.out.println("add spoke manager first : "+e.getMessage());
        }

        owner.governance.execute(createSingleTransaction(balanced.assetManager._address(), "addSpokeManager",
                new JsonArray().add(createParameter(injSpoke.toString()))).toString());

        owner.governance.execute(createSingleTransaction(balanced.assetManager._address(), "linkToken",
                new JsonArray().add(createParameter(tokenNetworkAddress.toString())).add(createParameter(linkTokenAddress))).toString());

        try{
            owner.governance.execute(createSingleTransaction(balanced.assetManager._address(), "linkToken",
                    new JsonArray().add(createParameter(tokenNetworkAddress.toString())).add(createParameter(linkTokenAddress))).toString());
        } catch (Exception e){
            System.out.println("token already exists: "+e.getMessage());
        }

        Address assetAddress = user.assetManager.getAssetAddress(tokenNetworkAddress.toString());
        assert  assetAddress.equals(linkTokenAddress);

        String nativeAssetAddress = user.assetManager.getNativeAssetAddress(linkTokenAddress);
        assert  nativeAssetAddress.equals(tokenNetworkAddress.toString());

    }

    @Test
    @Order(2)
    void removeToken() {
        Address linkTokenAddress = injLinkAsset._address();
        owner.governance.execute(createSingleTransaction(balanced.assetManager._address(), "removeToken",
                new JsonArray().add(createParameter(linkTokenAddress))).toString());

        try{
            owner.governance.execute(createSingleTransaction(balanced.assetManager._address(), "linkToken",
                    new JsonArray().add(createParameter(linkTokenAddress))).toString());
        } catch (Exception e){
            System.out.println("token already exists: "+e.getMessage());
        }
    }

    @Test
    @Order(3)
    void getAssets() {
        Map<String, String> assets = user.assetManager.getAssets();
        System.out.println("assets count: "+assets.size());
        assert assets.size() == 2 ;
    }

    @Test
    @Order(4)
    void getSpokes() {
        String[] spokes = user.assetManager.getSpokes();
        System.out.println("assets count: "+spokes.length);
        assert spokes.length == 3 ;
    }

    @Test
    @Order(5)
    void deposit(){
        NetworkAddress ethAccount = new NetworkAddress(balanced.ETH_NID, "0x123");
        BigInteger amount = BigInteger.TEN;
        NetworkAddress nativeAssetAddress = new NetworkAddress(balanced.ETH_NID, balanced.ETH_TOKEN_ADDRESS);

        //setup client
        Address assetAddress = user.assetManager.getAssetAddress(nativeAssetAddress.toString());
        DefaultScoreClient defaultScoreClient = new SpokeTokenScoreClient(chain.getEndpointURL(), chain.networkId, chain.godWallet, (foundation.icon.jsonrpc.Address) assetAddress);
        SpokeTokenScoreClient spokeTokenScoreClient = new SpokeTokenScoreClient(defaultScoreClient);

        //check initial amount
        BigInteger balance = spokeTokenScoreClient.xBalanceOf(ethAccount.toString());
        assert balance.equals(amount);

        //deposit
        byte[] deposit = AssetManagerMessages.deposit(balanced.ETH_TOKEN_ADDRESS, ethAccount.account(), "", amount, new byte[0]);
        owner.xcall.sendCall(owner.assetManager._address(), new NetworkAddress(balanced.ETH_NID, balanced.ETH_ASSET_MANAGER).toString(), deposit);

        //verify deposited amount
        balance = spokeTokenScoreClient.xBalanceOf(ethAccount.toString());
        assert balance.equals(amount.add(amount));
    }

    @Test
    @Order(6)
    void xWithdraw(){
        NetworkAddress ethAccount = new NetworkAddress(balanced.ETH_NID, "0x123");
        BigInteger amount = BigInteger.TEN;
        NetworkAddress nativeAssetAddress = new NetworkAddress(balanced.ETH_NID, balanced.ETH_TOKEN_ADDRESS);

        //setup client
        Address assetAddress = user.assetManager.getAssetAddress(nativeAssetAddress.toString());
        DefaultScoreClient defaultScoreClient = new SpokeTokenScoreClient(chain.getEndpointURL(), chain.networkId, chain.godWallet, (foundation.icon.jsonrpc.Address) assetAddress);
        SpokeTokenScoreClient spokeTokenScoreClient = new SpokeTokenScoreClient(defaultScoreClient);

        //deposit
        byte[] deposit = AssetManagerMessages.deposit(balanced.ETH_TOKEN_ADDRESS, ethAccount.account(), "", amount, new byte[0]);
        owner.xcall.sendCall(owner.assetManager._address(), new NetworkAddress(balanced.ETH_NID, balanced.ETH_ASSET_MANAGER).toString(), deposit);

        //check initial amount
        BigInteger balance = spokeTokenScoreClient.xBalanceOf(ethAccount.toString());
        assert balance.equals(amount.add(amount));

        //withdraw
        owner.governance.execute(createSingleTransaction(balanced.daofund._address(), "setXCallFeePermission",
                new JsonArray().add(createParameter(balanced.assetManager._address())).add(createParameter(balanced.ETH_NID)).add(createParameter(true))).toString());
        byte[] withdraw = AssetManagerMessages.xWithdraw(assetAddress, amount);
        owner.xcall.sendCall(owner.assetManager._address(), ethAccount.toString(), withdraw);

        //verify deposited amount
        balance = spokeTokenScoreClient.xBalanceOf(ethAccount.toString());
        assert balance.equals(amount);
    }

    @Test
    @Order(7)
    void withdrawRollback(){
        NetworkAddress ethAccount = new NetworkAddress(balanced.ETH_NID, "0x123");
        BigInteger amount = BigInteger.TEN;
        NetworkAddress nativeAssetAddress = new NetworkAddress(balanced.ETH_NID, balanced.ETH_TOKEN_ADDRESS);

        //setup client
        Address assetAddress = user.assetManager.getAssetAddress(nativeAssetAddress.toString());
        DefaultScoreClient defaultScoreClient = new SpokeTokenScoreClient(chain.getEndpointURL(), chain.networkId, chain.godWallet, (foundation.icon.jsonrpc.Address) assetAddress);
        SpokeTokenScoreClient spokeTokenScoreClient = new SpokeTokenScoreClient(defaultScoreClient);

        //deposit
        byte[] deposit = AssetManagerMessages.deposit(balanced.ETH_TOKEN_ADDRESS, ethAccount.account(), "", amount, new byte[0]);
        owner.xcall.sendCall(owner.assetManager._address(), new NetworkAddress(balanced.ETH_NID, balanced.ETH_ASSET_MANAGER).toString(), deposit);

        //check initial amount
        BigInteger balance = spokeTokenScoreClient.xBalanceOf(ethAccount.toString());
        assert balance.equals(amount);

        //withdraw
        owner.governance.execute(createSingleTransaction(balanced.daofund._address(), "setXCallFeePermission",
                new JsonArray().add(createParameter(balanced.assetManager._address())).add(createParameter(balanced.ETH_NID)).add(createParameter(true))).toString());
        byte[] withdraw = AssetManagerMessages.xWithdraw(assetAddress, amount);
        owner.xcall.sendCall(owner.assetManager._address(), ethAccount.toString(), withdraw);

        //verify deposited amount
        balance = spokeTokenScoreClient.xBalanceOf(ethAccount.toString());
        assert balance.equals(BigInteger.ZERO);


        //withdraw rollback
        owner.governance.execute(createSingleTransaction(balanced.daofund._address(), "setXCallFeePermission",
                new JsonArray().add(createParameter(balanced.assetManager._address())).add(createParameter(balanced.ETH_NID)).add(createParameter(true))).toString());
        byte[] withdrawRollback = AssetManagerMessages.withdrawRollback(nativeAssetAddress.toString(), ethAccount.toString(), amount);
        owner.xcall.sendCall(owner.assetManager._address(), new NetworkAddress(balanced.ICON_NID, owner.xcall._address()).toString(), withdrawRollback);

        //verify deposited amount
        balance = spokeTokenScoreClient.xBalanceOf(ethAccount.toString());
        assert balance.equals(amount);
    }

    @Test
    @Order(8)
    void withdrawTo() {
        NetworkAddress ethAccount = new NetworkAddress(balanced.ETH_NID, "0x123");
        BigInteger amount = BigInteger.TEN;
        NetworkAddress nativeAssetAddress = new NetworkAddress(balanced.ETH_NID, balanced.ETH_TOKEN_ADDRESS);
        Address assetAddress = user.assetManager.getAssetAddress(nativeAssetAddress.toString());
        DefaultScoreClient defaultScoreClient = new SpokeTokenScoreClient(chain.getEndpointURL(), chain.networkId, chain.godWallet, (foundation.icon.jsonrpc.Address) assetAddress);
        SpokeTokenScoreClient spokeTokenScoreClient = new SpokeTokenScoreClient(defaultScoreClient);

        //deposit amount to withdraw
        NetworkAddress iconAccount = new NetworkAddress(balanced.ICON_NID, user.getAddress());
        byte[] deposit = AssetManagerMessages.deposit(balanced.ETH_TOKEN_ADDRESS, ethAccount.account(), iconAccount.toString(), amount, new byte[0]);
        owner.xcall.sendCall(owner.assetManager._address(), new NetworkAddress(balanced.ETH_NID, balanced.ETH_ASSET_MANAGER).toString(), deposit);

        //validate deposited balance
        BigInteger balance = spokeTokenScoreClient.xBalanceOf(iconAccount.toString());
        assert balance.equals(amount);

        //withdraw the amount
        user.assetManager.withdrawTo(BigInteger.ONE, assetAddress, ethAccount.toString(), amount);

        //validate balance after withdraw
        balance = spokeTokenScoreClient.xBalanceOf(iconAccount.toString());
        assert balance.equals(BigInteger.ZERO);
    }


    //withdrawNativeTo method seems identical with withdrawTo except method name on icon side. integration test identical too
    @Test
    @Order(9)
    void withdrawNativeTo() {
        NetworkAddress ethAccount = new NetworkAddress(balanced.ETH_NID, "0x123");
        BigInteger amount = BigInteger.TEN;
        NetworkAddress nativeAssetAddress = new NetworkAddress(balanced.ETH_NID, balanced.ETH_TOKEN_ADDRESS);
        Address assetAddress = user.assetManager.getAssetAddress(nativeAssetAddress.toString());
        DefaultScoreClient defaultScoreClient = new SpokeTokenScoreClient(chain.getEndpointURL(), chain.networkId, chain.godWallet, (foundation.icon.jsonrpc.Address) assetAddress);
        SpokeTokenScoreClient spokeTokenScoreClient = new SpokeTokenScoreClient(defaultScoreClient);

        //deposit amount to withdraw
        NetworkAddress iconAccount = new NetworkAddress(balanced.ICON_NID, user.getAddress());
        byte[] deposit = AssetManagerMessages.deposit(balanced.ETH_TOKEN_ADDRESS, ethAccount.account(), iconAccount.toString(), amount, new byte[0]);
        owner.xcall.sendCall(owner.assetManager._address(), new NetworkAddress(balanced.ETH_NID, balanced.ETH_ASSET_MANAGER).toString(), deposit);

        //validate deposited balance
        BigInteger balance = spokeTokenScoreClient.xBalanceOf(iconAccount.toString());
        assert balance.equals(amount);

        //withdraw the amount
        user.assetManager.withdrawNativeTo(BigInteger.ONE, assetAddress, ethAccount.toString(), amount);

        //validate balance after withdraw
        balance = spokeTokenScoreClient.xBalanceOf(iconAccount.toString());
        assert balance.equals(BigInteger.ZERO);
    }


}
