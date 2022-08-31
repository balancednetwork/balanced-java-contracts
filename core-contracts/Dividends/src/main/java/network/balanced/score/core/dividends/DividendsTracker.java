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
import static network.balanced.score.core.dividends.Constants.*;
import static network.balanced.score.lib.utils.Constants.EXA;

import java.math.BigInteger;

import score.Address;
import score.BranchDB;
import score.Context;
import score.DictDB;
import score.VarDB;

public class DividendsTracker {
    protected static final BranchDB<Address, DictDB<Address, BigInteger>> userWeight = Context.newBranchDB("user_weight",
            BigInteger.class);
    private static final  VarDB<BigInteger> totalSupply = Context.newVarDB("balnSupply", BigInteger.class);
    private static final  DictDB<Address, BigInteger> totalWeight = Context.newDictDB("running_total",
            BigInteger.class);

    private static final BranchDB<Address, DictDB<Address, BigInteger>> boostedUserWeight = Context.newBranchDB(BBALN_USER_WEIGHT,
            BigInteger.class);
    private static final  VarDB<BigInteger> boostedTotalSupply = Context.newVarDB(BBALN_SUPPLY, BigInteger.class);
    private static final  DictDB<Address, BigInteger> boostedTotalWeight = Context.newDictDB(BBALN_TOTAL_WEIGHT,
            BigInteger.class);
    protected static final  DictDB<Address, BigInteger> userBalance = Context.newDictDB(USER_BBALN_BALANCE,
            BigInteger.class);
    protected static final VarDB<BigInteger> bBalnDay = Context.newVarDB(BBALN_DAY, BigInteger.class);


    public static BigInteger getUserWeight(Address user, Address token) {
        return userWeight.at(user).getOrDefault(token, BigInteger.ZERO);
    }

    public static BigInteger getUserBoostedWeight(Address user, Address token) {
        return boostedUserWeight.at(user).getOrDefault(token, BigInteger.ZERO);
    }

    protected static BigInteger getBoostedBalnDay(){
        return bBalnDay.getOrDefault(BigInteger.ZERO);
    }

    public static BigInteger getTotalSupply() {
        return totalSupply.getOrDefault(BigInteger.ZERO);
    }

    public static BigInteger getBoostedTotalSupply() {
        return boostedTotalSupply.getOrDefault(BigInteger.ZERO);
    }

    public static void setTotalSupply(BigInteger supply)  {
            totalSupply.set(supply);
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

    public static BigInteger updateUserData(Address token, Address user, BigInteger prevBalance, boolean readOnlyContext) {
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

    public static BigInteger updateBoostedUserData(Address token, Address user, BigInteger prevBalance, boolean readOnlyContext) {
        if (!continuousDividendsActive()) {
            return BigInteger.ZERO;
        }
        BigInteger currentUserWeight = getUserBoostedWeight(user, token);
        BigInteger totalWeight = getBoostedTotalWeight(token);
        if (!readOnlyContext) {
            boostedUserWeight.at(user).set(token, totalWeight);
        }
        return computeUserRewards(prevBalance, totalWeight, currentUserWeight);
    }

    protected static void setTotalWeight(Address token, BigInteger amountReceived){
        BigInteger addedWeight = amountReceived.multiply(EXA).divide(getTotalSupply());
        BigInteger previousTotalWeight = getTotalWeight(token);
        totalWeight.set(token, previousTotalWeight.add(addedWeight));
    }

    protected static void setBoostedTotalWeight(Address token, BigInteger amountReceived){
        BigInteger addedWeight = amountReceived.multiply(EXA).divide(getBoostedTotalSupply());
        BigInteger previousTotalWeight = getBoostedTotalWeight(token);
        boostedTotalWeight.set(token, previousTotalWeight.add(addedWeight));
    }

    protected static boolean checkBBalnDay(BigInteger day){
        return day.compareTo(getBoostedBalnDay()) >= 0 ;
    }

    public static void updateTotalWeight(Address token, BigInteger amountReceived) {

        setTotalWeight(token, amountReceived);
    }

    public static void updateBoostedTotalWeight(Address token, BigInteger amountReceived) {
        setBoostedTotalWeight(token, amountReceived);
    }

    protected static BigInteger computeUserRewards(BigInteger prevUserBalance, BigInteger totalWeight, BigInteger userWeight) {
        BigInteger deltaWeight = totalWeight.subtract(userWeight);
        return deltaWeight.multiply(prevUserBalance).divide(EXA);
    }
}