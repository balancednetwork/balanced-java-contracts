
package network.balanced.score.core.rewards;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.Map;

import static org.mockito.Mockito.*;

import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.interfaces.tokens.MintableScoreInterface;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.test.UnitTest;

import network.balanced.score.lib.test.mock.MockContract;

public class RewardsTestBase extends UnitTest {
    protected static final Long DAY = 43200L;
    protected static final BigInteger EXA = BigInteger.TEN.pow(18);

    protected static final ServiceManager sm = getServiceManager();
    protected static final Account owner = sm.createAccount();
    protected static final Account admin = sm.createAccount();

    DistributionPercentage loansDist = new DistributionPercentage();
    DistributionPercentage icxPoolDist = new DistributionPercentage();
    DistributionPercentage bwtDist = new DistributionPercentage();
    DistributionPercentage reserveDist = new DistributionPercentage();
    DistributionPercentage daoDist = new DistributionPercentage();

    int scoreCount = 0;
    protected final Account governance = Account.newScoreAccount(scoreCount++);
    protected final Account daoFund = Account.newScoreAccount(scoreCount++);
    protected final Account reserve = Account.newScoreAccount(scoreCount++);

    public MockContract<DataSourceScoreInterface> dex;
    public MockContract<DataSourceScoreInterface> loans;
    public MockContract<MintableScoreInterface> baln;
    public MockContract<MintableScoreInterface> bwt;
    public Score rewardsScore;

    void setup() throws Exception {
        dex = new MockContract<DataSourceScoreInterface>(DataSourceScoreInterface.class, sm, admin);
        loans = new MockContract<DataSourceScoreInterface>(DataSourceScoreInterface.class, sm, admin);
        baln = new MockContract<MintableScoreInterface>(MintableScoreInterface.class, sm, admin);
        bwt = new MockContract<MintableScoreInterface>(MintableScoreInterface.class, sm, admin);
        
        rewardsScore = sm.deploy(owner, RewardsImpl.class, governance.getAddress());

        rewardsScore.invoke(governance, "setAdmin", admin.getAddress());
        sm.getBlock().increase(DAY);

        rewardsScore.invoke(governance, "addNewDataSource", "sICX/ICX", dex.getAddress());
        rewardsScore.invoke(governance, "addNewDataSource", "Loans", loans.getAddress());

        Map<String, BigInteger> emptyDataSource = Map.of(
            "_balance", BigInteger.ZERO,
            "_totalSupply", BigInteger.ZERO
        );
        when(loans.mock.getBalanceAndSupply(any(String.class), any(Address.class))).thenReturn(emptyDataSource);
        when(dex.mock.getBalanceAndSupply(any(String.class), any(Address.class))).thenReturn(emptyDataSource);
        when(loans.mock.precompute(any(Integer.class), any(Integer.class))).thenReturn(true);
        when(dex.mock.precompute(any(Integer.class), any(Integer.class))).thenReturn(true);
        
        rewardsScore.invoke(admin, "setBaln", baln.getAddress());
        rewardsScore.invoke(admin, "setBwt", bwt.getAddress());
        rewardsScore.invoke(admin, "setDaofund", daoFund.getAddress());
        rewardsScore.invoke(admin, "setReserve", reserve.getAddress());

        setupDistributions();
        
        syncDistributions();
    }

    private void setupDistributions() {
        loansDist.recipient_name = "Loans";
        loansDist.dist_percent = ICX.divide(BigInteger.valueOf(5)); //20%

        icxPoolDist.recipient_name = "sICX/ICX";
        icxPoolDist.dist_percent = ICX.divide(BigInteger.valueOf(5)); //20%

        bwtDist.recipient_name = "Worker Tokens";
        bwtDist.dist_percent = ICX.divide(BigInteger.valueOf(5)); //20%

        reserveDist.recipient_name = "Reserve Fund";
        reserveDist.dist_percent = ICX.divide(BigInteger.valueOf(5)); //20%

        daoDist.recipient_name = "DAOfund";
        daoDist.dist_percent = ICX.divide(BigInteger.valueOf(5)); //20%

        DistributionPercentage[] distributionPercentages = new DistributionPercentage[]{loansDist, icxPoolDist, bwtDist, reserveDist, daoDist};

        rewardsScore.invoke(governance, "updateBalTokenDistPercentage", (Object) distributionPercentages);
    }

    public void syncDistributions() {
        for (long i = 0; i < sm.getBlock().getHeight()/(DAY); i++) {
            rewardsScore.invoke(admin, "distribute");
            rewardsScore.invoke(admin, "distribute");
        }
    }

    public void mockBalanceAndSupply(MockContract<DataSourceScoreInterface> dataSource, String name, Address address, BigInteger balance, BigInteger supply) {
        Map<String, BigInteger> balanceAndSupply = Map.of(
            "_balance", balance,
            "_totalSupply", supply
        );

        when(dataSource.mock.getBalanceAndSupply(name, address)).thenReturn(balanceAndSupply);
    }

    public void verifyBalnReward(Address address, BigInteger expectedReward) {
        verify(baln.mock, times(1)).transfer(eq(address), argThat(reward -> {
            assertEquals(expectedReward.divide(BigInteger.TEN), reward.divide(BigInteger.TEN));
            return true;
        }), eq(new byte[0]));
    }

    public void snapshotDistributionPercentage() {
        Object distributionPercentages = new DistributionPercentage[]{loansDist, icxPoolDist, bwtDist, reserveDist, daoDist};

        rewardsScore.invoke(governance, "updateBalTokenDistPercentage", distributionPercentages);
        sm.getBlock().increase(DAY);
    }
}
