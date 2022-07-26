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

import score.Context;
import score.VarDB;

public class NonReentrant {
    private final VarDB<Boolean> lock;

    public NonReentrant(String contractName) {
        this.lock = Context.newVarDB(contractName + "_locked", Boolean.class);
    }

    public void updateLock(boolean lock) {
        boolean lockStatus = this.lock.getOrDefault(false);
        if (lock) {
            Context.require(!lockStatus, "Reentrancy Lock: Can't change. The contract lock state: "  + lockStatus);
            this.lock.set(true);
        } else {
            Context.require(lockStatus, "Reentrancy Lock: Can't change. The contract lock state: " + lockStatus);
            this.lock.set(false);
        }
    }
}
