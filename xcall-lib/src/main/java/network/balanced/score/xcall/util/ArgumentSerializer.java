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

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonValue;

import score.Address;

import java.math.BigInteger;
import java.util.function.Function;

public class ArgumentSerializer {
    public static final Function<String, JsonValue> serializeString = Json::value;
    public static final Function<String[], JsonValue> serializeStringArray = (value) -> serializeArray(value, Json::value);
    public static final Function<Address, JsonValue> serializeAddress = address -> Json.value(address.toString());
    public static final Function<Address[], JsonValue> serializeAddressArray = (value) -> serializeArray(value, serializeAddress);
    public static final Function<BigInteger, JsonValue> serializeBigInteger = number -> Json.value(number.toString());
    public static final Function<BigInteger[], JsonValue> serializeBigIntegerArray = (value) -> serializeArray(value, serializeBigInteger);
    public static final Function<Boolean, JsonValue> serializeBoolean = Json::value;
    public static final Function<Boolean[], JsonValue> serializeBooleanArray = (value) -> serializeArray(value, serializeBoolean);
    public static final Function<byte[], JsonValue> serializeBytes =  ArgumentSerializer::serializeBytes;
    // TODO: support maps/structs

    public static <T> JsonValue serializeArray(T[] array, Function<T, JsonValue> serializer) {
        JsonArray jsonArray = new JsonArray();
        for (T val : array) {
            jsonArray.add(serializer.apply(val));
        }

        return jsonArray;
    }

    public static JsonValue serializeBytes(byte[] value) {
        String HEXES = "0123456789ABCDEF";
        final StringBuilder hex = new StringBuilder(2 * value.length);
        for (final byte b : value) {
            hex.append(HEXES.charAt((b & 0xF0) >> 4)).append(HEXES.charAt((b & 0x0F)));
        }

        return Json.value("0x" + hex.toString());
    }
}
