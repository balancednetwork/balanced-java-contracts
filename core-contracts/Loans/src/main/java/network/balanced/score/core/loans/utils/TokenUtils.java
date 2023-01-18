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

package network.balanced.score.core.loans.utils;

import score.Address;

import java.math.BigInteger;

import static network.balanced.score.core.loans.LoansImpl.call;
import static network.balanced.score.lib.utils.BalancedAddressManager.getBalancedOracle;
import static network.balanced.score.lib.utils.BalancedAddressManager.getBnusd;
import static network.balanced.score.lib.utils.Check.readonly;

public class TokenUtils {
    public static String symbol(Address tokenAddress) {
        return (String) call(tokenAddress, "symbol");
    }

    public static BigInteger decimals(Address tokenAddress) {
        return (BigInteger) call(tokenAddress, "decimals");
    }

    public static BigInteger balanceOf(Address tokenAddress, Address address) {
        return (BigInteger) call(tokenAddress, "balanceOf", address);
    }

    public static BigInteger getPriceInUSD(String symbol) {
        if (readonly()) {
            return (BigInteger) call(getBalancedOracle(), "getLastPriceInUSD", symbol);
        }

        return (BigInteger) call(getBalancedOracle(), "getPriceInUSD", symbol);
    }

    public static void mintAssetTo(Address to, BigInteger amount) {
        call(getBnusd(), "mintTo", to, amount, new byte[0]);
    }

    public static void burnAsset(BigInteger amount) {
        call(getBnusd(), "burn", amount);
    }

    public static void burnAssetFrom(Address from, BigInteger amount) {
        call(getBnusd(), "burnFrom", from, amount);
    }
}
