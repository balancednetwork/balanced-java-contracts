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

package network.balanced.score.lib.utils;

import com.eclipsesource.json.JsonArray;
import score.Context;
import score.UserRevertException;

import java.math.BigInteger;

public class StringUtils {
    public static BigInteger convertStringToBigInteger(String number) {
        try {
            if (number.startsWith("0x") || number.startsWith("-0x")) {
                return new BigInteger(number.replace("0x", ""), 16);
            } else {
                return new BigInteger(number);
            }
        } catch (NumberFormatException e) {
            throw new UserRevertException("Invalid numeric value: " + number);
        }
    }

}
