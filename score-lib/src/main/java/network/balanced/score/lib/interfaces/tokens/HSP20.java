package network.balanced.score.lib.interfaces.tokens;

import score.Address;

import java.math.BigInteger;

import foundation.icon.score.client.ScoreInterface;

@ScoreInterface
public interface HSP20 {
    // ================================================
    // Event Logs
    // ================================================

    /**
     * (EventLog) Must trigger on any successful token transfers.
     */
    void Transfer(Address _from, Address _to, BigInteger _value);

    /**
     * (EventLog) Must trigger on any successful call to
     * {@code approve(Address, int)}.
     */
    void Approval(Address _owner, Address _spender, BigInteger _value);

    // ================================================
    // External methods
    // ================================================

    /**
     * Returns the total token supply.
     */
    BigInteger totalSupply();

    /**
     * Returns the account balance of another account with address {@code _owner}.
     */
    BigInteger balanceOf(Address _owner);

    /**
     * Transfers {@code _value} amount of tokens to address {@code _to}, and MUST
     * fire the {@code Transfer} event.
     * This function SHOULD throw if the caller account balance does not have enough
     * tokens to spend.
     */
    boolean transfer(Address _to, BigInteger _value);

    /**
     * Returns the amount which {@code _spender} is still allowed to withdraw from
     * {@code _owner}.
     */
    BigInteger allowance(Address _owner, Address _spender);

    /**
     * Allows {@code _spender} to withdraw from your account multiple times, up to
     * the {@code _value} amount.
     * If this function is called again it overwrites the current allowance with
     * _value.
     */
    boolean approve(Address _spender, BigInteger _value);

    /**
     * Transfers {@code _value} amount of tokens from address {@code _from} to
     * address {@code _to}, and MUST fire the Transfer event.
     * The transferFrom method is used for a withdraw workflow, allowing contracts
     * to transfer tokens on your behalf.
     * This can be used for example to allow a contract to transfer tokens on your
     * behalf and/or to charge fees in sub-currencies.
     * The function SHOULD throw unless the {@code _from} account has deliberately
     * authorized the sender of the message via some mechanism.
     *
     * NOTE: Transfers of 0 values MUST be treated as normal transfers and fire the
     * Transfer event.
     */
    boolean transferFrom(Address _from, Address _to, BigInteger _value);
}