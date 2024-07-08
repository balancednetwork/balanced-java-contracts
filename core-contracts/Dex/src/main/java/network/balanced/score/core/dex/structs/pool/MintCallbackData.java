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

import score.Address;
import score.ObjectReader;
import score.ObjectWriter;

public class MintCallbackData {
    public PoolAddress.PoolKey poolKey;
    public Address payer;

    public MintCallbackData (PoolAddress.PoolKey poolKey, Address payer) {
        this.poolKey = poolKey;
        this.payer = payer;
    }

    public static MintCallbackData readObject(ObjectReader reader) {
        PoolAddress.PoolKey poolKey = reader.read(PoolAddress.PoolKey.class);
        Address payer = reader.readAddress();
        return new MintCallbackData(poolKey, payer);
    }

    public static void writeObject(ObjectWriter w, MintCallbackData obj) {
        w.write(obj.poolKey);
        w.write(obj.payer);
    }
}