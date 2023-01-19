package network.balanced.score.lib.interfaces.base;

import score.annotation.External;

public interface XCallHandling {

    @External
    void handleCallMessage(String _from, byte[] _data);

    // Usability method to test your byte data on ICON before doing a XCall
    @External(readonly = true)
    void testXCall(byte[] data);
}