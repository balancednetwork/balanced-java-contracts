package network.balanced.score.core.rewards.utils;

import network.balanced.score.core.rewards.RewardsImpl;

public class Check {
    public static boolean continuousRewardsActive() {
        return RewardsImpl.continuousRewardsDay.get() != null && 
               RewardsImpl.continuousRewardsDay.get().compareTo(RewardsImpl.getDay()) <= 0;
    }
}
