package network.balanced.score.lib.test.integration;
import foundation.icon.icx.KeyWallet;
import foundation.icon.jsonrpc.Address;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.interfaces.base.*;
import network.balanced.score.lib.interfaces.tokens.*;
import network.balanced.score.lib.interfaces.tokens.IRC2Mintable;
import network.balanced.score.lib.structs.BalancedAddresses;

import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.*;
import static network.balanced.score.lib.test.integration.BalancedUtils.*;

import java.math.BigInteger;
import java.util.Map;

import com.eclipsesource.json.JsonObject;

public class BalancedClient {
    private Balanced balanced;
    public KeyWallet wallet;

    @ScoreClient
    private Governance _governance;

    @ScoreClient
    private Staking _staking;

    @ScoreClient
    private BalancedDollar _bnUSD;
    
    @ScoreClient
    private DAOfund _daofund;

    @ScoreClient
    private static Rewards _rewards;

    @ScoreClient
    private static Loans _loans;

    @ScoreClient
    private static Baln _baln;
    
    @ScoreClient
    private static Rebalancing _rebalancing;

    @ScoreClient
    private static Sicx _sicx;

    @ScoreClient
    private static Dex _dex;

    @ScoreClient
    private static Stability _stability;

    @ScoreClient
    private static StakedLP _stakedLp;

    @ScoreClient
    private static Dividends _dividends;

    @ScoreClient
    private static Reserve _reserve;

    @ScoreClient
    private static BalancedOracle _balancedOracle;

    @ScoreClient
    private static IRC2Mintable _irc2Mintable;

    @ScoreClient
    private static SystemInterface _systemScore;

    public GovernanceScoreClient governance;
    public StakingScoreClient staking;
    public BalancedDollarScoreClient bnUSD;
    public DAOfundScoreClient daofund;
    public RewardsScoreClient rewards;
    public LoansScoreClient loans;
    public BalnScoreClient baln;
    public RebalancingScoreClient rebalancing;
    public SicxScoreClient sicx;
    public DexScoreClient dex;
    public StabilityScoreClient stability;
    public StakedLPScoreClient stakedLp;
    public DividendsScoreClient dividends;
    public ReserveScoreClient reserve;
    public BalancedOracleScoreClient balancedOracle;
    public SystemInterfaceScoreClient systemScore;

    public BalancedClient(Balanced balanced, KeyWallet wallet) {
        this.balanced = balanced;
        this.wallet = wallet;
        governance = new GovernanceScoreClient(chain.getEndpointURL(), chain.networkId, wallet, balanced.governance._address());
        staking = new StakingScoreClient(chain.getEndpointURL(), chain.networkId, wallet, balanced.staking._address());
        bnUSD = new BalancedDollarScoreClient(chain.getEndpointURL(), chain.networkId, wallet, balanced.bnusd._address());
        daofund = new DAOfundScoreClient(chain.getEndpointURL(), chain.networkId, wallet, balanced.daofund._address());
        rewards = new RewardsScoreClient(chain.getEndpointURL(), chain.networkId, wallet, balanced.rewards._address());
        loans = new LoansScoreClient(chain.getEndpointURL(), chain.networkId, wallet, balanced.loans._address());
        baln = new BalnScoreClient(chain.getEndpointURL(), chain.networkId, wallet, balanced.baln._address());
        rebalancing = new RebalancingScoreClient(chain.getEndpointURL(), chain.networkId, wallet, balanced.rebalancing._address());
        sicx = new SicxScoreClient(chain.getEndpointURL(), chain.networkId, wallet, balanced.sicx._address());
        dex = new DexScoreClient(chain.getEndpointURL(), chain.networkId, wallet, balanced.dex._address());
        stability = new StabilityScoreClient(chain.getEndpointURL(), chain.networkId, wallet, balanced.stability._address());
        stakedLp = new StakedLPScoreClient(chain.getEndpointURL(), chain.networkId, wallet, balanced.stakedLp._address());
        dividends = new DividendsScoreClient(chain.getEndpointURL(), chain.networkId, wallet, balanced.dividends._address());
        reserve = new ReserveScoreClient(chain.getEndpointURL(), chain.networkId, wallet, balanced.reserve._address());
        balancedOracle = new BalancedOracleScoreClient(chain.getEndpointURL(), chain.networkId, wallet, balanced.balancedOracle._address());

        systemScore = new SystemInterfaceScoreClient(chain.getEndpointURL(), chain.networkId, wallet, DefaultScoreClient.ZERO_ADDRESS);
    }
    
    public score.Address getAddress() {
        return score.Address.fromString(wallet.getAddress().toString());
    }

    public IRC2Mintable irc2(score.Address address) {
       return new IRC2MintableScoreClient(chain.getEndpointURL(), chain.networkId, wallet, new Address(address.toString()));
    }

    public byte[] createBorrowData(BigInteger amount) {
        JsonObject data = new JsonObject()
            .add("_asset", "bnUSD")
            .add("_amount", amount.toString());

        return data.toString().getBytes();
    }

    public void stakeDepositAndBorrow(BigInteger collateral, BigInteger amount) {
        staking.stakeICX(collateral, null, null);
        byte[] params = createBorrowData(amount);
        sicx.transfer(balanced.loans._address(), collateral, params);
    }

    public void depositAndBorrow(Address collateralAddress, BigInteger collateral, BigInteger amount) {
        byte[] params = createBorrowData(amount);
        irc2(collateralAddress).transfer(balanced.loans._address(), collateral, params);
    }

    public void borrowFrom(String collateral, BigInteger amount) {
        byte[] params = createBorrowData(amount);
        loans.borrow(collateral, "bnUSD", amount, null);
    }

    public BigInteger getLoansCollateralPosition(String symbol) {
        Map<String, Map<String, String>> assets = (Map<String, Map<String, String>>) loans.getAccountPositions(getAddress()).get("assets");
        if (!assets.containsKey(symbol)) {
            return BigInteger.ZERO;
        }
        return hexObjectToBigInteger(assets.get(symbol).get(symbol));
    }

    public BigInteger getLoansAssetPosition(String collateralSymbol, String assetSymbol) {
        Map<String, Map<String, String>> assets = (Map<String, Map<String, String>>) loans.getAccountPositions(getAddress()).get("assets");
        if (!assets.containsKey(collateralSymbol) || !assets.get(collateralSymbol).containsKey(assetSymbol) ) {
            return BigInteger.ZERO;
        }
        return hexObjectToBigInteger(assets.get(collateralSymbol).get(assetSymbol));
    }
}
