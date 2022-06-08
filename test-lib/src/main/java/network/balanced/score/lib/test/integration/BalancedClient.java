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
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.*;

import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.chain;

public class BalancedClient {
    private Balanced balanced;
    private KeyWallet wallet;

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

    public GovernanceScoreClient governance;
    public StakingScoreClient staking;
    public BalancedDollarScoreClient bnUSD;
    public DAOfundScoreClient daofund;
    public RewardsScoreClient rewards;
    public LoansScoreClient loans;
    public BalnScoreClient baln;
    public RebalancingScoreClient rebalancing;

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
    }
    
    public score.Address getAddress() {
        return score.Address.fromString(wallet.getAddress().toString());
    }
}
