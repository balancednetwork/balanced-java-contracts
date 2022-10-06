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

package network.balanced.score.lib.structs;

import score.ByteArrayObjectWriter;
import score.Context;
import score.ObjectReader;
import score.ObjectWriter;

import java.math.BigInteger;

public class Point {

    public BigInteger bias;
    public BigInteger slope;

    public Point(BigInteger bias, BigInteger slope) {
        this.bias = bias;
        this.slope = slope;
    }

    public Point() {
        this(BigInteger.ZERO, BigInteger.ZERO);
    }

    public Point newPoint() {
        return new Point(this.bias, this.slope);
    }

    public static void writeObject(ObjectWriter writer, Point obj) {
        obj.writeObject(writer);
    }

    public static Point readObject(ObjectReader reader) {
        Point obj = new Point();
        reader.beginList();
        obj.bias = reader.readBigInteger();
        obj.slope = reader.readBigInteger();
        reader.end();
        return obj;
    }

    public void writeObject(ObjectWriter writer) {
        writer.beginList(2);
        writer.write(this.bias);
        writer.write(this.slope);
        writer.end();
    }

    public static Point fromBytes(byte[] bytes) {
        ObjectReader reader = Context.newByteArrayObjectReader("RLPn", bytes);
        return Point.readObject(reader);
    }

    public byte[] toBytes() {
        ByteArrayObjectWriter writer = Context.newByteArrayObjectWriter("RLPn");
        Point.writeObject(writer, this);
        return writer.toByteArray();
    }
}
