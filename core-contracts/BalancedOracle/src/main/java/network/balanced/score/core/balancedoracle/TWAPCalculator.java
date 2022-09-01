 
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

import score.*;

import java.math.BigInteger;

public class TWAPCalculator {
    private static final BranchDB<String, DictDB<BigInteger, BigInteger>> cumulativeWeights = Context.newBranchDB(
            "cummulative_weights", BigInteger.class);
    private static final BranchDB<String, ArrayDB<BigInteger>> updateTimestamps = Context.newBranchDB(
            "price_update_timestamps", BigInteger.class);
    private static final BranchDB<String, VarDB<Integer>> indexDB = Context.newBranchDB("timestamp_indexes",
            Integer.class);
    private static final BranchDB<String, VarDB<BigInteger>> lastPrices = Context.newBranchDB("last_prices",
            BigInteger.class);

    public static BigInteger updatePrice(String symbol, BigInteger currentPrice, BigInteger timespan) {
        DictDB<BigInteger, BigInteger> cumulativeWeights = TWAPCalculator.cumulativeWeights.at(symbol);
        ArrayDB<BigInteger> timestamps = updateTimestamps.at(symbol);

        int numberOfTimestamps = timestamps.size();
        if (numberOfTimestamps == 0) {
            cumulativeWeights.set(BigInteger.ZERO, BigInteger.ZERO);
            timestamps.add(BigInteger.ZERO);
            numberOfTimestamps = 1;
        }

        BigInteger currentTime = BigInteger.valueOf(Context.getBlockTimestamp());
        BigInteger lastUpdate = timestamps.get(numberOfTimestamps - 1);
        BigInteger timeSinceLastUpdate = currentTime.subtract(lastUpdate);


        VarDB<BigInteger> lastPrice = lastPrices.at(symbol);

        BigInteger price = lastPrice.getOrDefault(BigInteger.ZERO);

        BigInteger addedPriceWeight = timeSinceLastUpdate.multiply(price);
        BigInteger lastCumulative = cumulativeWeights.getOrDefault(lastUpdate, BigInteger.ZERO);
        BigInteger newPriceWeight = addedPriceWeight.add(lastCumulative);

        cumulativeWeights.set(currentTime, newPriceWeight);
        lastPrice.set(currentPrice);
        timestamps.add(currentTime);

        BigInteger startTime = currentTime.subtract(timespan);
        BigInteger startPriceWeight = getCumulativePriceAt(symbol, startTime, timestamps, cumulativeWeights);
        BigInteger priceInLoop = newPriceWeight.subtract(startPriceWeight).divide(timespan);

        if (numberOfTimestamps < 2) {
            return currentPrice;
        }

        return priceInLoop;
    }

    private static BigInteger getCumulativePriceAt(String symbol, BigInteger time, ArrayDB<BigInteger> timestamps,
                                                   DictDB<BigInteger, BigInteger> cumulativeWeights) {
        VarDB<Integer> indexDB = TWAPCalculator.indexDB.at(symbol);
        int index = indexDB.getOrDefault(0);

        BigInteger lastTime = BigInteger.ZERO;
        BigInteger currentTime = timestamps.get(index);

        // if timespan has been increased
        while (currentTime.compareTo(time) >= 0) {
            index--;
            currentTime = timestamps.get(index);
        }

        while (currentTime.compareTo(time) < 0) {
            index++;
            lastTime = currentTime;
            currentTime = timestamps.get(index);
        }

        BigInteger lastWeight = cumulativeWeights.get(lastTime);
        BigInteger currentWeight = cumulativeWeights.get(currentTime);
        BigInteger weightDiff = currentWeight.subtract(lastWeight);
        BigInteger timeDiff = currentTime.subtract(lastTime);
        BigInteger price = weightDiff.divide(timeDiff);

        BigInteger activeWeight = lastWeight.add(time.subtract(lastTime).multiply(price));
        indexDB.set(index - 1);

        return activeWeight;
    }
}