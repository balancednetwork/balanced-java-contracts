/*
 * Copyright (c) 2022-2023 Balanced.network.
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

package network.balanced.score.lib.interfaces;

import foundation.icon.score.client.ScoreClient;
import foundation.icon.score.client.ScoreInterface;
import network.balanced.score.lib.annotations.XCall;
import network.balanced.score.lib.interfaces.base.TokenFallback;
import network.balanced.score.lib.interfaces.base.Version;
import network.balanced.score.lib.interfaces.base.Name;
import score.Address;
import score.annotation.External;
import score.annotation.Payable;
import score.annotation.Optional;

import java.math.BigInteger;

@ScoreClient
@ScoreInterface
public interface SpokeAssetManager extends Version, Name {

    /**
     * Burns tokens from user and unlocks on source
     *
     * @param from         xCall caller.
     * @param tokenAddress native token address as string.
     * @param toAddress    native caller address as string.
     * @param amount       amount to withdraw.
     */
    @XCall
    void WithdrawTo(String from, String tokenAddress, String toAddress, BigInteger amount);

        /**
     * Burns tokens from user and unlocks on source and tries to acquire the native token
     *
     * @param from         xCall caller.
     * @param tokenAddress native token address as string.
     * @param toAddress    native caller address as string.
     * @param amount       amount to withdraw.
     */
    @XCall
    void WithdrawNativeTo(String from, String tokenAddress, String toAddress, BigInteger amount);

        /**
     * EReverts a deposit message
     *
     * @param from         xCall caller.
     * @param token        token to be returned
     * @param to           the user who originally sent the funds
     * @param amount       amount to return
     */
    @XCall
    void DepositRevert(String from, Address token, Address to, BigInteger amount);

    @External
    void setXCallManager(Address address);

    @External(readonly = true)
    Address getXCallManager();

    @External
    void setICONAssetManager(String address);

    @External(readonly = true)
    String getICONAssetManager();

    @External
    void setXCall(Address address);

    @External(readonly = true)
    Address getXCall();

    @External
    @Payable
    void deposit(@Optional String _to, @Optional byte[] _data);
}
