/*
 * Copyright (c) 2022-2023 Balanced.network.
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

package network.balanced.score.core.rebalancing;

import java.math.BigInteger;

public class Constants {
    public static final String TAG = "Rebalancing";

    public static final String BNUSD_ADDRESS = "bnUSD_address";
    public static final String SICX_ADDRESS = "sicx_address";
    public static final String DEX_ADDRESS = "dex_address";
    public static final String LOANS_ADDRESS = "loans_address";
    public static final String ORACLE_ADDRESS = "oracle_address";
    public static final String GOVERNANCE_ADDRESS = "governance_address";
    public static final String ADMIN = "admin";
    public static final String PRICE_THRESHOLD = "_price_threshold";
    public static final String VERSION = "version";

    public static final BigInteger SICX_BNUSD_POOL_ID = BigInteger.TWO;
}
