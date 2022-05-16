package network.balanced.score.core.dex;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;

import score.Context;
import score.Address;
import score.annotation.Optional;

import org.mockito.Mockito;
import org.mockito.MockedStatic;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import org.mockito.stubbing.Answer;

import java.awt.List;
import java.math.BigInteger;
import java.util.Map;
import java.util.HashMap;

import static network.balanced.score.lib.utils.Constants.*;
import network.balanced.score.lib.test.UnitTest;

class DexTestBase extends UnitTest {
    protected static final ServiceManager sm = getServiceManager();
    protected static Account ownerAccount = sm.createAccount();
    protected static Account adminAccount = sm.createAccount();

    int scoreCount = 0;
    protected final Account governanceScore = Account.newScoreAccount(scoreCount++);
    protected final Account dividendsScore = Account.newScoreAccount(scoreCount++);
    protected final Account stakingScore = Account.newScoreAccount(scoreCount++);
    protected final Account rewardsScore = Account.newScoreAccount(scoreCount++);
    protected final Account bnusdScore = Account.newScoreAccount(scoreCount++);
    protected final Account balnScore = Account.newScoreAccount(scoreCount++);
    protected final Account sicxScore = Account.newScoreAccount(scoreCount++);
    protected final Account feehandlerScore = Account.newScoreAccount(scoreCount++);
    protected final Account stakedLPScore = Account.newScoreAccount(scoreCount++);

    public static Score dexScore;
    public static DexImpl dexScoreSpy;

    protected final MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS);

    public void setup() throws Exception {
        dexScore = sm.deploy(ownerAccount, DexImpl.class, governanceScore.getAddress());
        dexScore.invoke(governanceScore, "setTimeOffset", BigInteger.valueOf(Context.getBlockTimestamp()));
        dexScoreSpy = (DexImpl) spy(dexScore.getInstance());
        dexScore.setInstance(dexScoreSpy);
    }

    protected void turnDexOn() {
        dexScore.invoke(governanceScore, "turnDexOn");
    }

    protected void setupAddresses() {
        dexScore.invoke(governanceScore, "setAdmin", adminAccount.getAddress());

        Map<String, Address> addresses = Map.of(
            "setDividends", dividendsScore.getAddress(),
            "setStaking", stakingScore.getAddress(),
            "setRewards", rewardsScore.getAddress(),
            "setbnUSD", bnusdScore.getAddress(),
            "setBaln", balnScore.getAddress(),
            "setSicx", sicxScore.getAddress(),
            "setFeehandler", feehandlerScore.getAddress(),
            "setStakedLp", stakedLPScore.getAddress()
        );
        
        for (Map.Entry<String, Address> address : addresses.entrySet()) {
            dexScore.invoke(adminAccount, address.getKey(), address.getValue());
        }
    }

    protected void depositToken(Account depositor, Account tokenScore, BigInteger value) {
        setupAddresses();
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
        dexScore.invoke(tokenScore, "tokenFallback", depositor.getAddress(), value, tokenData("_deposit", new HashMap<>()));
    }

    protected void supplyLiquidity(Account supplier, Account baseTokenScore, Account quoteTokenScore, 
                                 BigInteger baseValue, BigInteger quoteValue, @Optional boolean withdrawUnused) {
        // Configure dex.
        turnDexOn();
        dexScore.invoke(governanceScore, "addQuoteCoin", quoteTokenScore.getAddress());

        // Mock these cross-contract calls.
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));

        // Deposit tokens and supply liquidity.
        dexScore.invoke(baseTokenScore, "tokenFallback", supplier.getAddress(), baseValue, tokenData("_deposit", new HashMap<>()));
        dexScore.invoke(quoteTokenScore, "tokenFallback", supplier.getAddress(), quoteValue, tokenData("_deposit", new HashMap<>()));
        dexScore.invoke(supplier, "add", baseTokenScore.getAddress(), quoteTokenScore.getAddress(), baseValue, quoteValue, withdrawUnused);
    }

    protected BigInteger computePrice(BigInteger tokenAValue, BigInteger tokenBValue) {
        return (tokenAValue.multiply(EXA)).divide(tokenBValue);
    }

    protected void supplyIcxLiquidity(Account supplier, BigInteger value) {
        contextMock.when(() -> Context.getValue()).thenReturn(value);
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("updateBatchRewardsData"), any(String.class), any(BigInteger.class), any())).thenReturn(null);
        supplier.addBalance("ICX", value);
        sm.transfer(supplier, dexScore.getAddress(), value);
    }


    // Not done yet.
    protected void swapSicxToIcx(Account sender, BigInteger value, BigInteger sicxIcxConversionRate) {
        turnDexOn();
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        doReturn(sicxIcxConversionRate).when(dexScoreSpy).getSicxRate();
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("updateBatchRewardsData"), eq("sICX/ICX"), eq(BigInteger.class), any(List.class))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(sicxScore.getAddress()), eq("transfer"), eq(feehandlerScore.getAddress()), any(BigInteger.class))).thenReturn(true);
        contextMock.when(() -> Context.transfer(eq(sender.getAddress()), any(BigInteger.class))).thenAnswer((Answer<Void>) invocation -> null);
        dexScore.invoke(sicxScore, "tokenFallback", sender.getAddress(), value, tokenData("_swap_icx", new HashMap<>()));  
    }

    protected void mockCall(Account score, String method, Object returnValue, Object... params) {
        contextMock.when(() -> Context.call(eq(score.getAddress()), eq(method), params)).thenReturn(returnValue);
    }

    protected void verifyCall(Account score, String method, Object... params) {
        contextMock.verify(() -> Context.call(eq(score.getAddress()), eq(method), params));
    }

}