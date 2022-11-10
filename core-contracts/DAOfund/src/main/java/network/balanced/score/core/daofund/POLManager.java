package network.balanced.score.core.daofund;

import static network.balanced.score.lib.utils.Constants.MAX_LOCK_TIME;

import java.math.BigInteger;
import java.util.Map;

import network.balanced.score.lib.utils.IterableDictDB;
import score.Address;
import score.Context;
import score.VarDB;
import scorex.util.HashMap;

import static network.balanced.score.lib.utils.BalancedAddressManager.getBoostedBaln;
import static network.balanced.score.lib.utils.BalancedAddressManager.getRewards;
import static network.balanced.score.lib.utils.BalancedAddressManager.getDividends;
import static network.balanced.score.lib.utils.BalancedAddressManager.getDex;
import static network.balanced.score.lib.utils.BalancedAddressManager.getStakedLp;

public class POLManager {
    private static final byte[] tokenDepositData = "{\"method\":\"_deposit\"}".getBytes();
    private static final String FEE_EARNINGS = "fee_earnings";
    private static final String BALN_EARNINGS = "baln_earnings";
    private static final IterableDictDB<Address, BigInteger> feeEarnings = new IterableDictDB<Address, BigInteger>(FEE_EARNINGS, BigInteger.class, Address.class, false);
    private static final VarDB<BigInteger> balnEarnings = Context.newVarDB(BALN_EARNINGS, BigInteger.class);

    private static final String FEE_PROCESSING = "fee_processing";
    private static final String REWARDS_PROCESSING = "rewards_processing";
    private static final VarDB<Boolean> feeProcessing = Context.newVarDB(FEE_PROCESSING, Boolean.class);
    private static final VarDB<Boolean> rewardsProcessing = Context.newVarDB(REWARDS_PROCESSING, Boolean.class);

    public static void claimRewards() {
        rewardsProcessing.set(true);
        Address rewardsAddress = getRewards();
        String[] sources = Context.call(String[].class, rewardsAddress, "getUserSources", Context.getAddress());
        Context.call(rewardsAddress, "claimRewards", (Object)sources);
        rewardsProcessing.set(false);
    }

    public static void claimNetworkFees() {
        feeProcessing.set(true);
        Context.call(getDividends(), "claimDividends");
        feeProcessing.set(false);
    }

    public static void supplyLiquidity(Address baseAddress, BigInteger baseAmount, Address quoteAddress, BigInteger quoteAmount) {
        Address dex = getDex();
        Context.call(baseAddress, "transfer", dex, baseAmount, tokenDepositData);
        Context.call(quoteAddress, "transfer", dex, quoteAmount, tokenDepositData);
        Context.call(dex, "add", baseAddress, quoteAddress, baseAmount, quoteAmount, true);


        BigInteger pid = Context.call(BigInteger.class, dex, "getPoolId", baseAddress, quoteAddress);
        BigInteger balance = Context.call(BigInteger.class, dex, "balanceOf", Context.getAddress(), pid);

        Context.call(dex, "transfer", getStakedLp(), balance, pid, new byte[0]);
    }

    public static void withdrawLiquidity(BigInteger pid, BigInteger amount) {
        Context.call(getStakedLp(), "unstake", pid, amount);
        Context.call(getDex(), "remove", pid, amount, true);
    }

    public static BigInteger getBalnEarnings() {
        return balnEarnings.get();
    }

    public static Map<Address, BigInteger> getFeeEarnings() {
        Map<Address, BigInteger> fees = new HashMap<>();
        for (Address address : feeEarnings.keys()) {
            fees.put(address, feeEarnings.get(address));
        }

        return fees;
    }

    public static boolean isProcessingFees() {
        return feeProcessing.getOrDefault(false);
    }

    public static boolean isProcessingRewards() {
        return rewardsProcessing.getOrDefault(false);
    }

    public static void handleRewards(BigInteger amount) {
        BigInteger previousEarnings = balnEarnings.getOrDefault(BigInteger.ZERO);
        balnEarnings.set(previousEarnings.add(amount));

        BigInteger unlockTime = BigInteger.valueOf(Context.getBlockTimestamp()).add(MAX_LOCK_TIME);

        String data;
        boolean hasLocked = Context.call(Boolean.class, getBoostedBaln(), "hasLocked", Context.getAddress());
        if (!hasLocked) {
            data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTime + "}}";
        } else {
            data = "{\"method\":\"increaseAmount\",\"params\":{\"unlockTime\":" + unlockTime + "}}";
        }

        Context.call(Context.getCaller(), "transfer", getBoostedBaln(), amount, data.getBytes());
    }

    public static void handleFee(BigInteger amount) {
        Address tokenAddress = Context.getCaller();
        BigInteger previousEarnings = feeEarnings.getOrDefault(tokenAddress, BigInteger.ZERO);
        feeEarnings.set(tokenAddress, previousEarnings.add(amount));
    }
}