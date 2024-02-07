package network.balanced.score.core.savings;

import static network.balanced.score.lib.utils.Constants.EXA;

import java.math.BigInteger;
import java.net.ContentHandler;
import java.util.Map;

import network.balanced.score.lib.utils.BalancedFloorLimits;
import network.balanced.score.lib.utils.EnumerableSetDB;
import score.Address;
import score.BranchDB;
import score.Context;
import score.DictDB;
import score.VarDB;
import scorex.util.HashMap;

public class RewardsManager {
    private static final DictDB<String, BigInteger> workingBalance = Context.newDictDB("workingBalance", BigInteger.class);
    private static final VarDB<BigInteger> totalWorkingBalance = Context.newVarDB("workingBalance", BigInteger.class);
    private static final DictDB<Address, BigInteger> tokenWeight = Context.newDictDB("tokenWeight", BigInteger.class);
    private static final BranchDB<String, DictDB<Address, BigInteger>> userRewards = Context.newBranchDB("userRewards", BigInteger.class);
    private static final BranchDB<String, DictDB<Address, BigInteger>> userWeights = Context.newBranchDB("userWeight", BigInteger.class);
    private static final EnumerableSetDB<Address> allowedTokens = new EnumerableSetDB<>("allowedRewardsTokens", Address.class);

    public static void updateUserRewards(String user, Address token, BigInteger userBalance) {
        updateUserRewards(user, token, userBalance, false);
    }

    public static BigInteger readUserRewards(String user, Address token, BigInteger userBalance) {
        return updateUserRewards(user, token, userBalance, true);

    }
    private static BigInteger updateUserRewards(String user, Address token, BigInteger userBalance, boolean readonly) {
        BigInteger prevRewards = userRewards.at(user).getOrDefault(token, BigInteger.ZERO);
        BigInteger totalWeight = tokenWeight.getOrDefault(token, BigInteger.ZERO);
        BigInteger userWeight = userWeights.at(user).getOrDefault(token, BigInteger.ZERO);
        BigInteger deltaWeight = totalWeight.subtract(userWeight);
        BigInteger reward = deltaWeight.multiply(userBalance).divide(EXA);

        if (!readonly) {
            userRewards.at(user).set(token, prevRewards.add(reward));
            userWeights.at(user).set(token, totalWeight);
        }

        return prevRewards.add(reward);
    }


    public static void updateAllUserRewards(String user) {
        BigInteger balance = workingBalance.getOrDefault(user, BigInteger.ZERO);

        int numberOfTokens = allowedTokens.length();
        for (int i = 0; i < numberOfTokens; i++) {
            Address token = allowedTokens.at(i);
            updateUserRewards(user, token, balance);
        }
    }

    public static void updateAllUserRewards(String user, BigInteger userBalance) {
        int numberOfTokens = allowedTokens.length();
        for (int i = 0; i < numberOfTokens; i++) {
            Address token = allowedTokens.at(i);
            updateUserRewards(user, token, userBalance);
        }
    }

    public static void addWeight(Address token, BigInteger amount) {
        BigInteger prevWeight = tokenWeight.getOrDefault(token, BigInteger.ZERO);
        BigInteger workingTotal = totalWorkingBalance.getOrDefault(BigInteger.ZERO);
        Context.require(workingTotal.compareTo(BigInteger.ZERO) > 0, "bnUSD must be locked in order to deposit rewards");
        BigInteger addedWeight = amount.multiply(EXA).divide(workingTotal);
        tokenWeight.set(token, prevWeight.add(addedWeight));
    }

    public static void changeLock(String user, BigInteger change) {
        checkAddressIsICON(user);
        BigInteger prevAmount = workingBalance.getOrDefault(user, BigInteger.ZERO);
        BigInteger prevTotal = totalWorkingBalance.getOrDefault(BigInteger.ZERO);
        updateAllUserRewards(user, prevAmount);

        BigInteger newBalance = prevAmount.add(change);
        Context.require(newBalance.compareTo(BigInteger.ZERO) >= 0, "Cannot unlock more than locked balance");
        workingBalance.set(user, newBalance);
        totalWorkingBalance.set(prevTotal.add(change));
    }

    public static void claimRewards(Address user) {
        updateAllUserRewards(user.toString());
        int numberOfTokens = allowedTokens.length();
        DictDB<Address, BigInteger> rewards = userRewards.at(user.toString());

        for (int i = 0; i < numberOfTokens; i++) {
            Address token = allowedTokens.at(i);
            BigInteger amount = rewards.getOrDefault(token, BigInteger.ZERO);
            rewards.set(token, null);
            BalancedFloorLimits.verifyWithdraw(token, amount);
            if (!amount.equals(BigInteger.ZERO)) {
                Context.call(token, "transfer", user, amount, new byte[0]);
            }
        }
    }

    public static Map<String, BigInteger> getUnclaimedRewards(String user) {
        int numberOfTokens = allowedTokens.length();
        BigInteger balance = workingBalance.getOrDefault(user, BigInteger.ZERO);
        Map<String, BigInteger> unclaimedRewards = new HashMap<>();
        for (int i = 0; i < numberOfTokens; i++) {
            Address token = allowedTokens.at(i);
            BigInteger amount = readUserRewards(user.toString(), token, balance);
            unclaimedRewards.put(token.toString(), amount);
        }

        return unclaimedRewards;
    }

    public static final BigInteger getTotalWorkingbalance() {
        return totalWorkingBalance.get();
    }

    public static BigInteger getLockedAmount(String user) {
        return workingBalance.get(user);
    }

    public static void addToken(Address token) {
        allowedTokens.add(token);
    }

    public static void removeToken(Address token) {
        allowedTokens.remove(token);
    }

    // For now only allow ICON addresses to lock
    // But keep as string to allow it in the future easily
    private static void checkAddressIsICON(String str) {
        Context.require(str.length() == Address.LENGTH * 2, "Only ICON addresses are allowed to lock into the saving account at this time");
        Context.require(str.startsWith("hx") || str.startsWith("cx"), "Only ICON addresses are allowed to lock into the saving account at this time");
    }
}
