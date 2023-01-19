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

package network.balanced.score.xcall.util;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import score.Address;
import score.Context;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.Map;
import java.util.function.Function;

public class ArgumentParser {
    public static final Function<JsonValue, String> parseString =  JsonValue::asString;
    public static final Function<JsonValue, String[]> parseStringArray = (value) -> parseArray(value, JsonValue::asString);
    public static final Function<JsonValue, Address> parseAddress = jsonValue -> Address.fromString(jsonValue.asString());
    public static final Function<JsonValue, Address[]> parseAddressArray = (value) -> parseArray(value, parseAddress);
    public static final Function<JsonValue, BigInteger> parseBigInteger = jsonValue -> parseNumber(jsonValue);
    public static final Function<JsonValue, BigInteger[]> parseBigIntegerArray = (value) -> parseArray(value, parseBigInteger);
    public static final Function<JsonValue, Boolean> parseBoolean = JsonValue::asBoolean;
    public static final Function<JsonValue, Boolean[]> parseBooleanArray = (value) -> parseArray(value, parseBoolean);
    public static final Function<JsonValue, Map<String, Object>> parseStruct = jsonValue -> parseStruct(jsonValue.asObject());
    public static final Function<JsonValue, Map<String, Object>[]>  parseMapArray = (value) -> parseArray(value, parseStruct);
    public static final Function<JsonValue, byte[]> parseBytes = ArgumentParser::parseBytesParam;

    public static Object parseParam(String type, JsonValue value, boolean isArray) {
        switch (type) {
            case "String":
                return parseString.apply(value);
            case "String[]":
                return parseStringArray.apply(value);
            case "Address":
                return parseAddress.apply(value);
            case "Address[]":
                return parseAddressArray.apply(value);
            case "BigInteger":
                return parseBigInteger.apply(value);
            case "BigInteger[]":
                return parseBigIntegerArray.apply(value);
            case "Boolean":
                return parseBoolean.apply(value);
            case "Boolean[]":
                return parseBooleanArray.apply(value);
            case "Map<String, Object>":
                return parseStruct.apply(value);
            case "Map<String, Object>[]":
                return  parseMapArray.apply(value);
            case "byte[]":
                return parseBytes.apply(value);
        }

        throw new IllegalArgumentException("Unknown type");
    }

    public static <T>  T[] parseArray(JsonValue value, Function<JsonValue, ?> parser) {
        JsonArray array = value.asArray();
        Object[] parseedArray = new Object[array.size()];
        int i = 0;
        for (JsonValue param : array) {
            parseedArray[i++] = parser.apply(param);
        }

        return (T[]) parseedArray;
    }

    public static byte[] parseBytesParam(JsonValue value) {
        String hex = value.asString();
        Context.require(hex.length() % 2 == 0, "Illegal bytes format");

        if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2);
        }

        int len = hex.length() / 2;
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            int j = i * 2;
            bytes[i] = (byte) Integer.parseInt(hex.substring(j, j + 2), 16);
        }

        return bytes;
    }

    public static Map<String, Object> parseStruct(JsonObject jsonStruct) {
        Map<String, Object> struct = new HashMap<>();
        for (JsonObject.Member member : jsonStruct) {
            String name = member.getName();
            JsonObject jsonObject = member.getValue().asObject();
            String type = jsonObject.getString("type", null);
            JsonValue jsonValue = jsonObject.get("value");

            if (type.endsWith("[]")) {
                struct.put(name, parseParam(type.substring(0, type.length() - 2), jsonValue.asArray(), true));
            } else {
                struct.put(name, parseParam(type, jsonValue, false));
            }
        }

        return struct;
    }

    public static BigInteger parseNumber(JsonValue value) {
        if (value == null) {
            return null;
        }
        if (value.isString()) {
            String number = value.asString();
            if (number.startsWith("0x") || number.startsWith("-0x")) {
                return new BigInteger(number.replace("0x", ""), 16);
            }

            return new BigInteger(number);

        } else if (value.isNumber()) {
            return new BigInteger(value.toString());
        }

        throw new RuntimeException("Cannot parse the number " + value);
    }
}
