package network.balanced.score.token.dex;

import java.math.BigInteger;

import score.Address;

public class StakedBalnTokenSnapshots {
	private Address address;
	private BigInteger amount;
	private BigInteger day;
	public Address getAddress() {
		return address;
	}
	public void setAddress(Address address) {
		this.address = address;
	}
	public BigInteger getAmount() {
		return amount;
	}
	public void setAmount(BigInteger amount) {
		this.amount = amount;
	}
	public BigInteger getDay() {
		return day;
	}
	public void setDay(BigInteger day) {
		this.day = day;
	}

}
