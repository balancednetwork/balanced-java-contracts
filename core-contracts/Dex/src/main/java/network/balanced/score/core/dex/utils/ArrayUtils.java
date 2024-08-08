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
import java.util.List;
import score.Address;

public class ArrayUtils {
    public static BigInteger[] arrayCopy (BigInteger[] array) {
        BigInteger[] result = new BigInteger[array.length];
        System.arraycopy(array, 0, result, 0, array.length);
        return result;
    }

    public static <T> void arrayFill (T[] array, T fill) {
        int size = array.length;
        for (int i = 0; i < size; i++) {
            array[i] = fill;
        }
    }

    public static BigInteger[] newFill (int size, BigInteger fill) {
        BigInteger[] result = new BigInteger[size];
        arrayFill(result, fill);
        return result;
    }

    public static Integer[] newFill (int size, Integer fill) {
        Integer[] result = new Integer[size];
        arrayFill(result, fill);
        return result;
    }

    public static Address[] newFill (int size, Address fill) {
        Address[] result = new Address[size];
        arrayFill(result, fill);
        return result;
    }

    public static String toString (Object[] array) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < array.length; i++) {
            sb.append(array[i].toString());
            if (i != array.length - 1) sb.append(", ");
        }
        sb.append(']');
        return sb.toString();
    }

    public static boolean contains (Object[] array, Object item) {
        for (Object current : array) {
            if (current.equals(item)) {
                return true;
            }
        }
        return false;
    }

    public static BigInteger[] fromList (List<BigInteger> list) {
        final int size = list.size();
        BigInteger[] result = new BigInteger[size];
        for (int i = 0; i < size; i++) {
            result[i] = list.get(i);
        }
        return result;
    }
}
