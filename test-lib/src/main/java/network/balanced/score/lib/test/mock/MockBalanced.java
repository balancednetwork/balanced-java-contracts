package network.balanced.score.lib.test.mock;

import static org.mockito.Mockito.when;

import java.math.BigInteger;

import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.ServiceManager;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.lib.utils.BalancedAddressManager;
import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.interfaces.*;

public class MockBalanced {
    private static MockedStatic<BalancedAddressManager> addressManagerMock;
    public MockContract<Loans> loans;
    public MockContract<Dex> dex;
    public MockContract<Staking> staking;
    public MockContract<Rewards> rewards;
    public MockContract<Reserve> reserve;
    public MockContract<Dividends> dividends; 
    public MockContract<DAOfund> daofund;
    public MockContract<Sicx> sicx;
    public MockContract<BalancedDollar> bnUSD; 
    public MockContract<BalancedToken> baln;
    public MockContract<WorkerToken> bwt;
    public MockContract<Router> router; 
    public MockContract<Rebalancing> rebalancing;
    public MockContract<FeeHandler> feehandler;
    public MockContract<StakedLP> stakedLp;
    public MockContract<Stability> stability;
    public MockContract<Oracle> oracle;
    public MockContract<BalancedOracle> balancedOracle;
    public MockContract<Governance> governance;

    public MockBalanced(ServiceManager sm, Account owner) throws Exception {
        loans = new MockContract<>(LoansScoreInterface.class, Loans.class, sm, owner);
        dex = new MockContract<>(DexScoreInterface.class, Dex.class, sm, owner);
        staking = new MockContract<>(StakingScoreInterface.class, Staking.class, sm, owner);
        rewards = new MockContract<>(RewardsScoreInterface.class, Rewards.class, sm, owner);
        reserve = new MockContract<>(ReserveScoreInterface.class, Reserve.class, sm, owner);
        dividends = new MockContract<>(DividendsScoreInterface.class, Dividends.class, sm, owner);
        daofund = new MockContract<>(DAOfundScoreInterface.class, DAOfund.class, sm, owner);
        sicx = new MockContract<>(SicxScoreInterface.class, Sicx.class, sm, owner);
        bnUSD = new MockContract<>(BalancedDollarScoreInterface.class, BalancedDollar.class, sm, owner);
        baln = new MockContract<>(BalancedTokenScoreInterface.class, BalancedToken.class, sm, owner);
        bwt = new MockContract<>(WorkerTokenScoreInterface.class, WorkerToken.class, sm, owner);
        router = new MockContract<>(RouterScoreInterface.class, Router.class, sm, owner);
        rebalancing = new MockContract<>(RebalancingScoreInterface.class, Rebalancing.class, sm, owner);
        feehandler = new MockContract<>(FeeHandlerScoreInterface.class, FeeHandler.class, sm, owner);
        stakedLp = new MockContract<>(StakedLPScoreInterface.class, StakedLP.class, sm, owner);
        stability = new MockContract<>(StabilityScoreInterface.class, Stability.class, sm, owner);
        oracle = new MockContract<>(OracleScoreInterface.class, Oracle.class, sm, owner);
        balancedOracle = new MockContract<>(BalancedOracleScoreInterface.class, BalancedOracle.class, sm, owner);
        governance = new MockContract<>(GovernanceScoreInterface.class, Governance.class, sm, owner);

        if (addressManagerMock != null) {
            addressManagerMock.close();
        }

        addressManagerMock = Mockito.mockStatic(BalancedAddressManager.class, Mockito.CALLS_REAL_METHODS);
        addressManagerMock.when(() -> BalancedAddressManager.fetchAddress(Names.LOANS)).thenReturn(loans.getAddress());
        addressManagerMock.when(() -> BalancedAddressManager.fetchAddress(Names.DEX)).thenReturn(dex.getAddress());
        addressManagerMock.when(() -> BalancedAddressManager.fetchAddress(Names.STAKING)).thenReturn(staking.getAddress());
        addressManagerMock.when(() -> BalancedAddressManager.fetchAddress(Names.REWARDS)).thenReturn(rewards.getAddress());
        addressManagerMock.when(() -> BalancedAddressManager.fetchAddress(Names.RESERVE)).thenReturn(reserve.getAddress());
        addressManagerMock.when(() -> BalancedAddressManager.fetchAddress(Names.DIVIDENDS)).thenReturn(dividends.getAddress());
        addressManagerMock.when(() -> BalancedAddressManager.fetchAddress(Names.DAOFUND)).thenReturn(daofund.getAddress());
        addressManagerMock.when(() -> BalancedAddressManager.fetchAddress(Names.ORACLE)).thenReturn(oracle.getAddress());
        addressManagerMock.when(() -> BalancedAddressManager.fetchAddress(Names.BALANCEDORACLE)).thenReturn(balancedOracle.getAddress());
        addressManagerMock.when(() -> BalancedAddressManager.fetchAddress(Names.SICX)).thenReturn(sicx.getAddress());
        addressManagerMock.when(() -> BalancedAddressManager.fetchAddress(Names.BNUSD)).thenReturn(bnUSD.getAddress());
        addressManagerMock.when(() -> BalancedAddressManager.fetchAddress(Names.BALN)).thenReturn(baln.getAddress());
        addressManagerMock.when(() -> BalancedAddressManager.fetchAddress(Names.WORKERTOKEN)).thenReturn(bwt.getAddress());
        addressManagerMock.when(() -> BalancedAddressManager.fetchAddress(Names.ROUTER)).thenReturn(router.getAddress());
        addressManagerMock.when(() -> BalancedAddressManager.fetchAddress(Names.REBALANCING)).thenReturn(rebalancing.getAddress());
        addressManagerMock.when(() -> BalancedAddressManager.fetchAddress(Names.FEEHANDLER)).thenReturn(feehandler.getAddress());
        addressManagerMock.when(() -> BalancedAddressManager.fetchAddress(Names.STAKEDLP)).thenReturn(stakedLp.getAddress());
        addressManagerMock.when(() -> BalancedAddressManager.fetchAddress(Names.STABILITY)).thenReturn(stability.getAddress());
        addressManagerMock.when(() -> BalancedAddressManager.fetchAddress(Names.BALANCEDORACLE)).thenReturn(balancedOracle.getAddress());

        when(bnUSD.mock.symbol()).thenReturn("bnUSD");
        when(sicx.mock.symbol()).thenReturn("sICX");
        when(baln.mock.symbol()).thenReturn("BALN");

        when(bnUSD.mock.decimals()).thenReturn(BigInteger.valueOf(18));
        when(sicx.mock.decimals()).thenReturn(BigInteger.valueOf(18));
        when(baln.mock.decimals()).thenReturn(BigInteger.valueOf(18));
    }
}
