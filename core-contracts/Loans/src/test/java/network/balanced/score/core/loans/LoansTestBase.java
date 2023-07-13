/*
 * Copyright (c) 2022-2023 Balanced.network.
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
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.interfaces.tokens.IRC2Mintable;
import network.balanced.score.lib.interfaces.tokens.IRC2MintableScoreInterface;
import network.balanced.score.lib.structs.RewardsDataEntry;
import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockBalanced;
import network.balanced.score.lib.test.mock.MockContract;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import score.Address;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.loans.utils.LoansConstants.*;
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
    protected MockBalanced mockBalanced;
    protected MockContract<Sicx> sicx;
    protected MockContract<BalancedDollar> bnusd;
    protected MockContract<? extends IRC2Mintable> ieth;

    protected MockContract<Dex> dex;
    protected MockContract<Staking> staking;
    protected MockContract<Governance> governance;
    protected MockContract<Rewards> rewards;
    protected MockContract<Dividends> dividends;
    protected MockContract<Reserve> reserve;
    protected MockContract<BalancedOracle> balancedOracle;
    protected LoansImpl loansSpy;

    protected void mockSicxBnusdPrice(BigInteger rate) {
        when(dex.mock.getPoolId(sicx.getAddress(), bnusd.getAddress())).thenReturn(BigInteger.valueOf(3));
        when(dex.mock.getBasePriceInQuote(BigInteger.valueOf(3))).thenReturn(rate);
    }

    protected void mockiETHBnusdPrice(BigInteger rate) {
        when(dex.mock.getPoolId(ieth.getAddress(), bnusd.getAddress())).thenReturn(BigInteger.valueOf(4));
        when(dex.mock.getBasePriceInQuote(BigInteger.valueOf(4))).thenReturn(rate);
    }

    protected void mockSwap(MockContract<? extends IRC2Mintable> tokenSent,
                            MockContract<? extends IRC2Mintable> tokenReceived, BigInteger in, BigInteger out) {
        Mockito.doAnswer((Answer<Void>) invocation -> {
            loans.invoke(tokenReceived.account, "tokenFallback", dex.getAddress(), out, new byte[0]);
            return null;
        }).when(tokenSent.mock).transfer(Mockito.eq(dex.getAddress()), Mockito.eq(in), Mockito.any(byte[].class));
    }

    protected void mockStakeICX(BigInteger amount) {
        Mockito.doAnswer((Answer<Void>) invocation -> {
            loans.invoke(sicx.account, "tokenFallback", EOA_ZERO, amount, new byte[0]);
            return null;
        }).when(staking.mock).stakeICX(Mockito.any(Address.class), Mockito.any(byte[].class));
    }


    private void setupOracle() {
        when(balancedOracle.mock.getPriceInUSD(Mockito.any(String.class))).thenReturn(EXA);
        when(balancedOracle.mock.getLastPriceInUSD(Mockito.any(String.class))).thenReturn(EXA);
    }

    public void mockOraclePrice(String symbol, BigInteger rate) {
        when(balancedOracle.mock.getPriceInUSD(symbol)).thenReturn(rate);
        when(balancedOracle.mock.getLastPriceInUSD(symbol)).thenReturn(rate);
    }

    protected void takeLoanSICX(Account account, BigInteger collateral, BigInteger loan) {
        JsonObject data = new JsonObject()
                .add("_asset", "bnUSD")
                .add("_amount", loan.toString());
        byte[] params = data.toString().getBytes();
        BigInteger balance = sicx.mock.balanceOf(loans.getAddress());
        if (balance == null) {
            balance = BigInteger.ZERO;
        }
        when(sicx.mock.balanceOf(loans.getAddress())).thenReturn(balance.add(collateral));
        loans.invoke(sicx.account, "tokenFallback", account.getAddress(), collateral, params);
    }

    protected void takeLoaniETH(Account account, BigInteger collateral, BigInteger loan) {
        JsonObject data = new JsonObject()
                .add("_asset", "bnUSD")
                .add("_amount", loan.toString());
        byte[] params = data.toString().getBytes();
        BigInteger balance = ieth.mock.balanceOf(ieth.getAddress());
        if (balance == null) {
            balance = BigInteger.ZERO;
        }

        when(ieth.mock.balanceOf(loans.getAddress())).thenReturn(balance.add(collateral));
        loans.invoke(ieth.account, "tokenFallback", account.getAddress(), collateral, params);
    }

    protected void takeLoanICX(Account account, String asset, BigInteger collateral, BigInteger loan) {
        mockStakeICX(collateral);
        BigInteger balance = sicx.mock.balanceOf(loans.getAddress());
        if (balance == null) {
            balance = BigInteger.ZERO;
        }
        when(sicx.mock.balanceOf(loans.getAddress())).thenReturn(balance.add(collateral));
        sm.call(account, collateral, loans.getAddress(), "depositAndBorrow", asset, loan, account.getAddress(),
                BigInteger.ZERO);
    }

    protected BigInteger calculateFee(BigInteger loan) {
        BigInteger feePercentage = (BigInteger) getParam("origination fee");
        return loan.multiply(feePercentage).divide(POINTS);
    }

    protected void verifyPosition(Address address, BigInteger collateral, BigInteger loan) {
        verifyPosition(address, collateral, loan, "sICX");
    }

    @SuppressWarnings("unchecked")
    protected void verifyPosition(Address address, BigInteger collateral, BigInteger loan, String collateralSymbol) {
        Map<String, Object> position = (Map<String, Object>) loans.call("getAccountPositions", address);
        Map<String, Map<String, Object>> standings = (Map<String, Map<String, Object>>) position.get("holdings");
        assertEquals(loan, standings.get(collateralSymbol).get("bnUSD"));
        assertEquals(collateral, standings.get(collateralSymbol).get(collateralSymbol));
    }

    protected boolean compareRewardsData(RewardsDataEntry[] expectedDataEntries, RewardsDataEntry[] dataEntries) {
        for (RewardsDataEntry entry : expectedDataEntries) {
            if (!containsRewardsData(entry, dataEntries)) {
                return false;
            }
        }

        return true;
    }

    private boolean containsRewardsData(RewardsDataEntry expectedData, RewardsDataEntry[] dataEntries) {
        for (RewardsDataEntry data : dataEntries) {
            if (data._user.equals(expectedData._user) && data._balance.equals(expectedData._balance)) {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    protected void verifySnapshot(int addNonZero, int removeFromNonzero, int preComputeIndex,
                                  BigInteger totalMiningDebt, BigInteger day, int miningCount) {
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
        assertEquals(expectedDebt, loans.call("getTotalDebt", "bnUSD"));
        BigInteger iETHDebt = (BigInteger) loans.call("getTotalCollateralDebt", "iETH", "bnUSD");
        BigInteger sICXDebt = (BigInteger) loans.call("getTotalCollateralDebt", "sICX", "bnUSD");
        assertEquals(expectedDebt, iETHDebt.add(sICXDebt));
    }

    @SuppressWarnings("unchecked")
    protected BigInteger getTotalDebt() {
        Map<String, BigInteger> balanceAndSupply = (Map<String, BigInteger>) loans.call("getBalanceAndSupply", "Loans"
                , EOA_ZERO);
        BigInteger totalDebt = balanceAndSupply.get("_totalSupply");

        return totalDebt;
    }

    @SuppressWarnings("unchecked")
    protected void verifyStanding(Standings standing, Address address) {
        Map<String, Object> positionStanding = (Map<String, Object>) loans.call("getPositionStanding", address,
                BigInteger.valueOf(-1));
        assertEquals(StandingsMap.get(standing), positionStanding.get("standing"));
    }

    public void governanceCall(String method, Object... params) {
        loans.invoke(governance.account, method, params);
    }

    @SuppressWarnings("unchecked")
    public Object getParam(String key) {
        Map<String, Object> params = (Map<String, Object>) loans.call("getParameters");
        return params.get(key);
    }

    public void setup() throws Exception {
        mockReadonly();
        mockBalanced = new MockBalanced(sm, admin);

        sicx = mockBalanced.sicx;
        bnusd = mockBalanced.bnUSD;
        dex = mockBalanced.dex;
        staking = mockBalanced.staking;
        governance = mockBalanced.governance;
        rewards = mockBalanced.rewards;
        dividends = mockBalanced.dividends;
        reserve = mockBalanced.reserve;
        balancedOracle = mockBalanced.balancedOracle;

        ieth = new MockContract<>(IRC2MintableScoreInterface.class, sm, admin);
        when(ieth.mock.symbol()).thenReturn("iETH");
        when(ieth.mock.decimals()).thenReturn(BigInteger.valueOf(18));

        loans = sm.deploy(admin, LoansImpl.class, governance.getAddress());
        loansSpy = (LoansImpl) spy(loans.getInstance());
        loans.setInstance(loansSpy);

        setupOracle();

        loans.invoke(governance.account, "addAsset", sicx.getAddress(), true, true);
        loans.invoke(governance.account, "addAsset", ieth.getAddress(), true, true);

        loans.invoke(governance.account, "setLockingRatio", "sICX", LOCKING_RATIO);
        loans.invoke(governance.account, "setLiquidationRatio", "sICX", LIQUIDATION_RATIO);
        loans.invoke(governance.account, "setLockingRatio", "iETH", LOCKING_RATIO);
        loans.invoke(governance.account, "setLiquidationRatio", "iETH", LIQUIDATION_RATIO);
    }
}