package network.balanced.score.core.rebalancing;

import java.math.BigInteger;

public class Constants {
    private static BigInteger pow(BigInteger base, int exponent){
        BigInteger res = BigInteger.ONE;

        for(int i = 1; i <= exponent; i++){
            res = res.multiply(base);
        }

        return res;
    }

    public final static BigInteger EXA = pow(BigInteger.TEN, 18);

}
