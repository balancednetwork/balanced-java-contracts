/*
 * Copyright (c) 2022-2022 Balanced.network.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http,//www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package network.balanced.score.core.governance;

import static java.util.Map.entry;
import static network.balanced.score.lib.utils.Math.pow;

import java.math.BigInteger;
import java.util.Map;

import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.utils.Constants;

public class GovernanceConstants extends Constants {
    public static final String TAG = "Governance";

    public static final BigInteger U_SECONDS_DAY = BigInteger.valueOf(86400).multiply(pow(BigInteger.TEN,6));
    public static final BigInteger MAJORITY = new BigInteger("666666666666666667", 10);
    public static final BigInteger DAY_ZERO = BigInteger.valueOf(18647);
    public static final BigInteger DAY_START = BigInteger.valueOf(61200).multiply(pow(BigInteger.TEN, 6));// 17:00 UTC
    public static final BigInteger BALNBNUSD_ID = BigInteger.valueOf(3);
    public static final BigInteger BALNSICX_ID =  BigInteger.valueOf(4);

    public static final String LAUNCH_DAY = "launch_day";
    public static final String LAUNCH_TIME = "launch_time";
    public static final String LAUNCHED = "launched";
    public static final String REBALANCING = "rebalancing";
    public static final String TIME_OFFSET = "time_offset";
    public static final String VOTE_DURATION = "vote_duration";
    public static final String MIN_BALN = "min_baln";
    public static final String DEFINITION_FEE = "definition_fee";
    public static final String QUORUM = "quorum";


    public static String[] CONTRACTS = {"loans", "dex", "staking", "rewards", "dividends", "daofund",
            "reserve", "sicx", "bnUSD", "baln", "bwt", "router", "feehandler", "stakedLp"};

    public static Map<String, String> ADMIN_ADDRESSES = Map.ofEntries(
        entry("loans", "governance"),
        entry("dex", "governance"),
        entry("rewards", "governance"),
        entry("dividends", "governance"),
        entry("daofund", "governance"),
        entry("reserve", "governance"),
        entry("bnUSD", "loans"),
        entry("baln", "rewards"),
        entry("bwt", "governance"),
        entry("router", "governance"),
        entry("stakedLp", "governance")
    );

// #-------------------------------------------------------------------------------
// # REWARDS LAUNCH CONFIG
// #-------------------------------------------------------------------------------

    public static Map<String, String>[] DATA_SOURCES = (Map<String, String>[]) new Map[] {
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
public static DistributionPercentage[] RECIPIENTS = new DistributionPercentage[] {
        createDistributionPercentage("Loans",  BigInteger.valueOf(25).multiply(pow(BigInteger.TEN,16))),
        createDistributionPercentage("sICX/ICX",  BigInteger.TEN.multiply(pow(BigInteger.TEN,16))),
        createDistributionPercentage("Worker Tokens",  BigInteger.valueOf(20).multiply(pow(BigInteger.TEN,16))),
        createDistributionPercentage("Reserve Fund",  BigInteger.valueOf(5).multiply(pow(BigInteger.TEN,16))),
        createDistributionPercentage("DAOfund",  BigInteger.valueOf(40).multiply(pow(BigInteger.TEN,16)))
    };


// #-------------------------------------------------------------------------------
// # LOANS LAUNCH CONFIG
// #-------------------------------------------------------------------------------
    public static Map<String, Object>[] ASSETS =  (Map<String, Object>[]) new Map[] {
        Map.of("address", "sicx", "active", true, "collateral", true),
        Map.of("address", "bnUSD", "active", true, "collateral", false),
        Map.of("address", "baln", "active", false, "collateral", true)
    };


}
