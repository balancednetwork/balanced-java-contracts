package network.balanced.score.tokens.utils;

import score.Context;

import java.math.BigInteger;

public class MathUtils {

    public static BigInteger safeSubtract(BigInteger from, BigInteger value) {
        BigInteger result = from.subtract(value);
        if (result.signum() == -1) {
            Context.revert(ErrorCodes.InvalidOperation.getCode(),
                    "MathUtils.SafeSubtract :: Invalid operation");
        }
        return result;
    }
}
