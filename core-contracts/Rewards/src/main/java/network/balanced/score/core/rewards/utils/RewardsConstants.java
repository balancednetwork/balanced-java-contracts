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

package network.balanced.score.core.rewards.utils;

import network.balanced.score.lib.utils.Constants;

import java.math.BigInteger;

public class RewardsConstants extends Constants {
    public static final int DEFAULT_BATCH_SIZE = 50;
    public static final String DATASOURCE_DB_PREFIX = "datasource";

    public static final String WORKER_TOKENS = "Worker Tokens";
    public static final String RESERVE_FUND = "Reserve Fund";
    public static final String DAOFUND = "DAOfund";

    public static final String TOTAL_SUPPLY = "_totalSupply";
    public static final String BALANCE = "_balance";

    public static final BigInteger HUNDRED_PERCENTAGE = EXA;
    public static final String IDS = "ids";
    public static final String AMOUNT = "amount";
}