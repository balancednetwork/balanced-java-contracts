package network.balanced.score.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigInteger;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;

import score.Context;

public class BalancedTokenTest extends TestBase {

	private static ServiceManager sm = getServiceManager();
	private static Account owner = sm.createAccount();

	//private BigInteger decimals = BigInteger.TEN;
	private static BigInteger totalSupply = BigInteger.valueOf(50000000000L);

	private Score balancedToken;
	private Account accountAddressProvider = sm.createAccount();
	private Account accountGovernance = sm.createAccount();

	private Account dexAccount = sm.createAccount();
	private Account oracleAccount = sm.createAccount();

	@BeforeAll
	public static void init() {
		owner.addBalance(BalancedToken.SYMBOL_NAME, totalSupply);
	}

	@BeforeEach
	public void setup() throws Exception {
		balancedToken = sm.deploy(owner, BalancedToken.class, accountGovernance.getAddress(), false);
	}

	@Test
	void shouldAllowToSetOracleName() {
		String newOracleName = "icon oracle";
		String oracleName = (String)balancedToken.call("getOracleName");
		
		assertNotNull(oracleName);
		assertEquals(BalancedToken.DEFAULT_ORACLE_NAME, oracleName);

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
	void ShouldGetActualPriceInLoop() {
		try(MockedStatic<Context> theMock = Mockito.mockStatic(Context.class)){

			balancedToken.invoke(accountGovernance, "setDex", dexAccount.getAddress());
			balancedToken.invoke(accountGovernance, "setOracle", oracleAccount.getAddress());

			theMock
			.when(() -> Context.getBlockTimestamp())
			.thenReturn(34834316634l);

			theMock
			.when(() -> Context.call(BigInteger.class, dexAccount.getAddress(), "getBalnPrice"))
			.thenReturn(BigInteger.valueOf(10));

			theMock
			.when(() -> Context.call(Map.class, oracleAccount.getAddress(),"get_reference_data","USD", "ICX"))
			.thenReturn(Map.of("rate", BigInteger.valueOf(35l)));

			BigInteger price = (BigInteger) balancedToken.call("priceInLoop");
			assertNotNull(price);
		}
	}
}
