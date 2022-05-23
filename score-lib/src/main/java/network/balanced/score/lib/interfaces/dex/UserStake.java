package network.balanced.score.lib.interfaces.dex;

import score.Address;
import score.annotation.External;

public interface UserStake {
    @External
    @score.annotation.Payable
    void stakeICX(Address _to, byte[] _data);
}
