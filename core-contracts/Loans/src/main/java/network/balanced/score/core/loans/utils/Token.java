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

import java.math.BigInteger;

import network.balanced.score.core.loans.LoansVariables;

import static network.balanced.score.core.loans.LoansImpl.call;

public class Token {

    private final Address tokenAddress;

    public Token(Address tokenAddress) {
        this.tokenAddress = tokenAddress;
    }

    public String symbol() {
        return (String) call(tokenAddress, "symbol");
    }

    public BigInteger decimals() {
        return (BigInteger) call(tokenAddress, "decimals");
    }

    public BigInteger totalSupply() {
        return (BigInteger) call(tokenAddress, "totalSupply");
    }

    public BigInteger balanceOf(Address address) {
        return (BigInteger) call(tokenAddress, "balanceOf", address);
    }

    public String getPeg() {
        return (String) call(tokenAddress, "getPeg");
    }

    public BigInteger priceInLoop() {
        return (BigInteger) call(LoansVariables.oracle.get(), "getPriceInLoop", symbol());
    }

    public BigInteger lastPriceInLoop() {
        return (BigInteger) call(LoansVariables.oracle.get(), "getLastPriceInLoop", symbol());
    }

    public void mintTo(Address to, BigInteger amount) {
        call(tokenAddress, "mintTo", to, amount, new byte[0]);
    }
}
