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

package network.balanced.score.token.balancedtoken;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.Map;

import static java.math.BigInteger.*;
import static network.balanced.score.lib.test.UnitTest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class BalancedTokenImplTest extends TestBase {

	private static final ServiceManager sm = getServiceManager();
	private static final Account owner = sm.createAccount();

	private Score balancedToken;
	private BalancedTokenImpl balancedTokenSpy;
	private Score mockDexScore;

	private final Account addressProvider = sm.createAccount();
	private final Account governance = Account.newScoreAccount(scoreCount++);
	private final Account adminAccount = sm.createAccount();

	private final Account dexScore = Account.newScoreAccount(scoreCount++);
	private final Account oracleScore = Account.newScoreAccount(scoreCount++);
	private final Account bnusdScore = Account.newScoreAccount(scoreCount++);
	private final Account dividendsScore = Account.newScoreAccount(scoreCount++);

	private final MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS);

	@BeforeEach
	void deploy() throws Exception {
		balancedToken = sm.deploy(owner, BalancedTokenImpl.class, governance.getAddress());
		mockDexScore = sm.deploy(owner, MockDexScore.class);

		balancedTokenSpy = (BalancedTokenImpl) spy(balancedToken.getInstance());
		balancedToken.setInstance(balancedTokenSpy);
	}

	private void setup() {
		balancedToken.invoke(governance, "setAdmin", adminAccount.getAddress());
		balancedToken.invoke(governance, "setBnusd", bnusdScore.getAddress());
		balancedToken.invoke(governance, "setOracle", oracleScore.getAddress());
		balancedToken.invoke(governance, "setDex", dexScore.getAddress());
		balancedToken.invoke(governance, "setDividends", dividendsScore.getAddress());
	}

	@Test
	void getPeg() {
		assertEquals("BALN", balancedToken.call("getPeg"));
	}

	@Test
	void setAndGetGovernance() {
		testGovernance(balancedToken, governance, owner);
	}

	@Test
	void setAndGetAdmin() {
		testAdmin(balancedToken, governance, adminAccount);
	}

	@Test
	void setAndGetBnusd() {
		testContractSettersAndGetters(balancedToken, governance, governance, "setBnusd", bnusdScore.getAddress(),
				"getBnusd");
	}

	@Test
	void setAndGetOracle() {
		testContractSettersAndGetters(balancedToken, governance, governance, "setOracle", oracleScore.getAddress(),
				"getOracle");
	}

	@Test
	void setAndGetDex() {
		testContractSettersAndGetters(balancedToken, governance, governance, "setDex", dexScore.getAddress(),
				"getDex");
	}

	@Test
	void setAndGetDividends() {
		testContractSettersAndGetters(balancedToken, governance, governance, "setDividends",
				dividendsScore.getAddress(), "getDividends");
	}

	@Test
	void setAndGetOracleName() {
		assertEquals("Balanced DEX", balancedToken.call("getOracleName"));

		String newOracleName = "ChainLink";
		testAdminControlMethods(balancedToken, governance, governance, "setOracleName", newOracleName,
				"getOracleName");
	}

	@Test
	void setAndGetMinIntervalTime() {
		BigInteger TWO_SECONDS = BigInteger.valueOf(2_000_000);
		assertEquals(TWO_SECONDS, balancedToken.call("getMinInterval"));
		BigInteger newMinInterval = BigInteger.valueOf(20_000_000);
//		balancedToken.invoke(owner, "removeGovernance");
		testAdminControlMethods(balancedToken, governance, governance, "setMinInterval", newMinInterval,
				"getMinInterval");
	}

	@Test
	void priceInLoop() {
		setup();
		BigInteger newBalnPriceInUsd = BigInteger.valueOf(250).multiply(BigInteger.TEN.pow(16));
		BigInteger newUsdPriceInIcx = BigInteger.valueOf(120).multiply(BigInteger.TEN.pow(16));

		BigInteger newBalnPriceInIcx = newBalnPriceInUsd.multiply(newUsdPriceInIcx).divide(ICX);

		Map<String, Object> priceData = Map.of("last_update_base", "0x5dd26d3bd5dcf", "last_update_quote",
				"0x5dd26cb9b4680", "rate", newUsdPriceInIcx);
		contextMock.when(() -> Context.call(eq(oracleScore.getAddress()), eq("get_reference_data"), eq("USD"), eq("ICX"
		))).thenReturn(priceData);
		contextMock.when(() -> Context.call(BigInteger.class, dexScore.getAddress(), "getBalnPrice")).thenReturn(newBalnPriceInUsd);

		balancedToken.invoke(owner, "priceInLoop");
		verify(balancedTokenSpy).OraclePrice("BALNICX", "Balanced DEX", dexScore.getAddress(), newBalnPriceInIcx);
		assertEquals(BigInteger.valueOf(sm.getBlock().getTimestamp()), balancedToken.call("getPriceUpdateTime"));
		assertEquals(newBalnPriceInIcx, balancedToken.call("priceInLoop"));

		assertEquals(newBalnPriceInIcx, balancedToken.call("lastPriceInLoop"));
	}

	@Test
	void setAndGetMinimumStake() {
		BigInteger minStake = (BigInteger) balancedToken.call("getMinimumStake");

		assertNotNull(minStake);
		assertEquals(BigInteger.valueOf(1000000000000000000L), minStake);

		BigInteger newMinStake = TEN.add(TWO).add(ONE);
		balancedToken.invoke(governance, "setAdmin", governance.getAddress());

		balancedToken.invoke(governance, "setMinimumStake", newMinStake);

		minStake = (BigInteger) balancedToken.call("getMinimumStake");

		assertNotNull(minStake);
		assertEquals(new BigInteger("13000000000000000000"), minStake);

	}

	@SuppressWarnings("unchecked")
	@Test
	void ShouldMint() {
		balancedToken.invoke(governance, "setAdmin", adminAccount.getAddress());
		balancedToken.invoke(owner, "setMinter", adminAccount.getAddress());

		BigInteger amount = BigInteger.valueOf(10000L);
		balancedToken.invoke(adminAccount, "mint", amount, "init gold".getBytes());
		Map<String, BigInteger>  balanceDetails = (Map<String, BigInteger>)balancedToken.call("detailsBalanceOf", adminAccount.getAddress());

		assertNotNull(balanceDetails);
		assertEquals(amount , balanceDetails.get("Total balance"));
		assertEquals(amount , balanceDetails.get("Available balance"));
	}

	@SuppressWarnings("unchecked")
	@Test
	void ShouldMintToSomeone() {
		Account accountToMint = sm.createAccount();
		balancedToken.invoke(governance, "setAdmin", adminAccount.getAddress());

		BigInteger amount = BigInteger.valueOf(10000L);
		balancedToken.invoke(owner, "setMinter", adminAccount.getAddress());

		balancedToken.invoke(adminAccount, "mintTo", accountToMint.getAddress(), amount, "init gold".getBytes());

		Map<String, BigInteger>  balanceDetails = (Map<String, BigInteger>)balancedToken.call("detailsBalanceOf", accountToMint.getAddress());
		assertNotNull(balanceDetails);
		assertEquals(amount , balanceDetails.get("Total balance"));
		assertEquals(amount , balanceDetails.get("Available balance"));
	}

	@SuppressWarnings("unchecked")
	@Test
	void ShouldMintAndStake() {
		//set admin
//		balancedToken.invoke(governance, "setAdmin", adminAccount.getAddress());

		//mint some tokens
		BigInteger amountToMint = BigInteger.valueOf(10000L).multiply(ICX);
		balancedToken.invoke(owner, "setMinter", adminAccount.getAddress());
		balancedToken.invoke(governance, "setAdmin", governance.getAddress());

		balancedToken.invoke(adminAccount, "mint", amountToMint, "init gold".getBytes());

		//enable staking
		balancedToken.invoke(governance, "toggleStakingEnabled");
		BigInteger stakedAmount = amountToMint.divide(TWO);
		balancedToken.invoke(governance, "setDex", mockDexScore.getAddress());
		contextMock.when(() -> Context.call(BigInteger.class, mockDexScore.getAddress(), "getTimeOffset")).thenReturn(BigInteger.valueOf(500));
		balancedToken.invoke(owner, "setTimeOffset");
		//stake
		balancedToken.invoke(adminAccount, "stake", stakedAmount);

		Map<String, BigInteger> balanceDetails = (Map<String, BigInteger>) balancedToken.call("detailsBalanceOf",
				adminAccount.getAddress());

		assertNotNull(balanceDetails);
		assertEquals(amountToMint , balanceDetails.get("Total balance"));
		assertEquals(amountToMint.divide(TWO) , balanceDetails.get("Available balance"));
		assertEquals(amountToMint.divide(TWO) , balanceDetails.get("Staked balance"));
	}

	@SuppressWarnings("unchecked")
	@Test
	void ShouldGetATransferAndStake() {
		//set admin
		balancedToken.invoke(governance, "setAdmin", adminAccount.getAddress());
		balancedToken.invoke(owner, "setMinter", adminAccount.getAddress());

		//mint some tokens
		BigInteger amountToMint =  BigInteger.valueOf(10000L).multiply(ICX);
		balancedToken.invoke(adminAccount, "mint", amountToMint, "init gold".getBytes());

		//transfer
		Account user = sm.createAccount();
		BigInteger amountToTransfer = amountToMint.divide(TWO).divide(TWO);
		balancedToken.invoke(adminAccount, "transfer", user.getAddress(), amountToTransfer, "quezadillas".getBytes());

		//enable staking
		balancedToken.invoke(governance, "toggleStakingEnabled");
		BigInteger stakedAmount = amountToTransfer.divide(TWO);
		balancedToken.invoke(governance, "setDex", mockDexScore.getAddress());
		contextMock.when(() -> Context.call(BigInteger.class, mockDexScore.getAddress(), "getTimeOffset")).thenReturn(BigInteger.valueOf(500));
		balancedToken.invoke(owner, "setTimeOffset");
		//stake
		balancedToken.invoke(user, "stake", stakedAmount);

		Map<String, BigInteger>  balanceDetails = (Map<String, BigInteger>)balancedToken.call("detailsBalanceOf", user.getAddress());

		assertNotNull(balanceDetails);
		assertEquals(amountToTransfer , balanceDetails.get("Total balance"));

		assertEquals(amountToTransfer.divide(TWO), balancedToken.call("availableBalanceOf", user.getAddress()));
		assertEquals(amountToTransfer.divide(TWO), balancedToken.call("stakedBalanceOf", user.getAddress()));
		assertEquals(ZERO, balancedToken.call("unstakedBalanceOf", user.getAddress()));

	}

	@SuppressWarnings("unchecked")
	@Test
	void ShouldTransfer() {
		//set admin
		balancedToken.invoke(governance, "setAdmin", adminAccount.getAddress());

		//mint some tokens
		BigInteger amountToMint =  BigInteger.valueOf(10000L).multiply(ICX);
		balancedToken.invoke(owner, "setMinter", adminAccount.getAddress());

		balancedToken.invoke(adminAccount, "mint", amountToMint, "init gold".getBytes());

		//enable staking
		balancedToken.invoke(governance, "toggleStakingEnabled");
		BigInteger stakedAmount = amountToMint.divide(TWO);

		balancedToken.invoke(governance, "setDex", mockDexScore.getAddress());

		contextMock.when(() -> Context.call(BigInteger.class, mockDexScore.getAddress(), "getTimeOffset")).thenReturn(BigInteger.valueOf(500));
		balancedToken.invoke(owner, "setTimeOffset");

		//Stake
		balancedToken.invoke(adminAccount, "stake", stakedAmount);

		//transfer
		Account user = sm.createAccount();
		BigInteger amountToTransfer = amountToMint.divide(TWO).divide(TWO);
		balancedToken.invoke(adminAccount, "transfer", user.getAddress(), amountToTransfer, "tacos".getBytes());

		Map<String, BigInteger>  balanceDetails = (Map<String, BigInteger>)balancedToken.call("detailsBalanceOf", user.getAddress());

		assertNotNull(balanceDetails);
		assertEquals(amountToTransfer , balanceDetails.get("Total balance"));
		assertEquals(amountToTransfer , balanceDetails.get("Available balance"));

	}

	@SuppressWarnings("unchecked")
	@Test
	void ShouldBurn() {
		balancedToken.invoke(governance, "setAdmin", adminAccount.getAddress());
		balancedToken.invoke(owner, "setMinter", adminAccount.getAddress());
		BigInteger amount = BigInteger.valueOf(10000L);
		balancedToken.invoke(adminAccount, "mint", amount, "init gold".getBytes());
		Map<String, BigInteger>  balanceDetails = (Map<String, BigInteger>)balancedToken.call("detailsBalanceOf", adminAccount.getAddress());

		assertNotNull(balanceDetails);
		assertEquals(amount , balanceDetails.get("Total balance"));
		assertEquals(amount , balanceDetails.get("Available balance"));

		BigInteger balanceToBurn = amount.divide(TWO);
		balancedToken.invoke(adminAccount, "burn", balanceToBurn);

		balanceDetails = (Map<String, BigInteger>)balancedToken.call("detailsBalanceOf", adminAccount.getAddress());

		assertNotNull(balanceDetails);
		assertEquals(balanceToBurn , balanceDetails.get("Total balance"));
		assertEquals(balanceToBurn , balanceDetails.get("Available balance"));
	}

	@SuppressWarnings("unchecked")
	@Test
	void ShouldBurnFrom() {
		Account accountToMint = sm.createAccount();
		balancedToken.invoke(governance, "setAdmin", adminAccount.getAddress());
		balancedToken.invoke(owner, "setMinter", adminAccount.getAddress());
		BigInteger amount = BigInteger.valueOf(10000L);
		balancedToken.invoke(adminAccount, "mintTo", accountToMint.getAddress(), amount, "init gold".getBytes());
		Map<String, BigInteger>  balanceDetails = (Map<String, BigInteger>)balancedToken.call("detailsBalanceOf", accountToMint.getAddress());

		assertNotNull(balanceDetails);
		assertEquals(amount , balanceDetails.get("Total balance"));
		assertEquals(amount , balanceDetails.get("Available balance"));

		BigInteger balanceToBurn = amount.divide(TWO);
		balancedToken.invoke(adminAccount, "burnFrom", accountToMint.getAddress(), balanceToBurn);

		balanceDetails = (Map<String, BigInteger>)balancedToken.call("detailsBalanceOf", accountToMint.getAddress());

		assertNotNull(balanceDetails);
		assertEquals(balanceToBurn , balanceDetails.get("Total balance"));
		assertEquals(balanceToBurn , balanceDetails.get("Available balance"));
	}

	@SuppressWarnings("unchecked")
	@Test
	void ShouldGetStakedBalanceOfAt() {
		boolean snapshotEnabled = (boolean)balancedToken.call("getSnapshotEnabled");
		if(!snapshotEnabled) {
			//enable snapshot of staked balances
			balancedToken.invoke(owner, "toggleEnableSnapshot");
		}

		//set admin
		balancedToken.invoke(governance, "setAdmin", adminAccount.getAddress());
		balancedToken.invoke(owner, "setMinter", adminAccount.getAddress());

		//mint some tokens
		BigInteger amountToMint =  BigInteger.valueOf(10000L).multiply(ICX);
		balancedToken.invoke(adminAccount, "mintTo", owner.getAddress(), amountToMint, "init gold".getBytes());

		//enable staking
		if( !(boolean)balancedToken.call("getStakingEnabled")) {
			balancedToken.invoke(governance, "toggleStakingEnabled");
		}

		balancedToken.invoke(governance, "setDex", mockDexScore.getAddress());
		BigInteger stakedAmount = amountToMint.divide(TWO);

		//staked balance when there is no stake for this address
		BigInteger day = BigInteger.valueOf(Context.getBlockTimestamp()).divide(Constants.DAY_TO_MICROSECOND);
		BigInteger stakedBalance = (BigInteger)balancedToken.call("stakedBalanceOfAt", owner.getAddress(), day);

		assertNotNull(stakedBalance);
		assertEquals(ZERO, stakedBalance);


		contextMock.when(() -> Context.call(BigInteger.class, mockDexScore.getAddress(), "getTimeOffset")).thenReturn(BigInteger.valueOf(500));
		//stake
		balancedToken.invoke(owner, "stake", stakedAmount);

		day = BigInteger.valueOf(Context.getBlockTimestamp()).divide(Constants.DAY_TO_MICROSECOND);
		stakedBalance = (BigInteger)balancedToken.call("stakedBalanceOfAt", owner.getAddress(), day);

		assertNotNull(stakedBalance);
		assertEquals(stakedAmount, stakedBalance);

		balancedToken.invoke(owner, "setTimeOffset");

		//move the day, so there are 2 snapshots
		sm.getBlock().increase(100_000_000_000L);

		//stake at next day
		BigInteger stakedAmountAtSecondDay = stakedAmount.divide(TWO);
		balancedToken.invoke(owner, "stake", stakedAmountAtSecondDay);

		day = BigInteger.valueOf(Context.getBlockTimestamp()).divide(Constants.DAY_TO_MICROSECOND);
		//ask for staked balance of snapshot 1
		stakedBalance = (BigInteger)balancedToken.call("stakedBalanceOfAt", owner.getAddress(), day);

		assertNotNull(stakedBalance);
		assertEquals(stakedAmountAtSecondDay, stakedBalance);

		//get stake amount of 1st snapshot at 2nd day
		day = BigInteger.valueOf(Context.getBlockTimestamp()).subtract(Constants.DAY_TO_MICROSECOND).divide(Constants.DAY_TO_MICROSECOND);
		//ask for staked balance of snapshot 1
		stakedBalance = (BigInteger)balancedToken.call("stakedBalanceOfAt", owner.getAddress(), day);

		assertNotNull(stakedBalance);
		assertEquals(stakedAmount, stakedBalance);

		Map<String, BigInteger> balanceDetails = (Map<String, BigInteger>)balancedToken.call("detailsBalanceOf", owner.getAddress());
		assertNotNull(balanceDetails);

		assertEquals(amountToMint , balanceDetails.get("Total balance"));
		assertEquals(stakedAmount, balanceDetails.get("Available balance"));
		assertEquals(stakedAmountAtSecondDay, balanceDetails.get("Staked balance"));

		balanceDetails = (Map<String, BigInteger>)balancedToken.call("detailsBalanceOf", owner.getAddress());

		assertEquals(stakedAmountAtSecondDay, balanceDetails.get("Unstaking balance"));
	}


	@Test
	void ShouldGetUnstakingPeriod() {
		BigInteger unstakingPeriod = (BigInteger) balancedToken.call("getUnstakingPeriod");

		assertNotNull(unstakingPeriod);
		assertEquals(TWO.add(ONE), unstakingPeriod);
	}

	@Test
	void ShouldSetUnstakingPeriod() {
		balancedToken.invoke(governance, "setAdmin", governance.getAddress());

		balancedToken.invoke(governance, "setUnstakingPeriod", TEN);

		BigInteger unstakingPeriod = (BigInteger) balancedToken.call("getUnstakingPeriod");

		assertNotNull(unstakingPeriod);
		assertEquals( TEN, unstakingPeriod);

		balancedToken.invoke(governance, "setUnstakingPeriod", ZERO);

		unstakingPeriod = (BigInteger) balancedToken.call("getUnstakingPeriod");

		assertNotNull(unstakingPeriod);
		assertEquals( ZERO, unstakingPeriod);
	}

	@Test
	void ShouldSetDividends() {
		//set admin
		balancedToken.invoke(governance, "setAdmin", governance.getAddress());
		balancedToken.invoke(governance, "setDividends", dividendsScore.getAddress());

		Address dividendsAddress = (Address) balancedToken.call("getDividends");

		assertNotNull(dividendsAddress);
		assertEquals(dividendsScore.getAddress(), dividendsAddress);
	}

	@AfterEach
	void contextClose() {
		contextMock.close();
	}

}
