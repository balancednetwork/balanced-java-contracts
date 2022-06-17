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

package network.balanced.score.lib.test.integration;

import foundation.icon.icx.KeyWallet;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.*;

import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.chain;

public class BalancedClient {

    private final KeyWallet wallet;

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
    public SystemInterfaceScoreClient systemScore;

    public BalancedClient(Balanced balanced, KeyWallet wallet) {
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
        systemScore = new SystemInterfaceScoreClient(chain.getEndpointURL(), chain.networkId, wallet, DefaultScoreClient.ZERO_ADDRESS);
    }
    
    public score.Address getAddress() {
        return score.Address.fromString(wallet.getAddress().toString());
    }
}
