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
import score.Context;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.BalancedAddressManager.getBalancedOracle;
import static network.balanced.score.lib.utils.BalancedAddressManager.getBnusd;

public class TokenUtils {
    public static String symbol(Address tokenAddress) {
        return (String) Context.call(tokenAddress, "symbol");
    }

    public static BigInteger decimals(Address tokenAddress) {
        return (BigInteger) Context.call(tokenAddress, "decimals");
    }

    public static BigInteger balanceOf(Address tokenAddress, Address address) {
        return (BigInteger) Context.call(tokenAddress, "balanceOf", address);
    }

    public static BigInteger getPriceInUSD(String symbol) {
        return (BigInteger) Context.call(getBalancedOracle(), "getLastPriceInUSD", symbol);
    }

    public static void mintAsset(BigInteger amount) {
        Context.call(getBnusd(), "mint", amount, new byte[0]);
    }

    public static void mintAssetTo(Address to, BigInteger amount) {
        Context.call(getBnusd(), "mintTo", to, amount, new byte[0]);
    }

    public static void burnAsset(BigInteger amount) {
        Context.call(getBnusd(), "burn", amount);
    }

    public static void burnAssetFrom(Address from, BigInteger amount) {
        Context.call(getBnusd(), "burnFrom", from, amount);
    }

    public static void crossTransfer(BigInteger fee, String to, BigInteger amount) {
        Context.call(fee, getBnusd(), "crossTransfer", to, amount, new byte[0]);
    }

    public static void hubTransfer(String to, BigInteger amount) {
        Context.call(getBnusd(), "hubTransfer", to, amount, new byte[0]);
    }

    public static void transfer(Address to, BigInteger amount) {
        Context.call(getBnusd(), "transfer", to, amount, new byte[0]);
    }
}
