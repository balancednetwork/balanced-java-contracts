 
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
import score.VarDB;
import score.DictDB;
import score.ArrayDB;
import score.BranchDB;
import score.Context;

import java.math.BigInteger;

public class TWAPCalculator {
   private static final BranchDB<String, DictDB<BigInteger, BigInteger>> cumulativePrices = Context.newBranchDB("tmp1", BigInteger.class);
   private static final BranchDB<String, ArrayDB<BigInteger>> updateTimestamps = Context.newBranchDB("tmp2", BigInteger.class);
   private static final BranchDB<String, VarDB<Integer>> indexDB = Context.newBranchDB("tmp3", Integer.class);

   public static BigInteger updatePrice(String symbol, BigInteger currentPrice, BigInteger timespan) {
      BigInteger currentTime = BigInteger.valueOf(Context.getBlockTimestamp());
      BigInteger startTime = currentTime.subtract(timespan);
      DictDB<BigInteger, BigInteger> cumulativePrices = TWAPCalculator.cumulativePrices.at(symbol);
      ArrayDB<BigInteger> timestamps = updateTimestamps.at(symbol);
      int numberOfTimestamps = timestamps.size();
      if (numberOfTimestamps == 0) {
         cumulativePrices.set(BigInteger.ZERO, BigInteger.ZERO);
         timestamps.add(BigInteger.ZERO);
         numberOfTimestamps = 1;
      }

      BigInteger lastUpdate = timestamps.get(numberOfTimestamps-1);
      BigInteger addedPriceWeight = currentTime.subtract(lastUpdate).multiply(currentPrice);
      BigInteger lastCumulative = cumulativePrices.getOrDefault(lastUpdate, BigInteger.ZERO);
      BigInteger newPriceWeight = addedPriceWeight.add(lastCumulative);

      cumulativePrices.set(currentTime, newPriceWeight);
      timestamps.add(currentTime);

      BigInteger oldPriceWeight = getCumulativePriceAt(symbol, startTime, timestamps, cumulativePrices);
      BigInteger priceInLoop = newPriceWeight.subtract(oldPriceWeight).divide(timespan);

      return priceInLoop;
   }

   private static BigInteger getCumulativePriceAt(String symbol, BigInteger time, ArrayDB<BigInteger> timestamps, DictDB<BigInteger, BigInteger> cumulativePrices) {
      VarDB<Integer> indexDB = TWAPCalculator.indexDB.at(symbol);
      int index = indexDB.getOrDefault(0);

      BigInteger lastTime = timestamps.get(index);
      BigInteger currentTime = timestamps.get(index);

      while (currentTime.compareTo(time) < 0) {
         index++;
         lastTime = currentTime;
         currentTime = timestamps.get(index);
      }

      BigInteger lastWeight = cumulativePrices.get(lastTime);
      BigInteger currentWeight = cumulativePrices.get(currentTime);
      BigInteger diff = currentWeight.subtract(lastWeight);
      BigInteger timeDiff = currentTime.subtract(lastTime);
      BigInteger price = diff.divide(timeDiff);
      BigInteger activeWeight = lastWeight.add(time.subtract(lastTime).multiply(price));
      indexDB.set(index - 1);

      return activeWeight;
   }
}