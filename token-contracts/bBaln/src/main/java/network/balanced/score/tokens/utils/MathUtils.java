package network.balanced.score.tokens.utils;

import java.math.BigInteger;

public class MathUtils {

    public static BigInteger pow(BigInteger num, int exponent) {
        BigInteger result = BigInteger.ONE;
        for (int i = 0; i < exponent; i++) {
            result = result.multiply(num);
        }
        return result;
    }

}
