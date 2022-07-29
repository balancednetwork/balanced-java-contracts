 
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

package network.balanced.score.core.balancedoracle;

import score.BranchDB;
import score.Context;
import score.VarDB;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Math.exaPow;

public class EMACalculator {
    private static final BranchDB<String, VarDB<BigInteger>> movingAverages = Context.newBranchDB(
            "exponential_moving_averages", BigInteger.class);
    private static final BranchDB<String, VarDB<BigInteger>> lastUpdateBlock = Context.newBranchDB(
            "last_update_blocks", BigInteger.class);
    private static final BranchDB<String, VarDB<BigInteger>> previousPrices = Context.newBranchDB("previous_prices",
            BigInteger.class);

    public static BigInteger updateEMA(String symbol, BigInteger currentPrice, BigInteger alpha) {
        VarDB<BigInteger> lastUpdate = lastUpdateBlock.at(symbol);
        VarDB<BigInteger> movingAverage = movingAverages.at(symbol);
        BigInteger lastBlock = lastUpdate.get();

        BigInteger currentBlock = BigInteger.valueOf(Context.getBlockHeight());
        if (lastBlock == null) {
            lastUpdate.set(currentBlock);
            movingAverage.set(currentPrice);
            previousPrices.at(symbol).set(currentPrice);

            return currentPrice;
        }

        BigInteger blockDiff = currentBlock.subtract(lastBlock);
        BigInteger currentMovingAverage = movingAverage.get();

        if (blockDiff.equals(BigInteger.ZERO)) {
            return currentMovingAverage;
        }

        VarDB<BigInteger> previousPrice = previousPrices.at(symbol);
        BigInteger price = previousPrice.getOrDefault(BigInteger.ZERO);
        previousPrice.set(currentPrice);

        BigInteger weight = exaPow(EXA.subtract(alpha), blockDiff.intValue());
        BigInteger priceChange = price.subtract(currentMovingAverage);
        BigInteger newMovingAverage = price.subtract(priceChange.multiply(weight).divide(EXA));

        lastUpdate.set(currentBlock);
        movingAverage.set(newMovingAverage);

        return newMovingAverage;
    }
}