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
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.interfaces.SystemInterface;
import network.balanced.score.lib.interfaces.SystemInterfaceScoreClient;
import network.balanced.score.lib.interfaces.base.*;
import network.balanced.score.lib.structs.BalancedAddresses;

import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.*;

import java.math.BigInteger;

public class BalancedClient {
    private Balanced balanced;
    private KeyWallet wallet;


    @ScoreClient
    public Governance governance;

    @ScoreClient
    public Staking staking;

    @ScoreClient
    public BalancedDollar bnUSD;

    @ScoreClient
    public DAOfund daofund;

    @ScoreClient
    public Rewards rewards;

    @ScoreClient
    public Loans loans;

    @ScoreClient
    public Baln baln;

    @ScoreClient
    public Rebalancing rebalancing;

    @ScoreClient
    public Sicx sicx;

    @ScoreClient
    public Dex dex;

    @ScoreClient
    public Stability stability;

    @ScoreClient
    public StakedLP stakedLp;

    @ScoreClient
    public Dividends dividends;

    @ScoreClient
    public SystemInterface systemScore;

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
        systemScore = new SystemInterfaceScoreClient(chain.getEndpointURL(), chain.networkId, wallet, DefaultScoreClient.ZERO_ADDRESS);
    }

    public score.Address getAddress() {
        return score.Address.fromString(wallet.getAddress().toString());
    }
}
