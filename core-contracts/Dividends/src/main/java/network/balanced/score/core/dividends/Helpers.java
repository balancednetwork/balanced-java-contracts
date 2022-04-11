package network.balanced.score.core.dividends;

import score.ArrayDB;

import java.math.BigInteger;

public class Helpers {

    public static boolean removeFromArrarDb(String item, ArrayDB<String> _array) {
        int length = _array.size();
        if (length < 1) {
            return false;
        }
        String top = _array.get(_array.size() - 1);
        for (int i = 0; i < length; i++) {
            if (_array.get(i).equals(item)) {
                _array.set(i, top);
                _array.removeLast();
                return true;
            }
        }

        return false;
    }

    public static BigInteger pow10(int exponent) {
        BigInteger result = BigInteger.ONE;
        for (int i = 0; i < exponent; i++) {
            result = result.multiply(BigInteger.TEN);
        }

        return result;
    }
}
