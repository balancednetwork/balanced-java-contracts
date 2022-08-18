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
    private static final BranchDB<Address, DictDB<Address, BigInteger>> userWeight = Context.newBranchDB("user_weight",
            BigInteger.class);
    private static final  DictDB<Address, Boolean> bBalnFlag = Context.newDictDB(BBALN_FLAG,
            Boolean.class);
    private static final  VarDB<BigInteger> totalSupply = Context.newVarDB("balnSupply", BigInteger.class);
    private static final  DictDB<Address, BigInteger> totalWeight = Context.newDictDB("running_total",
            BigInteger.class);

    private static final BranchDB<Address, DictDB<Address, BigInteger>> boostedUserWeight = Context.newBranchDB(BBALN_USER_WEIGHT,
            BigInteger.class);
    private static final  VarDB<BigInteger> boostedTotalSupply = Context.newVarDB(BBALN_SUPPLY, BigInteger.class);
    private static final  DictDB<Address, BigInteger> boostedTotalWeight = Context.newDictDB(BBALN_TOTAL_WEIGHT,
            BigInteger.class);
    protected static final VarDB<BigInteger> bBalnDay = Context.newVarDB(BBALN_DAY, BigInteger.class);


    public static BigInteger getUserWeight(Address user, Address token, boolean isUserMigrated) {
        if(isUserMigrated){
            return boostedUserWeight.at(user).getOrDefault(token, BigInteger.ZERO);
        }
        return userWeight.at(user).getOrDefault(token, BigInteger.ZERO);
    }

    protected static boolean isUserMigrated(Address user) {

        if(bBalnFlag.getOrDefault(user, false).equals(true)){
            return true;
        }
        return false;
    }

    protected static BigInteger getBoostedBalnDay(){
        return bBalnDay.getOrDefault(BigInteger.ZERO);
    }

    public static BigInteger getTotalSupply(boolean isBBalnDay) {
        if(isBBalnDay){
            return boostedTotalSupply.getOrDefault(BigInteger.ZERO);
        }
        return totalSupply.getOrDefault(BigInteger.ZERO);
    }

    public static void setFlag(Address user, boolean flag){
        if(bBalnFlag.get(user) == null){
            bBalnFlag.set(user, flag);
        }
    }

    public static void setTotalSupply(BigInteger supply)  {
            totalSupply.set(supply);
        }

    public static void setBBalnTotalSupply(BigInteger supply) {
        boostedTotalSupply.set(supply);
    }

    public static BigInteger getTotalWeight(Address token, boolean isBBalnDay) {
        if(isBBalnDay){
            return boostedTotalWeight.getOrDefault(token, BigInteger.ZERO);
        }
        return totalWeight.getOrDefault(token, BigInteger.ZERO);
    }

    public static BigInteger updateUserData(Address token, Address user, BigInteger prevBalance, boolean readOnlyContext) {
        if (!continuousDividendsActive()) {
            return BigInteger.ZERO;
        }
        boolean isUserMigrated = isUserMigrated(user);
        BigInteger currentUserWeight = getUserWeight(user, token, isUserMigrated);
        BigInteger totalWeight = getTotalWeight(token, isUserMigrated);
        if (!readOnlyContext) {
            if (isUserMigrated) {
                boostedUserWeight.at(user).set(token, totalWeight);
            } else {
                userWeight.at(user).set(token, totalWeight);
            }
        }
        return computeUserRewards(prevBalance, totalWeight, currentUserWeight);
    }

    protected static void setTotalWeight(Address token, BigInteger amountReceived, boolean isBBalnDay){
        BigInteger addedWeight = amountReceived.multiply(EXA).divide(getTotalSupply(isBBalnDay));
        BigInteger previousTotalWeight = getTotalWeight(token, isBBalnDay);
        if(isBBalnDay){
            boostedTotalWeight.set(token, previousTotalWeight.add(addedWeight));
        }
        else {
            totalWeight.set(token, previousTotalWeight.add(addedWeight));
        }
    }

    public static void updateTotalWeight(Address token, BigInteger amountReceived, BigInteger day) {

        setTotalWeight(token, amountReceived, day.compareTo(getBoostedBalnDay()) >= 0);
    }

    protected static BigInteger computeUserRewards(BigInteger prevUserBalance, BigInteger totalWeight, BigInteger userWeight) {
        BigInteger deltaWeight = totalWeight.subtract(userWeight);
        return deltaWeight.multiply(prevUserBalance).divide(EXA);
    }
}