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

package network.balanced.score.lib.utils;

import score.Context;
import score.VarDB;

public class NonReentrant {
    private static final VarDB<String> txLock  = Context.newVarDB("global_tx_locked", String.class);

    static public void globalReentryLock() {
        byte[] txHash = Context.getTransactionHash();
        if (txHash == null) {
            return;
        }

        String tx = txHash.toString();
        String lastTx = txLock.getOrDefault("");

        Context.require(!tx.equals(lastTx), "Reentrancy Lock: Can't call multiple times in one transaction");
        txLock.set(tx);
    }
}
