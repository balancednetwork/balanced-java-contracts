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
import foundation.icon.xcall.NetworkAddress;
import network.balanced.score.lib.interfaces.LoansMessages;
import network.balanced.score.lib.interfaces.tokens.SpokeToken;
import network.balanced.score.lib.interfaces.tokens.SpokeTokenScoreInterface;
import network.balanced.score.lib.test.mock.MockContract;
import score.ByteArrayObjectWriter;
import score.Context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static network.balanced.score.core.loans.utils.LoansConstants.LIQUIDATION_RATIO;
import static network.balanced.score.core.loans.utils.LoansConstants.LIQUIDATION_RATIO;
import static network.balanced.score.core.loans.utils.LoansConstants.LOCKING_RATIO;
import static network.balanced.score.lib.utils.Constants.EXA;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@DisplayName("Loans Tests")
class LoansTestCrosschain extends LoansTestBase {
    String BSC_NID = "0x3.BSC";
    String ETH_NID = "0x1.ETH";
    MockContract<SpokeToken> bnb;
    String BNB_SYMBOL = "BNB";

    @BeforeEach
    public void setup() throws Exception {
        super.setup();
        bnb = new MockContract<>(SpokeTokenScoreInterface.class, SpokeToken.class, sm, admin);
        when(bnb.mock.symbol()).thenReturn(BNB_SYMBOL);
        when(bnb.mock.decimals()).thenReturn(BigInteger.valueOf(18));

        loans.invoke(governance.account, "addAsset", bnb.getAddress(), true, true);
        loans.invoke(governance.account, "setLockingRatio", BNB_SYMBOL, LOCKING_RATIO);
        loans.invoke(governance.account, "setLiquidationRatio", BNB_SYMBOL, LIQUIDATION_RATIO);
    }

    protected void takeLoanBNB(NetworkAddress account, BigInteger collateral, BigInteger loan) {
        JsonObject data = new JsonObject()
                .add("_amount", loan.toString());
        byte[] params = data.toString().getBytes();

        loans.invoke(bnb.account, "xTokenFallback", account.toString(), collateral, params);
    }

    protected void repayLoanBNB(NetworkAddress account, BigInteger loan, BigInteger collateralToWithdraw) {
        JsonObject data = new JsonObject()
                .add("_collateral", BNB_SYMBOL)
                .add("_withdrawAmount", collateralToWithdraw.toString());
        byte[] params = data.toString().getBytes();
        loans.invoke(bnusd.account, "xTokenFallback", account.toString(), loan, params);
    }

    protected void repayLoanToBNB(NetworkAddress account, BigInteger loan, BigInteger collateralToWithdraw, String to) {
        JsonObject data = new JsonObject()
                .add("_collateral", BNB_SYMBOL)
                .add("_withdrawAmount", collateralToWithdraw.toString())
                .add("_to", to.toString());
        byte[] params = data.toString().getBytes();
        loans.invoke(bnusd.account, "xTokenFallback", account.toString(), loan, params);
    }

    @Test
    void xDepositCollateral() {
        // Arrange
        NetworkAddress user = new NetworkAddress(BSC_NID, "0x1");
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        // BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        // BigInteger expectedFee = calculateFee(loan);

        // Act
        takeLoanBNB(user, collateral, BigInteger.ZERO);

        // Assert
        verifyPosition(user.toString(), collateral, BigInteger.ZERO, BNB_SYMBOL);
    }

    @Test
    void xDepositCollateralAndBorrow() {
        // Arrange
        NetworkAddress user = new NetworkAddress(BSC_NID, "0x1");
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        // Act
        when(mockBalanced.daofund.mock.getXCallFeePermission(loans.getAddress(), BSC_NID)).thenReturn(true);
        when(mockBalanced.daofund.mock.claimXCallFee(BSC_NID, true)).thenReturn(BigInteger.TEN);
        takeLoanBNB(user, collateral, loan);

        // Assert
        verify(bnusd.mock).crossTransfer(user.toString(), loan, new byte[0]);
        verifyPosition(user.toString(), collateral, loan.add(expectedFee), BNB_SYMBOL);
    }

    @Test
    void xDepositCollateralAndBorrow_withoutWithdraw() {
        // Arrange
        NetworkAddress user = new NetworkAddress(BSC_NID, "0x1");
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        // Act
        when(mockBalanced.daofund.mock.getXCallFeePermission(loans.getAddress(), BSC_NID)).thenReturn(false);
        takeLoanBNB(user, collateral, loan);

        // Assert
        verify(bnusd.mock).hubTransfer(user.toString(), loan, new byte[0]);
        verifyPosition(user.toString(), collateral, loan.add(expectedFee), BNB_SYMBOL);
    }

    @Test
    void xRepayDebt() {
        // Arrange
        NetworkAddress user = new NetworkAddress(BSC_NID, "0x1");
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        takeLoanBNB(user, collateral, loan);

        // Act
        repayLoanBNB(user, loan, BigInteger.ZERO);

        // Assert
        verify(bnusd.mock).burn(loan);
        verify(bnb.mock, times(0)).hubTransfer(any(), any(), any());
        verifyPosition(user.toString(), collateral, expectedFee, BNB_SYMBOL);
    }

    @Test
    void xRepayDebt_to() {
        // Arrange
        NetworkAddress user = new NetworkAddress(BSC_NID, "0x1");
        NetworkAddress user2 = new NetworkAddress(ETH_NID, "0x1");
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        takeLoanBNB(user, collateral, loan);

        // Act
        repayLoanToBNB(user2, loan, BigInteger.ZERO, user.toString());

        // Assert
        verify(bnusd.mock).burn(loan);
        verify(bnb.mock, times(0)).hubTransfer(any(), any(), any());
        verifyPosition(user.toString(), collateral, expectedFee, BNB_SYMBOL);
    }

    @Test
    void xRepayDebtAndWithdraw() {
        // Arrange
        NetworkAddress user = new NetworkAddress(BSC_NID, "0x1");
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        takeLoanBNB(user, collateral, loan);

        // Act
        repayLoanBNB(user, loan, collateral.divide(BigInteger.TWO));

        // Assert
        verify(bnusd.mock).burn(loan);
        verify(bnb.mock).hubTransfer(user.toString(), collateral.divide(BigInteger.TWO), new byte[0]);
        verifyPosition(user.toString(), collateral.divide(BigInteger.TWO), expectedFee, BNB_SYMBOL);
    }

    @Test
    void xBorrow() {
        // Arrange
        NetworkAddress user = new NetworkAddress(BSC_NID, "0x1");
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        takeLoanBNB(user, collateral, BigInteger.ZERO);

        when(mockBalanced.daofund.mock.getXCallFeePermission(loans.getAddress(), BSC_NID)).thenReturn(true);
        when(mockBalanced.daofund.mock.claimXCallFee(BSC_NID, true)).thenReturn(BigInteger.TEN);

        ByteArrayObjectWriter writer = Context.newByteArrayObjectWriter("RLPn");
        writer.beginList(3);
        writer.write("xBorrow");
        writer.write(BNB_SYMBOL);
        writer.write(loan);
        writer.end();
        byte[] msg = writer.toByteArray();

        // Act
        loans.invoke(mockBalanced.xCall.account, "handleCallMessage", user.toString(), msg, new String[0]);

        // Assert
        verify(bnusd.mock).crossTransfer(user.toString(), loan, new byte[0]);
        verifyPosition(user.toString(), collateral, loan.add(expectedFee), BNB_SYMBOL);
    }

    @Test
    void xBorrow_withoutWithdraw() {
        // Arrange
        NetworkAddress user = new NetworkAddress(BSC_NID, "0x1");
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        takeLoanBNB(user, collateral, BigInteger.ZERO);

        when(mockBalanced.daofund.mock.getXCallFeePermission(loans.getAddress(), BSC_NID)).thenReturn(false);

        // Act
        byte[] msg = LoansMessages.xBorrow(BNB_SYMBOL, loan, "", new byte[0]);
        loans.invoke(mockBalanced.xCall.account, "handleCallMessage", user.toString(), msg, new String[0]);

        // Assert
        verify(bnusd.mock).hubTransfer(user.toString(), loan, new byte[0]);
        verifyPosition(user.toString(), collateral, loan.add(expectedFee), BNB_SYMBOL);
    }
    @Test
    void xBorrow_to() {
        // Arrange
        NetworkAddress user = new NetworkAddress(BSC_NID, "0x1");
        NetworkAddress to = new NetworkAddress(ETH_NID, "0x1");
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        takeLoanBNB(user, collateral, BigInteger.ZERO);

        when(mockBalanced.daofund.mock.getXCallFeePermission(loans.getAddress(), ETH_NID)).thenReturn(true);
        when(mockBalanced.daofund.mock.claimXCallFee(ETH_NID, true)).thenReturn(BigInteger.TEN);

        // Act
        byte[] msg = LoansMessages.xBorrow(BNB_SYMBOL, loan, to.toString(), "test".getBytes());
        loans.invoke(mockBalanced.xCall.account, "handleCallMessage", user.toString(), msg, new String[0]);

        // Assert
        verify(bnusd.mock).crossTransfer(to.toString(), loan, "test".getBytes());
        verifyPosition(user.toString(), collateral, loan.add(expectedFee), BNB_SYMBOL);
    }

    @Test
    void xWithdraw() {
        // Arrange
        NetworkAddress user = new NetworkAddress(BSC_NID, "0x1");
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        takeLoanBNB(user, collateral, BigInteger.ZERO);

        // Act
        byte[] msg = LoansMessages.xWithdraw(collateral, BNB_SYMBOL, "");
        loans.invoke(mockBalanced.xCall.account, "handleCallMessage", user.toString(), msg, new String[0]);

        // Assert
        verify(bnb.mock).hubTransfer(user.toString(), collateral, new byte[0]);
        verifyPosition(user.toString(), BigInteger.ZERO, BigInteger.ZERO, BNB_SYMBOL);
    }


    @Test
    void xWithdraw_to() {
        // Arrange
        NetworkAddress user = new NetworkAddress(BSC_NID, "0x1");
        NetworkAddress toUser = new NetworkAddress(ETH_NID, "0x1");
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        takeLoanBNB(user, collateral, BigInteger.ZERO);

        // Act
        byte[] msg = LoansMessages.xWithdraw(collateral, BNB_SYMBOL, toUser.toString());
        loans.invoke(mockBalanced.xCall.account, "handleCallMessage", user.toString(), msg, new String[0]);

        // Assert
        verify(bnb.mock).hubTransfer(toUser.toString(), collateral, new byte[0]);
        verifyPosition(user.toString(), BigInteger.ZERO, BigInteger.ZERO, BNB_SYMBOL);
    }
}