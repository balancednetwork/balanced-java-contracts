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

import com.iconloop.score.test.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.POINTS;
import static network.balanced.score.lib.utils.Constants.YEAR_IN_MICRO_SECONDS;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_SECOND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Loans Tests")
class LoansTestInterest extends LoansTestBase {
    BigInteger SICX_INTEREST = BigInteger.valueOf(1000);
    BigInteger IETH_INTEREST = BigInteger.valueOf(3000);

    @BeforeEach
    public void setupContractsAndWallets() throws Exception {
        super.setup();
        loans.invoke(mockBalanced.governance.account, "setInterestRate", "sICX", SICX_INTEREST);
        loans.invoke(mockBalanced.governance.account, "setInterestRate", "iETH", IETH_INTEREST);
    }

    @Test
    void testInterest() {
        // Arrange
        Account account1 = sm.createAccount();
        Account account2 = sm.createAccount();

        BigInteger collateral = BigInteger.valueOf(1000000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100000).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger expectedDebt = loan.add(expectedFee);

        // Act
        takeLoanICX(account1, "bnUSD", collateral, loan);
        sm.getBlock().increase(10000);
        takeLoanICX(account2, "bnUSD", collateral, loan);
        sm.getBlock().increase(20000);

        BigInteger timePassedUser1 = BigInteger.valueOf(30000 * 2).multiply(MICRO_SECONDS_IN_A_SECOND);
        BigInteger interest1 = expectedDebt.multiply(SICX_INTEREST).multiply(timePassedUser1)
                .divide(YEAR_IN_MICRO_SECONDS.multiply(POINTS));
        BigInteger timePassedUser2 = BigInteger.valueOf(20000 * 2).multiply(MICRO_SECONDS_IN_A_SECOND);
        BigInteger interest2 = expectedDebt.multiply(SICX_INTEREST).multiply(timePassedUser2)
                .divide(YEAR_IN_MICRO_SECONDS.multiply(POINTS));
        loans.invoke(mockBalanced.governance.account, "applyInterest");

        // Assert
        BigInteger user1Debt = getUserDebt(account1, "sICX");
        BigInteger user2Debt = getUserDebt(account2, "sICX");
        BigInteger debt = getTotalDebt();
        assertTrue(debt.subtract(user1Debt.add(user2Debt)).abs().compareTo(BigInteger.TEN) < 0);
        assertTrue(interest1.compareTo(EXA) > 0);
        assertTrue(interest2.compareTo(EXA) > 0);
        assertRoundedEquals(user1Debt, interest1.add(expectedDebt));
        assertRoundedEquals(user2Debt, interest2.add(expectedDebt));
        assertRoundedNotEquals(user1Debt, user2Debt);
    }

    @Test
    void testInterest_multiCollateral() {
        // Arrange
        Account account1 = sm.createAccount();
        Account account2 = sm.createAccount();
        Account account3 = sm.createAccount();

        BigInteger collateral = BigInteger.valueOf(1000000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100000).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger expectedDebt = loan.add(expectedFee);

        BigInteger timePassed = BigInteger.valueOf(40000);

        // Act
        takeLoanICX(account1, "bnUSD", collateral, loan);
        takeLoanICX(account2, "bnUSD", collateral, loan);
        takeLoaniETH(account2, collateral, loan);
        takeLoaniETH(account3, collateral, loan);

        sm.getBlock().increase(timePassed.longValue()/2);

        BigInteger sICXInterest = expectedDebt.multiply(SICX_INTEREST).multiply(timePassed.multiply(MICRO_SECONDS_IN_A_SECOND))
                .divide(YEAR_IN_MICRO_SECONDS.multiply(POINTS));
        BigInteger iETHInterest = expectedDebt.multiply(IETH_INTEREST).multiply(timePassed.multiply(MICRO_SECONDS_IN_A_SECOND))
                .divide(YEAR_IN_MICRO_SECONDS.multiply(POINTS));
        loans.invoke(mockBalanced.governance.account, "applyInterest");

        // Assert
        BigInteger user1DebtICX = getUserDebt(account1, "sICX");
        BigInteger user1DebtETH = getUserDebt(account1, "iETH");
        BigInteger user2DebtICX = getUserDebt(account2, "sICX");
        BigInteger user2DebtETH = getUserDebt(account2, "iETH");
        BigInteger user3DebtICX = getUserDebt(account3, "sICX");
        BigInteger user3DebtETH = getUserDebt(account3, "iETH");
        BigInteger debt = getTotalDebt();
        BigInteger combinedDebt = user1DebtICX.add(user1DebtETH).
                                  add(user2DebtICX).add(user2DebtETH).
                                  add(user3DebtICX).add(user3DebtETH);
        assertTrue(debt.subtract(combinedDebt).abs().compareTo(BigInteger.TEN) < 0);
        assertTrue(sICXInterest.compareTo(EXA) > 0);
        assertTrue(iETHInterest.compareTo(EXA) > 0);
        assertRoundedEquals(user1DebtICX, sICXInterest.add(expectedDebt));
        assertEquals(user1DebtETH, BigInteger.ZERO);
        assertRoundedEquals(user2DebtICX, sICXInterest.add(expectedDebt));
        assertRoundedEquals(user2DebtETH, iETHInterest.add(expectedDebt));
        assertEquals(user3DebtICX, BigInteger.ZERO);
        assertRoundedEquals(user3DebtETH, iETHInterest.add(expectedDebt));
    }

    @Test
    void returnAsset() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100000).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger expectedDebt = loan.add(expectedFee);
        BigInteger timePassed = BigInteger.valueOf(20000);

        // Act
        takeLoanICX(account, "bnUSD", collateral, loan);
        sm.getBlock().increase(timePassed.longValue()/2);
        loans.invoke(mockBalanced.governance.account, "applyInterest");
        BigInteger debt = getUserDebt(account, "sICX");

        when(bnusd.mock.balanceOf(account.getAddress())).thenReturn(debt);
        loans.invoke(account, "returnAsset", "bnUSD", debt, "sICX", "");

        // Assert
        BigInteger interest = expectedDebt.multiply(SICX_INTEREST).multiply(timePassed.multiply(MICRO_SECONDS_IN_A_SECOND))
                .divide(YEAR_IN_MICRO_SECONDS.multiply(POINTS));
                assertTrue(interest.compareTo(EXA) > 0);
                assertRoundedEquals(expectedDebt.add(interest), debt);
        verify(bnusd.mock).burnFrom(account.getAddress(), debt);
        assertEquals(BigInteger.ZERO, getUserDebt(account, "sICX"));
        verifyTotalDebt(BigInteger.ZERO);
    }

    @Test
    void claimInterest() {
        // Arrange
        Account account = sm.createAccount();
        BigInteger collateral = BigInteger.valueOf(1000000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100000).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger expectedDebt = loan.add(expectedFee);
        BigInteger timePassed = BigInteger.valueOf(20000);
        BigInteger savingsShare = BigInteger.valueOf(4000);
        loans.invoke(mockBalanced.governance.account, "setSavingsRateShare", savingsShare);

        // Act
        takeLoanICX(account, "bnUSD", collateral, loan);
        sm.getBlock().increase(timePassed.longValue()/2);
        loans.invoke(mockBalanced.governance.account, "applyInterest");
        BigInteger debt = getUserDebt(account, "sICX");
        loans.invoke(account, "claimInterest");

        // Assert
        BigInteger expectedInterest = expectedDebt.multiply(SICX_INTEREST).multiply(timePassed.multiply(MICRO_SECONDS_IN_A_SECOND))
                .divide(YEAR_IN_MICRO_SECONDS.multiply(POINTS));
        BigInteger interest = debt.subtract(expectedDebt);
        assertTrue(expectedInterest.compareTo(EXA) > 0);
        verify(mockBalanced.bnUSD.mock).mintTo(mockBalanced.savings.getAddress(), interest.multiply(savingsShare).divide(POINTS), new byte[0]);
        verify(mockBalanced.bnUSD.mock).mintTo(mockBalanced.feehandler.getAddress(), interest.subtract(interest.multiply(savingsShare).divide(POINTS)), new byte[0]);
        assertRoundedEquals(expectedDebt.add(interest), debt);
    }

    @Test
    void permissions() {
        assertOnlyCallableBy(mockBalanced.governance.getAddress(), loans, "setSavingsRateShare", BigInteger.ONE);
        assertOnlyCallableBy(mockBalanced.governance.getAddress(), loans, "setInterestRate", "sICX", BigInteger.ONE);
    }

    private void assertRoundedEquals(BigInteger expected, BigInteger actual) {
        assertEquals(expected.divide(EXA), actual.divide(EXA));
    }

    private void assertRoundedNotEquals(BigInteger expected, BigInteger actual) {
        assertNotEquals(expected.divide(EXA), actual.divide(EXA));
    }

    @SuppressWarnings("unchecked")
    private BigInteger getUserDebt(Account user, String symbol) {
        Map<String, Object> position = (Map<String, Object>) loans.call("getAccountPositions",
                user.getAddress().toString());
        Map<String, Map<String, Object>> standings = (Map<String, Map<String, Object>>) position.get("holdings");
        return (BigInteger) standings.get(symbol).get("bnUSD");
    }
}