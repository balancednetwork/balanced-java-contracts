
package network.balanced.score.core.rewards;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;

import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.*;

import network.balanced.score.lib.interfaces.DataSourceScoreInterface;
import network.balanced.score.lib.interfaces.tokens.MintableScoreInterface;
import network.balanced.score.lib.structs.DistributionPercentage;

import network.balanced.score.lib.test.mock.MockContract;

public class RewardsTestBase extends TestBase {
    protected static final Long DAY = 43200L;

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

    public MockContract<DataSourceScoreInterface> loans;
    public MockContract<DataSourceScoreInterface> dex;
    public MockContract<MintableScoreInterface> baln;
    public MockContract<MintableScoreInterface> bwt;
    public Score rewardsScore;

    void setup() throws Exception {
        dex = new MockContract<DataSourceScoreInterface>(DataSourceScoreInterface.class, sm, admin);
        loans = new MockContract<DataSourceScoreInterface>(DataSourceScoreInterface.class, sm, admin);
        baln = new MockContract<MintableScoreInterface>(MintableScoreInterface.class, sm, admin);
        bwt = new MockContract<MintableScoreInterface>(MintableScoreInterface.class, sm, admin);
                
        rewardsScore = sm.deploy(owner, RewardsImpl.class, governance.getAddress());
        rewardsScore.invoke(owner, "setAdmin", admin.getAddress());

        rewardsScore.invoke(governance, "addNewDataSource", "sICX/ICX", dex.getAddress());
        rewardsScore.invoke(governance, "addNewDataSource", "Loans", loans.getAddress());

        rewardsScore.invoke(admin, "setBaln", baln.getAddress());
        rewardsScore.invoke(admin, "setBwt", bwt.getAddress());
        setupDistributions();
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


    public void mockBalanceAndSupply(MockContract<DataSourceScoreInterface> dataSource, String name, Address address, BigInteger balance, BigInteger supply) {
        Map<String, BigInteger> balanceAndSupply = Map.of(
            "_balance", balance,
            "_totalSupply", supply
        );

        when(dataSource.mock.getBalanceAndSupply(name, address)).thenReturn(balanceAndSupply);
    }
}
