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

package network.balanced.score.core.loans.utils;

import score.Address;
import score.Context;

import java.math.BigInteger;

import network.balanced.score.core.loans.LoansVariables;

import static network.balanced.score.core.loans.LoansImpl.call;
import static network.balanced.score.core.loans.utils.LoansConstants.BNUSD_SYMBOL;

public  class TokenUtils {

    public static String symbol(Address tokenAddress) {
        return (String) call(tokenAddress, "symbol");
    }

    public static BigInteger decimals(Address tokenAddress) {
        return (BigInteger) call(tokenAddress, "decimals");
    }

    public static BigInteger balanceOf(Address tokenAddress, Address address) {
        return (BigInteger) call(tokenAddress, "balanceOf", address);
    }

    public static BigInteger getBnUSDPriceInLoop() {
        return getBnUSDPriceInLoop(false);
    }

    public static BigInteger getBnUSDPriceInLoop(boolean readOnly) {
        return getPriceInLoop(BNUSD_SYMBOL, readOnly);
    }

    public static BigInteger getPriceInLoop(String symbol, boolean readOnly) {
        if (readOnly) {
            return  Context.call(BigInteger.class, LoansVariables.oracle.get(), "getLastPriceInLoop", symbol);
        }

        return Context.call(BigInteger.class, LoansVariables.oracle.get(), "getPriceInLoop", symbol);
    }

    public static void mintTo(Address bnUSDAddress, Address to, BigInteger amount) {
        call(bnUSDAddress, "mintTo", to, amount, new byte[0]);
    }

    public static void burn(Address bnUSDAddress, BigInteger amount) {
        call(bnUSDAddress, "burn", amount);
    }

    public static void burnFrom(Address bnUSDAddress, Address from, BigInteger amount) {
        call(bnUSDAddress, "burnFrom", from, amount);
    }



}
