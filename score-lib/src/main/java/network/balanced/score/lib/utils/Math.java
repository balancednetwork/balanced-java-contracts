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

import com.eclipsesource.json.JsonValue;
import score.UserRevertedException;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Constants.EXA;

public class Math {
    public static BigInteger pow(BigInteger base, int exponent) {
        BigInteger res = BigInteger.ONE;
        for (int i = 1; i <= exponent; i++) {
            res = res.multiply(base);
        }
        return res;
    }

    public static BigInteger exaPow(BigInteger base, int exponent) {
        BigInteger res = base;
        if (exponent % 2 == 0) {
            res = EXA;
        }

        exponent = exponent / 2;

        while (exponent != 0) {
            base = base.multiply(base).divide(EXA);
            if (exponent % 2 != 0) {
                res = res.multiply(base).divide(EXA);
            }

            exponent = exponent / 2;
        }

        return res;
    }

    public static BigInteger convertToNumber(JsonValue value) {
        if (value == null) {
            return null;
        }
        if (value.isString()) {
            String number = value.asString();
            try {
                if (number.startsWith("0x") || number.startsWith("-0x")) {
                    return new BigInteger(number.replace("0x", ""), 16);
                } else {
                    return new BigInteger(number);
                }
            } catch (NumberFormatException e) {
                throw new UserRevertedException("Invalid numeric value: " + number);
            }
        } else if (value.isNumber()) {
            return new BigInteger(value.toString());
        }
        throw new UserRevertedException("Invalid value format for number in json: " + value);
    }
}
