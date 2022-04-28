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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.Map;

import static java.math.BigInteger.*;
import static network.balanced.score.token.balancedtoken.Constants.DEFAULT_ORACLE_NAME;
import static network.balanced.score.token.balancedtoken.Constants.SYMBOL_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BalancedTokenImplTest extends TestBase {

	private static final ServiceManager sm = getServiceManager();
	private static final Account owner = sm.createAccount();

	//private BigInteger decimals = BigInteger.TEN;
	private static final BigInteger totalSupply = BigInteger.valueOf(50000000000L);

	private Score balancedToken;
	private Score mockDexScore;
	private final Account accountAddressProvider = sm.createAccount();
	private final Account accountGovernance = sm.createAccount();
	private final Account adminAccount = sm.createAccount();

	private final Account dexAccount = sm.createAccount();
	private final Account oracleAccount = sm.createAccount();

	@BeforeAll
	static void init() {
		owner.addBalance(SYMBOL_NAME, totalSupply);
	}

	@BeforeEach
	void setup() throws Exception {
		balancedToken = sm.deploy(owner, BalancedTokenImpl.class, accountGovernance.getAddress());
		mockDexScore = sm.deploy(owner, MockDexScore.class);
	}

	@Test
	void shouldAllowToSetOracleName() {
		String newOracleName = "icon oracle";
		String oracleName = (String)balancedToken.call("getOracleName");
		
		assertNotNull(oracleName);
		assertEquals(DEFAULT_ORACLE_NAME, oracleName);

		balancedToken.invoke(accountGovernance, "setOracleName", newOracleName);

		oracleName = (String)balancedToken.call("getOracleName");
		assertNotNull(oracleName);
		assertEquals(newOracleName, oracleName);
	}

	@Test
	void shouldNotAllowToSetOracleName() {
		AssertionError error = Assertions.assertThrows(AssertionError.class, 
				() -> balancedToken.invoke(accountAddressProvider, "setOracleName", "failed assigment"));
		assertEquals(
				"Reverted(0): BALN: SenderNotGovernanceError: (sender)"+accountAddressProvider.getAddress()+" (governance)"+accountGovernance.getAddress(),
				error.getMessage());
	}

	@Test
	void ShouldSetAndGetActualPriceInLoop() {
		try(MockedStatic<Context> theMock = Mockito.mockStatic(Context.class)){

			balancedToken.invoke(accountGovernance, "setDex", dexAccount.getAddress());
			balancedToken.invoke(accountGovernance, "setOracle", oracleAccount.getAddress());

			theMock
			.when(Context::getBlockTimestamp)
			.thenReturn(34834316634L);

			BigInteger balnPrice = BigInteger.TEN.subtract(BigInteger.ONE);
			theMock
			.when(() -> Context.call(BigInteger.class, dexAccount.getAddress(), "getBalnPrice"))
			.thenReturn(balnPrice);

			BigInteger rate = BigInteger.valueOf(3500000000000000000L);
			theMock
			.when(() -> Context.call(Map.class, oracleAccount.getAddress(),"get_reference_data","USD", "ICX"))
			.thenReturn(Map.of("rate", rate));

			//the icon test fwk has an issue where it does not allow returning
			//values from write method if they actually write any value.
			//first call, it will update last price
			balancedToken.invoke(accountAddressProvider, "priceInLoop");
			//second call it will return the last price only
			BigInteger price = (BigInteger) balancedToken.call("priceInLoop");
			assertNotNull(price);
			BigInteger expectedPrice = rate.multiply(balnPrice).divide(ICX);
			assertEquals(expectedPrice, price);
		}
	}

	@Test
	void ShouldGetLastPriceInLoop() {
		try(MockedStatic<Context> theMock = Mockito.mockStatic(Context.class)){

			balancedToken.invoke(accountGovernance, "setDex", dexAccount.getAddress());
			balancedToken.invoke(accountGovernance, "setOracle", oracleAccount.getAddress());

			theMock
			.when(Context::getBlockTimestamp)
			.thenReturn(34834316634L);

			BigInteger balnPrice = BigInteger.TEN.subtract(BigInteger.ONE);
			theMock
			.when(() -> Context.call(BigInteger.class, dexAccount.getAddress(), "getBalnPrice"))
			.thenReturn(balnPrice);

			BigInteger rate = BigInteger.valueOf(3500000000000000000L);
			theMock
			.when(() -> Context.call(Map.class, oracleAccount.getAddress(),"get_reference_data","USD", "ICX"))
			.thenReturn(Map.of("rate", rate));

			BigInteger price = (BigInteger) balancedToken.call("lastPriceInLoop");
			assertNotNull(price);
			BigInteger expectedPrice = rate.multiply(balnPrice).divide(ICX);
			assertEquals(expectedPrice, price);
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	void ShouldMint() {
		balancedToken.invoke(accountGovernance, "setAdmin", adminAccount.getAddress());
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
		balancedToken.invoke(accountGovernance, "setAdmin", adminAccount.getAddress());

		BigInteger amount = BigInteger.valueOf(10000L);
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
		balancedToken.invoke(accountGovernance, "setAdmin", adminAccount.getAddress());

		//mint some tokens
		BigInteger amountToMint = BigInteger.valueOf(10000L).multiply(ICX);
		balancedToken.invoke(adminAccount, "mint", amountToMint, "init gold".getBytes());

		//enable staking
		balancedToken.invoke(accountGovernance, "toggleStakingEnabled");
		BigInteger stakedAmount = amountToMint.divide(TWO);
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
		balancedToken.invoke(accountGovernance, "setAdmin", adminAccount.getAddress());

		//mint some tokens
		BigInteger amountToMint =  BigInteger.valueOf(10000L).multiply(ICX);
		balancedToken.invoke(adminAccount, "mint", amountToMint, "init gold".getBytes());

		//transfer
		Account user = sm.createAccount();
		BigInteger amountToTransfer = amountToMint.divide(TWO).divide(TWO);
		balancedToken.invoke(adminAccount, "transfer", user.getAddress(), amountToTransfer, "quezadillas".getBytes());

		//enable staking
		balancedToken.invoke(accountGovernance, "toggleStakingEnabled");
		BigInteger stakedAmount = amountToTransfer.divide(TWO);
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
		balancedToken.invoke(accountGovernance, "setAdmin", adminAccount.getAddress());

		//mint some tokens
		BigInteger amountToMint =  BigInteger.valueOf(10000L).multiply(ICX);
		balancedToken.invoke(adminAccount, "mint", amountToMint, "init gold".getBytes());

		//enable staking
		balancedToken.invoke(accountGovernance, "toggleStakingEnabled");
		BigInteger stakedAmount = amountToMint.divide(TWO);
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
		balancedToken.invoke(accountGovernance, "setAdmin", adminAccount.getAddress());
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
		balancedToken.invoke(accountGovernance, "setAdmin", adminAccount.getAddress());
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
		balancedToken.invoke(accountGovernance, "setAdmin", adminAccount.getAddress());

		//mint some tokens
		BigInteger amountToMint =  BigInteger.valueOf(10000L).multiply(ICX);
		balancedToken.invoke(adminAccount, "mintTo", owner.getAddress(), amountToMint, "init gold".getBytes());

		//enable staking
		if( !(boolean)balancedToken.call("getStakingEnabled")) {
			balancedToken.invoke(accountGovernance, "toggleStakingEnabled");
		}

		balancedToken.invoke(accountGovernance, "setDex", mockDexScore.getAddress());
		BigInteger stakedAmount = amountToMint.divide(TWO);

		//staked balance when there is no stake for this address
		BigInteger day = BigInteger.valueOf(Context.getBlockTimestamp()).divide(Constants.DAY_TO_MICROSECOND);
		BigInteger stakedBalance = (BigInteger)balancedToken.call("stakedBalanceOfAt", owner.getAddress(), day);

		assertNotNull(stakedBalance);
		assertEquals(ZERO, stakedBalance);

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
		balancedToken.invoke(accountGovernance, "setUnstakingPeriod", TEN);

		BigInteger unstakingPeriod = (BigInteger) balancedToken.call("getUnstakingPeriod");

		assertNotNull(unstakingPeriod);
		assertEquals( TEN, unstakingPeriod);

		balancedToken.invoke(accountGovernance, "setUnstakingPeriod", ZERO);

		unstakingPeriod = (BigInteger) balancedToken.call("getUnstakingPeriod");

		assertNotNull(unstakingPeriod);
		assertEquals( ZERO, unstakingPeriod);
	}

	@Test
	void ShouldSetMinimumStake() {
		BigInteger minStake = (BigInteger) balancedToken.call("getMinimumStake");

		assertNotNull(minStake);
		assertEquals(BigInteger.valueOf(1000000000000000000L), minStake);

		BigInteger newMinStake = TEN.add(TWO).add(ONE);
		balancedToken.invoke(accountGovernance, "setMinimumStake", newMinStake);

		minStake = (BigInteger) balancedToken.call("getMinimumStake");

		assertNotNull(minStake);
		assertEquals(new BigInteger("13000000000000000000"), minStake);

	}

	@Test
	void ShouldSetDividends() {
		Account dividendsAccount = sm.createAccount();
		balancedToken.invoke(accountGovernance, "setDividends", dividendsAccount.getAddress());

		Address dividendsAddress = (Address) balancedToken.call("getDividends");

		assertNotNull(dividendsAddress);
		assertEquals(dividendsAccount.getAddress(), dividendsAddress);
	}

	@Test
	void ShouldRevertWhenNoDividends() {
		try {
			balancedToken.invoke(owner, "dividendsOnly");
		}catch (Error e) {
			assertEquals("Reverted(0): BALN: This method can only be called by the dividends distribution contract.",
					e.getMessage());
		}
	}

}
