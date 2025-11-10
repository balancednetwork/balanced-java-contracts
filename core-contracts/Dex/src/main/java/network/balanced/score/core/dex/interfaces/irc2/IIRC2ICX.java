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
import network.balanced.score.core.dex.utils.JSONUtils;
import network.balanced.score.core.dex.utils.ICX;
import score.Address;

public class IIRC2ICX {

  public static void transfer (Address token, Address destination, BigInteger value) {
    if (ICX.isICX(token)) {
      ICX.transfer(destination, value);
    } else {
      IIRC2.transfer(token, destination, value, "".getBytes());
    }
  }

  public static void transfer (Address token, Address destination, BigInteger value, String method) {
    if (ICX.isICX(token)) {
      ICX.transfer(destination, value, method + "Icx");
    } else {
      IIRC2.transfer(token, destination, value, JSONUtils.method(method));
    }
  }

  public static void transfer (Address token, Address destination, BigInteger value, String method, IRC2ICXParam params) {
    if (ICX.isICX(token)) {
      ICX.transfer(destination, value, method + "Icx", params.toRaw());
    } else {
      IIRC2.transfer(token, destination, value, JSONUtils.method(method, params.toJson()));
    }
  }

  public static String symbol (Address token) {
    if (ICX.isICX(token)) {
      return ICX.symbol();
    } else {
      return IIRC2.symbol(token);
    }
  }

  public static int decimals (Address token) {
    if (ICX.isICX(token)) {
      return ICX.decimals();
    } else {
      return IIRC2.decimals(token);
    }
  }

  public static BigInteger balanceOf(Address token, Address address) {
    if (ICX.isICX(token)) {
      return ICX.balanceOf(address);
    } else {
      return IIRC2.balanceOf(token, address);
    }
  }
}
