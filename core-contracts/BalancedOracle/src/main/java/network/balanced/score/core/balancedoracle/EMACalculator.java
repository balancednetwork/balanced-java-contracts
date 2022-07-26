 
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
import score.BranchDB;
import score.Context;

import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Math.exaPow;
import java.math.BigInteger;

public class EMACalculator {
   private static final BranchDB<String, VarDB<BigInteger>> movingAverages = Context.newBranchDB("tmp1", BigInteger.class);
   private static final BranchDB<String, VarDB<BigInteger>> lastUpdateBlock = Context.newBranchDB("tmp2", BigInteger.class);
   private static final BranchDB<String, VarDB<BigInteger>> lastPrices = Context.newBranchDB("tmp3", BigInteger.class);

   public static BigInteger updatePrice(String symbol, BigInteger currentPrice, BigInteger alpha) {
      VarDB<BigInteger> lastUpdate = lastUpdateBlock.at(symbol);
      VarDB<BigInteger> movingAverage = movingAverages.at(symbol);
      BigInteger lastBlock = lastUpdate.get();

      if (lastBlock == null) {
         BigInteger currentBlock = BigInteger.valueOf(Context.getBlockHeight());
         lastUpdate.set(currentBlock);
         movingAverage.set(currentPrice);
         lastPrices.at(symbol).set(currentPrice);
         
         return currentPrice;
      }

      BigInteger currentBlock = BigInteger.valueOf(Context.getBlockHeight());
      BigInteger blockDiff = currentBlock.subtract(lastBlock);
      BigInteger currentMovingAverage = movingAverage.get();

      if (blockDiff.equals(BigInteger.ZERO)) {
         return currentMovingAverage;
      }

      VarDB<BigInteger> lastPrice = lastPrices.at(symbol);
      BigInteger price = lastPrice.getOrDefault(BigInteger.ZERO);
      lastPrice.set(currentPrice);

      
      BigInteger factor = exaPow(EXA.subtract(alpha), blockDiff.intValue());
      BigInteger delta = price.subtract(currentMovingAverage);
      BigInteger newMovingAverge = price.subtract(delta.multiply(factor).divide(EXA));

      lastUpdate.set(currentBlock);
      movingAverage.set(newMovingAverge);

      return newMovingAverge;
   }
}