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

package network.balanced.score.tokens.utils;

import score.UserRevertedException;

import java.math.BigInteger;

public class UnsignedBigInteger implements Comparable<UnsignedBigInteger> {

    private final BigInteger value;

    public static final UnsignedBigInteger ZERO = new UnsignedBigInteger(BigInteger.ZERO, true);
    public static final UnsignedBigInteger ONE = new UnsignedBigInteger(BigInteger.ONE, true);
    public static final UnsignedBigInteger TWO = new UnsignedBigInteger(BigInteger.TWO, true);
    public static final UnsignedBigInteger TEN = new UnsignedBigInteger(BigInteger.TEN, true);


    public UnsignedBigInteger(BigInteger value) {
        require(value.signum() >= 0, "invalid value for unsigned numbers");
        this.value = value;
    }


    public UnsignedBigInteger() {
        this(BigInteger.ZERO);
    }

    public UnsignedBigInteger(int value) {
        this(BigInteger.valueOf(value));
    }

    public UnsignedBigInteger(long value) {
        this(BigInteger.valueOf(value));
    }

    public UnsignedBigInteger(String value) {
        this(new BigInteger(value));
    }

    public UnsignedBigInteger(String value, int radix) {
        this(new BigInteger(value, radix));
    }


    private UnsignedBigInteger(BigInteger value, boolean dummy) {
        this.value = value;
    }


    public UnsignedBigInteger add(UnsignedBigInteger other) {
        return new UnsignedBigInteger(value.add(other.value), true);
    }


    public UnsignedBigInteger subtract(UnsignedBigInteger other) {
        BigInteger diff = value.subtract(other.value);
        require(diff.signum() >= 0, "subtraction underflow for unsigned numbers");
        return new UnsignedBigInteger(diff, true);
    }


    public UnsignedBigInteger multiply(UnsignedBigInteger other) {
        return new UnsignedBigInteger(value.multiply(other.value), true);
    }

    public UnsignedBigInteger divide(UnsignedBigInteger other) {
        require(other.value.signum() != 0, "division by zero");
        return new UnsignedBigInteger(value.divide(other.value), true);
    }


    public UnsignedBigInteger mod(UnsignedBigInteger divisor) {
        require(divisor.value.signum() != 0, "mod by zero");
        return new UnsignedBigInteger(value.mod(divisor.value), true);
    }


    public UnsignedBigInteger pow(int exponent) {
        return new UnsignedBigInteger(value.pow(exponent), true);
    }


    public UnsignedBigInteger max(UnsignedBigInteger other) {
        return new UnsignedBigInteger(value.max(other.value), true);
    }


    public UnsignedBigInteger min(UnsignedBigInteger other) {
        return new UnsignedBigInteger(value.min(other.value), true);
    }

    @Override
    public int compareTo(UnsignedBigInteger other) {
        return value.compareTo(other.value);
    }

    public int compareTo(BigInteger other) {
        return value.compareTo(other);
    }


    public int signum() {
        return value.equals(BigInteger.ZERO) ? 0 : 1;
    }


    @Override
    public boolean equals(Object other) {
        return other == this || (other instanceof UnsignedBigInteger && value.equals(
                ((UnsignedBigInteger) other).value));
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }


    public BigInteger toBigInteger() {
        return value;
    }


    @Override
    public String toString() {
        return value.toString();
    }


    public String toString(int radix) {
        return value.toString(radix);
    }


    public static UnsignedBigInteger valueOf(long value) {
        return new UnsignedBigInteger(BigInteger.valueOf(value));
    }

    public byte[] toByteArray() {
        return value.toByteArray();
    }

    public int intValue() {
        return value.intValue();
    }

    public long longValue() {
        return value.longValue();
    }

    public float floatValue() {
        return value.floatValue();
    }

    public double doubleValue() {
        return value.doubleValue();
    }


    public static UnsignedBigInteger pow10(int exponent) {
        UnsignedBigInteger result = UnsignedBigInteger.ONE;
        for (int i = 0; i < exponent; i++) {
            result = result.multiply(UnsignedBigInteger.TEN);
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new UserRevertedException(message);
        }
    }


}
