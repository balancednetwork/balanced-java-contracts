package network.balanced.score.lib.utils;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import foundation.icon.xcall.NetworkAddress;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockBalanced;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Address;
import score.Context;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Constants.EXA;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

public class TokenTransferTest extends UnitTest {

    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private static final Account user = sm.createAccount();
    private static final Account contract = sm.createAccount();

    private static Score dummyScore;

    protected static MockedStatic<Context> contextMock;
    protected static MockedStatic<BalancedAddressManager> addressManagerMock;

    @BeforeAll
    public static void setup() throws Exception{
        dummyScore = sm.deploy(owner, DummyScore.class);
        MockBalanced mockBalanced = new MockBalanced(sm, owner);
        contextMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS);
        addressManagerMock = MockBalanced.addressManagerMock;
    }

    @Test
    public void crossTransfer(){
        // Arrange
        addressManagerMock.when(BalancedAddressManager::getXCall).thenReturn(contract.getAddress());
        addressManagerMock.when(BalancedAddressManager::getDaofund).thenReturn(contract.getAddress());
        addressManagerMock.when(BalancedAddressManager::getAssetManager).thenReturn(contract.getAddress());

        contextMock.when(() -> Context.call(any(Address.class), eq("getNetworkId"))).thenReturn("0x2.ICON");
        contextMock.when(Context::getAddress).thenReturn(contract.getAddress());
        contextMock.when(() -> Context.call(any(Address.class), eq("getXCallFeePermission"), any(Address.class), any(String.class))).thenReturn(true); //can withdraw true - crossTransfer
        contextMock.when(() -> Context.call(any(Address.class), eq("getNativeAssetAddress"), any(Address.class), any(String.class))).thenReturn(null);
        contextMock.when(() -> Context.call(any(Address.class), eq("claimXCallFee"), any(String.class), any(Boolean.class))).thenReturn(EXA);
        contextMock.when(() -> Context.call(any(Address.class), eq("claimXCallFee"), any(String.class), any(Boolean.class))).thenReturn(EXA);

        // Act
        try {
            TokenTransfer.transfer(dummyScore.getAddress(), new NetworkAddress("0x1.ETH", user.getAddress()).toString(), BigInteger.TWO);
        }catch (Exception ignore){}

        contextMock.when(() -> Context.call(any(BigInteger.class), any(Address.class), eq("crossTransfer"), any(String.class), any(BigInteger.class), any(byte[].class))).thenReturn(true);

        // Verify
        TokenTransfer.transfer(dummyScore.getAddress(), new NetworkAddress("0x1.ETH", user.getAddress()).toString(), BigInteger.TWO);

    }

    @Test
    public void hubTransfer(){
        // Arrange
        addressManagerMock.when(BalancedAddressManager::getXCall).thenReturn(contract.getAddress());
        addressManagerMock.when(BalancedAddressManager::getDaofund).thenReturn(contract.getAddress());
        addressManagerMock.when(BalancedAddressManager::getAssetManager).thenReturn(contract.getAddress());

        contextMock.when(() -> Context.call(any(Address.class), eq("getNetworkId"))).thenReturn("0x2.ICON");
        contextMock.when(Context::getAddress).thenReturn(contract.getAddress());
        contextMock.when(() -> Context.call(any(Address.class), eq("getXCallFeePermission"), any(Address.class), any(String.class))).thenReturn(false); //can withdraw false - causes hub transfer
        contextMock.when(() -> Context.call(any(Address.class), eq("getNativeAssetAddress"), any(Address.class), any(String.class))).thenReturn(null);
        contextMock.when(() -> Context.call(any(Address.class), eq("claimXCallFee"), any(String.class), any(Boolean.class))).thenReturn(EXA);

        // Act
        try {
            TokenTransfer.transfer(dummyScore.getAddress(), new NetworkAddress("0x1.ETH", user.getAddress()).toString(), BigInteger.TWO);
        }catch (Exception ignore){}

        contextMock.when(() -> Context.call(any(Address.class), eq("hubTransfer"), any(String.class), any(BigInteger.class), any(byte[].class))).thenReturn(true);

        // Verify
        TokenTransfer.transfer(dummyScore.getAddress(), new NetworkAddress("0x1.ETH", user.getAddress()).toString(), BigInteger.TWO);

    }

    @Test
    public void transfer(){
        // Arrange
        addressManagerMock.when(BalancedAddressManager::getXCall).thenReturn(contract.getAddress());
        addressManagerMock.when(BalancedAddressManager::getDaofund).thenReturn(contract.getAddress());
        addressManagerMock.when(BalancedAddressManager::getAssetManager).thenReturn(contract.getAddress());

        contextMock.when(() -> Context.call(any(Address.class), eq("getNetworkId"))).thenReturn("0x2.ICON");

        // Act
        try {
            TokenTransfer.transfer(dummyScore.getAddress(), new NetworkAddress("0x2.ICON", user.getAddress()).toString(), BigInteger.TWO); // native nid for transfer
        }catch (Exception ignore){}

        contextMock.when(() -> Context.call(any(Address.class), eq("transfer"), any(Address.class), any(BigInteger.class), any(byte[].class))).thenReturn(true);

        // Verify
        TokenTransfer.transfer(dummyScore.getAddress(), new NetworkAddress("0x2.ICON", user.getAddress()).toString(), BigInteger.TWO); // native nid for transfer


    }

    @Test
    public void withdrawTo(){
        // Arrange
        addressManagerMock.when(BalancedAddressManager::getXCall).thenReturn(contract.getAddress());
        addressManagerMock.when(BalancedAddressManager::getDaofund).thenReturn(contract.getAddress());
        addressManagerMock.when(BalancedAddressManager::getAssetManager).thenReturn(contract.getAddress());

        contextMock.when(() -> Context.call(any(Address.class), eq("getNetworkId"))).thenReturn("0x2.ICON");
        contextMock.when(Context::getAddress).thenReturn(contract.getAddress());
        contextMock.when(() -> Context.call(any(Address.class), eq("getXCallFeePermission"), any(Address.class), any(String.class))).thenReturn(true); //can withdraw false - causes hub transfer
        contextMock.when(() -> Context.call(any(Address.class), eq("getNativeAssetAddress"), any(Address.class), any(String.class))).thenReturn(contract.getAddress().toString()); // if exists on asset manager should call withdrawTo
        contextMock.when(() -> Context.call(any(Address.class), eq("claimXCallFee"), any(String.class), any(Boolean.class))).thenReturn(EXA);

        // Act
        try {
            TokenTransfer.transfer(dummyScore.getAddress(), new NetworkAddress("0x1.ETH", user.getAddress()).toString(), BigInteger.TWO);
        }catch (Exception ignore){}

        contextMock.when(() -> Context.call(any(BigInteger.class), any(Address.class), eq("withdrawTo"), any(Address.class), any(String.class), any(BigInteger.class))).thenReturn(true);

        // Verify
        TokenTransfer.transfer(dummyScore.getAddress(), new NetworkAddress("0x1.ETH", user.getAddress()).toString(), BigInteger.TWO);

    }

    @AfterAll
    public static void teardown() {
        if (contextMock != null) {
            contextMock.close();
            contextMock = null;
        }
        if (addressManagerMock != null) {
            addressManagerMock.close();
            addressManagerMock = null;
        }
    }

    public static class DummyScore {
        public DummyScore() {

        }
    }


}
