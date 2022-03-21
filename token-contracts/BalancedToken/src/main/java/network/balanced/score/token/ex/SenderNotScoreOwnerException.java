package network.balanced.score.token.ex;

import score.Address;

public class SenderNotScoreOwnerException extends RuntimeException{

	private static final long serialVersionUID = 1L;
	private String message;
	public SenderNotScoreOwnerException(Address sender, Address owner) {
		this.message = "SenderNotScoreOwnerError: [ sender = "+ sender + " , owner = "+owner+" ]";
	}
	public String getMessage() {
		return message;
	}

}
