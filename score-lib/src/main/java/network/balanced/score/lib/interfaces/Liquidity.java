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

package network.balanced.score.lib.interfaces;

import foundation.icon.score.client.ScoreInterface;
import network.balanced.score.lib.interfaces.addresses.*;
import network.balanced.score.lib.interfaces.base.Name;
import score.annotation.External;
import score.Address;

import java.math.BigInteger;
import java.util.Map;

@ScoreInterface
public interface Liquidity extends AdminAddress, DexAddress, GovernanceAddress, DaofundAddress,
         StakedLpAddress, Name {

    @External
    void addPoolsToWhitelist(BigInteger[] ids);

    @External
    void removePoolsFromWhitelist(BigInteger[] ids);

    @External(readonly = true)
    BigInteger[] getWhitelistedPoolIds();

    @External
    void supplyLiquidity(Address baseToken, Address quoteToken, BigInteger baseValue, BigInteger quoteValue);
    
    @External
    void withdrawLiquidity(BigInteger poolID, BigInteger lptokens, boolean withdrawToDaofund);

    @External
    void stakeLPTokens(BigInteger id, BigInteger amount);

    @External
    void unstakeLPTokens(BigInteger id, BigInteger amount);

    @External(readonly = true)
    Map<String, BigInteger> getTokenBalances(boolean symbolsAsKeys);

    @External
    void claimFunding();

    @External 
    void transferToDaofund(Address token, BigInteger amount);

    @External
    void tokenFallback(Address _from, BigInteger _value, byte[] _data);

    @External
    void onIRC31Received(Address _operator, Address _from, BigInteger _id, BigInteger _value, byte[] _data);
}