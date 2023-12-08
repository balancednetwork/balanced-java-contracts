
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

package network.balanced.score.lib.structs;

import score.ByteArrayObjectWriter;
import score.Context;
import score.ObjectReader;
import score.ObjectWriter;

import java.math.BigInteger;
import static network.balanced.score.lib.utils.Constants.POINTS;

public class PriceProtectionConfig {
    public boolean increaseOnly;
    public BigInteger priceChangePoints;
    public BigInteger priceChangeTimeWindowUs;

    private PriceProtectionConfig() {
    }

    public PriceProtectionConfig(boolean increaseOnly, BigInteger priceChangePoints,
            BigInteger priceChangeTimeWindowUs) {
        this.increaseOnly = increaseOnly;
        this.priceChangePoints = priceChangePoints;
        this.priceChangeTimeWindowUs = priceChangeTimeWindowUs;
    }

    public void verifyPriceUpdate(BigInteger prevTimestamp, BigInteger prevPrice, BigInteger currentTimestamp, BigInteger currentPrice) {
        if (increaseOnly) {
            Context.require(currentPrice.compareTo(prevPrice) > 0, "Price of this asset can only increase");
        }
        if (priceChangePoints.equals(BigInteger.ZERO)) {
            return;
        }

        BigInteger priceChange = currentPrice.multiply(POINTS).divide(prevPrice).subtract(POINTS).abs();
        BigInteger timeElapsed = currentTimestamp.subtract(prevTimestamp);
        BigInteger maxPriceChange = priceChangePoints.multiply(timeElapsed).divide(priceChangeTimeWindowUs);
        System.out.println(priceChange);
        System.out.println(timeElapsed);
        System.out.println(maxPriceChange);
        Context.require(maxPriceChange.compareTo(priceChange) >= 0, "Price of this asset has moved to fast");
    }

    public static void writeObject(ObjectWriter writer, PriceProtectionConfig obj) {
        obj.writeObject(writer);
    }

    public static PriceProtectionConfig readObject(ObjectReader reader) {
        PriceProtectionConfig obj = new PriceProtectionConfig();
        reader.beginList();
        obj.increaseOnly = reader.readBoolean();
        obj.priceChangePoints = reader.readBigInteger();
        obj.priceChangeTimeWindowUs = reader.readBigInteger();
        reader.end();
        return obj;
    }

    public void writeObject(ObjectWriter writer) {
        writer.beginList(3);
        writer.write(this.increaseOnly);
        writer.write(this.priceChangePoints);
        writer.write(this.priceChangeTimeWindowUs);
        writer.end();
    }

    public static PriceProtectionConfig fromBytes(byte[] bytes) {
        ObjectReader reader = Context.newByteArrayObjectReader("RLPn", bytes);
        return PriceProtectionConfig.readObject(reader);
    }

    public byte[] toBytes() {
        ByteArrayObjectWriter writer = Context.newByteArrayObjectWriter("RLPn");
        PriceProtectionConfig.writeObject(writer, this);
        return writer.toByteArray();
    }
}
