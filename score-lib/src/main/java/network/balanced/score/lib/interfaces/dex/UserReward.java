package network.balanced.score.lib.interfaces.dex;

import score.annotation.External;

import java.util.List;

public interface UserReward {

    @External
    void distribute();

    @External(readonly = true)
    List<String> getDataSourceNames();



}
