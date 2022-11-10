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

package network.balanced.score.core.governance.utils;

import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.utils.Constants;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import network.balanced.score.lib.utils.Names;

import static java.util.Map.entry;
import static network.balanced.score.lib.utils.Math.pow;

public class GovernanceConstants extends Constants {
    public static final String TAG = Names.GOVERNANCE;
    public static final int successfulVoteExecutionRevertID = 20;

    public static final BigInteger MAJORITY = new BigInteger("666666666666666667", 10);
    public static final BigInteger DAY_ZERO = BigInteger.valueOf(18647);
    public static final BigInteger DAY_START = BigInteger.valueOf(61200).multiply(pow(BigInteger.TEN, 6));// 17:00 UTC
    public static final BigInteger BALNBNUSD_ID = BigInteger.valueOf(3);
    public static final BigInteger BALNSICX_ID = BigInteger.valueOf(4);

    public static final String LAUNCH_DAY = "launch_day";
    public static final String LAUNCH_TIME = "launch_time";
    public static final String LAUNCHED = "launched";
    public static final String REBALANCING = "rebalancing";
    public static final String TIME_OFFSET = "time_offset";
    public static final String MAX_VOTE_DURATION = "vote_duration_max";
    public static final String MIN_VOTE_DURATION = "vote_duration_min";
    public static final String MIN_BALN = "min_baln";
    public static final String DEFINITION_FEE = "definition_fee";
    public static final String QUORUM = "quorum";

    public static String[] CONTRACTS = {"loans", "dex", "staking", "rewards", "dividends", "daofund",
            "reserve", "sicx", "bnUSD", "baln", "bwt", "router", "feehandler", "stakedLp", "rebalancing", "bBaln",
            "balancedOracle"};

    public static Map<String, List<String>> ADDRESSES = Map.ofEntries(
            entry("loans", List.of("rewards", "dividends", "staking", "reserve", "dex", "rebalancing",
                    "balancedOracle")),
            entry("dex", List.of("rewards", "dividends", "staking", "sicx", "bnUSD", "baln", "feehandler", "stakedLp")),
            entry("rewards", List.of("reserve", "baln", "bwt", "daofund", "stakedLp", "bBaln")),
            entry("dividends", List.of("loans", "daofund", "dex", "baln")),
            entry("daofund", List.of("loans")),
            entry("reserve", List.of("loans", "baln", "sicx")),
            entry("bnUSD", List.of("oracle")),
            entry("baln", List.of("dividends", "oracle", "dex", "bnUSD")),
            entry("bwt", List.of("baln")),
            entry("router", List.of("dex", "sicx", "staking")),
            entry("stakedLp", List.of("dex", "rewards")),
            entry("rebalancing", List.of("loans", "dex", "bnUSD", "sicx", "balancedOracle")),
            entry("balancedOracle", List.of("dex", "staking", "oracle"))
    );

    public static Map<String, String> ADMIN_ADDRESSES = Map.ofEntries(
            entry("loans", "governance"),
            entry("dex", "governance"),
            entry("rewards", "governance"),
            entry("dividends", "governance"),
            entry("reserve", "governance"),
            entry("bnUSD", "loans"),
            entry("baln", "rewards"),
            entry("bwt", "governance"),
            entry("router", "governance"),
            entry("rebalancing", "governance"),
            entry("balancedOracle", "governance")
    );

    public static Map<String, String> oldNamesMap = Map.ofEntries(
        entry("loans", Names.LOANS),
        entry("dex", Names.DEX),
        entry("rewards", Names.REWARDS),
        entry("dividends", Names.DIVIDENDS),
        entry("daofund", Names.DAOFUND),
        entry("reserve", Names.RESERVE),
        entry("baln", Names.BALN),
        entry("bwt", Names.WORKERTOKEN),
        entry("router", Names.ROUTER),
        entry("stakedLp", Names.STAKEDLP),
        entry("rebalancing", Names.REBALANCING),
        entry("balancedOracle", Names.BALANCEDORACLE),
        entry("oracle", Names.ORACLE),
        entry("staking", Names.STAKING),
        entry("sicx", Names.SICX),
        entry("bnUSD", Names.BNUSD),
        entry("feehandler", Names.FEEHANDLER),
        entry("bBaln", Names.BOOSTED_BALN)

    );

    public static Map<String, String> SETTERS = Map.ofEntries(
            entry("loans", "setLoans"),
            entry("dex", "setDex"),
            entry("staking", "setStaking"),
            entry("rewards", "setRewards"),
            entry("reserve", "setReserve"),
            entry("dividends", "setDividends"),
            entry("daofund", "setDaofund"),
            entry("oracle", "setOracle"),
            entry("sicx", "setSicx"),
            entry("bnUSD", "setBnusd"),
            entry("baln", "setBaln"),
            entry("bwt", "setBwt"),
            entry("rebalancing", "setRebalance"),
            entry("router", "setRouter"),
            entry("feehandler", "setFeehandler"),
            entry("stakedLp", "setStakedLp"),
            entry("bBaln", "setBoostedBaln"),
            entry("balancedOracle", "setOracle")
    );

    // #-------------------------------------------------------------------------------
    // # REWARDS LAUNCH CONFIG
    // #-------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public static Map<String, String>[] DATA_SOURCES = (Map<String, String>[]) new Map[]{
            Map.of("name", "Loans", "address", "loans"),
            Map.of("name", "sICX/ICX", "address", "dex")
    };

    public static DistributionPercentage createDistributionPercentage(String name, BigInteger percentage) {
        DistributionPercentage recipient = new DistributionPercentage();
        recipient.recipient_name = name;
        recipient.dist_percent = percentage;
        return recipient;
    }

    // # First day rewards recipients split
    public static DistributionPercentage[] RECIPIENTS = new DistributionPercentage[]{
            createDistributionPercentage("Loans", BigInteger.valueOf(25).multiply(pow(BigInteger.TEN, 16))),
            createDistributionPercentage("sICX/ICX", BigInteger.TEN.multiply(pow(BigInteger.TEN, 16))),
            createDistributionPercentage("Worker Tokens", BigInteger.valueOf(20).multiply(pow(BigInteger.TEN, 16))),
            createDistributionPercentage("Reserve Fund", BigInteger.valueOf(5).multiply(pow(BigInteger.TEN, 16))),
            createDistributionPercentage("DAOfund", BigInteger.valueOf(40).multiply(pow(BigInteger.TEN, 16)))
    };

    // #-------------------------------------------------------------------------------
    // # LOANS LAUNCH CONFIG
    // #-------------------------------------------------------------------------------
    @SuppressWarnings("unchecked")
    public static Map<String, Object>[] ASSETS = (Map<String, Object>[]) new Map[]{
            Map.of("address", "sicx", "active", true, "collateral", true),
            Map.of("address", "bnUSD", "active", true, "collateral", false),
            Map.of("address", "baln", "active", false, "collateral", true)
    };


}
