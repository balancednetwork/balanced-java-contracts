/*
 * Copyright (c) 2024 Balanced.network.
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

package network.balanced.score.core.dex.structs.pool;

import java.math.BigInteger;
import java.util.Map;
import score.ObjectReader;
import score.ObjectWriter;

public class Slot0 {
    // The current price of the pool as a sqrt(token1/token0) Q64.96 value
    public BigInteger sqrtPriceX96;
    // The current tick of the pool, i.e. according to the last tick transition that was run.
    // This value may not always be equal to SqrtTickMath.getTickAtSqrtRatio(sqrtPriceX96) if the price is on a tick boundary
    public int tick;
    // The index of the last oracle observation that was written
    public int observationIndex;
    // the current maximum number of observations that are being stored
    public int observationCardinality;
    // the next maximum number of observations to store, triggered in observations.write
    public int observationCardinalityNext;
    // The current protocol fee as a percentage of the swap fee taken on withdrawal
    // represented as an integer denominator (1/x)%
    // Encoded as two 4 bit values, where the protocol fee of token1 is shifted 4 bits and the protocol fee of token0
    // is the lower 4 bits. Used as the denominator of a fraction of the swap fee, e.g. 4 means 1/4th of the swap fee.
    public int feeProtocol;
    // Whether the pool is locked
    public boolean unlocked;

    public static void writeObject (ObjectWriter w, Slot0 obj) {
        w.write(obj.sqrtPriceX96);
        w.write(obj.tick);
        w.write(obj.observationIndex);
        w.write(obj.observationCardinality);
        w.write(obj.observationCardinalityNext);
        w.write(obj.feeProtocol);
        w.write(obj.unlocked);
    }

    public static Slot0 readObject(ObjectReader r) {
        return new Slot0(
            r.readBigInteger(), // sqrtPriceX96
            r.readInt(), // tick
            r.readInt(), // observationIndex
            r.readInt(), // observationCardinality
            r.readInt(), // observationCardinalityNext
            r.readInt(), // feeProtocol
            r.readBoolean() // unlocked
        );
    }

    public static Slot0 fromMap(Object call) {
        @SuppressWarnings("unchecked")
        Map<String,Object> map = (Map<String,Object>) call;
        return new Slot0(
            (BigInteger) map.get("sqrtPriceX96"),
            ((BigInteger) map.get("tick")).intValue(),
            ((BigInteger) map.get("observationIndex")).intValue(),
            ((BigInteger) map.get("observationCardinality")).intValue(),
            ((BigInteger) map.get("observationCardinalityNext")).intValue(),
            ((BigInteger) map.get("feeProtocol")).intValue(),
            (Boolean) map.get("unlocked")
        );
    }

    public Slot0 (
        BigInteger sqrtPriceX96,
        int tick,
        int observationIndex,
        int observationCardinality,
        int observationCardinalityNext,
        int feeProtocol,
        boolean unlocked
    ) {
        this.sqrtPriceX96 = sqrtPriceX96;
        this.tick = tick;
        this.observationIndex = observationIndex;
        this.observationCardinality = observationCardinality;
        this.observationCardinalityNext = observationCardinalityNext;
        this.feeProtocol = feeProtocol;
        this.unlocked = unlocked;
    }

    public Slot0 () {}
}