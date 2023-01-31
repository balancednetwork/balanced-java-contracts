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

package network.balanced.score.core.dex.utils;

import score.Context;

import java.math.BigInteger;

import static network.balanced.score.core.dex.DexDBVariables.dexOn;
import static network.balanced.score.core.dex.DexDBVariables.nonce;
import static network.balanced.score.core.dex.utils.Const.TAG;

public class Check {

    public static void isDexOn() {
        Context.require(dexOn.getOrDefault(false), "NotLaunched: Function cannot be called " +
                "before the DEX is turned on");
    }

    public static void isValidPoolId(BigInteger id) {
        isValidPoolId(id.intValue());
    }

    public static void isValidPoolId(Integer id) {
        Context.require(id > 0 && id <= nonce.get(), TAG + ": Invalid pool ID");
    }
}
