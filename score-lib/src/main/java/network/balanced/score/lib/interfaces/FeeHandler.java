package network.balanced.score.lib.interfaces;

import score.annotation.External;

public interface FeeHandler {

    @External
    void enable();

    @External
    void disable();

    @External
    void setFeeProcessingInterval();

    @External(readonly = true)
    int getFeeProcessingInterval();
}
