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

package network.balanced.score.core.governance;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import network.balanced.score.core.governance.utils.ArbitraryCallManager;
import network.balanced.score.lib.test.UnitTest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;
import score.UserRevertedException;

import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ArbitraryCallManagerTest extends UnitTest {

    @Test
    void stringParameter() {
        String testString = "test";
        JsonObject stringParam =  new JsonObject()
            .add("type", "String")
            .add("value", testString);

        // Act
        Object parsedParameter = ArbitraryCallManager.getConvertedParameter(stringParam);

        // Assert
        assertEquals(String.class, parsedParameter.getClass());
        assertEquals(testString, parsedParameter);
    }

    @Test
    void stringArrayParameter() {
        // Arrange
        JsonArray testStringArray = new JsonArray()
            .add("test1")
            .add("test2")
            .add("test3");

        JsonObject stringParam =  new JsonObject()
            .add("type", "String[]")
            .add("value", testStringArray);


        // Act
        Object parsedParameter = ArbitraryCallManager.getConvertedParameter(stringParam);

        // Assert
        assertEquals(Object[].class, parsedParameter.getClass());
        Object[] parsedParameterArray = (Object[])parsedParameter;
        assertEquals("test1", parsedParameterArray[0]);
        assertEquals("test2", parsedParameterArray[1]);
        assertEquals("test3", parsedParameterArray[2]);
    }

    @Test
    void addressParameter() {
        String testAddress = "cxf42e1bc5d514d988818c8d17a8a7597bebe4b025";
        JsonObject addressParam =  new JsonObject()
            .add("type", "Address")
            .add("value", testAddress);

        // Act
        Object parsedParameter = ArbitraryCallManager.getConvertedParameter(addressParam);

        // Assert
        assertEquals(Address.class, parsedParameter.getClass());
        assertEquals(Address.fromString(testAddress), parsedParameter);
    }

    @Test
    void numberParameter() {
        // Arrange
        BigInteger testNumber = BigInteger.valueOf(42);
        JsonObject numberParam =  new JsonObject()
            .add("type", "int")
            .add("value", testNumber.toString());

        // Act
        Object parsedParameter = ArbitraryCallManager.getConvertedParameter(numberParam);

        // Assert
        assertEquals(BigInteger.class, parsedParameter.getClass());
        assertEquals(testNumber, parsedParameter);
    }

    @Test
    void numberArrayParameter() {
        // Arrange
        JsonArray testNumberArray = new JsonArray()
            .add("41")
            .add("42")
            .add("43");

        JsonObject numberParam =  new JsonObject()
            .add("type", "BigInteger[]")
            .add("value", testNumberArray);

        // Act
        Object parsedParameter = ArbitraryCallManager.getConvertedParameter(numberParam);

        // Assert
        assertEquals(Object[].class, parsedParameter.getClass());
        Object[] parsedParameterArray = (Object[])parsedParameter;
        assertEquals(BigInteger.valueOf(41), parsedParameterArray[0]);
        assertEquals(BigInteger.valueOf(42), parsedParameterArray[1]);
        assertEquals(BigInteger.valueOf(43), parsedParameterArray[2]);
    }

    @Test
    void booleanParameter() {
        // Arrange
        boolean testBool = true;
        JsonObject boolParam =  new JsonObject()
            .add("type", "boolean")
            .add("value", testBool);

        // Act
        Object parsedParameter = ArbitraryCallManager.getConvertedParameter(boolParam);

        // Assert
        assertEquals(Boolean.class, parsedParameter.getClass());
        assertEquals(testBool, parsedParameter);
    }

    @Test
    void bytesParameter() {
        // Arrange
        JsonObject testData = new JsonObject()
            .add("_asset", "bnUSD")
            .add("_amount", "234");
        byte[] bytes = testData.toString().getBytes();
        String hexBytes = getHex(bytes);

        JsonObject bytesParam =  new JsonObject()
            .add("type", "bytes")
            .add("value", hexBytes);

        // Act
        Object parsedParameter = ArbitraryCallManager.getConvertedParameter(bytesParam);

        // Assert
        assertEquals(byte[].class, parsedParameter.getClass());
        assertEquals(hexBytes, getHex((byte[])parsedParameter));
    }

    @Test
    void bytesParameter_withHexNotation() {
        // Arrange
        JsonObject testData = new JsonObject()
            .add("_asset", "bnUSD")
            .add("_amount", "234");
        byte[] bytes = testData.toString().getBytes();
        String hexBytes = getHex(bytes);
        String hexBytesWithNotation = "0x"+hexBytes;

        JsonObject bytesParam =  new JsonObject()
            .add("type", "bytes")
            .add("value", hexBytesWithNotation);

        // Act
        Object parsedParameter = ArbitraryCallManager.getConvertedParameter(bytesParam);

        // Assert
        assertEquals(byte[].class, parsedParameter.getClass());
        assertEquals(hexBytes, getHex((byte[])parsedParameter));
    }

    @Test
    void invalidBytes() {
        // Arrange
        JsonObject testData = new JsonObject()
            .add("_asset", "bnUSD")
            .add("_amount", "234");
        byte[] bytes = testData.toString().getBytes();
        String hexBytes = getHex(bytes);
        String invalidByes = "["+hexBytes;

        JsonObject bytesParam =  new JsonObject()
            .add("type", "bytes")
            .add("value", invalidByes);

        // Act & Assert
        Executable parseInvalidBytes = () ->  ArbitraryCallManager.getConvertedParameter(bytesParam);
        UserRevertedException e = Assertions.assertThrows(UserRevertedException.class, parseInvalidBytes);
        assertEquals("Reverted(0): Illegal bytes format", e.getMessage());
    }

    @Test
    void structParameter() {
        // Arrange
        String testString = "test";
        JsonObject stringParam =  new JsonObject()
            .add("type", "String")
            .add("value", testString);

        JsonArray testStringArray = new JsonArray()
            .add("test1")
            .add("test2")
            .add("test3");

        JsonObject stringArrayParam =  new JsonObject()
            .add("type", "String[]")
            .add("value", testStringArray);

        String testAddress = "cxf42e1bc5d514d988818c8d17a8a7597bebe4b025";
        JsonObject addressParam =  new JsonObject()
            .add("type", "Address")
            .add("value", testAddress);

        BigInteger testNumber = BigInteger.valueOf(42);
        JsonObject numberParam =  new JsonObject()
            .add("type", "int")
            .add("value", testNumber.toString());

        boolean testBool = true;
        JsonObject boolParam =  new JsonObject()
            .add("type", "boolean")
            .add("value", testBool);

        JsonObject testBytes = new JsonObject()
            .add("_asset", "bnUSD")
            .add("_amount", "234");

        byte[] bytes = testBytes.toString().getBytes();
        String hexBytes = getHex(bytes);

        JsonObject bytesParam =  new JsonObject()
            .add("type", "bytes")
            .add("value", hexBytes);


        JsonObject struct =  new JsonObject()
            .add("String", stringParam)
            .add("String[]", stringArrayParam)
            .add("Address", addressParam)
            .add("Number", numberParam)
            .add("boolean", boolParam)
            .add("bytes", bytesParam);

        JsonObject strcutParam =  new JsonObject()
            .add("type", "Struct")
            .add("value", struct);

        // Act
        Map<String, Object> parsedParameter = (Map<String, Object>)ArbitraryCallManager.getConvertedParameter(strcutParam);

        // Assert
        assertEquals(testString, parsedParameter.get("String"));
        Object[] parsedStringArray = (Object[])parsedParameter.get("String[]");
        assertEquals("test1", parsedStringArray[0]);
        assertEquals("test2", parsedStringArray[1]);
        assertEquals("test3", parsedStringArray[2]);
        assertEquals(Address.fromString(testAddress), parsedParameter.get("Address"));
        assertEquals(testNumber, parsedParameter.get("Number"));
        assertEquals(testBool, parsedParameter.get("boolean"));
        assertEquals(hexBytes, getHex((byte[])parsedParameter.get("bytes")));
    }

    @Test
    void illegalParameter() {
        // Arrange
        double testDouble = 3.14;
        JsonObject doubleParam =  new JsonObject()
            .add("type", "double")
            .add("value", testDouble);

        // Act & Assert
        Executable parseDouble = () -> ArbitraryCallManager.getConvertedParameter(doubleParam);
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class, parseDouble);
        assertEquals("Unknown type", e.getMessage());
    }

    private static String getHex(byte[] raw) {
        String HEXES = "0123456789ABCDEF";
        final StringBuilder hex = new StringBuilder(2 * raw.length);
        for (final byte b : raw) {
            hex.append(HEXES.charAt((b & 0xF0) >> 4)).append(HEXES.charAt((b & 0x0F)));
        }

        return hex.toString();
    }
}