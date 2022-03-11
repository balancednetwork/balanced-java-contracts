package network.balanced.score.mock;

import score.VarDB;
import score.Address;
import score.Context;
import score.annotation.External;

import java.math.BigInteger;

public class RebalanceMock {
    public static final VarDB<Address> loans = Context.newVarDB("loans", Address.class);
    public RebalanceMock(Address _loansAddress) {
        loans.set(_loansAddress);
    }

    @External
    public void raisePrice(BigInteger _total_tokens_required) {
        Context.call(loans.get(), "raisePrice", _total_tokens_required);
    }

    @External
    public void lowerPrice(BigInteger _total_tokens_required) {
        Context.call(loans.get(), "lowerPrice", _total_tokens_required);
    }
}