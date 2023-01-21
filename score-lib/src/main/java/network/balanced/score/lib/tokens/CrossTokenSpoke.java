package network.balanced.score.lib.tokens;

import java.math.BigInteger;

import network.balanced.score.lib.interfaces.tokens.IRC2;
/**
 * Spoke side of CrossToken standard.
 * Implementation should be done on XCall enabled chains.
 * This interface demonstrates how that would look on ICON.
 */
public interface CrossTokenSpoke extends IRC2 {
    /**
    * Returns the Address of the corresponding hub contract
    */
    String getHub();

    /**
     * Transfers {@code _value} amount of tokens to address {@code _to}, and MUST fire the {@code XTransfer} event.
     * This function SHOULD throw if the caller account balance does not have enough tokens to spend.
     * If {@code _to} is a ICON contract, this function MUST invoke the function {@code tokenFallback(Address, int, bytes)}
     * in {@code _to}. If the {@code tokenFallback} function is not implemented in {@code _to} (receiver contract),
     * then the transaction must fail and the transfer of tokens should not occur.
     * If {@code _to} is an externally owned ICON address, then the transaction must be sent without trying to execute
     * {@code tokenFallback} in {@code _to}.
     * If {@code _to} is an btp address, then the transaction must trigger xTransfer via XCall on corresponding spoke chain.
     * XCall rollback message is specified to match {@link #xTransferRevert}.
     * If a XCall xTransfer fails
     * {@code _data} can be attached to this token transaction.
     * {@code _data} can be empty.
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
    void xTransferRevert(String from, String to, BigInteger amount, byte[] data);

    /**
     * (EventLog) Must trigger on any successful token transfers from cross chain addresses.
     */
    void XTransfer(String _from, String _to, BigInteger _value, byte[] _data);

}
