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

package network.balanced.score.tokens.balancedtoken;

import network.balanced.score.lib.utils.Names;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Constants.*;
import static network.balanced.score.lib.utils.Math.pow;

public interface Constants {

    BigInteger MIN_UPDATE_TIME = BigInteger.TWO.multiply(MICRO_SECONDS_IN_A_SECOND); //2 seconds

    // String IDS = "ids";
    // String AMOUNT = "amount";
    String TAG = "BALN";
    String TOKEN_NAME = Names.BALN;
    String SYMBOL_NAME = "BALN";
    String VERSION = "version";
    String MINTER = "admin";

}
