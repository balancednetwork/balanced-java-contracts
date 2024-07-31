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

import com.eclipsesource.json.JsonObject;
import foundation.icon.icx.KeyWallet;
import foundation.icon.jsonrpc.Address;
import foundation.icon.score.client.DefaultScoreClient;
import network.balanced.score.lib.interfaces.*;
import network.balanced.score.lib.interfaces.tokens.IRC2Mintable;
import network.balanced.score.lib.interfaces.tokens.IRC2MintableScoreClient;
import network.balanced.score.lib.interfaces.tokens.SpokeToken;
import network.balanced.score.lib.interfaces.tokens.SpokeTokenScoreClient;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.test.integration.BalancedUtils.hexObjectToBigInteger;
import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.chain;

public class BalancedClient {
    private final Balanced balanced;
    public final KeyWallet wallet;

    public GovernanceScoreClient governance;
    public StakingScoreClient staking;
    public BalancedDollarScoreClient bnUSD;
    public DAOfundScoreClient daofund;
    public RewardsScoreClient rewards;
    public LoansScoreClient loans;
    public BalancedTokenScoreClient baln;
    public BoostedBalnScoreClient boostedBaln;
    public RebalancingScoreClient rebalancing;
    public SicxScoreClient sicx;
    public DexScoreClient dex;
    public StabilityScoreClient stability;
    public StakedLPScoreClient stakedLp;
    public DividendsScoreClient dividends;
    public ReserveScoreClient reserve;
    public BalancedOracleScoreClient balancedOracle;
    public AssetManagerScoreClient assetManager;
    public SavingsScoreClient savings;
    public XCallMockScoreClient xcall;
    public XCallManagerScoreClient xcallManager;
    public FeeHandlerScoreClient feeHandler;
    public SystemInterfaceScoreClient systemScore;

    public BalancedClient(Balanced balanced, KeyWallet wallet) {
        this.wallet = wallet;
        this.balanced = balanced;
        governance = new GovernanceScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                balanced.governance._address());
        staking = new StakingScoreClient(chain.getEndpointURL(), chain.networkId, wallet, balanced.staking._address());
        bnUSD = new BalancedDollarScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                balanced.bnusd._address());
        daofund = new DAOfundScoreClient(chain.getEndpointURL(), chain.networkId, wallet, balanced.daofund._address());
        rewards = new RewardsScoreClient(chain.getEndpointURL(), chain.networkId, wallet, balanced.rewards._address());
        loans = new LoansScoreClient(chain.getEndpointURL(), chain.networkId, wallet, balanced.loans._address());
        baln = new BalancedTokenScoreClient(chain.getEndpointURL(), chain.networkId, wallet, balanced.baln._address());
        boostedBaln = new BoostedBalnScoreClient(chain.getEndpointURL(), chain.networkId, wallet, balanced.bBaln._address());
        rebalancing = new RebalancingScoreClient(chain.getEndpointURL(), chain.networkId, wallet, balanced.rebalancing._address());
        sicx = new SicxScoreClient(chain.getEndpointURL(), chain.networkId, wallet, balanced.sicx._address());
        dex = new DexScoreClient(chain.getEndpointURL(), chain.networkId, wallet, balanced.dex._address());
        stability = new StabilityScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                balanced.stability._address());
        stakedLp = new StakedLPScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                balanced.stakedLp._address());
        dividends = new DividendsScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                balanced.dividends._address());
        reserve = new ReserveScoreClient(chain.getEndpointURL(), chain.networkId, wallet, balanced.reserve._address());
        balancedOracle = new BalancedOracleScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                balanced.balancedOracle._address());
        assetManager = new AssetManagerScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                balanced.assetManager._address());
        savings = new SavingsScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                balanced.savings._address());
        xcall = new XCallMockScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                balanced.xcall._address());
        xcallManager = new XCallManagerScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                balanced.xcall._address());
        feeHandler = new FeeHandlerScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                balanced.feehandler._address());
        systemScore = new SystemInterfaceScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                DefaultScoreClient.ZERO_ADDRESS);
    }

    public score.Address getAddress() {
        return score.Address.fromString(wallet.getAddress().toString());
    }

    public IRC2Mintable irc2(score.Address address) {
        return new IRC2MintableScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                new Address(address.toString()));
    }

    public SpokeToken spokeToken(score.Address address) {
        return new SpokeTokenScoreClient(chain.getEndpointURL(), chain.networkId, wallet,
                new Address(address.toString()));
    }

    public byte[] createBorrowData(BigInteger amount) {
        JsonObject data = new JsonObject()
                .add("_asset", "bnUSD")
                .add("_amount", amount.toString());

        return data.toString().getBytes();
    }

    public void stakeDepositAndBorrow(BigInteger collateral, BigInteger amount) {
        // Add approximately 10% extra to compensate for staking rewards
        BigInteger stakeAmount = collateral.multiply(BigInteger.TEN).divide(BigInteger.valueOf(9));
        staking.stakeICX(stakeAmount, null, null);
        byte[] params = createBorrowData(amount);
        sicx.transfer(balanced.loans._address(), collateral, params);
    }

    public void depositAndBorrow(Address collateralAddress, BigInteger collateral, BigInteger amount) {
        byte[] params = createBorrowData(amount);
        irc2(collateralAddress).transfer(balanced.loans._address(), collateral, params);
    }

    public void borrowFrom(String collateral, BigInteger amount) {
        loans.borrow(collateral, "bnUSD", amount, null, null);
    }

    @SuppressWarnings("unchecked")
    public BigInteger getLoansCollateralPosition(String symbol) {
        Map<String, Map<String, String>> assets =
                (Map<String, Map<String, String>>) loans.getAccountPositions(getAddress().toString()).get("holdings");
        if (!assets.containsKey(symbol)) {
            return BigInteger.ZERO;
        }
        return hexObjectToBigInteger(assets.get(symbol).get(symbol));
    }

    @SuppressWarnings("unchecked")
    public BigInteger getLoansAssetPosition(String collateralSymbol, String assetSymbol) {
        Map<String, Map<String, String>> assets =
                (Map<String, Map<String, String>>) loans.getAccountPositions(getAddress().toString()).get("holdings");
        if (!assets.containsKey(collateralSymbol) || !assets.get(collateralSymbol).containsKey(assetSymbol)) {
            return BigInteger.ZERO;
        }
        return hexObjectToBigInteger(assets.get(collateralSymbol).get(assetSymbol));
    }
}
