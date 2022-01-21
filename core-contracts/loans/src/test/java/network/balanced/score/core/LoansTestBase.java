package network.balanced.score.core;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import com.iconloop.score.token.irc2.IRC2Mintable;
import com.eclipsesource.json.Json;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;

import score.Context;
import score.Address;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import network.balanced.score.mock.DexMock;
import network.balanced.score.core.RewardsMock;
import network.balanced.score.core.StakingMock;
import network.balanced.score.core.ReserveMock;
import network.balanced.score.core.sICXMintBurn;
import network.balanced.score.core.bnUSDMintBurn;
import network.balanced.score.core.Loans;
import network.balanced.score.core.Constants;


class LoansTestsBase extends TestBase {
    // 2 second blockTime gives 1 day 43200 block
    protected static final Long DAY = 43200L;
    protected static final Long WEEK = 7 * DAY;

    protected static final ServiceManager sm = getServiceManager();

    protected final Account admin = sm.createAccount();
    protected Score loans;
    protected Score sicx;
    protected Score bnusd;
    protected Score dex;
    protected Score rewards;
    protected Score reserve;
    protected Score staking;
    protected Score governance;
    protected Loans loansSpy;

    // Loans score deployment settings.
    protected final ArrayList<Account> accounts = new ArrayList<>();
    protected final BigInteger MINT_AMOUNT = BigInteger.TEN.pow(40);
    protected final BigInteger EXA = BigInteger.TEN.pow(18);
    protected final BigInteger POINTS = BigInteger.valueOf(10000);

    // Sicx score deployment settings.
    protected static final String nameSicx = "Staked icx";
    protected static final String symbolSicx = "sICX";
    protected static final String nameBnusd = "Balanced usd";
    protected static final String symbolBnusd = "bnUSD";

    protected static final int tokenDecimals = 18;
    protected static final BigInteger initalaupplyTokens = BigInteger.TEN.pow(50);

    protected static final BigInteger initalsICXDexLiquidity = BigInteger.TEN.pow(24);
    protected static final BigInteger initalbnUSDDexLiquidity = BigInteger.TEN.pow(24).multiply(BigInteger.valueOf(2));

    protected void setupAccounts() {
        int numberOfAccounts = 10;
        for (int accountNumber = 0; accountNumber < numberOfAccounts; accountNumber++) {
            Account account = sm.createAccount();
            accounts.add(account);
            sicx.invoke(admin, "mintTo", account.getAddress(), MINT_AMOUNT);
        }
    }

    protected void setupDex() throws Exception{
        Account account = sm.createAccount();
        sicx.invoke(admin, "mintTo", account.getAddress(), initalsICXDexLiquidity);
        bnusd.invoke(admin, "mintTo", account.getAddress(), initalbnUSDDexLiquidity);  
        dex = sm.deploy(admin, DexMock.class, sicx.getAddress(), bnusd.getAddress());
        sicx.invoke(account, "transfer", dex.getAddress(), initalsICXDexLiquidity, new byte[0]);
        bnusd.invoke(account, "transfer", dex.getAddress(), initalbnUSDDexLiquidity, new byte[0]);
    }

    protected void takeLoanSICX(Account account, BigInteger collateral, int loan) {
        Map<String, Object> map = new HashMap<>();
        map.put("_asset", "bnUSD");
        map.put("_amount", loan);
        JSONObject data = new JSONObject(map);
        byte[] params = data.toString().getBytes();

        sicx.invoke(account, "transfer", loans.getAddress(), collateral, params);
    }
    
    protected void takeLoanICX(Account account, String asset, BigInteger collateral, BigInteger loan) {
        sm.call(account, collateral, loans.getAddress(), "depositAndBorrow", asset, loan, account.getAddress(), BigInteger.ZERO);
    }

    protected BigInteger calculateFee(BigInteger loan) {
        BigInteger feePercentage = (BigInteger)getParam("origination fee");
        return loan.multiply(feePercentage).divide(POINTS);
    }

    protected void verifyPosition(Address address, BigInteger collateral, BigInteger loan) {
        Map<String, Object> position = (Map<String, Object>)loans.call("getAccountPositions", address);
        assertEquals(loan, position.get("total_debt"));
        assertEquals(collateral, position.get("collateral"));
    }

    protected void verifySnapshot(int addNonZero, int removeFromNonzero, int preComputeIndex, BigInteger totalMiningDebt, int day, int miningCount) {
        Map<String, Object> snap = (Map<String, Object>) loans.call("getSnapshot", day);
        System.out.println(snap);
        assertEquals(addNonZero, snap.get("add_to_nonzero_count"));
        assertEquals(removeFromNonzero, snap.get("remove_from_nonzero_count"));
        assertEquals(preComputeIndex, snap.get("precompute_index"));
        assertEquals(totalMiningDebt, snap.get("total_mining_debt"));
        assertEquals(day, snap.get("snap_day"));
        assertEquals(miningCount, snap.get("mining_count"));
    }

    public void expectErrorMessage(Executable contractCall, String errorMessage) {
        AssertionError e = Assertions.assertThrows(AssertionError.class, contractCall);
        assertEquals(errorMessage, e.getMessage());
    }

    public void governanceCall(String method, Object... params) {
        governance.invoke(admin, "call", loans.getAddress(), method, params);
    }

    public Object getParam(String key) {
        Map<String, Object> params = (Map<String, Object>)loans.call("getParameters");
        return params.get(key);
    }

 
    public void setup() throws Exception {
        sicx = sm.deploy(admin, sICXMintBurn.class, nameSicx, symbolSicx, tokenDecimals, initalaupplyTokens);
        bnusd = sm.deploy(admin, bnUSDMintBurn.class, nameBnusd, symbolBnusd, tokenDecimals, initalaupplyTokens);

        governance = sm.deploy(admin, GovernanceMock.class);
        loans = sm.deploy(admin, Loans.class, governance.getAddress());
        loansSpy = (Loans) spy(loans.getInstance());
        loans.setInstance(loansSpy);

        setupAccounts();

        rewards = sm.deploy(admin, RewardsMock.class, loans.getAddress());
        staking = sm.deploy(admin, StakingMock.class, sicx.getAddress());
        reserve = sm.deploy(admin, ReserveMock.class, sicx.getAddress());

        setupDex();

        loans.invoke(admin, "setDex", dex.getAddress());
        loans.invoke(admin, "setDividends", admin.getAddress());
        loans.invoke(admin, "setReserve", admin.getAddress());
        loans.invoke(admin, "setRebalance", admin.getAddress());
        loans.invoke(admin, "setStaking", staking.getAddress());

        governanceCall("turnLoansOn");
        loans.invoke(admin, "setRewards", rewards.getAddress());
        loans.invoke(admin, "setDividends", rewards.getAddress());
        loans.invoke(admin, "setReserve", reserve.getAddress());
        governanceCall("setContinuousRewardsDay", Integer.MAX_VALUE);
        sicx.invoke(admin, "setMinter", staking.getAddress());
        bnusd.invoke(admin, "setMinter", loans.getAddress());
        loans.invoke(admin, "addAsset", bnusd.getAddress(), true, false);
        loans.invoke(admin, "addAsset", sicx.getAddress(), true, true);
    }
}