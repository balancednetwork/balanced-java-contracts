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

package network.balanced.score.core.batchDisbursement;

import score.*;
import score.annotation.Optional;

import java.math.BigInteger;

import network.balanced.score.lib.utils.BalancedAddressManager;


public class BatchDisbursement {

    public static final VarDB<Address> governance = Context.newVarDB("governance", Address.class);

    public BatchDisbursement(@Optional Address _governance) {
        if (governance.get() == null) {
            governance.set(_governance);
        }

        BalancedAddressManager.setGovernance(governance.get());
        Address sICX = BalancedAddressManager.getSicx();
        BigInteger balance = Context.call(BigInteger.class, sICX, "balanceOf", Context.getAddress());
        Context.call(sICX, "transfer", BalancedAddressManager.getDaofund(), balance, new byte[0]);
    }



}
