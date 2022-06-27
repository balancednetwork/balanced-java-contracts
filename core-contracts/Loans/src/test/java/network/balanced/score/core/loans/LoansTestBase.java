/*
 * Copyright (c) 2022-2022 Balanced.network.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package network.balanced.score.core.loans;

import com.eclipsesource.json.JsonObject;
import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import network.balanced.score.core.loans.mocks.bnUSD.bnUSDMintBurn;
import network.balanced.score.core.loans.mocks.iETH.iETHMintBurn;
import network.balanced.score.core.loans.mocks.sICX.sICXMintBurn;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.structs.RewardsDataEntry;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockContract;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import score.Address;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static network.balanced.score.core.loans.utils.LoansConstants.Standings;
import static network.balanced.score.core.loans.utils.LoansConstants.StandingsMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class LoansTestBase extends UnitTest {
    // 2 second blockTime gives 1 day 43200 block
    protected static final Long DAY = 43200L;
    protected static final Long WEEK = 7 * DAY;
    protected static final String TAG = LoansImpl.TAG + ": ";
    protected static final ServiceManager sm = getServiceManager();

    protected final Account admin = sm.createAccount();
    protected final Account feehandler = Account.newScoreAccount(scoreCount++);
    protected final Account rebalancing = Account.newScoreAccount(scoreCount++);
    protected Score loans;
    protected Score sicx;
    protected Score bnusd;
    protected Score ieth;

    protected MockContract<Dex> dex;
    protected MockContract<Staking> staking;
    protected MockContract<Governance> governance;
    protected MockContract<Rewards> rewards;
    protected MockContract<Dividends> dividends;
    protected MockContract<Reserve> reserve;
    protected MockContract<BalancedOracle> balancedOracle;
    protected LoansImpl loansSpy;

    protected final ArrayList<Account> accounts = new ArrayList<>();
    protected final BigInteger MINT_AMOUNT = BigInteger.TEN.pow(40);
    protected final BigInteger EXA = BigInteger.TEN.pow(18);
    protected final BigInteger POINTS = BigInteger.valueOf(10000);

    protected static final String nameSicx = "Staked icx";
    protected static final String symbolSicx = "sICX";
    protected static final String nameBnusd = "Balanced usd";
    protected static final String symbolBnusd = "bnUSD";

    protected static final int tokenDecimals = 18;
    protected static final BigInteger initalaupplyTokens = BigInteger.TEN.pow(50);

    protected void setupAccounts() {
        int numberOfAccounts = 10;
        for (int accountNumber = 0; accountNumber < numberOfAccounts; accountNumber++) {
            Account account = sm.createAccount();
            accounts.add(account);
            sicx.invoke(admin, "mintTo", account.getAddress(), MINT_AMOUNT);
            ieth.invoke(admin, "mintTo", account.getAddress(), MINT_AMOUNT);
        }
    }

    private void setupDex() throws Exception {

        dex = new MockContract<Dex>(DexScoreInterface.class, sm, admin);
        sicx.invoke(admin, "mintTo", dex.getAddress(), MINT_AMOUNT);
        bnusd.invoke(admin, "mintTo", dex.getAddress(), MINT_AMOUNT);
        ieth.invoke(admin, "mintTo", dex.getAddress(), MINT_AMOUNT);
    }

    protected void mockSicxBnusdPrice(BigInteger rate) {
        when(dex.mock.getPoolId(sicx.getAddress(), bnusd.getAddress())).thenReturn(BigInteger.valueOf(3));
        when(dex.mock.getBasePriceInQuote(BigInteger.valueOf(3))).thenReturn(rate);
    }


    protected void mockiETHBnusdPrice(BigInteger rate) {
        when(dex.mock.getPoolId(ieth.getAddress(), bnusd.getAddress())).thenReturn(BigInteger.valueOf(4));
        when(dex.mock.getBasePriceInQuote(BigInteger.valueOf(4))).thenReturn(rate);
    }

    protected void mockSwap(Score tokenRecived, BigInteger in, BigInteger out) {
        Mockito.doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                tokenRecived.invoke(dex.account, "transfer", loans.getAddress(), out, new byte[0]);
                return null;
            }
        }).when(dex.mock).tokenFallback(Mockito.eq(loans.getAddress()), Mockito.eq(in), Mockito.any(byte[].class));
    }

    private void setupGovernance() throws Exception {
        governance = new MockContract<Governance>(GovernanceScoreInterface.class, sm, admin);
        when(governance.mock.getContractAddress("feehandler")).thenReturn(feehandler.getAddress());
    }

    private void setupStaking() throws Exception {
        staking = new MockContract<Staking>(StakingScoreInterface.class, sm, admin);
    }

    protected void mockStakeICX(BigInteger amount) {
        Mockito.doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                final Object[] args = invocation.getArguments();
                sicx.invoke(staking.account, "mintTo", args[0], amount);
                return null;
            }
        }).when(staking.mock).stakeICX(Mockito.any(Address.class), Mockito.any(byte[].class));
    }

    private void setupReserve() throws Exception {
        reserve = new MockContract<Reserve>(ReserveScoreInterface.class, sm, admin);
    }

    private void setupRewards() throws Exception {
        rewards = new MockContract<Rewards>(RewardsScoreInterface.class, sm, admin);
        when(rewards.mock.distribute()).thenReturn(true);
    }

    private void setupDividends() throws Exception {
        dividends = new MockContract<Dividends>(DividendsScoreInterface.class, sm, admin);
        when(dividends.mock.distribute()).thenReturn(true);

    }

    private void setupOracle() throws Exception {
        balancedOracle = new MockContract<BalancedOracle>(BalancedOracleScoreInterface.class, sm, admin);
        when(balancedOracle.mock.getPriceInLoop(Mockito.any(String.class))).thenReturn(EXA);
        when(balancedOracle.mock.getLastPriceInLoop(Mockito.any(String.class))).thenReturn(EXA);

    }

    public void mockOraclePrice(String symbol, BigInteger rate) {
        when(balancedOracle.mock.getPriceInLoop(symbol)).thenReturn(rate);
        when(balancedOracle.mock.getLastPriceInLoop(symbol)).thenReturn(rate);

    }

    protected void takeLoanSICX(Account account, BigInteger collateral, BigInteger loan) {
        JsonObject data = new JsonObject()
                .add("_asset", "bnUSD")
                .add("_amount", loan.toString());
        byte[] params = data.toString().getBytes();

        sicx.invoke(account, "transfer", loans.getAddress(), collateral, params);
    }

    protected void takeLoaniETH(Account account, BigInteger collateral, BigInteger loan) {
        JsonObject data = new JsonObject()
                .add("_asset", "bnUSD")
                .add("_amount", loan.toString());
        byte[] params = data.toString().getBytes();

        ieth.invoke(account, "transfer", loans.getAddress(), collateral, params);
    }

    protected void takeLoanICX(Account account, String asset, BigInteger collateral, BigInteger loan) {
        mockStakeICX(collateral);
        sm.call(account, collateral, loans.getAddress(), "depositAndBorrow", asset, loan, account.getAddress(), BigInteger.ZERO);
    }

    protected BigInteger calculateFee(BigInteger loan) {
        BigInteger feePercentage = (BigInteger)getParam("origination fee");
        return loan.multiply(feePercentage).divide(POINTS);
    }

    protected void verifyPosition(Address address, BigInteger collateral, BigInteger loan) {
        verifyPosition(address, collateral, loan, "sICX");
    }

    protected void verifyPosition(Address address, BigInteger collateral, BigInteger loan, String collateralSymbol) {
        Map<String, Object> position = (Map<String, Object>)loans.call("getAccountPositions", address);
        Map<String, Map<String, Object>> standings = (Map<String, Map<String, Object>>)position.get("standings");
        assertEquals(loan, standings.get(collateralSymbol).get("total_debt"));
        assertEquals(collateral, standings.get(collateralSymbol).get("collateral"));
    }

    protected boolean compareRewardsData(RewardsDataEntry[] expectedDataEntires, RewardsDataEntry[] dataEntires) {
        for (RewardsDataEntry entry : expectedDataEntires) {
            if (!containsRewardsData(entry, dataEntires)) {
                return false;
            }
        }

        return true;
    }

    private boolean containsRewardsData(RewardsDataEntry expectedData, RewardsDataEntry[] dataEntires) {
        for (RewardsDataEntry data : dataEntires) {
            if (data._user.equals(expectedData._user) && data._balance.equals(expectedData._balance)) {
                return true;
            }
        }

        return false;
    }

    protected void verifySnapshot(int addNonZero, int removeFromNonzero, int preComputeIndex, BigInteger totalMiningDebt, BigInteger day, int miningCount) {
        Map<String, Object> snap = (Map<String, Object>) loans.call("getSnapshot", day);
        assertEquals(addNonZero, snap.get("add_to_nonzero_count"));
        assertEquals(removeFromNonzero, snap.get("remove_from_nonzero_count"));
        assertEquals(preComputeIndex, snap.get("precompute_index"));
        assertEquals(totalMiningDebt, snap.get("total_mining_debt"));
        assertEquals(day.intValue(), snap.get("snap_day"));
        assertEquals(miningCount, snap.get("mining_count"));
    }

    protected void verifyTotalDebt(BigInteger expectedDebt) {
        assertEquals(expectedDebt, getTotalDebt());
    }

    protected BigInteger getTotalDebt() {
        Map<String, BigInteger> balanceAndSupply = (Map<String, BigInteger>) loans.call("getBalanceAndSupply", "Loans", admin.getAddress());
        BigInteger totalDebt = balanceAndSupply.get("_totalSupply");

        return totalDebt;
    }

    protected void verifyStanding(Standings standing, Address address) {
        Map<String, Object> positionStanding = (Map<String, Object>) loans.call("getPositionStanding", address, BigInteger.valueOf(-1));
        assertEquals(StandingsMap.get(standing), positionStanding.get("standing"));
    }

    public void governanceCall(String method, Object... params) {
        loans.invoke(governance.account, method, params);
    }

    public Object getParam(String key) {
        Map<String, Object> params = (Map<String, Object>)loans.call("getParameters");
        return params.get(key);
    }

    public void setup() throws Exception {
        sicx = sm.deploy(admin, sICXMintBurn.class, nameSicx, symbolSicx, tokenDecimals, initalaupplyTokens);
        bnusd = sm.deploy(admin, bnUSDMintBurn.class, nameBnusd, symbolBnusd, tokenDecimals, initalaupplyTokens);
        ieth = sm.deploy(admin, iETHMintBurn.class, "ICON ETH", "iETH", 18, initalaupplyTokens);

        setupGovernance();

        loans = sm.deploy(admin, LoansImpl.class, governance.getAddress());
        loansSpy = (LoansImpl) spy(loans.getInstance());
        loans.setInstance(loansSpy);

        setupAccounts();
        setupStaking();
        setupRewards();
        setupDividends();
        setupReserve();
        setupDex();
        setupOracle();

        loans.invoke(governance.account, "setAdmin", admin.getAddress());
        loans.invoke(admin, "setDex", dex.getAddress());
        loans.invoke(admin, "setDividends", dividends.getAddress());
        loans.invoke(admin, "setReserve", reserve.getAddress());
        loans.invoke(admin, "setRebalance", rebalancing.getAddress());
        loans.invoke(admin, "setStaking", staking.getAddress());
        loans.invoke(admin, "setOracle", balancedOracle.getAddress());

        governanceCall("turnLoansOn");
        loans.invoke(admin, "setRewards", rewards.getAddress());
        loans.invoke(admin, "setDividends", rewards.getAddress());
        loans.invoke(admin, "setReserve", reserve.getAddress());
        sicx.invoke(admin, "setMinter", staking.getAddress());
        bnusd.invoke(admin, "setMinter", loans.getAddress());
        loans.invoke(admin, "addAsset", bnusd.getAddress(), true, false);
        loans.invoke(admin, "addAsset", sicx.getAddress(), true, true);
        loans.invoke(admin, "addAsset", ieth.getAddress(), true, true);
    }
}