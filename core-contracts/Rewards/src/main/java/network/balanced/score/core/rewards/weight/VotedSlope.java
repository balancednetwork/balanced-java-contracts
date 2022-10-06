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

package network.balanced.score.core.rewards.weight;

import score.ByteArrayObjectWriter;
import score.Context;
import score.ObjectReader;
import score.ObjectWriter;

import java.math.BigInteger;

public class VotedSlope {

    public BigInteger slope;
    public BigInteger power;
    public BigInteger end;

    public VotedSlope(BigInteger slope, BigInteger power, BigInteger end) {
        this.slope = slope;
        this.power = power;
        this.end = end;
    }

    public VotedSlope() {
        this(BigInteger.ZERO, BigInteger.ZERO,  BigInteger.ZERO);
    }

    public VotedSlope newPoint() {
        return new VotedSlope(this.slope, this.power, this.end);
    }

    public static void writeObject(ObjectWriter writer, VotedSlope obj) {
        obj.writeObject(writer);
    }

    public static VotedSlope readObject(ObjectReader reader) {
        VotedSlope obj = new VotedSlope();
        reader.beginList();
        obj.slope = reader.readBigInteger();
        obj.power = reader.readBigInteger();
        obj.end = reader.readBigInteger();
        reader.end();
        return obj;
    }

    public void writeObject(ObjectWriter writer) {
        writer.beginList(2);
        writer.write(this.slope);
        writer.write(this.power);
        writer.write(this.end);
        writer.end();
    }

    public static VotedSlope fromBytes(byte[] bytes) {
        ObjectReader reader = Context.newByteArrayObjectReader("RLPn", bytes);
        return VotedSlope.readObject(reader);
    }

    public byte[] toBytes() {
        ByteArrayObjectWriter writer = Context.newByteArrayObjectWriter("RLPn");
        VotedSlope.writeObject(writer, this);
        return writer.toByteArray();
    }
}
