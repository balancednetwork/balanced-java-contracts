package network.balanced.score.tokens.sicx;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.mockito.Mockito.*;


import score.Address;
import score.Context;


import org.junit.jupiter.api.function.Executable;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class testSicxImpl extends TestBase {

    public static ServiceManager sm = getServiceManager();
    public static final Account owner = sm.createAccount();
    public static final Account staking = Account.newScoreAccount(90);
    public static final Account scoreAddress = Account.newScoreAccount(20);
    public static final Account user = sm.createAccount();
    public static Score sicxScore;

    private static final MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS);

    static String zero = "0";
    final protected static Address EOA_ZERO = Address.fromString("hx" + zero.repeat(40));

    static MockedStatic.Verification getTodayRate = () -> Context.call(staking.getAddress(), "getTodayRate");

    static MockedStatic.Verification tokenFallback = () -> Context.call(any(Address.class), eq("tokenFallback"), any(Address.class), any(BigInteger.class), any(byte[].class));

    static MockedStatic.Verification transferUpdateDelegations = () -> Context.call(any(Address.class), eq("transferUpdateDelegations"), any(Address.class), any(Address.class), any(BigInteger.class));


    @BeforeEach
    void setup() throws Exception {
        sicxScore = sm.deploy(owner, SicxImpl.class, staking.getAddress());
        assertEquals(
                staking.getAddress(),
                sicxScore.call("getStakingAddress")
        );
        sicxScore.invoke(owner, "setAdmin", staking.getAddress());
    }

    @Test
    void testName() {
        assertEquals(
                "Staked ICX",
                sicxScore.call("name")
        );
    }

    @Test
    void testSupply() {
        assertEquals(
                new BigInteger("0"),
                sicxScore.call("totalSupply")
        );
    }

    @Test
    void testSymbol() {
        assertEquals(
                "sICX",
                sicxScore.call("symbol")
        );
    }

    @Test
    void testDecimals() {
        assertEquals(
                BigInteger.valueOf(18L),
                sicxScore.call("decimals")
        );
    }

    @Test
    void testAdmin() {
        // test initial admin setup
        assertEquals(
                staking.getAddress(),
                sicxScore.call("getAdmin")
        );

        // set user as admin
        sicxScore.invoke(owner, "setAdmin", user.getAddress());
        assertEquals(
                user.getAddress(),
                sicxScore.call("getAdmin")
        );
    }

    @Test
    void testStakingAddress() {
        // setStakingAddress not called by owner
        Executable InvalidValue = () -> sicxScore.invoke(user, "setStakingAddress", staking.getAddress());
        String expectedErrorMessage = "SenderNotScoreOwner: Sender=" + user.getAddress() + "Owner=" + owner.getAddress();
        AssertionError e = Assertions.assertThrows(AssertionError.class, InvalidValue);
        assertEquals(expectedErrorMessage, e.getMessage());
        // setStakingAddress called by owner
        sicxScore.invoke(owner, "setStakingAddress", staking.getAddress());
        assertEquals(
                staking.getAddress(),
                sicxScore.call("getStakingAddress")
        );
    }

    @Test
    void testGetPeg() {
        assertEquals(
                "sICX",
                sicxScore.call("getPeg")
        );
    }

    @Test
    void testPriceInLoop() {
        contextMock.when(getTodayRate).thenReturn(BigInteger.ONE);
        assertEquals(
                BigInteger.ONE,
                sicxScore.call("priceInLoop")
        );
    }

    @Test
    void testLastPriceInLoop() {
        contextMock.when(getTodayRate).thenReturn(BigInteger.ONE);
        assertEquals(
                BigInteger.ONE,
                sicxScore.call("lastPriceInLoop")
        );
    }

    @Test
    void testBurn() {
        // trying to burn 0 sICX
        Executable InvalidValue = () -> sicxScore.invoke(staking, "burn", new BigInteger("0"));
        String expectedErrorMessage = "Invalid Value";
        AssertionError e = Assertions.assertThrows(AssertionError.class, InvalidValue);
        String errorMessage = e.getMessage().substring(13);
        assertEquals(expectedErrorMessage, errorMessage);

        // sender not admin
        InvalidValue = () -> sicxScore.invoke(owner, "burn", new BigInteger("0"));
        expectedErrorMessage = "Authorization Check: Authorization failed. Caller: " + owner.getAddress() + " Authorized Caller: "+ staking.getAddress();
        e = Assertions.assertThrows(AssertionError.class, InvalidValue);
        errorMessage = e.getMessage();
        assertEquals(expectedErrorMessage, errorMessage);

        // condition where totalSupply is less than 0
        InvalidValue = () -> sicxScore.invoke(staking, "burn", new BigInteger("1000"));
        expectedErrorMessage = "Total Supply can not be set to negative";
        e = Assertions.assertThrows(AssertionError.class, InvalidValue);
        errorMessage = e.getMessage().substring(13);
        assertEquals(expectedErrorMessage, errorMessage);

        // mint 1000 sicx for owner
        sicxScore.invoke(staking, "mintTo", owner.getAddress(), new BigInteger("1000"), "".getBytes());

        //mint 100 sicx for stakingAddress
        contextMock.when(tokenFallback).thenReturn(null);
        sicxScore.invoke(staking, "mintTo", staking.getAddress(), new BigInteger("100"), "data".getBytes());
        contextMock.verify(tokenFallback);

        // condition where userBalance is less than 0
        InvalidValue = () -> sicxScore.invoke(staking, "burn", new BigInteger("1000"));
        expectedErrorMessage = "User Balance can not be set to negative";
        e = Assertions.assertThrows(AssertionError.class, InvalidValue);
        errorMessage = e.getMessage().substring(13);
        assertEquals(expectedErrorMessage, errorMessage);

        // invoke real burn method
        sicxScore.invoke(staking, "burn", new BigInteger("50"));
        assertEquals(new BigInteger("1050"), sicxScore.call("totalSupply"));
        assertEquals(new BigInteger("1000"), sicxScore.call("balanceOf", owner.getAddress()));
        assertEquals(new BigInteger("50"), sicxScore.call("balanceOf", staking.getAddress()));
    }

    @Test
    void testBurnFrom() {
        // trying to burn 0 sICX
        Executable InvalidValue = () -> sicxScore.invoke(staking, "burnFrom", user.getAddress(), new BigInteger("0"));
        String expectedErrorMessage = "Invalid Value";
        AssertionError e = Assertions.assertThrows(AssertionError.class, InvalidValue);
        String errorMessage = e.getMessage().substring(13);
        assertEquals(expectedErrorMessage, errorMessage);

        // sender not admin
        InvalidValue = () -> sicxScore.invoke(owner, "burnFrom", user.getAddress(), new BigInteger("0"));
        expectedErrorMessage = "Authorization Check: Authorization failed. Caller: " + owner.getAddress() + " Authorized Caller: "+ staking.getAddress();
        e = Assertions.assertThrows(AssertionError.class, InvalidValue);
        errorMessage = e.getMessage();
        assertEquals(expectedErrorMessage, errorMessage);


        // condition where totalSupply is less than 0
        InvalidValue = () -> sicxScore.invoke(staking, "burnFrom", owner.getAddress(), new BigInteger("1000"));
        expectedErrorMessage = "Total Supply can not be set to negative";
        e = Assertions.assertThrows(AssertionError.class, InvalidValue);
        errorMessage = e.getMessage().substring(13);
        assertEquals(expectedErrorMessage, errorMessage);

        // mint 1000 sicx for owner
        sicxScore.invoke(staking, "mintTo", owner.getAddress(), new BigInteger("1000"), "".getBytes());

        //mint 100 sicx for stakingAddress

        contextMock.when(tokenFallback).thenReturn(null);
        sicxScore.invoke(staking, "mintTo", staking.getAddress(), new BigInteger("100"), "data".getBytes());
//        contextMock.verify(tokenFallback, times(3));
        // condition where userBalance is less than 0
        InvalidValue = () -> sicxScore.invoke(staking, "burnFrom", staking.getAddress(), new BigInteger("1000"));
        expectedErrorMessage = "User Balance can not be set to negative";
        e = Assertions.assertThrows(AssertionError.class, InvalidValue);
        errorMessage = e.getMessage().substring(13);
        assertEquals(expectedErrorMessage, errorMessage);

        // invoke real burnFrom method
        sicxScore.invoke(staking, "burnFrom", owner.getAddress(), new BigInteger("50"));
        assertEquals(new BigInteger("1050"), sicxScore.call("totalSupply"));
        assertEquals(new BigInteger("950"), sicxScore.call("balanceOf", owner.getAddress()));
        assertEquals(new BigInteger("100"), sicxScore.call("balanceOf", staking.getAddress()));
    }

    @Test
    void testMint() {
        // trying to mint 0 sICX
        String data = "";
        Executable InvalidValue = () -> sicxScore.invoke(staking, "mint", new BigInteger("0"), data.getBytes());
        String expectedErrorMessage = "Invalid Value";
        AssertionError e = Assertions.assertThrows(AssertionError.class, InvalidValue);
        String errorMessage = e.getMessage().substring(13);
        assertEquals(expectedErrorMessage, errorMessage);

        // sender not admin
        InvalidValue = () -> sicxScore.invoke(owner, "mint", new BigInteger("0"), data.getBytes());
        expectedErrorMessage = "Authorization Check: Authorization failed. Caller: " + owner.getAddress() + " Authorized Caller: "+ staking.getAddress();
        e = Assertions.assertThrows(AssertionError.class, InvalidValue);
        errorMessage = e.getMessage();
        assertEquals(expectedErrorMessage, errorMessage);

        // invoke real method

        contextMock.when(tokenFallback).thenReturn(null);
        sicxScore.invoke(staking, "mint", new BigInteger("1000"), data.getBytes());
//        contextMock.verify(tokenFallback, times(2));
        assertEquals(new BigInteger("1000"), sicxScore.call("totalSupply"));
        assertEquals(new BigInteger("1000"), sicxScore.call("balanceOf", staking.getAddress()));
    }

    @Test
    void testMintTo() {
        // trying to mint 0 sICX
        String data = "";
        Executable InvalidValue = () -> sicxScore.invoke(staking, "mintTo", user.getAddress(), new BigInteger("0"), data.getBytes());
        String expectedErrorMessage = "Invalid Value";
        AssertionError e = Assertions.assertThrows(AssertionError.class, InvalidValue);
        String errorMessage = e.getMessage().substring(13);
        assertEquals(expectedErrorMessage, errorMessage);

        // sender not admin
        InvalidValue = () -> sicxScore.invoke(owner, "mintTo", user.getAddress(), new BigInteger("0"), data.getBytes());
        expectedErrorMessage = "Authorization Check: Authorization failed. Caller: " + owner.getAddress() + " Authorized Caller: "+ staking.getAddress();
        e = Assertions.assertThrows(AssertionError.class, InvalidValue);
        errorMessage = e.getMessage();
        assertEquals(expectedErrorMessage, errorMessage);

//        // invoke real method
        sicxScore.invoke(staking, "mintTo", user.getAddress(), new BigInteger("1000"), data.getBytes());
        assertEquals(new BigInteger("1000"), sicxScore.call("totalSupply"));
        assertEquals(new BigInteger("1000"), sicxScore.call("balanceOf", user.getAddress()));
        contextMock.when(tokenFallback).thenReturn(null);
        sicxScore.invoke(staking, "mintTo", scoreAddress.getAddress(), new BigInteger("1000"), "data".getBytes());
        contextMock.verify(tokenFallback, times(1));
    }

    @Test
    void testTransfer() {
        // trying to transfer 0 sICX
        String data = "";
        Executable InvalidValue = () -> sicxScore.invoke(staking, "transfer", staking.getAddress(), new BigInteger("0"), data.getBytes());
        String expectedErrorMessage = "Staked ICX: _value needs to be positive";
        AssertionError e = Assertions.assertThrows(AssertionError.class, InvalidValue);
        String errorMessage = e.getMessage();
        assertEquals(expectedErrorMessage, errorMessage);

        // trying to transfer sicx more than balance
        sicxScore.invoke(staking, "mintTo", user.getAddress(), new BigInteger("50"), data.getBytes());
        InvalidValue = () -> sicxScore.invoke(user, "transfer", staking.getAddress(), new BigInteger("60"), data.getBytes());
        expectedErrorMessage = "Staked ICX: Insufficient balance";
        e = Assertions.assertThrows(AssertionError.class, InvalidValue);
        errorMessage = e.getMessage();
        assertEquals(expectedErrorMessage, errorMessage);

        // transfer amount to another user
        sicxScore.invoke(staking, "mintTo", user.getAddress(), new BigInteger("50"), data.getBytes());
        contextMock.when(transferUpdateDelegations).thenReturn(null);
        sicxScore.invoke(user, "transfer", owner.getAddress(), new BigInteger("30"), data.getBytes());
        contextMock.verify(transferUpdateDelegations);
        assertEquals(new BigInteger("30"), sicxScore.call("balanceOf", owner.getAddress()));
        assertEquals(new BigInteger("70"), sicxScore.call("balanceOf", user.getAddress()));

        // transferring sICX to contract address
        contextMock.when(tokenFallback).thenReturn(null);
        sicxScore.invoke(user, "transfer", scoreAddress.getAddress(), new BigInteger("10"), "data".getBytes());
        contextMock.verify(tokenFallback, times(1));
    }
}
