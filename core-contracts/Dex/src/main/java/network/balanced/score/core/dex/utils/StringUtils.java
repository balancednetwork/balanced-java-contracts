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

import score.Context;

public class StringUtils {

    public static BigInteger toBigInt (String input) {
        if (input.startsWith("0x")) {
            return new BigInteger(input.substring(2), 16);
        }

        if (input.startsWith("-0x")) {
            return new BigInteger(input.substring(3), 16).negate();
        }

        return new BigInteger(input, 10);
    }

    /**
     * Convert a hexstring with or without leading "0x" to byte array
     * @param hexstring a hexstring
     * @return a byte array
     */
    public static byte[] hexToByteArray(String hexstring) {
        /* hexstring must be an even-length string. */
        Context.require(hexstring.length() % 2 == 0,
            "hexToByteArray: invalid hexstring length");
        
        if (hexstring.startsWith("0x")) {
            hexstring = hexstring.substring(2);
        }

        int len = hexstring.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
                int c1 = Character.digit(hexstring.charAt(i), 16) << 4;
            int c2 = Character.digit(hexstring.charAt(i+1), 16);

            if (c1 == -1 || c2 == -1) {
                Context.revert("hexToByteArray: invalid hexstring character at pos " + i);
            }

            data[i / 2] = (byte) (c1 + c2);
        }
        return data;
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public static String byteArrayToHex(byte[] data) {
        char[] hexChars = new char[data.length * 2];

        for (int j = 0; j < data.length; j++) {
            int v = data[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }

        return new String(hexChars);
    }
}
