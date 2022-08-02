 
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

import score.DictDB;
import score.Context;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Math.exaPow;

public class EMACalculator {
    private static final DictDB<String, BigInteger> movingAverages = Context.newDictDB(
            "exponential_moving_averages", BigInteger.class);
    private static final DictDB<String, BigInteger> lastUpdateBlock = Context.newDictDB(
            "last_update_blocks", BigInteger.class);
    private static final DictDB<String, BigInteger> previousPrices = Context.newDictDB("previous_prices",
            BigInteger.class);

    public static BigInteger updateEMA(String symbol, BigInteger currentPrice, BigInteger alpha) {
        BigInteger lastBlock = lastUpdateBlock.get(symbol);
        BigInteger currentBlock = BigInteger.valueOf(Context.getBlockHeight());
        if (lastBlock == null) {
            lastUpdateBlock.set(symbol, currentBlock);
            movingAverages.set(symbol, currentPrice);
            previousPrices.set(symbol, currentPrice);

            return currentPrice;
        }

        BigInteger blockDiff = currentBlock.subtract(lastBlock);
        BigInteger currentMovingAverage = movingAverages.get(symbol);

        if (blockDiff.equals(BigInteger.ZERO)) {
            return currentMovingAverage;
        }

        BigInteger price = previousPrices.get(symbol);
        previousPrices.set(symbol, currentPrice);

        BigInteger weight = exaPow(EXA.subtract(alpha), blockDiff.intValue());
        BigInteger priceChange = price.subtract(currentMovingAverage);
        BigInteger newMovingAverage = price.subtract(priceChange.multiply(weight).divide(EXA));

        lastUpdateBlock.set(symbol, currentBlock);
        movingAverages.set(symbol, newMovingAverage);

        return newMovingAverage;
    }

    public static BigInteger calculateEMA(String symbol, BigInteger alpha) {
        BigInteger lastBlock = lastUpdateBlock.get(symbol);
        BigInteger currentBlock = BigInteger.valueOf(Context.getBlockHeight());

        BigInteger blockDiff = currentBlock.subtract(lastBlock);
        BigInteger currentMovingAverage = movingAverages.get(symbol);

        if (blockDiff.equals(BigInteger.ZERO)) {
            return currentMovingAverage;
        }

        BigInteger price = previousPrices.get(symbol);

        BigInteger weight = exaPow(EXA.subtract(alpha), blockDiff.intValue());
        BigInteger priceChange = price.subtract(currentMovingAverage);
        BigInteger newMovingAverage = price.subtract(priceChange.multiply(weight).divide(EXA));
        
        return newMovingAverage;
    }
}