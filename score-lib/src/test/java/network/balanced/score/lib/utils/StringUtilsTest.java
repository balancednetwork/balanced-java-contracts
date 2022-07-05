/*
 * Copyright (c) 2022 Balanced.network.
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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import score.UserRevertException;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StringUtilsTest {

    @BeforeAll
    public static void setup() {
    }

    @Test
    public void testConvertStringToBigInteger(){
       assertEquals(BigInteger.valueOf(19), StringUtils.convertStringToBigInteger("19"));
       assertEquals(BigInteger.valueOf(18), StringUtils.convertStringToBigInteger("0x12"));
       assertEquals(BigInteger.valueOf(-18), StringUtils.convertStringToBigInteger("-0x12"));
    }

    @Test
    public void testConvertStringToBigIntegerThrowsException() {
        UserRevertException e = Assertions.assertThrows(UserRevertException.class,
                () -> StringUtils.convertStringToBigInteger("abc"));
        assertEquals(e.getMessage(), "Invalid numeric value: " + "abc");

        e = Assertions.assertThrows(UserRevertException.class, () -> StringUtils.convertStringToBigInteger("0xa+bc"));
        assertEquals(e.getMessage(), "Invalid numeric value: " + "0xa+bc");
    }
}
