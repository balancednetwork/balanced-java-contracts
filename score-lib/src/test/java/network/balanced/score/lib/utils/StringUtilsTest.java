package network.balanced.score.lib.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import score.UserRevertException;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StringUtilsTest {

    @BeforeAll
    public static void setup() throws Exception {
    }

    @Test
    public void testConvertStringToBigInteger(){
       assertEquals(BigInteger.valueOf(19), StringUtils.convertStringToBigInteger("19"));
       assertEquals(BigInteger.valueOf(18), StringUtils.convertStringToBigInteger("0x12"));
       assertEquals(BigInteger.valueOf(-18), StringUtils.convertStringToBigInteger("-0x12"));
    }

    @Test
    public void testConvertStringToBigIntegerThrowsException(){
        UserRevertException e = Assertions.assertThrows(UserRevertException.class, () -> {
            StringUtils.convertStringToBigInteger("abc");
        });
        assertEquals(e.getMessage(), "Invalid numeric value: " + "abc");

        e = Assertions.assertThrows(UserRevertException.class, () -> {
            StringUtils.convertStringToBigInteger("0xa+bc");
        });
        assertEquals(e.getMessage(), "Invalid numeric value: " + "0xa+bc");
    }
}
