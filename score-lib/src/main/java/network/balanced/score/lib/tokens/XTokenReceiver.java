package network.balanced.score.lib.tokens;

import java.math.BigInteger;

public interface XTokenReceiver {
      /**
     * Receives tokens cross chain enabled tokens where the from is in a String Address format,
     * pointing to a address on a XCall connected chain.
     *
     * Use BTPAddress as_from parameter?
     */
    void xTokenFallback(String _from, BigInteger _value, byte[] _data);
}
