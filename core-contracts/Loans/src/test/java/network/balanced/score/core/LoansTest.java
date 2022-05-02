package network.balanced.score.core.loans;

import static network.balanced.score.core.loans.utils.LoansConstants.StandingsMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

import java.math.BigInteger;
import java.util.Map;

import com.iconloop.score.test.Account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import network.balanced.score.core.loans.sICX;
import network.balanced.score.core.loans.utils.LoansConstants.Standings;
import score.Address;
import scorex.util.ArrayList;

@DisplayName("Loans Tests")
class LoansTests extends LoansTestsBase {

    @BeforeEach
    public void setupContractsAndWallets() throws Exception {
        super.setup();
    }

    @Test
    void getLoansOn() {
        Boolean loansOn = (Boolean) loans.call("getLoansOn");
        assertTrue(loansOn);

        governanceCall("toggleLoansOn");
        loansOn = (Boolean) loans.call("getLoansOn");
        assertTrue(!loansOn);
    }

    @Test
    void getDistributionsDone() {
        Map<String, Boolean> rewardsDoneMap =  (Map<String, Boolean>)loans.call("getDistributionsDone");
        assertTrue(rewardsDoneMap.containsKey("Rewards"));
        assertTrue(rewardsDoneMap.containsKey("Dividends"));
    }

    @Test
    void getSetParameters() {
        loans.invoke(admin, "setRedeemBatchSize", 1);
        loans.invoke(admin, "setMaxRetirePercent", BigInteger.valueOf(2));
        governanceCall("setTimeOffset", BigInteger.valueOf(3));
        loans.invoke(admin, "setMinMiningDebt", BigInteger.valueOf(4));
        loans.invoke(admin, "setNewLoanMinimum", BigInteger.valueOf(5));
        loans.invoke(admin, "setLiquidationReward", BigInteger.valueOf(6));
        loans.invoke(admin, "setRedemptionFee", BigInteger.valueOf(7));
        loans.invoke(admin, "setOriginationFee", BigInteger.valueOf(8));
        loans.invoke(admin, "setLiquidationRatio", BigInteger.valueOf(9));
        loans.invoke(admin, "setLockingRatio", BigInteger.valueOf(10));
        loans.invoke(admin, "setMiningRatio", BigInteger.valueOf(11));

        loans.invoke(admin, "setStaking", accounts.get(0).getAddress());
        loans.invoke(admin, "setRewards", accounts.get(1).getAddress());
        loans.invoke(admin, "setReserve", accounts.get(2).getAddress());
        loans.invoke(admin, "setDividends", accounts.get(3).getAddress());

        Map<String, Object> params = (Map<String, Object>)loans.call("getParameters");
        assertEquals(admin.getAddress(), params.get("admin"));
        assertEquals(governance.getAddress(), params.get("governance"));
        assertEquals(accounts.get(0).getAddress(), params.get("staking"));
        assertEquals(accounts.get(1).getAddress(), params.get("rewards"));
        assertEquals(accounts.get(2).getAddress(), params.get("reserve_fund"));
        assertEquals(accounts.get(3).getAddress(), params.get("dividends"));

        assertEquals(1, params.get("redeem batch size"));
        assertEquals(BigInteger.valueOf(2), params.get("retire percent max"));
        assertEquals(BigInteger.valueOf(3), params.get("time offset"));
        assertEquals(BigInteger.valueOf(4), params.get("min mining debt"));
        assertEquals(BigInteger.valueOf(5), params.get("new loan minimum"));
        assertEquals(BigInteger.valueOf(6), params.get("liquidation reward"));
        assertEquals(BigInteger.valueOf(7), params.get("redemption fee"));
        assertEquals(BigInteger.valueOf(8), params.get("origination fee"));
        assertEquals(BigInteger.valueOf(9), params.get("liquidation ratio"));
        assertEquals(BigInteger.valueOf(10), params.get("locking ratio"));
        assertEquals(BigInteger.valueOf(11), params.get("mining ratio"));
        assertEquals(400, params.get("max div debt length"));
    }

    @Test
    void setMaxRetirePercent_ToLow() {
        Executable setMaxRetirePercent = () -> loans.invoke(admin, "setMaxRetirePercent", BigInteger.valueOf(-1));
        String expectedErrorMessage = "Input parameter must be in the range 0 to 10000 points.";   
        
        expectErrorMessage(setMaxRetirePercent, expectedErrorMessage);
    }

    @Test
    void setMaxRetirePercent_ToHigh() {
        Executable setMaxRetirePercent = () -> loans.invoke(admin, "setMaxRetirePercent", BigInteger.valueOf(-2));
        String expectedErrorMessage = "Input parameter must be in the range 0 to 10000 points.";   
    
        expectErrorMessage(setMaxRetirePercent, expectedErrorMessage);
    }

    @Test
    void getAccountPositions() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        // Act
        takeLoanICX(account, "bnUSD", collateral, loan);
    
        // Assert
        BigInteger day = (BigInteger) loans.call("getDay");
        Map<String, Object> position = (Map<String, Object>) loans.call("getAccountPositions", account.getAddress());
        Map<String, BigInteger> assets = (Map<String, BigInteger>) position.get("assets");
        assertEquals(1, position.get("pos_id"));
        assertEquals(account.getAddress(), position.get("address"));
        assertEquals(day, position.get("snap_id"));
        assertEquals(1, position.get("snaps_length"));
        assertEquals(day, position.get("last_snap"));
        assertEquals(day, position.get("first day"));
        assertEquals(loan.add(expectedFee), position.get("total_debt"));
        assertEquals(collateral, position.get("collateral"));
        assertEquals(collateral.multiply(EXA).divide(loan.add(expectedFee)), position.get("ratio"));
        assertEquals(StandingsMap.get(Standings.MINING), position.get("standing"));

        assertEquals(loan.add(expectedFee), assets.get("bnUSD"));
        assertEquals(collateral, assets.get("sICX"));
    }

    @Test
    void getPositionStanding_Mining() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        // Act
        takeLoanICX(account, "bnUSD", collateral, loan);
    
        // Assert
        verifyStanding(Standings.MINING, account.getAddress());
    }

    @Test
    void getPositionStanding_NotMining_toLowDebt() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger minMiningDebt = (BigInteger) getParam("min mining debt");
        BigInteger collateral = minMiningDebt.multiply(BigInteger.TEN);
        BigInteger loan = minMiningDebt.subtract(BigInteger.ONE.multiply(EXA));
        BigInteger expectedFee = calculateFee(loan);

        // Act
        takeLoanICX(account, "bnUSD", collateral, loan);
    
        // Assert
        verifyStanding(Standings.NOT_MINING, account.getAddress());
    }

    @Test
    void getPositionStanding_NotMining_toHighDebt() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger miningRatio = (BigInteger) getParam("mining ratio");
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        // Act
        takeLoanICX(account, "bnUSD", collateral, loan);
        BigInteger newPrice = EXA.multiply(BigInteger.valueOf(2));
        bnusd.invoke(admin, "setPrice", newPrice);
    
        // Assert
        verifyStanding(Standings.NOT_MINING, account.getAddress());
    }

    @Test
    void getPositionStanding_Locked() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger lockingRatio = (BigInteger) getParam("locking ratio");
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        // Act
        takeLoanICX(account, "bnUSD", collateral, loan);
        BigInteger newPrice = EXA.multiply(BigInteger.valueOf(4));
        bnusd.invoke(admin, "setPrice", newPrice);
    
        // Assert
        verifyStanding(Standings.LOCKED, account.getAddress());
    }

    @Test
    void getPositionStanding_Liquidate() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger lockingRatio = (BigInteger) getParam("locking ratio");
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        // Act
        takeLoanICX(account, "bnUSD", collateral, loan);
        BigInteger newPrice = EXA.multiply(BigInteger.valueOf(6));
        bnusd.invoke(admin, "setPrice", newPrice);
    
        // Assert
        verifyStanding(Standings.LOCKED, account.getAddress());
    }

    @Test
    void getBalanceAndSupply() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        takeLoanICX(account, "bnUSD", collateral, loan);

        // Act
        Map<String, BigInteger> balanceAndSupply = (Map<String, BigInteger>) loans.call("getBalanceAndSupply", "Loans", account.getAddress());
    
        // Assert
        BigInteger totalSupply = (BigInteger) bnusd.call("totalSupply");
        assertEquals(loan.add(expectedFee), balanceAndSupply.get("_balance"));
        assertEquals(totalSupply, balanceAndSupply.get("_totalSupply"));
    }

    @Test
    void getBalanceAndSupply_noPositition() {
        // Arrange
        Account account = accounts.get(0);

        // Act
        Map<String, BigInteger> balanceAndSupply = (Map<String, BigInteger>) loans.call("getBalanceAndSupply", "Loans", account.getAddress());
    
        // Assert
        BigInteger totalSupply = (BigInteger) bnusd.call("totalSupply");
        assertEquals(BigInteger.ZERO, balanceAndSupply.get("_balance"));
        assertEquals(BigInteger.ZERO, balanceAndSupply.get("_totalSupply"));
    }


    @Test
    void checkDeadMarkets_Empty() {
        // Arrange
        Account account = accounts.get(0);

        // Act
        ArrayList<String> deadMarkets = (ArrayList<String>) loans.call("checkForDeadMarkets");
    
        // Assert
        assertEquals(0, deadMarkets.size());
    }

    @Test
    void getDay() {
        BigInteger currentDay = (BigInteger) loans.call("getDay");
        sm.getBlock().increase(DAY);

        assertEquals(currentDay.add(BigInteger.ONE), loans.call("getDay"));
    }

    @Test
    void tokenFallback_DepositAndBorrow() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        int loan = 100;
        BigInteger bigLoan = BigInteger.valueOf(loan).multiply(EXA);

        // Assert
        doNothing().when(loansSpy).depositAndBorrow("bnUSD", bigLoan, account.getAddress(), collateral);

        // Act
        takeLoanSICX(account, collateral, loan);
        verify(loansSpy).depositAndBorrow("bnUSD", bigLoan, account.getAddress(), collateral);
    }

    @Test
    void tokenFallback_ZeroValue() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger value = BigInteger.valueOf(0);
        String expectedErrorMessage = "Token Fallback: Token value should be a positive number";   

        // Assert & Act
        Executable transferToken = () -> sicx.invoke(account, "transfer", loans.getAddress(), value, new byte[0]);
        expectErrorMessage(transferToken, expectedErrorMessage);
    }

    @Test
    void tokenFallback_NotSICX() {
        // Arrange
        Account account = admin;
        BigInteger value = BigInteger.valueOf(100).multiply(EXA);
        String expectedErrorMessage = "The Balanced Loans contract does not accept that token type.";   

        // Assert & Act
        Executable transferToken = () -> bnusd.invoke(account, "transfer", loans.getAddress(), value, new byte[0]);
        expectErrorMessage(transferToken, expectedErrorMessage);
    }

    @Test
    void tokenFallback_EmptyData() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger value = BigInteger.valueOf(100).multiply(EXA);
        String expectedErrorMessage = "Token Fallback: Data can't be empty";
        byte[] data = new byte[0];   

        // Assert & Act
        Executable transferToken = () -> sicx.invoke(account, "transfer", loans.getAddress(), value, data);
        expectErrorMessage(transferToken, expectedErrorMessage);
    }

    @Test
    void DepositAndBorrow_ICX() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        // Act
        takeLoanICX(account, "bnUSD", collateral, loan);
    
        // Assert
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));
    }

    @Test
    void DepositAndBorrow_StakeOnly() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.ZERO;

        // Act
        takeLoanICX(account, "", collateral, loan);

        // Assert
        Map<String, Object> position = (Map<String, Object>)loans.call("getAccountPositions", account.getAddress());
        Map<String, BigInteger> assetHoldings = (Map<String, BigInteger>) position.get("assets");

        assertEquals(collateral, assetHoldings.get("sICX"));
        assertEquals(false, assetHoldings.containsKey("bnUSD"));
    }

    @Test
    void DepositAndBorrow_OriginateLoan_NotBorrowable() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);;
        String expectedErrorMessage = "Loans of collateral assets are not allowed.";

        // Assert & Act
        Executable depositAndBorrow = () -> takeLoanICX(account, "sICX", collateral, loan);
        expectErrorMessage(depositAndBorrow, expectedErrorMessage);
    }

    @Test
    void DepositAndBorrow_OriginateLoan_NotActive() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(100).multiply(EXA);;
        String expectedErrorMessage = "Loans of inactive assets are not allowed.";
        loans.invoke(admin, "toggleAssetActive", "bnUSD");

        // Assert & Act
        Executable depositAndBorrow = () -> takeLoanICX(account, "bnUSD", collateral, loan);
        expectErrorMessage(depositAndBorrow, expectedErrorMessage);
    }

    @Test
    void DepositAndBorrow_OriginateLoan_LowerThanMinimum() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger newLoanMinimum = (BigInteger) getParam("new loan minimum");

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger toSmallLoan = newLoanMinimum.subtract(BigInteger.ONE.multiply(EXA));
        String expectedErrorMessage = "The initial loan of any" +
                                    "asset must have a minimum value" +
                                    "of "+ newLoanMinimum.divide(EXA) +" dollars.";

        // Assert & Act
        Executable depositAndBorrow = () -> takeLoanICX(account, "bnUSD", collateral, toSmallLoan);
        expectErrorMessage(depositAndBorrow, expectedErrorMessage);
    }

    @Test
    void DepositAndBorrow_OriginateLoan_ToLargeDebt() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(500).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger lockingRatio = ((BigInteger)getParam("locking ratio")).divide(POINTS);
        
        String expectedErrorMessage = collateral + " collateral is insufficient to originate a loan of " + loan.divide(EXA) + " bnUSD " + 
                                        "when max_debt_value = "+ collateral.divide(lockingRatio) + ", new_debt_value = " + loan.add(expectedFee) + ", " + 
                                        "which includes a fee of " + expectedFee.divide(EXA) +" bnUSD, given an existing loan value of 0.";

        // Assert & Act
        Executable depositAndBorrow = () -> takeLoanICX(account, "bnUSD", collateral, loan);
        expectErrorMessage(depositAndBorrow, expectedErrorMessage);
    }

    @Test
    void returnAsset() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger loanToRepay = BigInteger.valueOf(100).multiply(EXA);
        
        takeLoanICX(account, "bnUSD", collateral, loan);
        BigInteger balancePre = (BigInteger) bnusd.call("balanceOf", account.getAddress());

        // Act 
        loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, true);

        // Assert
        BigInteger balancePost = (BigInteger) bnusd.call("balanceOf", account.getAddress());
        assertEquals(balancePre.subtract(loanToRepay), balancePost);
        verifyPosition(account.getAddress(), collateral, loan.subtract(loanToRepay).add(expectedFee));
    }

    @Test
    void returnAsset_MoreThanHoldings() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger loanToRepay = loan.add(expectedFee).add(BigInteger.ONE);
        String expectedErrorMessage = "Repaid amount is greater than the amount in the position of " + account.getAddress();

        bnusd.invoke(admin, "transfer", account.getAddress(), loanToRepay, new byte[0]);
        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert & Act
        Executable returnToMuch = () ->  loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, true);
        expectErrorMessage(returnToMuch, expectedErrorMessage);
    }

    @Test
    void returnAsset_returnCollateral() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger collateralToRepay = BigInteger.valueOf(100).multiply(EXA);
        String expectedErrorMessage = "sICX is not an active, borrowable asset on Balanced.";

        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert & Act
        Executable returnCollateral = () ->  loans.invoke(account, "returnAsset", "sICX", collateralToRepay, true);
        expectErrorMessage(returnCollateral, expectedErrorMessage);
    }

    @Test
    void returnAsset_returnNonActiveAssest() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger loanToRepay = BigInteger.valueOf(100).multiply(EXA);
        String expectedErrorMessage = "bnUSD is not an active, borrowable asset on Balanced.";

        takeLoanICX(account, "bnUSD", collateral, loan);
        loans.invoke(admin, "toggleAssetActive", "bnUSD");

        // Assert & Act
        Executable nonActiveAsset = () ->  loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, true);
        expectErrorMessage(nonActiveAsset, expectedErrorMessage);
    }

    @Test
    void returnAsset_InsufficientBalance() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger loanToRepay = BigInteger.valueOf(100).multiply(EXA);

        String expectedErrorMessage = "Insufficient balance.";

        takeLoanICX(account, "bnUSD", collateral, loan);
        bnusd.invoke(account, "transfer", admin.getAddress(), loan, new byte[0]);

        // Assert & Act
        Executable returnWithInsufficientBalance = () ->  loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, true);
        expectErrorMessage(returnWithInsufficientBalance, expectedErrorMessage);
    }

    @Test
    void returnAsset_NoPosition() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger loanToRepay = BigInteger.valueOf(100).multiply(EXA);
        bnusd.invoke(admin, "transfer", account.getAddress(), loanToRepay, new byte[0]);

        String expectedErrorMessage = "No debt repaid because, " + account.getAddress() + " does not have a position in Balanced";

        // Assert & Act
        Executable returnWithNoPosition = () ->  loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, true);
        expectErrorMessage(returnWithNoPosition, expectedErrorMessage);
    }

    @Test
    void returnAsset_doNotRepay() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger loanToRepay = BigInteger.valueOf(100).multiply(EXA);
        
        takeLoanICX(account, "bnUSD", collateral, loan);
        String expectedErrorMessage = "No debt repaid because, repay=false";

        // Assert & Act
        Executable returnWithNoPosition = () ->  loans.invoke(account, "returnAsset", "bnUSD", loanToRepay, false);
        expectErrorMessage(returnWithNoPosition, expectedErrorMessage);
    }

    @Test
    void withdrawCollateral() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger collateralToWithdraw = BigInteger.valueOf(100).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        takeLoanICX(account, "bnUSD", collateral, loan);
        BigInteger balancePre = (BigInteger) sicx.call("balanceOf", account.getAddress());

        // Act
        loans.invoke(account, "withdrawCollateral", collateralToWithdraw);

        // Assert
        BigInteger balancePost = (BigInteger) sicx.call("balanceOf", account.getAddress());
        assertEquals(balancePre.add(collateralToWithdraw), balancePost);
        verifyPosition(account.getAddress(), collateral.subtract(collateralToWithdraw), loan.add(expectedFee));
    }

    @Test
    void withdrawCollateral_ZeroCollateral() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger collateralToWithdraw = BigInteger.valueOf(0);
        String expectedErrorMessage = "Withdraw amount must be more than zero.";

        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert & Act
        Executable withdrawZeroCollateral = () -> loans.invoke(account, "withdrawCollateral", collateralToWithdraw);
        expectErrorMessage(withdrawZeroCollateral, expectedErrorMessage);
    }

    @Test
    void withdrawCollateral_NoPosition() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger collateralToWithdraw = BigInteger.valueOf(200).multiply(EXA);
        String expectedErrorMessage = "This address does not have a position on Balanced.";

        // Assert & Act
        Executable withdrawWithNoPosition = () -> loans.invoke(account, "withdrawCollateral", collateralToWithdraw);
        expectErrorMessage(withdrawWithNoPosition, expectedErrorMessage);
    }

    @Test
    void withdrawCollateral_TooMuchCollateral() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger collateralToWithdraw = BigInteger.valueOf(1100).multiply(EXA);
        String expectedErrorMessage = "Position holds less collateral than the requested withdrawal.";

        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert & Act
        Executable withdrawTooMuchCollateral = () -> loans.invoke(account, "withdrawCollateral", collateralToWithdraw);
        expectErrorMessage(withdrawTooMuchCollateral, expectedErrorMessage);
    }

    @Test
    void withdrawCollateral_NewLockingRatioToLow() {
        // Arrange
        Account account = accounts.get(0);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger collateralToWithdraw = BigInteger.valueOf(800).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        BigInteger lockingRatio = ((BigInteger)getParam("locking ratio")).divide(POINTS);
        String expectedErrorMessage = "Requested withdrawal is more than available collateral. " +
                                    "total debt value: " + loan.add(expectedFee) + " ICX " +
                                    "remaining collateral value: " + collateral.subtract(collateralToWithdraw) + " ICX " +
                                    "locking value (max debt): " + lockingRatio.multiply(loan.add(expectedFee)) + " ICX";

        takeLoanICX(account, "bnUSD", collateral, loan);

        // Assert & Act
        Executable withdrawTooMuchCollateral = () -> loans.invoke(account, "withdrawCollateral", collateralToWithdraw);
        expectErrorMessage(withdrawTooMuchCollateral, expectedErrorMessage);
    }

    @Test
    void liquidate() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        BigInteger liquidationReward = (BigInteger) getParam("liquidation reward");
        BigInteger expectedReward = collateral.multiply(liquidationReward).divide(POINTS);
        BigInteger liquidaterBalancePre = (BigInteger) sicx.call("balanceOf", liquidater.getAddress());

        takeLoanICX(account, "bnUSD", collateral, loan);
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        bnusd.invoke(admin, "setPrice", newPrice);

        // Act
        loans.invoke(liquidater, "liquidate", account.getAddress());

        // Assert
        BigInteger liquidaterBalancePost = (BigInteger) sicx.call("balanceOf", liquidater.getAddress());
        assertEquals(liquidaterBalancePre.add(expectedReward), liquidaterBalancePost);
        verifyPosition(account.getAddress(), BigInteger.ZERO, BigInteger.ZERO);
         
        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>)loans.call("getAvailableAssets")).get("bnUSD");
        
        BigInteger expectedBadDebt = loan.add(expectedFee);
        BigInteger expectedLiquidationPool = collateral.subtract(expectedReward);
        assertEquals(expectedBadDebt, bnusdAsset.get("bad_debt"));
        assertEquals(expectedLiquidationPool, bnusdAsset.get("liquidation_pool"));
    }

    @Test
    void liquidate_PostionNotInLiquidateStanding() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);

        takeLoanICX(account, "bnUSD", collateral, loan);
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));

        // Act
        loans.invoke(liquidater, "liquidate", account.getAddress());

        // Assert
        verifyPosition(account.getAddress(), collateral, loan.add(expectedFee));
    }

    @Test
    void liquidate_NoPosition() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
        String expectedErrorMessage = "This address does not have a position on Balanced.";
       
        // Assert & Act
        Executable liquidateAccountWithNoPosition = () ->  loans.invoke(liquidater, "liquidate", account.getAddress());
        expectErrorMessage(liquidateAccountWithNoPosition, expectedErrorMessage);
    }

    @Test
    void retireBadDebt_retireAll() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
        Account badDebtReedemer =  accounts.get(2);

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        bnusd.invoke(admin, "transfer", badDebtReedemer.getAddress(), loan.add(expectedFee), new byte[0]);

        BigInteger liquidationReward = (BigInteger) getParam("liquidation reward");
        BigInteger expectedReward = collateral.multiply(liquidationReward).divide(POINTS);
        takeLoanICX(account, "bnUSD", collateral, loan);

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        bnusd.invoke(admin, "setPrice", newPrice);
        loans.invoke(liquidater, "liquidate", account.getAddress());

        BigInteger badDebt = loan.add(expectedFee);
        BigInteger liquidationPool = collateral.subtract(expectedReward);

        // Act
        BigInteger bnUSDBalancePre = (BigInteger) bnusd.call("balanceOf", badDebtReedemer.getAddress());
        BigInteger sICXBalancePre = (BigInteger) sicx.call("balanceOf", badDebtReedemer.getAddress());

        loans.invoke(badDebtReedemer, "retireBadDebt", "bnUSD", badDebt);
     
        // Assert
        BigInteger bnUSDBalancePost = (BigInteger) bnusd.call("balanceOf", badDebtReedemer.getAddress());
        BigInteger sICXBalancePost = (BigInteger) sicx.call("balanceOf", badDebtReedemer.getAddress());
       
        BigInteger bonus = POINTS.add(BigInteger.valueOf(1000));
        BigInteger icxPrice = (BigInteger) sicx.call("priceInLoop");
        BigInteger debtInsICX = bonus.multiply(badDebt).multiply(newPrice).divide(icxPrice.multiply(POINTS));
      
        assertEquals(bnUSDBalancePre.subtract(badDebt), bnUSDBalancePost);
        assertEquals(sICXBalancePre.add(debtInsICX), sICXBalancePost);

        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>)loans.call("getAvailableAssets")).get("bnUSD");
        BigInteger expectedBadDebt = loan.add(expectedFee);
        
        BigInteger expectedLiquidationPool = liquidationPool.subtract(debtInsICX);
        assertEquals(BigInteger.ZERO, bnusdAsset.get("bad_debt"));
        assertEquals(expectedLiquidationPool, bnusdAsset.get("liquidation_pool"));
    }

    @Test
    void retireBadDebt_UseReserve() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
        Account badDebtReedemer =  accounts.get(2);

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger badDebtRedeemed = loan.add(expectedFee);

        bnusd.invoke(admin, "transfer", badDebtReedemer.getAddress(), badDebtRedeemed, new byte[0]);

        BigInteger liquidationReward = (BigInteger) getParam("liquidation reward");
        BigInteger expectedReward = collateral.multiply(liquidationReward).divide(POINTS);
        takeLoanICX(account, "bnUSD", collateral, loan);

        BigInteger pricePreLiquidation = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        BigInteger pricePostLiquidation = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(6));

        bnusd.invoke(admin, "setPrice", pricePreLiquidation);
        loans.invoke(liquidater, "liquidate", account.getAddress());
        bnusd.invoke(admin, "setPrice", pricePostLiquidation);
    
        BigInteger liquidationPool = collateral.subtract(expectedReward);

        BigInteger bonus = POINTS.add(BigInteger.valueOf(1000));
        BigInteger icxPrice = (BigInteger) sicx.call("priceInLoop");
        BigInteger debtInsICX = bonus.multiply(badDebtRedeemed).multiply(pricePostLiquidation).divide(icxPrice.multiply(POINTS));
        sicx.invoke(admin, "transfer", reserve.getAddress(), debtInsICX, new byte[0]);

        // Act
        BigInteger bnUSDBalancePre = (BigInteger) bnusd.call("balanceOf", badDebtReedemer.getAddress());
        BigInteger sICXBalancePre = (BigInteger) sicx.call("balanceOf", badDebtReedemer.getAddress());

        BigInteger amountRedeemed = debtInsICX.subtract(liquidationPool);
        mockRedeemFromReserve(badDebtReedemer.getAddress(), amountRedeemed, icxPrice);
        loans.invoke(badDebtReedemer, "retireBadDebt", "bnUSD", badDebtRedeemed);

        // Assert
        BigInteger bnUSDBalancePost = (BigInteger) bnusd.call("balanceOf", badDebtReedemer.getAddress());
        BigInteger sICXBalancePost = (BigInteger) sicx.call("balanceOf", badDebtReedemer.getAddress());
       
      
        assertEquals(bnUSDBalancePre.subtract(badDebtRedeemed), bnUSDBalancePost);
        assertEquals(sICXBalancePre.add(debtInsICX), sICXBalancePost);

        Map<String, Object> bnusdAsset = ((Map<String, Map<String, Object>>)loans.call("getAvailableAssets")).get("bnUSD");
        BigInteger expectedBadDebt = loan.add(expectedFee);
        
        BigInteger expectedLiquidationPool = liquidationPool.subtract(debtInsICX);
        assertEquals(BigInteger.ZERO, bnusdAsset.get("bad_debt"));
        assertEquals(BigInteger.ZERO, bnusdAsset.get("liquidation_pool"));
    }

    @Test
    void retireBadDebt_ZeroAmount() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
        Account badDebtReedemer =  accounts.get(2);
        String expectedErrorMessage = "Amount retired must be greater than zero.";

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
    
        bnusd.invoke(admin, "transfer", badDebtReedemer.getAddress(), loan.add(expectedFee), new byte[0]);
        takeLoanICX(account, "bnUSD", collateral, loan);

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        bnusd.invoke(admin, "setPrice", newPrice);
        loans.invoke(liquidater, "liquidate", account.getAddress());

        // Assert & Act
        Executable retireBadDebtZeroAmount = () ->  loans.invoke(badDebtReedemer, "retireBadDebt", "bnUSD", BigInteger.ZERO);
        expectErrorMessage(retireBadDebtZeroAmount, expectedErrorMessage);
    }

    @Test
    void retireBadDebt_Collateral() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
        Account badDebtReedemer =  accounts.get(2);
        String expectedErrorMessage = "sICX is not an active, borrowable asset on Balanced.";

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger badDebt = loan.add(expectedFee);
    
        bnusd.invoke(admin, "transfer", badDebtReedemer.getAddress(), loan.add(expectedFee), new byte[0]);
        takeLoanICX(account, "bnUSD", collateral, loan);

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        bnusd.invoke(admin, "setPrice", newPrice);
        loans.invoke(liquidater, "liquidate", account.getAddress());

        // Assert & Act
        Executable retireBadDebtZeroAmount = () ->  loans.invoke(badDebtReedemer, "retireBadDebt", "sICX", badDebt);
        expectErrorMessage(retireBadDebtZeroAmount, expectedErrorMessage);
    }

    @Test
    void retireBadDebt_AssetNotActive() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
        Account badDebtReedemer =  accounts.get(2);
        String expectedErrorMessage = "bnUSD is not an active, borrowable asset on Balanced.";

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger badDebt = loan.add(expectedFee);
    
        bnusd.invoke(admin, "transfer", badDebtReedemer.getAddress(), loan.add(expectedFee), new byte[0]);
        takeLoanICX(account, "bnUSD", collateral, loan);

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        bnusd.invoke(admin, "setPrice", newPrice);
        loans.invoke(liquidater, "liquidate", account.getAddress());

        // Assert & Act
        loans.invoke(admin, "toggleAssetActive", "bnUSD");
        Executable retireBadDebtZeroAmount = () ->  loans.invoke(badDebtReedemer, "retireBadDebt", "bnUSD", badDebt);
        expectErrorMessage(retireBadDebtZeroAmount, expectedErrorMessage);
    }

    @Test
    void retireBadDebt_InsufficientBalance() {
        // Arrange
        Account account = accounts.get(0);
        Account liquidater = accounts.get(1);
        Account badDebtReedemer =  accounts.get(2);
        String expectedErrorMessage = "Insufficient balance.";

        BigInteger collateral = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger loan = BigInteger.valueOf(200).multiply(EXA);
        BigInteger expectedFee = calculateFee(loan);
        BigInteger badDebt = loan.add(expectedFee);
    
        takeLoanICX(account, "bnUSD", collateral, loan);

        BigInteger newPrice = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(4));
        bnusd.invoke(admin, "setPrice", newPrice);
        loans.invoke(liquidater, "liquidate", account.getAddress());

        // Assert & Act
        Executable retireBadDebtZeroAmount = () ->  loans.invoke(badDebtReedemer, "retireBadDebt", "bnUSD", badDebt);
        expectErrorMessage(retireBadDebtZeroAmount, expectedErrorMessage);
    }

    @Test
    void retireBadDebt_NoBadDebt() {
        // Arrange
        Account badDebtReedemer =  accounts.get(0);
        BigInteger badDebt = BigInteger.valueOf(200).multiply(EXA);
        bnusd.invoke(admin, "transfer", badDebtReedemer.getAddress(), badDebt, new byte[0]);

        String expectedErrorMessage = "No bad debt for bnUSD";
    
        // Assert & Act
        Executable retireBadDebtZeroAmount = () ->  loans.invoke(badDebtReedemer, "retireBadDebt", "bnUSD", badDebt);
        expectErrorMessage(retireBadDebtZeroAmount, expectedErrorMessage);
    }

    @Test
    void raisePrice() {
        // Arrange
        BigInteger accountZeroCollateral = BigInteger.valueOf(10000).multiply(EXA);
        BigInteger accountOneCollateral = BigInteger.valueOf(20000).multiply(EXA);
        BigInteger accountTwoCollateral = BigInteger.valueOf(30000).multiply(EXA);
        BigInteger accountZeroLoan = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger accountOneLoan = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger accountTwoLoan = BigInteger.valueOf(3000).multiply(EXA);
        BigInteger accountZeroFee = calculateFee(accountZeroLoan);
        BigInteger accountOneFee = calculateFee(accountOneLoan);
        BigInteger accountTwoFee = calculateFee(accountTwoLoan);
        BigInteger accountZeroDebt = accountZeroFee.add(accountZeroLoan);
        BigInteger accountOneDebt = accountOneFee.add(accountOneLoan);
        BigInteger accountTwoDebt = accountTwoFee.add(accountTwoLoan);

        BigInteger rebalanceAmount = BigInteger.valueOf(100).multiply(EXA);
        BigInteger totalCollateral = accountZeroCollateral.add(accountOneCollateral).add(accountTwoCollateral);
        BigInteger totalDebt = accountZeroDebt.add(accountOneDebt).add(accountTwoDebt);

        takeLoanICX(accounts.get(0), "bnUSD", accountZeroCollateral, accountZeroLoan);
        takeLoanICX(accounts.get(1), "bnUSD", accountOneCollateral, accountOneLoan);
        takeLoanICX(accounts.get(2), "bnUSD", accountTwoCollateral, accountTwoLoan);

        BigInteger rate = EXA.divide(BigInteger.TWO);
        BigInteger expectedBnusdRecived = rebalanceAmount.multiply(BigInteger.TWO);
        mockSicxBnusdPrice(rate);
        mockSwap(bnusd, rebalanceAmount, expectedBnusdRecived);

        // Act
        loans.invoke(admin, "raisePrice", rebalanceAmount);

        // Assert
        BigInteger remainingBnusd = expectedBnusdRecived;

        BigInteger accountZeroExpectedCollateralSold = rebalanceAmount.multiply(accountZeroDebt).divide(totalDebt);
        BigInteger accountZeroExpectedDebtRepaid = remainingBnusd.multiply(accountZeroDebt).divide(totalDebt);
        verifyPosition(accounts.get(0).getAddress(), accountZeroCollateral.subtract(accountZeroExpectedCollateralSold),  accountZeroDebt.subtract(accountZeroExpectedDebtRepaid));
        
        totalDebt = totalDebt.subtract(accountZeroDebt);
        rebalanceAmount = rebalanceAmount.subtract(accountZeroExpectedCollateralSold);
        remainingBnusd = remainingBnusd.subtract(accountZeroExpectedDebtRepaid);

        BigInteger accountOneExpectedCollateralSold = rebalanceAmount.multiply(accountOneDebt).divide(totalDebt);
        BigInteger accountOneExpectedDebtRepaid = remainingBnusd.multiply(accountOneDebt).divide(totalDebt);
        verifyPosition(accounts.get(1).getAddress(), accountOneCollateral.subtract(accountOneExpectedCollateralSold),  accountOneDebt.subtract(accountOneExpectedDebtRepaid));
        
        totalDebt = totalDebt.subtract(accountOneDebt);
        rebalanceAmount = rebalanceAmount.subtract(accountOneExpectedCollateralSold);
        remainingBnusd = remainingBnusd.subtract(accountOneExpectedDebtRepaid);

        BigInteger accountTwoExpectedCollateralSold = rebalanceAmount.multiply(accountTwoDebt).divide(totalDebt);
        BigInteger accountTwoExpectedDebtRepaid = remainingBnusd.multiply(accountTwoDebt).divide(totalDebt);
        verifyPosition(accounts.get(2).getAddress(), accountTwoCollateral.subtract(accountTwoExpectedCollateralSold), accountTwoDebt.subtract(accountTwoExpectedDebtRepaid));
    }

    @Test
    void raisePrice_OverMaxRetirePercent() {
        // Arrange
        BigInteger accountZeroCollateral = BigInteger.valueOf(10000).multiply(EXA);
        BigInteger accountOneCollateral = BigInteger.valueOf(20000).multiply(EXA);
        BigInteger accountTwoCollateral = BigInteger.valueOf(30000).multiply(EXA);
        BigInteger accountZeroLoan = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger accountOneLoan = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger accountTwoLoan = BigInteger.valueOf(3000).multiply(EXA);
        BigInteger accountZeroFee = calculateFee(accountZeroLoan);
        BigInteger accountOneFee = calculateFee(accountOneLoan);
        BigInteger accountTwoFee = calculateFee(accountTwoLoan);
        BigInteger accountZeroDebt = accountZeroFee.add(accountZeroLoan);
        BigInteger accountOneDebt = accountOneFee.add(accountOneLoan);
        BigInteger accountTwoDebt = accountTwoFee.add(accountTwoLoan);

        BigInteger totalDebt = accountZeroDebt.add(accountOneDebt).add(accountTwoDebt);
        BigInteger totalCollateral = accountZeroCollateral.add(accountOneCollateral).add(accountTwoCollateral);

        BigInteger totalTokenRequired = BigInteger.valueOf(10000).multiply(EXA);
        BigInteger maxRetirePercent = (BigInteger) getParam("retire percent max");
        BigInteger rate = EXA; 
        mockSicxBnusdPrice(rate);
        BigInteger rebalanceAmount = maxRetirePercent.multiply(totalDebt).multiply(EXA).divide(POINTS.multiply(rate));

        takeLoanICX(accounts.get(0), "bnUSD", accountZeroCollateral, accountZeroLoan);
        takeLoanICX(accounts.get(1), "bnUSD", accountOneCollateral, accountOneLoan);
        takeLoanICX(accounts.get(2), "bnUSD", accountTwoCollateral, accountTwoLoan);

        BigInteger expectedBnusdRecived = rebalanceAmount;
        mockSwap(bnusd, rebalanceAmount, rebalanceAmount);

        // Act
        loans.invoke(admin, "raisePrice", totalTokenRequired);

        // Assert
        BigInteger remainingBnusd = expectedBnusdRecived;

        BigInteger accountZeroExpectedCollateralSold = rebalanceAmount.multiply(accountZeroDebt).divide(totalDebt);
        BigInteger accountZeroExpectedDebtRepaid = remainingBnusd.multiply(accountZeroDebt).divide(totalDebt);
        verifyPosition(accounts.get(0).getAddress(), accountZeroCollateral.subtract(accountZeroExpectedCollateralSold),  accountZeroDebt.subtract(accountZeroExpectedDebtRepaid));
        
        totalDebt = totalDebt.subtract(accountZeroDebt);
        rebalanceAmount = rebalanceAmount.subtract(accountZeroExpectedCollateralSold);
        remainingBnusd = remainingBnusd.subtract(accountZeroExpectedDebtRepaid);

        BigInteger accountOneExpectedCollateralSold = rebalanceAmount.multiply(accountOneDebt).divide(totalDebt);
        BigInteger accountOneExpectedDebtRepaid = remainingBnusd.multiply(accountOneDebt).divide(totalDebt);
        verifyPosition(accounts.get(1).getAddress(), accountOneCollateral.subtract(accountOneExpectedCollateralSold),  accountOneDebt.subtract(accountOneExpectedDebtRepaid));
        
        totalDebt = totalDebt.subtract(accountOneDebt);
        rebalanceAmount = rebalanceAmount.subtract(accountOneExpectedCollateralSold);
        remainingBnusd = remainingBnusd.subtract(accountOneExpectedDebtRepaid);

        BigInteger accountTwoExpectedCollateralSold = rebalanceAmount.multiply(accountTwoDebt).divide(totalDebt);
        BigInteger accountTwoExpectedDebtRepaid = remainingBnusd.multiply(accountTwoDebt).divide(totalDebt);
        verifyPosition(accounts.get(2).getAddress(), accountTwoCollateral.subtract(accountTwoExpectedCollateralSold), accountTwoDebt.subtract(accountTwoExpectedDebtRepaid));
    }

    @Test
    void lowerPrice() {
        // Arrange
        BigInteger accountZeroCollateral = BigInteger.valueOf(10000).multiply(EXA);
        BigInteger accountOneCollateral = BigInteger.valueOf(20000).multiply(EXA);
        BigInteger accountTwoCollateral = BigInteger.valueOf(30000).multiply(EXA);
        BigInteger accountZeroLoan = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger accountOneLoan = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger accountTwoLoan = BigInteger.valueOf(3000).multiply(EXA);
        BigInteger accountZeroFee = calculateFee(accountZeroLoan);
        BigInteger accountOneFee = calculateFee(accountOneLoan);
        BigInteger accountTwoFee = calculateFee(accountTwoLoan);
        BigInteger accountZeroDebt = accountZeroFee.add(accountZeroLoan);
        BigInteger accountOneDebt = accountOneFee.add(accountOneLoan);
        BigInteger accountTwoDebt = accountTwoFee.add(accountTwoLoan);

        BigInteger rebalanceAmount = BigInteger.valueOf(50).multiply(EXA);
        BigInteger totalCollateral = accountZeroCollateral.add(accountOneCollateral).add(accountTwoCollateral);
        BigInteger totalDebt = accountZeroDebt.add(accountOneDebt).add(accountTwoDebt);

        takeLoanICX(accounts.get(0), "bnUSD", accountZeroCollateral, accountZeroLoan);
        takeLoanICX(accounts.get(1), "bnUSD", accountOneCollateral, accountOneLoan);
        takeLoanICX(accounts.get(2), "bnUSD", accountTwoCollateral, accountTwoLoan);

        BigInteger rate = EXA;
        BigInteger expectedSICXRecived = rebalanceAmount;
        mockSicxBnusdPrice(rate);
        mockSwap(sicx, rebalanceAmount, rebalanceAmount);

        // Act
        loans.invoke(admin, "lowerPrice", rebalanceAmount);

        // Assert
        BigInteger remainingSicx = expectedSICXRecived;

        BigInteger accountZeroExpectedCollateralAdded = remainingSicx.multiply(accountZeroDebt).divide(totalDebt);
        BigInteger accountZeroExpectedDebtAdded = rebalanceAmount.multiply(accountZeroDebt).divide(totalDebt);
        verifyPosition(accounts.get(0).getAddress(), accountZeroCollateral.add(accountZeroExpectedCollateralAdded),  accountZeroDebt.add(accountZeroExpectedDebtAdded));
        
        totalDebt = totalDebt.subtract(accountZeroDebt);
        rebalanceAmount = rebalanceAmount.subtract(accountZeroExpectedDebtAdded);
        remainingSicx = remainingSicx.subtract(accountZeroExpectedCollateralAdded);

        BigInteger accountOneExpectedCollateralAdded = remainingSicx.multiply(accountOneDebt).divide(totalDebt);
        BigInteger accountOneExpectedDebtAdded = rebalanceAmount.multiply(accountOneDebt).divide(totalDebt);
        verifyPosition(accounts.get(1).getAddress(), accountOneCollateral.add(accountOneExpectedCollateralAdded),  accountOneDebt.add(accountOneExpectedDebtAdded));
        
        totalDebt = totalDebt.subtract(accountOneDebt);
        rebalanceAmount = rebalanceAmount.subtract(accountOneExpectedDebtAdded);
        remainingSicx = remainingSicx.subtract(accountOneExpectedCollateralAdded);

        BigInteger accountTwoExpectedCollateralAdded = remainingSicx.multiply(accountTwoDebt).divide(totalDebt);
        BigInteger accountTwoExpectedDebtAdded = rebalanceAmount.multiply(accountTwoDebt).divide(totalDebt);
        verifyPosition(accounts.get(2).getAddress(), accountTwoCollateral.add(accountTwoExpectedCollateralAdded), accountTwoDebt.add(accountTwoExpectedDebtAdded));
    }


    @Test
    void lowerPrice_OverMaxRetirePercent() {
        // Arrange
        BigInteger accountZeroCollateral = BigInteger.valueOf(10000).multiply(EXA);
        BigInteger accountOneCollateral = BigInteger.valueOf(20000).multiply(EXA);
        BigInteger accountTwoCollateral = BigInteger.valueOf(30000).multiply(EXA);
        BigInteger accountZeroLoan = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger accountOneLoan = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger accountTwoLoan = BigInteger.valueOf(3000).multiply(EXA);
        BigInteger accountZeroFee = calculateFee(accountZeroLoan);
        BigInteger accountOneFee = calculateFee(accountOneLoan);
        BigInteger accountTwoFee = calculateFee(accountTwoLoan);
        BigInteger accountZeroDebt = accountZeroFee.add(accountZeroLoan);
        BigInteger accountOneDebt = accountOneFee.add(accountOneLoan);
        BigInteger accountTwoDebt = accountTwoFee.add(accountTwoLoan);

        BigInteger totalCollateral = accountZeroCollateral.add(accountOneCollateral).add(accountTwoCollateral);
        BigInteger totalDebt = accountZeroDebt.add(accountOneDebt).add(accountTwoDebt);

        BigInteger totalTokenRequired = BigInteger.valueOf(10000).multiply(EXA);
        BigInteger maxRetirePercent = (BigInteger) getParam("retire percent max");
        BigInteger rebalanceAmount = maxRetirePercent.multiply(totalDebt).divide(POINTS);

        takeLoanICX(accounts.get(0), "bnUSD", accountZeroCollateral, accountZeroLoan);
        takeLoanICX(accounts.get(1), "bnUSD", accountOneCollateral, accountOneLoan);
        takeLoanICX(accounts.get(2), "bnUSD", accountTwoCollateral, accountTwoLoan);

        BigInteger rate = EXA;
        BigInteger expectedSICXRecived = rebalanceAmount;
        mockSicxBnusdPrice(rate);
        mockSwap(sicx, rebalanceAmount, rebalanceAmount);

        // Act
        loans.invoke(admin, "lowerPrice", totalTokenRequired);

        // Assert
        BigInteger remainingSicx = expectedSICXRecived;

        BigInteger accountZeroExpectedCollateralAdded = remainingSicx.multiply(accountZeroDebt).divide(totalDebt);
        BigInteger accountZeroExpectedDebtAdded = rebalanceAmount.multiply(accountZeroDebt).divide(totalDebt);
        verifyPosition(accounts.get(0).getAddress(), accountZeroCollateral.add(accountZeroExpectedCollateralAdded),  accountZeroDebt.add(accountZeroExpectedDebtAdded));
        
        totalDebt = totalDebt.subtract(accountZeroDebt);
        rebalanceAmount = rebalanceAmount.subtract(accountZeroExpectedDebtAdded);
        remainingSicx = remainingSicx.subtract(accountZeroExpectedCollateralAdded);

        BigInteger accountOneExpectedCollateralAdded = remainingSicx.multiply(accountOneDebt).divide(totalDebt);
        BigInteger accountOneExpectedDebtAdded = rebalanceAmount.multiply(accountOneDebt).divide(totalDebt);
        verifyPosition(accounts.get(1).getAddress(), accountOneCollateral.add(accountOneExpectedCollateralAdded),  accountOneDebt.add(accountOneExpectedDebtAdded));
        
        totalDebt = totalDebt.subtract(accountOneDebt);
        rebalanceAmount = rebalanceAmount.subtract(accountOneExpectedDebtAdded);
        remainingSicx = remainingSicx.subtract(accountOneExpectedCollateralAdded);

        BigInteger accountTwoExpectedCollateralAdded = remainingSicx.multiply(accountTwoDebt).divide(totalDebt);
        BigInteger accountTwoExpectedDebtAdded = rebalanceAmount.multiply(accountTwoDebt).divide(totalDebt);
        verifyPosition(accounts.get(2).getAddress(), accountTwoCollateral.add(accountTwoExpectedCollateralAdded), accountTwoDebt.add(accountTwoExpectedDebtAdded));
    }

    @Test
    void precompute_notYetCalled() {
        // Arrange
        BigInteger accountZeroCollateral = BigInteger.valueOf(10000).multiply(EXA);
        BigInteger accountOneCollateral = BigInteger.valueOf(20000).multiply(EXA);
        BigInteger accountZeroLoan = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger accountOneLoan = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger accountZeroFee = calculateFee(accountZeroLoan);
        BigInteger accountOneFee = calculateFee(accountOneLoan);
        BigInteger accountZeroDebt = accountZeroFee.add(accountZeroLoan);
        BigInteger accountOneDebt = accountOneFee.add(accountOneLoan);
  
        // Act
        takeLoanICX(accounts.get(0), "bnUSD", accountZeroCollateral, accountZeroLoan);
        takeLoanICX(accounts.get(1), "bnUSD", accountOneCollateral, accountOneLoan);
     
        BigInteger day = (BigInteger) loans.call("getDay");

        // Assert
        int expectedNonZeroPositiosnToAdd = 2;
        int expectedPositiosnToRemove = 0;
        int expectedCurrentPrecomputeIndex = 0;
        BigInteger expectedTotalMiningDebt = BigInteger.ZERO;
        int expectedMiningCount = 0;
        
        verifySnapshot(
            expectedNonZeroPositiosnToAdd,
            expectedPositiosnToRemove,
            expectedCurrentPrecomputeIndex,
            expectedTotalMiningDebt,
            day,
            expectedMiningCount
        );
    }

    @Test
    void precompute_complete() {
        // Arrange
        BigInteger accountZeroCollateral = BigInteger.valueOf(10000).multiply(EXA);
        BigInteger accountOneCollateral = BigInteger.valueOf(20000).multiply(EXA);
        BigInteger accountZeroLoan = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger accountOneLoan = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger accountZeroFee = calculateFee(accountZeroLoan);
        BigInteger accountOneFee = calculateFee(accountOneLoan);
        BigInteger accountZeroDebt = accountZeroFee.add(accountZeroLoan);
        BigInteger accountOneDebt = accountOneFee.add(accountOneLoan);
  
        // Act
        takeLoanICX(accounts.get(0), "bnUSD", accountZeroCollateral, accountZeroLoan);
        takeLoanICX(accounts.get(1), "bnUSD", accountOneCollateral, accountOneLoan);
     
        BigInteger day = (BigInteger) loans.call("getDay");
        loans.invoke(rewards.account, "precompute", day, 0);
        loans.invoke(rewards.account, "precompute", day, 2);

        // Assert
        int expectedNonZeroPositionsToAdd = 0;
        int expectedPositionsToRemove = 0;
        int expectedCurrentPrecomputeIndex = 2;
        BigInteger expectedTotalMiningDebt = accountZeroDebt.add(accountOneDebt);
        int expectedMiningCount = 2;
        
        verifySnapshot(
            expectedNonZeroPositionsToAdd,
            expectedPositionsToRemove,
            expectedCurrentPrecomputeIndex,
            expectedTotalMiningDebt,
            day,
            expectedMiningCount
        );
    }

    @Test
    void precompute_OnlyAddPositions() {
        // Arrange
        BigInteger accountZeroCollateral = BigInteger.valueOf(10000).multiply(EXA);
        BigInteger accountOneCollateral = BigInteger.valueOf(20000).multiply(EXA);
        BigInteger accountZeroLoan = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger accountOneLoan = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger accountZeroFee = calculateFee(accountZeroLoan);
        BigInteger accountOneFee = calculateFee(accountOneLoan);
        BigInteger accountZeroDebt = accountZeroFee.add(accountZeroLoan);
        BigInteger accountOneDebt = accountOneFee.add(accountOneLoan);
  
        // Act
        takeLoanICX(accounts.get(0), "bnUSD", accountZeroCollateral, accountZeroLoan);
        takeLoanICX(accounts.get(1), "bnUSD", accountOneCollateral, accountOneLoan);
     
        BigInteger day = (BigInteger) loans.call("getDay");
        loans.invoke(rewards.account, "precompute", day, 0);

        // Assert
        int expectedNonZeroPositionsToAdd = 0;
        int expectedPositionsToRemove = 0;
        int expectedCurrentPrecomputeIndex = 0;
        BigInteger expectedTotalMiningDebt = BigInteger.ZERO;
        int expectedMiningCount = 0;
        
        verifySnapshot(
            expectedNonZeroPositionsToAdd,
            expectedPositionsToRemove,
            expectedCurrentPrecomputeIndex,
            expectedTotalMiningDebt,
            day,
            expectedMiningCount
        );
    }

    @Test
    void precompute_OnlyRemovePositions() {
        // Arrange
        BigInteger accountZeroCollateral = BigInteger.valueOf(10000).multiply(EXA);
        BigInteger accountOneCollateral = BigInteger.valueOf(20000).multiply(EXA);
        BigInteger accountTwoCollateral = BigInteger.valueOf(30000).multiply(EXA);
        BigInteger accountZeroLoan = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger accountOneLoan = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger accountTwoLoan = BigInteger.valueOf(3000).multiply(EXA);
        BigInteger accountZeroFee = calculateFee(accountZeroLoan);
        BigInteger accountOneFee = calculateFee(accountOneLoan);
        BigInteger accountTwoFee = calculateFee(accountTwoLoan);
        BigInteger accountZeroDebt = accountZeroFee.add(accountZeroLoan);
        BigInteger accountOneDebt = accountOneFee.add(accountOneLoan);
        BigInteger accountTwoDebt = accountTwoFee.add(accountTwoLoan);
  
        bnusd.invoke(admin, "transfer", accounts.get(0).getAddress(), accountZeroFee, new byte[0]);
        bnusd.invoke(admin, "transfer", accounts.get(1).getAddress(), accountOneFee, new byte[0]);
 
        takeLoanICX(accounts.get(0), "bnUSD", accountZeroCollateral, accountZeroLoan);
        takeLoanICX(accounts.get(1), "bnUSD", accountOneCollateral, accountOneLoan);
        takeLoanICX(accounts.get(2), "bnUSD", accountTwoCollateral, accountTwoLoan);
        
        BigInteger day = (BigInteger) loans.call("getDay");
        
        loans.invoke(rewards.account, "precompute", day, 0);
        loans.invoke(rewards.account, "precompute", day, 3);
     
        sm.getBlock().increase(DAY);
        loans.invoke(admin, "checkForNewDay");

        // Act
        loans.invoke(accounts.get(0), "returnAsset", "bnUSD", accountZeroDebt, true);
        loans.invoke(accounts.get(1), "returnAsset", "bnUSD", accountOneDebt, true);
     
        day = (BigInteger) loans.call("getDay");

        loans.invoke(rewards.account, "precompute", day, 0);
        loans.invoke(rewards.account, "precompute", day, 2);

        // Assert
        int expectedNonZeroPositionsToAdd = 0;
        int expectedPositionsToRemove = 0;
        int expectedCurrentPrecomputeIndex = 1;
        BigInteger expectedTotalMiningDebt = accountTwoDebt;
        int expectedMiningCount = 1;
        
        verifySnapshot(
            expectedNonZeroPositionsToAdd,
            expectedPositionsToRemove,
            expectedCurrentPrecomputeIndex,
            expectedTotalMiningDebt,
            day,
            expectedMiningCount
        );
    }

    @Test
    void precompute_calculatePartialStandings() {
        // Arrange
        BigInteger accountZeroCollateral = BigInteger.valueOf(10000).multiply(EXA);
        BigInteger accountOneCollateral = BigInteger.valueOf(20000).multiply(EXA);
        BigInteger accountZeroLoan = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger accountOneLoan = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger accountZeroFee = calculateFee(accountZeroLoan);
        BigInteger accountOneFee = calculateFee(accountOneLoan);
        BigInteger accountZeroDebt = accountZeroFee.add(accountZeroLoan);
        BigInteger accountOneDebt = accountOneFee.add(accountOneLoan);
  
        // Act
        takeLoanICX(accounts.get(0), "bnUSD", accountZeroCollateral, accountZeroLoan);
        takeLoanICX(accounts.get(1), "bnUSD", accountOneCollateral, accountOneLoan);
     
        BigInteger day = (BigInteger) loans.call("getDay");
        loans.invoke(rewards.account, "precompute", day, 0);
        loans.invoke(rewards.account, "precompute", day, 1);

        // Assert
        int expectedNonZeroPositionsToAdd = 0;
        int expectedPositionsToRemove = 0;
        int expectedCurrentPrecomputeIndex = 1;
        BigInteger expectedTotalMiningDebt = accountZeroDebt;
        int expectedMiningCount = 1;
        
        verifySnapshot(
            expectedNonZeroPositionsToAdd,
            expectedPositionsToRemove,
            expectedCurrentPrecomputeIndex,
            expectedTotalMiningDebt,
            day,
            expectedMiningCount
        );
    }

    @Test
    void precompute_completeMultipleBatches() {
        // Arrange
        BigInteger accountZeroCollateral = BigInteger.valueOf(10000).multiply(EXA);
        BigInteger accountOneCollateral = BigInteger.valueOf(20000).multiply(EXA);
        BigInteger accountZeroLoan = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger accountOneLoan = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger accountZeroFee = calculateFee(accountZeroLoan);
        BigInteger accountOneFee = calculateFee(accountOneLoan);
        BigInteger accountZeroDebt = accountZeroFee.add(accountZeroLoan);
        BigInteger accountOneDebt = accountOneFee.add(accountOneLoan);
  
        // Act
        takeLoanICX(accounts.get(0), "bnUSD", accountZeroCollateral, accountZeroLoan);
        takeLoanICX(accounts.get(1), "bnUSD", accountOneCollateral, accountOneLoan);
     
        BigInteger day = (BigInteger) loans.call("getDay");
        loans.invoke(rewards.account, "precompute", day, 0);
        loans.invoke(rewards.account, "precompute", day, 1);
        loans.invoke(rewards.account, "precompute", day, 1);

        // Assert
        int expectedNonZeroPositionsToAdd = 0;
        int expectedPositionsToRemove = 0;
        int expectedCurrentPrecomputeIndex = 2;
        BigInteger expectedTotalMiningDebt = accountZeroDebt.add(accountOneDebt);
        int expectedMiningCount = 2;
        
        verifySnapshot(
            expectedNonZeroPositionsToAdd,
            expectedPositionsToRemove,
            expectedCurrentPrecomputeIndex,
            expectedTotalMiningDebt,
            day,
            expectedMiningCount
        );
    }

    @Test
    void getDataBatch_AllSnapshotsCalculated() {
        // Arrange
        Address accountZeroAddress = accounts.get(0).getAddress();
        Address accountOneAddress = accounts.get(1).getAddress();
        Address accountTwoAddress = accounts.get(2).getAddress();
        BigInteger accountZeroCollateral = BigInteger.valueOf(10000).multiply(EXA);
        BigInteger accountOneCollateral = BigInteger.valueOf(20000).multiply(EXA);
        BigInteger accountTwoCollateral = BigInteger.valueOf(30000).multiply(EXA);
        BigInteger accountZeroLoan = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger accountOneLoan = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger accountTwoLoan = BigInteger.valueOf(3000).multiply(EXA);
        BigInteger accountZeroFee = calculateFee(accountZeroLoan);
        BigInteger accountOneFee = calculateFee(accountOneLoan);
        BigInteger accountTwoFee = calculateFee(accountTwoLoan);
        BigInteger accountZeroDebt = accountZeroFee.add(accountZeroLoan);
        BigInteger accountOneDebt = accountOneFee.add(accountOneLoan);
        BigInteger accountTwoDebt = accountTwoFee.add(accountTwoLoan);
    
        // Day 1
        takeLoanICX(accounts.get(0), "bnUSD", accountZeroCollateral, accountZeroLoan);
        takeLoanICX(accounts.get(1), "bnUSD", accountOneCollateral, accountOneLoan);

        BigInteger day = (BigInteger) loans.call("getDay");
        loans.invoke(rewards.account, "precompute", day, 0);
        loans.invoke(rewards.account, "precompute", day, 2);
        sm.getBlock().increase(DAY);

        // Day 2
        takeLoanICX(accounts.get(2), "bnUSD", accountTwoCollateral, accountTwoLoan);

        day = (BigInteger) loans.call("getDay");
        loans.invoke(rewards.account, "precompute", day, 0);
        loans.invoke(rewards.account, "precompute", day, 3);
        sm.getBlock().increase(DAY);

        // Day 3
        BigInteger accountOneRepaidDebt = BigInteger.valueOf(500).multiply(EXA);
        loans.invoke(accounts.get(1), "returnAsset", "bnUSD", accountOneRepaidDebt, true);

        day = (BigInteger) loans.call("getDay");
        loans.invoke(rewards.account, "precompute", day, 0);
        loans.invoke(rewards.account, "precompute", day, 3);
        sm.getBlock().increase(DAY);

        // Assert
        Map<Address, BigInteger> snapshotBatch0 = (Map<Address, BigInteger>)loans.call("getDataBatch", "loans", day.subtract(BigInteger.TWO), 3, 0);
        Map<Address, BigInteger> snapshotBatch1 = (Map<Address, BigInteger>)loans.call("getDataBatch", "loans", day.subtract(BigInteger.ONE), 3, 0);
        Map<Address, BigInteger> snapshotBatch2 = (Map<Address, BigInteger>)loans.call("getDataBatch", "loans", day, 3, 0);

        assertEquals(2, snapshotBatch0.size());
        assertEquals(accountZeroDebt, snapshotBatch0.get(accountZeroAddress));
        assertEquals(accountOneDebt, snapshotBatch0.get(accountOneAddress));

        assertEquals(3, snapshotBatch1.size());
        assertEquals(accountZeroDebt, snapshotBatch1.get(accountZeroAddress));
        assertEquals(accountOneDebt, snapshotBatch1.get(accountOneAddress));
        assertEquals(accountTwoDebt, snapshotBatch1.get(accountTwoAddress));

        assertEquals(3, snapshotBatch2.size());
        assertEquals(accountZeroDebt, snapshotBatch2.get(accountZeroAddress));
        assertEquals(accountOneDebt.subtract(accountOneRepaidDebt), snapshotBatch2.get(accountOneAddress));
        assertEquals(accountTwoDebt, snapshotBatch2.get(accountTwoAddress));
    }

    @Test
    void getDataBatch_LastSnapshotNotCalculated() {
        // Arrange
        Address accountZeroAddress = accounts.get(0).getAddress();
        Address accountOneAddress = accounts.get(1).getAddress();
        Address accountTwoAddress = accounts.get(2).getAddress();
        BigInteger accountZeroCollateral = BigInteger.valueOf(10000).multiply(EXA);
        BigInteger accountOneCollateral = BigInteger.valueOf(20000).multiply(EXA);
        BigInteger accountTwoCollateral = BigInteger.valueOf(30000).multiply(EXA);
        BigInteger accountZeroLoan = BigInteger.valueOf(1000).multiply(EXA);
        BigInteger accountOneLoan = BigInteger.valueOf(2000).multiply(EXA);
        BigInteger accountTwoLoan = BigInteger.valueOf(3000).multiply(EXA);
        BigInteger accountZeroFee = calculateFee(accountZeroLoan);
        BigInteger accountOneFee = calculateFee(accountOneLoan);
        BigInteger accountTwoFee = calculateFee(accountTwoLoan);
        BigInteger accountZeroDebt = accountZeroFee.add(accountZeroLoan);
        BigInteger accountOneDebt = accountOneFee.add(accountOneLoan);
        BigInteger accountTwoDebt = accountTwoFee.add(accountTwoLoan);
    
        // Day 1
        takeLoanICX(accounts.get(0), "bnUSD", accountZeroCollateral, accountZeroLoan);
        takeLoanICX(accounts.get(1), "bnUSD", accountOneCollateral, accountOneLoan);

        BigInteger day = (BigInteger) loans.call("getDay");
        loans.invoke(rewards.account, "precompute", day, 0);
        loans.invoke(rewards.account, "precompute", day, 2);
        sm.getBlock().increase(DAY);

        // Day 2
        takeLoanICX(accounts.get(2), "bnUSD", accountTwoCollateral, accountTwoLoan);

        day = (BigInteger) loans.call("getDay");
        loans.invoke(rewards.account, "precompute", day, 0);
        loans.invoke(rewards.account, "precompute", day, 3);
        sm.getBlock().increase(DAY);

        // Day 3
        BigInteger accountOneRepaidDebt = BigInteger.valueOf(500).multiply(EXA);
        loans.invoke(accounts.get(1), "returnAsset", "bnUSD", accountOneRepaidDebt, true);
        sm.getBlock().increase(DAY);

        // Assert
        Map<Address, BigInteger> snapshotBatch0 = (Map<Address, BigInteger>)loans.call("getDataBatch", "loans", BigInteger.ZERO, 3, 0);
        Map<Address, BigInteger> snapshotBatch1 = (Map<Address, BigInteger>)loans.call("getDataBatch", "loans", BigInteger.ONE, 3, 0);
        Map<Address, BigInteger> snapshotBatch2 = (Map<Address, BigInteger>)loans.call("getDataBatch", "loans", BigInteger.TWO, 3, 0);

        assertEquals(2, snapshotBatch0.size());
        assertEquals(accountZeroDebt, snapshotBatch0.get(accountZeroAddress));
        assertEquals(accountOneDebt, snapshotBatch0.get(accountOneAddress));

        assertEquals(3, snapshotBatch1.size());
        assertEquals(accountZeroDebt, snapshotBatch1.get(accountZeroAddress));
        assertEquals(accountOneDebt, snapshotBatch1.get(accountOneAddress));
        assertEquals(accountTwoDebt, snapshotBatch1.get(accountTwoAddress));
        
        assertEquals(0, snapshotBatch2.size());
        assertEquals(null, snapshotBatch2.get(accountZeroAddress));
        assertEquals(null, snapshotBatch2.get(accountOneAddress));
        assertEquals(null, snapshotBatch2.get(accountTwoAddress));
    }
}