
/*
 * Copyright (c) 2023-2023 Balanced.network.
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

package network.balanced.score.core.balancedoracle.structs;

import score.ByteArrayObjectWriter;
import score.Context;
import score.ObjectReader;
import score.ObjectWriter;

import java.math.BigInteger;
import java.util.Map;

public class PriceData {
    public BigInteger rate;
    public BigInteger timestamp;

    private PriceData() {
    }

    public PriceData(BigInteger rate, BigInteger timestamp) {
        this.rate = rate;
        this.timestamp = timestamp;
    }

    public Map<String, BigInteger> toMap() {
        return Map.of("rate", this.rate, "timestamp", this.timestamp);
    }

    public static void writeObject(ObjectWriter writer, PriceData obj) {
        obj.writeObject(writer);
    }

    public static PriceData readObject(ObjectReader reader) {
        PriceData obj = new PriceData();
        reader.beginList();
        obj.rate = reader.readBigInteger();
        obj.timestamp = reader.readBigInteger();
        reader.end();
        return obj;
    }

    public void writeObject(ObjectWriter writer) {
        writer.beginList(2);
        writer.write(this.rate);
        writer.write(this.timestamp);
        writer.end();
    }

    public static PriceData fromBytes(byte[] bytes) {
        ObjectReader reader = Context.newByteArrayObjectReader("RLPn", bytes);
        return PriceData.readObject(reader);
    }

    public byte[] toBytes() {
        ByteArrayObjectWriter writer = Context.newByteArrayObjectWriter("RLPn");
        PriceData.writeObject(writer, this);
        return writer.toByteArray();
    }
}
