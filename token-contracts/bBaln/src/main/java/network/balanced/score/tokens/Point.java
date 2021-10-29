/*
 * Copyright (c) 2021 Balanced.network.
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

package network.balanced.score.tokens;

import score.ByteArrayObjectWriter;
import score.Context;
import score.ObjectReader;

import java.math.BigInteger;

public class Point {

    public BigInteger bias;
    public BigInteger slope;
    public BigInteger timestamp;
    public BigInteger block;

    public Point(BigInteger bias, BigInteger slope, BigInteger timestamp, BigInteger block) {
        this.bias = bias;
        this.slope = slope;
        this.timestamp = timestamp;
        this.block = block;
    }

    public Point() { this(BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO);}

    public byte[] toByteArray() {
        ByteArrayObjectWriter pointInBytes = Context.newByteArrayObjectWriter("RLPn");
        pointInBytes.write(this.bias);
        pointInBytes.write(this.slope);
        pointInBytes.write(this.timestamp);
        pointInBytes.write(this.block);
        return pointInBytes.toByteArray();
    }

    public Point newPoint() {
        return new Point(this.bias, this.slope, this.timestamp, this.block);
    }

    public static Point toPoint(byte[] pointBytesArray) {
        if (pointBytesArray == null) {
            return new Point();
        } else {
            ObjectReader point = Context.newByteArrayObjectReader("RLPn", pointBytesArray);
            return new Point(point.readBigInteger(), point.readBigInteger(), point.readBigInteger(), point.readBigInteger());
        }
    }
}
