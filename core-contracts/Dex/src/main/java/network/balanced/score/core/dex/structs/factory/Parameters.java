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

package network.balanced.score.core.dex.structs.factory;

import java.math.BigInteger;
import java.util.Map;

import score.Address;
import score.ObjectReader;
import score.ObjectWriter;

public class Parameters {
    public Address factory;
    public Address token0;
    public Address token1;
    public Integer fee;
    public Integer tickSpacing;

    public static void writeObject(ObjectWriter w, Parameters obj) {
        w.write(obj.factory);
        w.write(obj.token0);
        w.write(obj.token1);
        w.write(obj.fee);
        w.write(obj.tickSpacing);
    }

    public static Parameters readObject(ObjectReader r) {
        return new Parameters(
            r.readAddress(), // factory, 
            r.readAddress(), // token0, 
            r.readAddress(), // token1, 
            r.readInt(), // fee, 
            r.readInt() // tickSpacing
        );
    }

    public Parameters (
        Address factory,
        Address token0,
        Address token1,
        Integer fee,
        Integer tickSpacing
    ) {
        this.factory = factory;
        this.token0 = token0;
        this.token1 = token1;
        this.fee = fee;
        this.tickSpacing = tickSpacing;
    }

    public static Parameters fromMap (Object call) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) call;
        return new Parameters (
            (Address) map.get("factory"), 
            (Address) map.get("token0"), 
            (Address) map.get("token1"), 
            ((BigInteger) map.get("fee")).intValue(),
            ((BigInteger) map.get("tickSpacing")).intValue()
        );
    }
}
