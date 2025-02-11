/*
 * Copyright (c) 2024 Balanced.network.
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

import java.math.BigInteger;

import score.Address;
import score.Context;

public class ICX {
  public static Address ADDRESS = Address.fromString("cx1111111111111111111111111111111111111111");
  private static final int DECIMALS = 18;
  private static final String SYMBOL = "ICX";

  public static void transfer (
    Address targetAddress, 
    BigInteger value
  ) {
    Context.transfer(targetAddress, value);
  }

  public static void transfer (
    Address targetAddress, 
    BigInteger value,
    String method,
    Object... params
  ) {
    if (targetAddress.isContract()) {
      Context.call(value, targetAddress, method, params);
    } else {
      Context.transfer(targetAddress, value);
    }
  }

  public static Address getAddress () {
    return ICX.ADDRESS;
  }

  public static boolean isICX (Address token) {
    return token.equals(ADDRESS);
  }
  
  public static String symbol () {
    return ICX.SYMBOL;
  }

  public static int decimals () {
    return ICX.DECIMALS;
  }

  public static BigInteger balanceOf(Address address) {
    return Context.getBalance(address);
  }
}
