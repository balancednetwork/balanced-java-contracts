package network.balanced.score.lib.tokens;

import java.math.BigInteger;

public interface CrossTokenHub extends IRC2Spoke {
    /**
    * Returns the total token supply across all connected chains.
    */
    BigInteger xTotalSupply();

     /**
    * Returns the total token supply on a connected chain.
    */
    BigInteger xSupply(String spokeAddress);

    /**
    * Returns a list of all bnUSD contracts across all connected chains
    */
    String[] getConnectedChains();

    /**
     * If {@code _to} is a ICON address, use IRC2 transfer
     * If {@code _to} is a BTPAddress, then the transaction must trigger xTransfer via XCall on corresponding spoke chain
     * and MUST fire the {@code XTransfer} event.
     * {@code _data} can be attached to this token transaction.
     * {@code _data} can be empty.
     * XCall rollback message is specified to match {@link #xTransferRevert}.
     */
    void xTransfer(String to, BigInteger amount, byte[] data);
    /**
     * XCall version of xTransfer.
     *Identical to {@link #xTransfer} expected with the from parameter specified rather than fetched from the Call.
     */
    void xTransfer(String from, String to, BigInteger amount, byte[] data);

    /**
     * TODO
     * */
    void xTransferRevert(BigInteger id,String from, String to, BigInteger amount, byte[] data);


    /**
     * (EventLog) Must trigger on any successful token transfers from cross chain addresses.
     */
    void XTransfer(BigInteger id, String _from, String _to, BigInteger _value, byte[] _data);
}
