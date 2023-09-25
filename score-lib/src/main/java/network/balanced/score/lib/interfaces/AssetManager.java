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
import icon.xcall.lib.annotation.XCall;
import network.balanced.score.lib.interfaces.addresses.AddressManager;
import network.balanced.score.lib.interfaces.base.Fallback;
import network.balanced.score.lib.interfaces.base.Version;
import score.Address;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;

import java.math.BigInteger;
import java.util.Map;

@ScoreClient
@ScoreInterface
public interface AssetManager extends AddressManager, Version, Fallback {
    @External
    void deployAsset(String tokenNetworkAddress, String name, String symbol, BigInteger decimals);

    @External
    void addSpokeManager(String spokeAssetManager);


    @External(readonly = true)
    Map<String, String> getAssets();

    @External(readonly = true)
    String[] getSpokes();

    @External(readonly = true)
    Address getAssetAddress(String spokeAddress);

    @External(readonly = true)
    String getNativeAssetAddress(Address token);

    /**
     * withdraws amount to `to` address
     *
     * @param asset  icon asset address to be withdrawn.
     * @param to     address to withdraw to.
     * @param amount amount to withdraw.
     */
    @External
    @Payable
    void withdrawTo(Address asset, String to, BigInteger amount);

    /**
     * deposits to fromAddress then initiate a transfer to toAddress
     *
     * @param from         xCall caller.
     * @param tokenAddress native token address as string.
     * @param fromAddress  native caller address as string.
     * @param toAddress    network address to receive deposit, if empty string deposit is to fromAddress
     * @param _amount      amount to deposit and transfer
     * @param _data        transfer data
     */
    @XCall
    void deposit(String from, String tokenAddress, String fromAddress, String toAddress, BigInteger _amount, @Optional byte[] _data);

    /**
     * Withdraws tokens back to caller
     *
     * @param from         xCall caller.
     * @param tokenAddress token address
     * @param amount       amount to withdraw.
     */
    @XCall
    void xWithdraw(String from, Address tokenAddress, BigInteger amount);

    /**
     * return amount to _to in case of withdraw failure
     *
     * @param from         Always XCall address.
     * @param tokenAddress native token address as string.
     * @param _to          rollback network address.
     * @param _amount      amount to return.
     */
    @XCall
    void withdrawRollback(String from, String tokenAddress, String _to, BigInteger _amount);
}
