package network.balanced.score.token;

import java.math.BigInteger;

import network.balanced.score.token.util.Mathematics;

public interface Constants {

	final BigInteger ZERO = BigInteger.valueOf(0l);
	final BigInteger TEN = BigInteger.valueOf(10l);
	final BigInteger DEFAULT_DECIMAL_VALUE = new BigInteger("18");
	final BigInteger DEFAULT_INITIAL_SUPPLY = ZERO;
	final BigInteger DAY_TO_MICROSECOND = new BigInteger("86400").multiply(Mathematics.pow(TEN, 6));
	final BigInteger EXA = Mathematics.pow(TEN, 18);

	final BigInteger MAX_LOOP = new BigInteger("100");
	final BigInteger INITIAL_PRICE_ESTIMATE = Mathematics.pow(TEN, 17); //# loop
	final BigInteger MIN_UPDATE_TIME = new BigInteger("2000000"); //# seconds
	final BigInteger MINIMUM_STAKE_AMOUNT = Mathematics.pow(TEN, 18);
	final BigInteger DEFAULT_UNSTAKING_PERIOD = new BigInteger("3").multiply(DAY_TO_MICROSECOND);

	final String IDS = "ids";
	final String AMOUNT = "amount";

}
