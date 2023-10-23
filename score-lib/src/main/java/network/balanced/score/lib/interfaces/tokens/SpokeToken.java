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

package network.balanced.score.lib.interfaces.tokens;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.EventLog;

import java.math.BigInteger;
import icon.xcall.lib.annotation.XCall;

public interface SpokeToken extends IRC2 {
    /**
     * Returns the account balance of another account with string address {@code _owner},
     * which can be both ICON and BTP Address format.
     */
    @External(readonly = true)
    BigInteger xBalanceOf(String _owner);

     /**
     * If {@code _to} is a ICON address, use IRC2 transfer
     * Transfers {@code _value} amount of tokens to BTP address {@code _to},
     * and MUST fire the {@code HubTransfer} event.
     * This function SHOULD throw if the caller account balance does not have enough tokens to spend.
     */
    @External
    void hubTransfer(String _to, BigInteger _value, @Optional byte[] _data);

    /**
     * Callable only via XCall service on ICON.
     * Transfers {@code _value} amount of tokens to address {@code _to},
     * and MUST fire the {@code HubTransfer} event.
     * This function SHOULD throw if the caller account balance does not
     * have enough tokens to spend.
     * If {@code _to} is a contract, this function MUST invoke the function
     * {@code xTokenFallback(String, int, bytes)}
     * in {@code _to}. If the {@code xTokenFallback} function is not implemented
     * in {@code _to} (receiver contract),
     * then the transaction must fail and the transfer of tokens should not occur.
     * If {@code _to} is an externally owned address, then the transaction
     * must be sent without trying to execute
     * {@code XTokenFallback} in {@code _to}.
     * {@code _data} can be attached to this token transaction.
     * {@code _data} can be empty.
     */
    @XCall
    void xHubTransfer(String from, String _to, BigInteger _value, byte[] _data);

    /**
     * (EventLog) Must trigger on any successful hub token transfers.
     */
    @EventLog
    void HubTransfer(String _from, String _to, BigInteger _value, byte[] _data);
}