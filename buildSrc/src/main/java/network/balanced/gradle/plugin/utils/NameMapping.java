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

package network.balanced.gradle.plugin.utils;

public enum NameMapping {
    multicall("Multicall"),
    disbursement("BatchDisbursement"),
    reserve("Reserve"),
    dividends("Dividends"),
    rebalancing("Rebalancing"),
    rewards("Rewards"),
    stakedLp("StakedLP"),
    worker_token("WorkerToken"),
    router("Router"),
    daofund("DAOfund"),
    bnUSD("BalancedDollar"),
    stability("Stability"),
    loans("Loans"),
    governance("Governance"),
    oracle("DummyOracle"),
    dex("Dex"),
    staking("Staking"),
    sicx("Sicx"),
    baln("BalancedToken"),
    feehandler("FeeHandler"),
    balancedoracle("BalancedOracle"),
    bBaln("bBaln");

    private final String module;

    NameMapping(String module) {
        this.module = module;
    }

    public String toString() {
        return this.module;
    }
}
