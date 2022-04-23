package network.balanced.score.core.governance.interfaces;

import score.annotation.External;
import score.annotation.Payable;

import foundation.icon.score.client.ScoreInterface;

@ScoreInterface
public interface Staking extends Setter {
    @External
    @Payable
    void stakeICX();
}
