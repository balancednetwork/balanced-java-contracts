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

package network.balanced.score.core.dex.interfaces.irc2;

import java.math.BigInteger;

import score.Address;
import score.Context;

public class IIRC2 {

  public static void transfer (
    Address irc2,
    Address to,
    BigInteger amount,
    byte[] data
  ) {
    Context.call(irc2, "transfer", to, amount, data);
  }

  public static int decimals (Address irc2) {
    return ((BigInteger) Context.call(irc2, "decimals")).intValue();
  }

  public static String symbol (Address irc2) {
    return ((String) Context.call(irc2, "symbol"));
  }

  public static BigInteger totalSupply (Address irc2) {
    return (BigInteger) Context.call(irc2, "totalSupply");
  }

  public static BigInteger balanceOf(Address irc2, Address address) {
    return (BigInteger) Context.call(irc2, "balanceOf", address);
  }
}
