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

import foundation.icon.xcall.NetworkAddress;
import network.balanced.score.lib.utils.AddressDictDB;
import network.balanced.score.lib.utils.BranchedNetworkAddressDictDB;
import network.balanced.score.lib.utils.NetworkAddressBranchDictDB;
import score.*;

import java.math.BigInteger;

import static network.balanced.score.core.dividends.Constants.*;
import static network.balanced.score.lib.utils.Constants.EXA;

public class DividendsTracker {
    protected static final NetworkAddressBranchDictDB<Address, BigInteger> userWeight = new NetworkAddressBranchDictDB<>(
            "user_weight", BigInteger.class);
    private static final VarDB<BigInteger> totalSupply = Context.newVarDB("balnSupply", BigInteger.class);
    private static final DictDB<Address, BigInteger> totalWeight = Context.newDictDB("running_total",
            BigInteger.class);

    private static final NetworkAddressBranchDictDB<Address, BigInteger> boostedUserWeight =
            new NetworkAddressBranchDictDB<>(BBALN_USER_WEIGHT, BigInteger.class);
    private static final DictDB<Address, BigInteger> boostedTotalWeight = Context.newDictDB(BBALN_TOTAL_WEIGHT,
            BigInteger.class);

    protected static final AddressDictDB<BigInteger> userBalance = new AddressDictDB<>(USER_BBALN_BALANCE,
            BigInteger.class);
    
    private static final VarDB<BigInteger> boostedTotalSupply = Context.newVarDB(BBALN_SUPPLY, BigInteger.class);

    protected static final BranchedNetworkAddressDictDB<Address, Boolean> balnRewardsClaimed = new BranchedNetworkAddressDictDB<>(
            "baln_to_bBaln_migration_check", Boolean.class);

    public static BigInteger getUserWeight(String user, Address token) {
        return userWeight.getOrDefault(NetworkAddress.valueOf(user), token, BigInteger.ZERO);
    }

    public static void setTotalSupply(BigInteger supply) {
        totalSupply.set(supply);
    }

    public static BigInteger getUserBoostedWeight(String user, Address token) {
        return boostedUserWeight.getOrDefault(NetworkAddress.valueOf(user), token, BigInteger.ZERO);
    }

    public static BigInteger getBoostedTotalSupply() {
        return boostedTotalSupply.getOrDefault(BigInteger.ZERO);
    }

    public static void setBBalnTotalSupply(BigInteger supply) {
        boostedTotalSupply.set(supply);
    }

    public static BigInteger getTotalWeight(Address token) {
        return totalWeight.getOrDefault(token, BigInteger.ZERO);
    }

    public static BigInteger getBoostedTotalWeight(Address token) {
        return boostedTotalWeight.getOrDefault(token, BigInteger.ZERO);
    }

    public static Boolean balnRewardsClaimed(String user, Address token) {
        return balnRewardsClaimed.at(token).getOrDefault(NetworkAddress.valueOf(user), false);
    }

    public static BigInteger updateUserData(Address token, String user, BigInteger prevBalance,
                                            boolean readOnlyContext) {
        BigInteger currentUserWeight = getUserWeight(user, token);
        BigInteger totalWeight = getTotalWeight(token);
        if (!readOnlyContext) {
            userWeight.set(NetworkAddress.valueOf(user), token, totalWeight);
            balnRewardsClaimed.at(token).set(NetworkAddress.valueOf(user), true);
        }

        return computeUserRewards(prevBalance, totalWeight, currentUserWeight);
    }

    public static BigInteger updateBoostedUserData(Address token, String user, BigInteger prevBalance,
                                                   boolean readOnlyContext) {
        BigInteger currentUserWeight = getUserBoostedWeight(user, token);
        BigInteger totalWeight = getBoostedTotalWeight(token);
        if (!readOnlyContext) {
            boostedUserWeight.set(NetworkAddress.valueOf(user), token, totalWeight);
        }

        return computeUserRewards(prevBalance, totalWeight, currentUserWeight);
    }

    protected static void setBoostedTotalWeight(Address token, BigInteger amountReceived) {
        BigInteger previousTotalWeight = getBoostedTotalWeight(token);
        BigInteger addedWeight = amountReceived.multiply(EXA).divide(getBoostedTotalSupply());
        boostedTotalWeight.set(token, previousTotalWeight.add(addedWeight));
    }

    public static void updateBoostedTotalWeight(Address token, BigInteger amountReceived) {
        setBoostedTotalWeight(token, amountReceived);
    }

    protected static BigInteger computeUserRewards(BigInteger prevUserBalance, BigInteger totalWeight,
                                                   BigInteger userWeight) {
        BigInteger deltaWeight = totalWeight.subtract(userWeight);
        return deltaWeight.multiply(prevUserBalance).divide(EXA);
    }
}