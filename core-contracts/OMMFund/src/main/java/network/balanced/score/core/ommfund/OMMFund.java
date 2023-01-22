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

package network.balanced.score.core.ommfund;

import score.*;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.utils.Check.onlyOwner;
import static network.balanced.score.lib.utils.Constants.EOA_ZERO;

public class OMMFund{

    @EventLog(indexed = 3)
    public void Disburse(Address token, BigInteger amount, Address recipient, String note) {
    }


    public OMMFund() {
    }

    @External(readonly = true)
    public String name() {
        return "OMM Recovery fund";
    }

    @External
    public void disburse(Address token, Address recipient, BigInteger amount, @Optional byte[] data) {
        onlyOwner();
        Context.call(token, "transfer", recipient, amount, data);
        Disburse(token, amount, recipient,
                "OMM recovery fund disbursement " + amount + " sent to " + recipient.toString());
    }

    @External
    public void disburseICX(Address recipient, BigInteger amount) {
        onlyOwner();
        Context.transfer(recipient, amount);
        Disburse(EOA_ZERO, amount, recipient,
                "OMM recovery fund disbursement " + amount + " sent to " + recipient.toString());
    }




    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
    }

    @Payable
    public void fallback() {
    }


}
