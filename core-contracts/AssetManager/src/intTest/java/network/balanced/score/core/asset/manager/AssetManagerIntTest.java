package network.balanced.score.core.asset.manager;

import com.eclipsesource.json.JsonArray;
import foundation.icon.score.client.RevertedException;
import foundation.icon.xcall.NetworkAddress;
import network.balanced.score.lib.interfaces.AssetManagerMessages;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;
import score.Address;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static java.math.BigInteger.ZERO;
import static network.balanced.score.lib.test.integration.BalancedUtils.createParameter;
import static network.balanced.score.lib.test.integration.BalancedUtils.createSingleTransaction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AssetManagerIntTest {

    private static Balanced balanced;
    private static BalancedClient owner;
    private static BalancedClient reader;
    private static BalancedClient user;

    public String INJ_NID = "0x4.INJ";
    public String LINK_NID = "0x4.LINK";
    String injAsset1Address = "inj1x3";
    String linkAsset1Address = "link1x3";

    NetworkAddress injSpoke = new NetworkAddress(INJ_NID, "inj1x1");
    NetworkAddress linkSpoke = new NetworkAddress(LINK_NID, "link1x1");

    @BeforeAll
    static void setup() throws Exception {
        balanced = new Balanced();
        balanced.setupBalanced();

        owner = balanced.ownerClient;
        reader = balanced.newClient(ZERO);
        user = balanced.newClient();
    }

    @Test
    @Order(1)
    void linkToken() {
        // Arrange
        NetworkAddress tokenNetworkAddress = new NetworkAddress(INJ_NID, injAsset1Address);
        Address linkTokenAddress = owner.sicx._address();

        //Act
        JsonArray params = new JsonArray().add(createParameter(tokenNetworkAddress.toString())).add(createParameter(linkTokenAddress));
        Executable linkTokenWithoutAssetManager = () -> owner.governance.execute(createSingleTransaction(balanced.assetManager._address(), "linkToken", params).toString());
        assertThrows(RevertedException.class, linkTokenWithoutAssetManager);

        owner.governance.execute(createSingleTransaction(balanced.assetManager._address(), "addSpokeManager", new JsonArray().add(createParameter(injSpoke.toString()))).toString());

        owner.governance.execute(createSingleTransaction(balanced.assetManager._address(), "linkToken", params).toString());

        Executable tokenNetworkAlreadyLinked = () -> owner.governance.execute(createSingleTransaction(balanced.assetManager._address(), "linkToken", params).toString());
        assertThrows(RevertedException.class, tokenNetworkAlreadyLinked);

        //Assert
        Address assetAddress = user.assetManager.getAssetAddress(tokenNetworkAddress.toString());
        assert assetAddress.equals(linkTokenAddress);

        List<String> nativeAssetAddresses = user.assetManager.getNativeAssetAddresses(linkTokenAddress);
        assert nativeAssetAddresses.get(0).equals(tokenNetworkAddress.toString());

    }

    @Test
    @Order(2)
    void withdrawTo() {
        // Arrange
        NetworkAddress ethAccount = new NetworkAddress(balanced.ETH_NID, "0x123");
        BigInteger amount = BigInteger.TEN;
        NetworkAddress nativeAssetAddress = new NetworkAddress(balanced.ETH_NID, balanced.ETH_TOKEN_ADDRESS);
        Address assetAddress = user.assetManager.getAssetAddress(nativeAssetAddress.toString());

        NetworkAddress iconAccount = new NetworkAddress(balanced.ICON_NID, user.getAddress());

        // Act
        byte[] deposit = AssetManagerMessages.deposit(balanced.ETH_TOKEN_ADDRESS, ethAccount.account(), iconAccount.toString(), amount, new byte[0]);
        owner.xcall.sendCall(owner.assetManager._address(), new NetworkAddress(balanced.ETH_NID, balanced.ETH_ASSET_MANAGER).toString(), deposit);
        user.assetManager.withdrawTo(BigInteger.ONE, assetAddress, ethAccount.toString(), amount);

        // Assert
        BigInteger balance = user.spokeToken(assetAddress).xBalanceOf(iconAccount.toString());
        assert balance.equals(BigInteger.ZERO);
    }

    @Test
    @Order(3)
    void withdrawRollback() {
        // Arrange
        NetworkAddress ethAccount = new NetworkAddress(balanced.ETH_NID, "0x123");
        BigInteger amount = BigInteger.TEN;
        NetworkAddress nativeAssetAddress = new NetworkAddress(balanced.ETH_NID, balanced.ETH_TOKEN_ADDRESS);

        Address assetAddress = user.assetManager.getAssetAddress(nativeAssetAddress.toString());
        BigInteger prevAssetDeposit = user.assetManager.getAssetDeposit(assetAddress, balanced.ETH_NID);

        // Act
        byte[] deposit = AssetManagerMessages.deposit(balanced.ETH_TOKEN_ADDRESS, ethAccount.account(), "", amount, new byte[0]);
        owner.xcall.sendCall(owner.assetManager._address(), new NetworkAddress(balanced.ETH_NID, balanced.ETH_ASSET_MANAGER).toString(), deposit);

        balanced.addXCallFeePermission(balanced.assetManager._address(), balanced.ETH_NID, true);
        byte[] withdraw = AssetManagerMessages.xWithdraw(assetAddress, amount);
        owner.xcall.sendCall(owner.assetManager._address(), ethAccount.toString(), withdraw);

        byte[] withdrawRollback = AssetManagerMessages.withdrawRollback(nativeAssetAddress.toString(), ethAccount.toString(), amount);
        owner.xcall.sendCall(owner.assetManager._address(), new NetworkAddress(balanced.ICON_NID, owner.xcall._address()).toString(), withdrawRollback);

        // Assert
        BigInteger assetDeposit = user.assetManager.getAssetDeposit(assetAddress, balanced.ETH_NID);
        assertEquals(assetDeposit, prevAssetDeposit.add(amount));
        BigInteger balance = user.spokeToken(assetAddress).xBalanceOf(ethAccount.toString());
        assert balance.equals(amount);
    }

    @Test
    @Order(4)
    void linkToken_multipleNetworkAddress() {
        // Arrange
        NetworkAddress tokenNetworkAddress = new NetworkAddress(INJ_NID, injAsset1Address);
        NetworkAddress nextTokenNetworkAddress = new NetworkAddress(LINK_NID, linkAsset1Address);
        Address linkTokenAddress = owner.sicx._address();

        // Act
        owner.governance.execute(createSingleTransaction(balanced.assetManager._address(), "addSpokeManager", new JsonArray().add(createParameter(linkSpoke.toString()))).toString());

        owner.governance.execute(createSingleTransaction(balanced.assetManager._address(), "linkToken", new JsonArray().add(createParameter(nextTokenNetworkAddress.toString())).add(createParameter(linkTokenAddress))).toString());

        // Assert
        Address assetAddress = user.assetManager.getAssetAddress(tokenNetworkAddress.toString());
        Address nextAssetAddress = user.assetManager.getAssetAddress(nextTokenNetworkAddress.toString());
        assert assetAddress.equals(linkTokenAddress);
        assert nextAssetAddress.equals(linkTokenAddress);

        List<String> nativeAssetAddresses = user.assetManager.getNativeAssetAddresses(linkTokenAddress);
        assert nativeAssetAddresses.get(0).equals(tokenNetworkAddress.toString());
        assert nativeAssetAddresses.get(1).equals(nextTokenNetworkAddress.toString());
    }

    @Test
    @Order(5)
    void removeToken() {
        //Arrange
        Address linkTokenAddress = owner.sicx._address();
        JsonArray params = new JsonArray().add(createParameter(linkTokenAddress)).add(createParameter(INJ_NID));

        //Act
        owner.governance.execute(createSingleTransaction(balanced.assetManager._address(), "removeToken", params).toString());

        // Assert
        Executable notAvailable = () -> owner.governance.execute(createSingleTransaction(balanced.assetManager._address(), "removeToken", params).toString());
        assertThrows(RevertedException.class, notAvailable);
    }

    @Test
    @Order(6)
    void getAssets() {
        //Act
        Map<String, String> assets = user.assetManager.getAssets();

        // Assert
        assert assets.size() == 3;
    }

    @Test
    @Order(7)
    void getSpokes() {
        // Act
        String[] spokes = user.assetManager.getSpokes();

        // Assert
        assert spokes.length == 4;
    }

    @Test
    @Order(8)
    void xWithdraw() {
        // Arrange
        NetworkAddress ethAccount = new NetworkAddress(balanced.ETH_NID, "0x123");
        BigInteger amount = BigInteger.TEN;
        NetworkAddress nativeAssetAddress = new NetworkAddress(balanced.ETH_NID, balanced.ETH_TOKEN_ADDRESS);

        Address assetAddress = user.assetManager.getAssetAddress(nativeAssetAddress.toString());
        BigInteger prevAssetDeposit = user.assetManager.getAssetDeposit(assetAddress, balanced.ETH_NID);

        // Act
        byte[] deposit = AssetManagerMessages.deposit(balanced.ETH_TOKEN_ADDRESS, ethAccount.account(), "", amount, new byte[0]);
        owner.xcall.sendCall(owner.assetManager._address(), new NetworkAddress(balanced.ETH_NID, balanced.ETH_ASSET_MANAGER).toString(), deposit);

        balanced.addXCallFeePermission(balanced.assetManager._address(), balanced.ETH_NID, true);
        byte[] withdraw = AssetManagerMessages.xWithdraw(assetAddress, amount);
        owner.xcall.sendCall(owner.assetManager._address(), ethAccount.toString(), withdraw);

        // Assert
        BigInteger assetDeposit = user.assetManager.getAssetDeposit(assetAddress, balanced.ETH_NID);
        assertEquals(assetDeposit, prevAssetDeposit);
        BigInteger balance = user.spokeToken(assetAddress).xBalanceOf(ethAccount.toString());
        assert balance.equals(amount);
    }

    @Test
    @Order(9)
    void deposit() {
        //Arrange
        NetworkAddress ethAccount = new NetworkAddress(balanced.ETH_NID, "0x123");
        BigInteger amount = BigInteger.TEN;
        NetworkAddress nativeAssetAddress = new NetworkAddress(balanced.ETH_NID, balanced.ETH_TOKEN_ADDRESS);

        Address assetAddress = user.assetManager.getAssetAddress(nativeAssetAddress.toString());
        BigInteger prevAssetDeposit = user.assetManager.getAssetDeposit(assetAddress, balanced.ETH_NID);

        // Act
        byte[] deposit = AssetManagerMessages.deposit(balanced.ETH_TOKEN_ADDRESS, ethAccount.account(), "", amount, new byte[0]);
        owner.xcall.sendCall(owner.assetManager._address(), new NetworkAddress(balanced.ETH_NID, balanced.ETH_ASSET_MANAGER).toString(), deposit);

        // Assert
        BigInteger assetDeposit = user.assetManager.getAssetDeposit(assetAddress, balanced.ETH_NID);
        assertEquals(assetDeposit, prevAssetDeposit.add(amount));
        BigInteger balance = user.spokeToken(assetAddress).xBalanceOf(ethAccount.toString());
        assert balance.equals(amount.add(amount));
    }

    @Test
    @Order(10)
    void deposit_limitDeposit(){
        //Arrange
        NetworkAddress ethAccount = new NetworkAddress(balanced.ETH_NID, "0x123");
        BigInteger amount = BigInteger.valueOf(1000000);
        NetworkAddress nativeAssetAddress = new NetworkAddress(balanced.ETH_NID, balanced.ETH_TOKEN_ADDRESS);

        Address assetAddress = user.assetManager.getAssetAddress(nativeAssetAddress.toString());

        // Act & Assert
        //pass
        byte[] deposit = AssetManagerMessages.deposit(balanced.ETH_TOKEN_ADDRESS, ethAccount.account(), "", amount, new byte[0]);
        owner.xcall.sendCall(owner.assetManager._address(), new NetworkAddress(balanced.ETH_NID, balanced.ETH_ASSET_MANAGER).toString(), deposit);

        JsonArray params = new JsonArray().add(createParameter(assetAddress)).add(createParameter(balanced.ETH_NID)).add(createParameter(amount.subtract(BigInteger.ONE)));
        owner.governance.execute(createSingleTransaction(balanced.assetManager._address(), "setAssetChainDepositLimit",
                params).toString());
        //fail
        Executable ethChainDepositLimitExceeded = () -> owner.xcall.sendCall(owner.assetManager._address(), new NetworkAddress(balanced.ETH_NID, balanced.ETH_ASSET_MANAGER).toString(), deposit);
        assertThrows(RevertedException.class,   ethChainDepositLimitExceeded);
    }


}
