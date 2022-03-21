package network.balanced.score.token.util;

import java.math.BigInteger;

public final class Mathematics {

	public static BigInteger pow(BigInteger base, int exponent) {
		BigInteger result = BigInteger.ONE;
		for (int i = 0; i < exponent; i++) {
			result = result.multiply(base);
		}
		return result;
	}

}
