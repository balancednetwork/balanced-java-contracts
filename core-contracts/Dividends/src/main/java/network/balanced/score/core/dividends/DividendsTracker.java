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

package network.balanced.score.core.dividends;

import static network.balanced.score.core.dividends.Check.continuousDividendsActive;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.MICRO_SECONDS_IN_A_DAY;

import java.math.BigInteger;

import score.Address;
import score.BranchDB;
import score.Context;
import score.DictDB;
import score.VarDB;

public class DividendsTracker {
    private static final BranchDB<Address, DictDB<String, BigInteger>> userWeight = Context.newBranchDB("user_weight",
            BigInteger.class);
    private static final DictDB<String, BigInteger>  lastUpdateTimeUs = Context.newDictDB("last_update_us",
            BigInteger.class);
    private static final  VarDB<BigInteger> totalSupply = Context.newVarDB("balnSupply", BigInteger.class);
    private static final  DictDB<String, BigInteger> totalWeight = Context.newDictDB("running_total",
            BigInteger.class);

    public static BigInteger getUserWeight(Address user, String token) {
        return userWeight.at(user).getOrDefault(token, BigInteger.ZERO);
    }

    public static BigInteger getLastUpdateTimeUs(String token) {
        return lastUpdateTimeUs.getOrDefault(token, getContinuousDividendsDayInUS());
    }

    public static BigInteger getTotalSupply() {
        return totalSupply.getOrDefault(BigInteger.ZERO);
    }

    public static void setTotalSupply(BigInteger supply) {
        totalSupply.set(supply);
    }

    private static BigInteger getContinuousDividendsDayInUS() {
        Context.require(DividendsImpl.continuousDividendsDay.get() != null, "continuous Dividends Day is not yet set");
        return DividendsImpl.continuousDividendsDay.get().multiply(MICRO_SECONDS_IN_A_DAY);
    }

    public static BigInteger getTotalWeight(String token) {
        return totalWeight.getOrDefault(token, BigInteger.ZERO);
    }

    public static BigInteger getBalnBalance(Address user) {
        return Context.call(BigInteger.class, DividendsImpl.balnScore.get(), "stakedBalanceOf", user);
    }

    public static BigInteger updateUserData(String token, Address user, BigInteger prevBalance, boolean readOnlyContext) {
        if (!continuousDividendsActive()) {
            return BigInteger.ZERO;
        }

        BigInteger currentUserWeight = getUserWeight(user, token);
        BigInteger totalWeight = getTotalWeight(token);
        if (!readOnlyContext) {
            userWeight.at(user).set(token, totalWeight);
        }

        return computeUserRewards(prevBalance, totalWeight, currentUserWeight);
    }

    public static void updateTotalWeight(String token, BigInteger amountRecived, BigInteger currentTime) {
        if (!continuousDividendsActive()) {
            return;
        }

        BigInteger previousTotalWeight = getTotalWeight(token);
        BigInteger lastUpdateTimestamp = getLastUpdateTimeUs(token);
        
        BigInteger timeDelta = currentTime.subtract(lastUpdateTimestamp);
        BigInteger addedWeight = amountRecived.multiply(timeDelta).divide(getTotalSupply());
        
        totalWeight.set(token, previousTotalWeight.add(addedWeight));
        lastUpdateTimeUs.set(token, currentTime);   
    }

    private static BigInteger computeUserRewards(BigInteger prevUserBalance, BigInteger totalWeight, BigInteger userWeight) {
        BigInteger deltaWeight = totalWeight.subtract(userWeight);
        return deltaWeight.multiply(prevUserBalance).divide(EXA);
    }
}